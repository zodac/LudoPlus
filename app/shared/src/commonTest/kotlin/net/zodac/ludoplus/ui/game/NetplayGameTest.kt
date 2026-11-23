package net.zodac.ludoplus.ui.game

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.game.SavedGameJson
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.RollOff
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.netplay.Discovery
import net.zodac.ludoplus.netplay.FoundGame
import net.zodac.ludoplus.netplay.GuestState
import net.zodac.ludoplus.netplay.Hosting
import net.zodac.ludoplus.netplay.Joining
import net.zodac.ludoplus.netplay.LinkEvent
import net.zodac.ludoplus.netplay.Nearby
import net.zodac.ludoplus.netplay.NetCodec
import net.zodac.ludoplus.netplay.NetMessage
import net.zodac.ludoplus.netplay.PROTOCOL_VERSION
import net.zodac.ludoplus.netplay.RefusedReason
import net.zodac.ludoplus.netplay.SeatKind

/** A Nearby that links nothing: what's sent, kept; each side's events, delivered by hand. */
internal class FakeNearby : Nearby {
    override val available = true
    var hostEvents: ((LinkEvent) -> Unit)? = null
    var joinEvents: ((LinkEvent) -> Unit)? = null
    val hostSent = mutableListOf<Pair<String, NetMessage>>()
    val guestSent = mutableListOf<NetMessage>()
    var findable = true
    var found: ((List<FoundGame>) -> Unit)? = null
    val joined = mutableListOf<FoundGame>()

    override suspend fun requestPermissions() = true

    override fun host(name: String, events: (LinkEvent) -> Unit): Hosting {
        hostEvents = events
        return object : Hosting {
            override fun send(endpoint: String, bytes: ByteArray) {
                hostSent += endpoint to checkNotNull(NetCodec.decode(bytes))
            }

            override fun setFindable(findable: Boolean) {
                this@FakeNearby.findable = findable
            }

            override fun close() = Unit
        }
    }

    override fun discover(found: (List<FoundGame>) -> Unit): Discovery {
        this.found = found
        return object : Discovery {
            override fun stop() {
                this@FakeNearby.found = null
            }
        }
    }

    override fun join(game: FoundGame, name: String, events: (LinkEvent) -> Unit): Joining {
        joinEvents = events
        joined += game
        return object : Joining {
            override fun send(bytes: ByteArray) {
                guestSent += checkNotNull(NetCodec.decode(bytes))
            }

            override fun close() = Unit
        }
    }

    fun fromGuest(endpoint: String, message: NetMessage) = checkNotNull(hostEvents)(LinkEvent.Received(endpoint, NetCodec.encode(message)))

    fun fromHost(message: NetMessage) = checkNotNull(joinEvents)(LinkEvent.Received("host", NetCodec.encode(message)))
}

