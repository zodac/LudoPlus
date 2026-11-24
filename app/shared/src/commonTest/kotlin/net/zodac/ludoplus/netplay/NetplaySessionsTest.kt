package net.zodac.ludoplus.netplay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.data.game.SavedGameJson
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.ui.game.StartedGame

private val TABLE = listOf(
    LobbySeat(SeatKind.HOST, "Sam", PlayerColour.RED),
    LobbySeat(SeatKind.NEARBY, null, PlayerColour.GREEN),
    LobbySeat(SeatKind.CPU, "Ada", PlayerColour.YELLOW),
    LobbySeat(SeatKind.NEARBY, null, PlayerColour.BLUE),
)

private val GAME = StartedGame(
    mode = GameMode.CLASSIC,
    players = listOf(
        PlayerConfig(1, PlayerType.HUMAN, "Sam"),
        PlayerConfig(2, PlayerType.HUMAN, "Jo"),
        PlayerConfig(3, PlayerType.AI, "Ada"),
        PlayerConfig(4, PlayerType.HUMAN, "Alex"),
    ),
    state = GameState.new(GameMode.CLASSIC, 4, first = 1),
    first = 1,
)

/** The netplay protocol and its two sessions, over no radio: what each side sends, and what it makes of the other's. */
@OptIn(ExperimentalCoroutinesApi::class)
class NetplaySessionsTest {

    /** A host over a fake link: each message sent, by endpoint. */
    private class Host(seats: List<LobbySeat> = TABLE) {
        val sent = mutableListOf<Pair<String, NetMessage>>()
        private var tokens = 0
        val session = HostSession(seats, newToken = { "t${++tokens}" }) { endpoint, message -> sent += endpoint to message }

        fun sentTo(endpoint: String) = sent.filter { it.first == endpoint }.map { it.second }
    }

    @Test
    fun `every message survives the codec - one it can't read is none`() {
        val messages = listOf(
            NetMessage.Hello(PROTOCOL_VERSION, "Jo", PlayerColour.BLUE),
            NetMessage.Hello(PROTOCOL_VERSION, "Jo", null),
            NetMessage.Hello(PROTOCOL_VERSION, "Jo", PlayerColour.GREEN, token = "t1"),
            NetMessage.Roll,
            NetMessage.Move(2, owner = 3, die = 5),
            NetMessage.Move(0, owner = null, die = null),
            NetMessage.Move(0, owner = null, die = null, swap = PawnRef(2, 1)),
            NetMessage.Leave,
            NetMessage.Welcome(3, "t1"),
            NetMessage.Refused(RefusedReason.FULL),
            NetMessage.Lobby(TABLE),
            NetMessage.Game(7, "{\"x\":1}"),
            NetMessage.Ended,
            NetMessage.TurnEnding,
        )
        for (message in messages) assertEquals(message, NetCodec.decode(NetCodec.encode(message)))
        assertNull(NetCodec.decode("not json".encodeToByteArray()))
        assertNull(NetCodec.decode("{\"type\":\"teleport\"}".encodeToByteArray()))
        assertNull(NetCodec.decode("{\"type\":\"move\"}".encodeToByteArray()))
    }

    @Test
    fun `guests take the open seats in order - named - in a free colour they asked for - and everyone hears the table`() {
        val host = Host()
        host.session.handle("jo", NetMessage.Hello(PROTOCOL_VERSION, "  Jo ", PlayerColour.YELLOW))
        host.session.handle("alex", NetMessage.Hello(PROTOCOL_VERSION, "Alex", PlayerColour.RED))
        assertEquals(NetMessage.Welcome(1, "t1"), host.sentTo("jo").first())
        assertEquals(NetMessage.Welcome(3, "t2"), host.sentTo("alex").first())
        // Yellow is Ada's, red Sam's: each guest keeps its seat's colour.
        assertEquals(LobbySeat(SeatKind.NEARBY, "Jo", PlayerColour.GREEN), host.session.seats.value[1])
        assertEquals(LobbySeat(SeatKind.NEARBY, "Alex", PlayerColour.BLUE), host.session.seats.value[3])
        assertEquals(NetMessage.Lobby(host.session.seats.value), host.sentTo("jo").last())
        assertTrue(host.session.full)
        assertEquals(setOf(1, 3), host.session.guestSeats)
    }