/** A game across phones, through the view model: the host's lobby and game, and a guest's. */
@OptIn(ExperimentalCoroutinesApi::class)
class NetplayGameTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(records: GameRecordRepository = GameRecordRepository(InMemoryGameResultDao()), seed: Int = 1): GameViewModel =
        GameViewModel(
            SettingsRepository(InMemoryPreferences()),
            InProgressGameRepository(InMemoryPreferences(), backgroundScope),
            records,
            Random(seed),
            elapsedMillis = { 0L },
            now = { 1_000L },
        ).also {
            it.setPacing(GamePacing(thinkMillis = 700, pawnsAnimated = false, dieAnimated = false))
            runCurrent()
        }

    /** A host with seat 2 a Nearby seat, Jo's phone in it, and the game started. */
    private fun TestScope.hostWithGuest(nearby: FakeNearby, seed: Int = 1): GameViewModel {
        val host = viewModel(seed = seed)
        host.setupForm.setPlayerCount(2)
        host.setupForm.setPlayerType(2, PlayerType.NEARBY)
        assertTrue(host.setupHasNearbySeats)
        host.openLobby(nearby, "Sam") { "Player $it" }
        runCurrent()
        assertEquals(SeatKind.NEARBY, host.lobby.value?.get(1)?.kind)
        assertFalse(host.startHostedGame { "Player $it" })
        checkNotNull(nearby.hostEvents)(LinkEvent.Connected("jo"))
        nearby.fromGuest("jo", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null))
        runCurrent()
        assertEquals("Jo", host.lobby.value?.get(1)?.name)
        assertTrue(host.startHostedGame { "Player $it" })
        runCurrent()
        return host
    }

    @Test
    fun `the host starts with the guest in its seat - sends it the game - and takes its roll only on its turn`() = runTest(dispatcher) {
        val nearby = FakeNearby()
        // A seed whose roll-off puts Jo, seat 2, first.
        val seed = generateSequence(0) { it + 1 }.first { RollOff.roll(2, 6, Random(it)).first == 1 }
        val host = hostWithGuest(nearby, seed)
        val game = assertNotNull(host.game.value)
        assertEquals(PlayerType.NEARBY, game.players[1].type)
        assertEquals("Jo", game.players[1].name)
        assertEquals(1, game.state.current)
        assertTrue(game.currentIsCpu)
        assertIs<NetMessage.Game>(nearby.hostSent.last().second)

        // Sam's tap is no roll of Jo's.
        host.roll()
        assertEquals(TurnPhase.AwaitingRoll, host.game.value?.state?.phase)
        nearby.fromGuest("jo", NetMessage.Roll)
        runCurrent()
        assertTrue(host.game.value!!.state.lastEvent != null)
        assertEquals(SavedGameJson.encode(host.game.value!!, withLog = false), (nearby.hostSent.last().second as NetMessage.Game).json)
    }

    /** A seed whose roll-off puts Jo, seat 2, first. */
    private val joFirst = generateSequence(0) { it + 1 }.first { RollOff.roll(2, 6, Random(it)).first == 1 }

    /** A seed whose roll-off puts Jo first, who then rolls a 6: a pawn to bring out. */
    private val joFirstWithASix = generateSequence(0) { it + 1 }.first { seed ->
        val random = Random(seed)
        RollOff.roll(2, 6, random).first == 1 && random.nextInt(1, 7) == 6
    }

    @Test
    fun `a guest that leaves a game under way is played by the CPU`() = runTest(dispatcher) {
        val nearby = FakeNearby()
        val host = hostWithGuest(nearby)
        nearby.fromGuest("jo", NetMessage.Leave)
        runCurrent()
        assertEquals(PlayerType.AI, host.game.value?.players?.get(1)?.type)
        assertTrue(host.awaySeats.value.isEmpty())
    }

    @Test
    fun `a guest whose phone drops out is played by the CPU until it's back - the host findable meanwhile`() = runTest(dispatcher) {
        val nearby = FakeNearby()
        val host = hostWithGuest(nearby, joFirst)
        assertFalse(nearby.findable)
        checkNotNull(nearby.hostEvents)(LinkEvent.Disconnected("jo"))
        runCurrent()
        assertEquals(setOf(1), host.awaySeats.value)
        assertEquals(PlayerType.NEARBY, host.game.value?.players?.get(1)?.type)
        assertTrue(nearby.findable)
        // The CPU rolls for Jo, after its usual wait.
        advanceTimeBy(1_000)
        runCurrent()
        assertNotNull(host.game.value?.state?.lastEvent)

        val token = nearby.hostSent.first { it.first == "jo" && it.second is NetMessage.Welcome }.second.let { (it as NetMessage.Welcome).token }
        checkNotNull(nearby.hostEvents)(LinkEvent.Connected("jo-again"))
        nearby.fromGuest("jo-again", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null, token))
        runCurrent()
        assertTrue(host.awaySeats.value.isEmpty())
        assertFalse(nearby.findable)
        assertEquals(SavedGameJson.encode(host.game.value!!, withLog = false), (nearby.hostSent.last { it.first == "jo-again" }.second as NetMessage.Game).json)
    }

    @Test
    fun `a guest that lets its turn run out has the rest of it played by the CPU`() = runTest(dispatcher) {
        val nearby = FakeNearby()
        val host = hostWithGuest(nearby, joFirst)
        advanceTimeBy(GameViewModel.GUEST_TURN_MILLIS - 1)
        runCurrent()
        assertNull(host.game.value?.state?.lastEvent)
        // Out of time: the CPU plays the rest of Jo's turn, after its usual wait.
        advanceTimeBy(1_000)
        runCurrent()
        val after = assertNotNull(host.game.value)
        assertNotNull(after.state.lastEvent)
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, host.game.value?.state?.current)

        // Sam's turn, at this phone: never timed.
        advanceTimeBy(GameViewModel.GUEST_TURN_MILLIS * 2)
        runCurrent()
        assertEquals(0, host.game.value?.state?.current)
    }

    @Test
    fun `a guest that plays once its time is out takes its turn back from the CPU`() = runTest(dispatcher) {
        val nearby = FakeNearby()
        val host = hostWithGuest(nearby, joFirstWithASix)
        advanceTimeBy(GameViewModel.GUEST_TURN_MILLIS)
        runCurrent()
        // The CPU is about to roll for Jo, but Jo rolls first: a 6.
        nearby.fromGuest("jo", NetMessage.Roll)
        runCurrent()
        val rolled = assertNotNull(host.game.value).state
        assertIs<TurnPhase.AwaitingMove>(rolled.phase)
        assertEquals(1, rolled.current)
        // And the move is Jo's to make, in Jo's own time.
        advanceTimeBy(GameViewModel.GUEST_TURN_MILLIS - 1)
        runCurrent()
        assertEquals(rolled, host.game.value?.state)
    }

    @Test
    fun `a guest shows the host's game at its own seat - sends its roll on its turn - and records only its own seat`() = runTest(dispatcher) {
        val nearby = FakeNearby()
        val dao = InMemoryGameResultDao()
        val guest = viewModel(GameRecordRepository(dao))
        guest.join(nearby, FoundGame("host", "Sam"), "Jo", null)
        checkNotNull(nearby.joinEvents)(LinkEvent.Connected("host"))
        assertEquals(NetMessage.Hello(PROTOCOL_VERSION, "Jo", null), nearby.guestSent.single())
        nearby.fromHost(NetMessage.Welcome(1, "t1"))
        runCurrent()
        assertIs<GuestState.Seated>(guest.guestState.value)

        val players = listOf(PlayerConfig(1, PlayerType.HUMAN, "Sam"), PlayerConfig(2, PlayerType.NEARBY, "Jo"))
        val start = StartedGame(GameMode.CLASSIC, players, GameState.new(GameMode.CLASSIC, 2, first = 1), RollOff(emptyList(), 1), id = "a".repeat(32))
        nearby.fromHost(NetMessage.Game(1, SavedGameJson.encode(start)))
        runCurrent()
        assertEquals(1, guest.game.value?.guestSeat)
        assertFalse(guest.game.value!!.currentIsCpu)
        guest.roll()
        assertEquals(NetMessage.Roll, nearby.guestSent.last())
        // Nothing rolled here: the host rolls.
        assertNull(guest.game.value?.state?.lastEvent)

        // The host's game, finished: Jo's seat recorded here as this phone's own, Sam's not.
        val home = start.state.board.homeProgress
        val finished = start.copy(
            state = start.state.copy(phase = TurnPhase.Finished, finishingOrder = listOf(1, 0), players = start.state.players.map { it.copy(pawns = List(4) { home }) }),
            playMillis = 60_000,
            finishMillis = mapOf(1 to 50_000L, 0 to 60_000L),
        )
        nearby.fromHost(NetMessage.Game(2, SavedGameJson.encode(finished)))
        runCurrent()
        val rows = dao.allByPlayer()
        assertEquals(listOf("Jo"), rows.map { it.playerName })
        assertTrue(rows.single().isPrimaryPlayer)
    }

    @Test
    fun `a guest that loses the host in a game finds it again - gets its seat back - and plays on - or gives up in time`() = runTest(dispatcher) {
        val nearby = FakeNearby()
        val guest = viewModel()
        guest.join(nearby, FoundGame("host", "Sam"), "Jo", null)
        checkNotNull(nearby.joinEvents)(LinkEvent.Connected("host"))
        nearby.fromHost(NetMessage.Welcome(1, "t1"))
        val players = listOf(PlayerConfig(1, PlayerType.HUMAN, "Sam"), PlayerConfig(2, PlayerType.NEARBY, "Jo"))
        val start = StartedGame(GameMode.CLASSIC, players, GameState.new(GameMode.CLASSIC, 2, first = 0), RollOff(emptyList(), 0), id = "a".repeat(32))
        nearby.fromHost(NetMessage.Game(1, SavedGameJson.encode(start)))
        runCurrent()

        checkNotNull(nearby.joinEvents)(LinkEvent.Disconnected("host"))
        runCurrent()
        assertIs<GuestState.Reconnecting>(guest.guestState.value)
        // The board stays up meanwhile.
        assertEquals(1, guest.game.value?.guestSeat)
        // Another game, then the host's, under a new id.
        checkNotNull(nearby.found)(listOf(FoundGame("other", "Ada")))
        checkNotNull(nearby.found)(listOf(FoundGame("other", "Ada"), FoundGame("host2", "Sam")))
        assertEquals(FoundGame("host2", "Sam"), nearby.joined.last())
        checkNotNull(nearby.joinEvents)(LinkEvent.Connected("host2"))
        assertEquals(NetMessage.Hello(PROTOCOL_VERSION, "Jo", null, token = "t1"), nearby.guestSent.last())
        nearby.fromHost(NetMessage.Welcome(1, "t1"))
        nearby.fromHost(NetMessage.Game(2, SavedGameJson.encode(start.copy(state = start.state.copy(current = 1)))))
        runCurrent()
        assertIs<GuestState.Playing>(guest.guestState.value)
        assertNull(nearby.found)
        guest.roll()
        assertEquals(NetMessage.Roll, nearby.guestSent.last())

        // Lost again, and the host not found in time.
        checkNotNull(nearby.joinEvents)(LinkEvent.Disconnected("host2"))
        runCurrent()
        assertIs<GuestState.Reconnecting>(guest.guestState.value)
        advanceTimeBy(GameViewModel.RECONNECT_MILLIS + 1)
        runCurrent()
        assertEquals(GuestState.Lost, guest.guestState.value)
        assertNull(nearby.found)
    }

    @Test
    fun `a guest is warned before its turn runs out - and only then`() = runTest(dispatcher) {
        val nearby = FakeNearby()
        hostWithGuest(nearby, joFirst)
        advanceTimeBy(GameViewModel.GUEST_TURN_MILLIS - GameViewModel.TURN_WARNING_MILLIS - 1)
        runCurrent()
        assertTrue(nearby.hostSent.none { it.second == NetMessage.TurnEnding })
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("jo"), nearby.hostSent.filter { it.second == NetMessage.TurnEnding }.map { it.first })
    }

    @Test
    fun `the host's Play Again keeps each guest in its seat - the CPU in a seat whose guest left - and sends the new game`() = runTest(dispatcher) {
        val nearby = FakeNearby()
        val host = hostWithGuest(nearby)
        val first = assertNotNull(host.game.value)
        nearby.hostSent.clear()
        assertTrue(host.playAgain { "Player $it" })
        runCurrent()
        val again = assertNotNull(host.game.value)
        assertTrue(again.id != first.id)
        assertEquals(PlayerType.NEARBY, again.players[1].type)
        assertEquals("Jo", again.players[1].name)
        assertEquals(SavedGameJson.encode(again, withLog = false), (nearby.hostSent.first { it.first == "jo" }.second as NetMessage.Game).json)

        nearby.fromGuest("jo", NetMessage.Leave)
        runCurrent()
        assertTrue(host.playAgain { "Player $it" })
        runCurrent()
        val third = assertNotNull(host.game.value)
        assertEquals(PlayerType.AI, third.players[1].type)
        assertEquals("Jo", third.players[1].name)
    }

    @Test
    fun `a guest can't play again itself - and shows the host's next game afresh`() = runTest(dispatcher) {
        val nearby = FakeNearby()
        val guest = viewModel()
        guest.join(nearby, FoundGame("host", "Sam"), "Jo", null)
        checkNotNull(nearby.joinEvents)(LinkEvent.Connected("host"))
        nearby.fromHost(NetMessage.Welcome(1, "t1"))
        val players = listOf(PlayerConfig(1, PlayerType.HUMAN, "Sam"), PlayerConfig(2, PlayerType.NEARBY, "Jo"))
        val start = StartedGame(GameMode.CLASSIC, players, GameState.new(GameMode.CLASSIC, 2, first = 0), RollOff(emptyList(), 0), id = "a".repeat(32))
        nearby.fromHost(NetMessage.Game(1, SavedGameJson.encode(start)))
        runCurrent()
        nearby.fromHost(NetMessage.Game(2, SavedGameJson.encode(start.copy(state = start.state.copy(current = 1)))))
        runCurrent()
        assertEquals(1, guest.game.value?.steps)

        assertFalse(guest.playAgain { "Player $it" })
        assertEquals("a".repeat(32), guest.game.value?.id)

        val next = start.copy(id = "b".repeat(32), startedAtEpochMillis = 2_000L)
        nearby.fromHost(NetMessage.Game(3, SavedGameJson.encode(next)))
        runCurrent()
        assertEquals("b".repeat(32), guest.game.value?.id)
        assertEquals(0, guest.game.value?.steps)
        assertEquals(1, guest.game.value?.guestSeat)
    }

    @Test
    fun `a guest reconnecting to another host of the same name is refused - and tries the next one`() = runTest(dispatcher) {
        val nearby = FakeNearby()
        val guest = viewModel()
        guest.join(nearby, FoundGame("host", "Sam"), "Jo", null)
        checkNotNull(nearby.joinEvents)(LinkEvent.Connected("host"))
        nearby.fromHost(NetMessage.Welcome(1, "t1"))
        val players = listOf(PlayerConfig(1, PlayerType.HUMAN, "Sam"), PlayerConfig(2, PlayerType.NEARBY, "Jo"))
        val start = StartedGame(GameMode.CLASSIC, players, GameState.new(GameMode.CLASSIC, 2, first = 0), RollOff(emptyList(), 0), id = "a".repeat(32))
        nearby.fromHost(NetMessage.Game(1, SavedGameJson.encode(start)))
        runCurrent()
        checkNotNull(nearby.joinEvents)(LinkEvent.Disconnected("host"))
        runCurrent()

        val sams = listOf(FoundGame("other-sam", "Sam"), FoundGame("host2", "Sam"))
        checkNotNull(nearby.found)(sams)
        assertEquals(FoundGame("other-sam", "Sam"), nearby.joined.last())
        checkNotNull(nearby.joinEvents)(LinkEvent.Connected("other-sam"))
        nearby.fromHost(NetMessage.Refused(RefusedReason.STARTED))
        runCurrent()
        assertIs<GuestState.Reconnecting>(guest.guestState.value)
        assertEquals(FoundGame("host2", "Sam"), nearby.joined.last())
        // The search finding both again doesn't send it back to the one that refused it.
        checkNotNull(nearby.found)(sams)
        assertEquals(FoundGame("host2", "Sam"), nearby.joined.last())

        checkNotNull(nearby.joinEvents)(LinkEvent.Connected("host2"))
        assertEquals(NetMessage.Hello(PROTOCOL_VERSION, "Jo", null, token = "t1"), nearby.guestSent.last())
        nearby.fromHost(NetMessage.Welcome(1, "t1"))
        nearby.fromHost(NetMessage.Game(2, SavedGameJson.encode(start)))
        runCurrent()
        assertIs<GuestState.Playing>(guest.guestState.value)
    }
}