    @Test
    fun `a full table - a started game - or another protocol is refused`() {
        val host = Host(TABLE.take(2))
        host.session.handle("x", NetMessage.Hello(PROTOCOL_VERSION + 1, "X", null))
        assertEquals(listOf<NetMessage>(NetMessage.Refused(RefusedReason.VERSION)), host.sentTo("x"))
        host.session.handle("jo", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null))
        host.session.handle("alex", NetMessage.Hello(PROTOCOL_VERSION, "Alex", null))
        assertEquals(listOf<NetMessage>(NetMessage.Refused(RefusedReason.FULL)), host.sentTo("alex"))
        assertTrue(host.session.start())
        host.session.handle("late", NetMessage.Hello(PROTOCOL_VERSION, "Late", null))
        assertEquals(listOf<NetMessage>(NetMessage.Refused(RefusedReason.STARTED)), host.sentTo("late"))
    }

    @Test
    fun `the game can't start with a seat still open - and a guest leaving the lobby opens its seat again`() {
        val host = Host()
        host.session.handle("jo", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null))
        assertFalse(host.session.start())
        host.session.handle("jo", NetMessage.Leave)
        assertTrue(host.session.seats.value[1].open)
        assertEquals(emptySet(), host.session.guestSeats)
    }

    @Test
    fun `once started a guest's roll and move are passed on from its seat - a stranger's aren't - and leaving hands the seat to the CPU`() = runTest {
        val host = Host(TABLE.take(2))
        val actions = mutableListOf<GuestAction>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { host.session.actions.toList(actions) }
        host.session.handle("jo", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null))
        host.session.handle("jo", NetMessage.Roll)
        assertEquals(emptyList(), actions)
        host.session.start()
        host.session.handle("jo", NetMessage.Roll)
        host.session.handle("jo", NetMessage.Move(2, null, 6))
        host.session.handle("stranger", NetMessage.Roll)
        host.session.handle("jo", NetMessage.Leave)
        assertEquals(listOf(GuestAction.Roll(1), GuestAction.Move(1, 2, null, 6), GuestAction.Left(1)), actions)
        // Gone for good: its token takes nothing back.
        host.session.handle("jo2", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null, token = "t1"))
        assertEquals(listOf<NetMessage>(NetMessage.Refused(RefusedReason.STARTED)), host.sentTo("jo2"))
    }

    @Test
    fun `a guest whose link drops in the game is away - and comes back to its seat with its token - over a new link - to the latest game`() = runTest {
        val host = Host(TABLE.take(2))
        val actions = mutableListOf<GuestAction>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { host.session.actions.toList(actions) }
        host.session.handle("jo", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null))
        host.session.start()
        host.session.gameChanged(GAME)
        host.session.disconnected("jo")
        assertEquals(setOf(1), host.session.away.value)
        assertEquals(emptySet(), host.session.guestSeats)

        // Someone else's token, or none, gets no seat.
        host.session.handle("x", NetMessage.Hello(PROTOCOL_VERSION, "X", null, token = "nope"))
        host.session.handle("y", NetMessage.Hello(PROTOCOL_VERSION, "Y", null))
        assertEquals(listOf<NetMessage>(NetMessage.Refused(RefusedReason.STARTED)), host.sentTo("x"))
        assertEquals(listOf<NetMessage>(NetMessage.Refused(RefusedReason.STARTED)), host.sentTo("y"))

        host.session.gameChanged(GAME)
        host.session.handle("jo-again", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null, token = "t1"))
        assertEquals(NetMessage.Welcome(1, "t1"), host.sentTo("jo-again").first())
        assertEquals(2, (host.sentTo("jo-again").last() as NetMessage.Game).number)
        assertEquals(emptySet(), host.session.away.value)
        assertEquals(setOf(1), host.session.guestSeats)
        assertEquals(listOf(GuestAction.Away(1), GuestAction.Back(1)), actions)
    }

    @Test
    fun `a guest coming back before its old link is seen to drop takes the seat from it`() {
        val host = Host(TABLE.take(2))
        host.session.handle("jo", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null))
        host.session.start()
        host.session.handle("jo-again", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null, token = "t1"))
        host.session.gameChanged(GAME)
        assertTrue(host.sentTo("jo").none { it is NetMessage.Game })
        assertIs<NetMessage.Game>(host.sentTo("jo-again").last())
        // The old link's drop, seen late, changes nothing.
        host.session.disconnected("jo")
        assertEquals(emptySet(), host.session.away.value)
    }

    @Test
    fun `each game the host sends is numbered - and ending tells every guest`() {
        val host = Host()
        host.session.handle("jo", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null))
        host.session.gameChanged(GAME)
        host.session.gameChanged(GAME)
        val games = host.sentTo("jo").filterIsInstance<NetMessage.Game>()
        assertEquals(listOf(1, 2), games.map { it.number })
        assertEquals(GAME, SavedGameJson.decode(games.last().json))
        host.session.end()
        assertEquals(NetMessage.Ended, host.sentTo("jo").last())
    }

    @Test
    fun `a guest says hello - is seated - plays the latest game it's sent - and ignores one arriving late`() {
        val sent = mutableListOf<NetMessage>()
        val guest = GuestSession { sent += it }
        guest.hello("Jo", PlayerColour.GREEN)
        assertEquals(listOf<NetMessage>(NetMessage.Hello(PROTOCOL_VERSION, "Jo", PlayerColour.GREEN)), sent)
        assertEquals(GuestState.Joining, guest.state.value)
        guest.handle(NetMessage.Welcome(1, "t1"))
        guest.handle(NetMessage.Lobby(TABLE))
        assertEquals(GuestState.Seated(1, TABLE), guest.state.value)

        val later = GAME.copy(state = LudoEngine.applyRoll(GAME.state, 4))
        guest.handle(NetMessage.Game(2, SavedGameJson.encode(later)))
        guest.handle(NetMessage.Game(1, SavedGameJson.encode(GAME)))
        assertEquals(GuestState.Playing(1, later), guest.state.value)
    }

    @Test
    fun `a guest sends its roll and move only on its own turn`() {
        val sent = mutableListOf<NetMessage>()
        val guest = GuestSession { sent += it }
        guest.handle(NetMessage.Welcome(1, "t1"))
        // Seat 1 is to roll.
        guest.handle(NetMessage.Game(1, SavedGameJson.encode(GAME)))
        guest.move(0)
        guest.roll()
        assertEquals(listOf<NetMessage>(NetMessage.Roll), sent)

        sent.clear()
        guest.handle(NetMessage.Game(2, SavedGameJson.encode(GAME.copy(state = GAME.state.copy(current = 0)))))
        guest.roll()
        assertEquals(emptyList(), sent)
    }

    @Test
    fun `refused - ended - or lost - and a lost link after the end changes nothing`() {
        val guest = GuestSession {}
        guest.handle(NetMessage.Refused(RefusedReason.STARTED))
        assertEquals(GuestState.Refused(RefusedReason.STARTED), guest.state.value)
        val other = GuestSession {}
        other.handle(NetMessage.Welcome(1, "t1"))
        other.lost()
        assertIs<GuestState.Lost>(other.state.value)
        val ended = GuestSession {}
        ended.handle(NetMessage.Ended)
        ended.lost()
        assertEquals(GuestState.Ended, ended.state.value)
    }

    @Test
    fun `a guest that loses the host in a game reconnects - asks for its seat back with its token - and plays on from the game it's sent`() {
        val sent = mutableListOf<NetMessage>()
        val guest = GuestSession { sent += it }
        guest.hello("Jo", PlayerColour.GREEN)
        guest.handle(NetMessage.Welcome(1, "t1"))
        guest.handle(NetMessage.Game(1, SavedGameJson.encode(GAME)))
        guest.lost()
        assertEquals(GuestState.Reconnecting(1, GAME), guest.state.value)
        // Nothing reaches the host meanwhile.
        sent.clear()
        guest.roll()
        assertEquals(emptyList(), sent)

        guest.rejoin()
        assertEquals(listOf<NetMessage>(NetMessage.Hello(PROTOCOL_VERSION, "Jo", PlayerColour.GREEN, token = "t1")), sent)
        guest.handle(NetMessage.Welcome(1, "t1"))
        assertIs<GuestState.Reconnecting>(guest.state.value)
        val later = GAME.copy(state = LudoEngine.applyRoll(GAME.state, 4))
        guest.handle(NetMessage.Game(2, SavedGameJson.encode(later)))
        assertEquals(GuestState.Playing(1, later), guest.state.value)
    }

    @Test
    fun `a guest stops reconnecting when it gives up - and a link lost before the game is lost for good`() {
        val playing = { GuestSession {}.apply { handle(NetMessage.Welcome(1, "t1")); handle(NetMessage.Game(1, SavedGameJson.encode(GAME))); lost() } }
        val givenUp = playing()
        givenUp.giveUp()
        assertEquals(GuestState.Lost, givenUp.state.value)
        val seated = GuestSession {}
        seated.handle(NetMessage.Welcome(1, "t1"))
        seated.lost()
        assertEquals(GuestState.Lost, seated.state.value)
    }

    @Test
    fun `a host refuses a guest coming back to a seat it never gave - in the lobby or the game`() {
        val host = Host()
        host.session.handle("x", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null, token = "theirs"))
        assertEquals(listOf<NetMessage>(NetMessage.Refused(RefusedReason.STARTED)), host.sentTo("x"))
        assertNull(host.session.seats.value[1].name)
        host.session.handle("jo", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null))
        host.session.handle("alex", NetMessage.Hello(PROTOCOL_VERSION, "Alex", null))
        host.session.start()
        host.session.handle("y", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null, token = "theirs"))
        assertEquals(listOf<NetMessage>(NetMessage.Refused(RefusedReason.STARTED)), host.sentTo("y"))
    }

    @Test
    fun `a host tells only the seat's own guest that its turn is nearly up`() {
        val host = Host()
        host.session.handle("jo", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null))
        host.session.handle("alex", NetMessage.Hello(PROTOCOL_VERSION, "Alex", null))
        host.session.start()
        host.sent.clear()
        host.session.turnEnding(3)
        assertEquals(listOf<Pair<String, NetMessage>>("alex" to NetMessage.TurnEnding), host.sent)
    }

    @Test
    fun `a guest hears its turn is nearly up only on its own turn - until the next game it's sent`() {
        val guest = GuestSession {}
        guest.handle(NetMessage.Welcome(1, "t1"))
        // Sam's turn: not Jo's to hurry.
        guest.handle(NetMessage.Game(1, SavedGameJson.encode(GAME.copy(state = GAME.state.copy(current = 0)))))
        guest.handle(NetMessage.TurnEnding)
        assertEquals(false, (guest.state.value as GuestState.Playing).turnEnding)
        guest.handle(NetMessage.Game(2, SavedGameJson.encode(GAME)))
        guest.handle(NetMessage.TurnEnding)
        assertEquals(true, (guest.state.value as GuestState.Playing).turnEnding)
        val later = GAME.copy(state = LudoEngine.applyRoll(GAME.state, 4))
        guest.handle(NetMessage.Game(3, SavedGameJson.encode(later)))
        assertEquals(GuestState.Playing(1, later), guest.state.value)
    }

    @Test
    fun `a guest reconnecting to a host that won't have it back - or seats it afresh - stays reconnecting and says so`() {
        var refusals = 0
        val guest = GuestSession(onRejoinRefused = { refusals++ }) {}
        guest.handle(NetMessage.Welcome(1, "t1"))
        guest.handle(NetMessage.Game(1, SavedGameJson.encode(GAME)))
        guest.lost()
        guest.handle(NetMessage.Refused(RefusedReason.STARTED))
        assertEquals(1, refusals)
        assertIs<GuestState.Reconnecting>(guest.state.value)
        // An older host of the same name seats it in its lobby: another token, so not its own.
        guest.handle(NetMessage.Welcome(2, "other"))
        assertEquals(2, refusals)
        assertIs<GuestState.Reconnecting>(guest.state.value)
        // Its own host, at last.
        guest.handle(NetMessage.Welcome(1, "t1"))
        assertEquals(2, refusals)
        guest.handle(NetMessage.Game(2, SavedGameJson.encode(GAME)))
        assertEquals(GuestState.Playing(1, GAME), guest.state.value)
    }
}
