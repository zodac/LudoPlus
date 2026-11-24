package net.zodac.ludoplus.ui.game

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.data.records.playerStatistics
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.CustomMode
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS
import net.zodac.ludoplus.model.PlayerType

private const val Y = YARD_PROGRESS
private const val HOME = 56

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * A started game whose die comes from [seed]; seats 2 to 4 are CPUs unless [humans]. It saves to [saved],
     * and its play time is read from [clock].
     */
    private fun TestScope.started(
        seed: Int,
        humans: Boolean = false,
        saved: InProgressGameRepository = InProgressGameRepository(InMemoryPreferences(), backgroundScope),
        clock: () -> Long = { 0L },
        records: GameRecordRepository = GameRecordRepository(InMemoryGameResultDao()),
    ): GameViewModel {
        val viewModel = GameViewModel(SettingsRepository(InMemoryPreferences()), saved, records, Random(seed), elapsedMillis = clock, now = { START })
        // A 700ms think, and nothing animated to wait for.
        viewModel.setPacing(GamePacing(thinkMillis = 700, pawnsAnimated = false, dieAnimated = false))
        runCurrent()
        if (humans) (2..4).forEach { viewModel.setupForm.setPlayerType(it, PlayerType.HUMAN) }
        viewModel.startGame { "Player $it" }
        runCurrent()
        return viewModel
    }

    private val GameViewModel.state get() = assertNotNull(game.value).state

    /** A seed whose first roll isn't a 6: with every pawn in the yard, player 1's roll passes the turn ([passToCpu]). */
    private val noSixFirst = generateSequence(0) { it + 1 }.first { Random(it).nextInt(1, 7) != 6 }

    /** Player 1 rolls no 6 (the [noSixFirst] seed's roll), and the turn passes to player 2, a CPU. */
    private fun TestScope.passToCpu(viewModel: GameViewModel) {
        viewModel.roll()
        runCurrent()
        assertEquals(0, viewModel.state.lastEvent?.player)
        assertEquals(1, viewModel.state.current)
    }

    @Test
    fun `a new game starts with player 1 to roll - nothing rolled for them - every pawn in its yard`() = runTest(dispatcher) {
        // Every seat but player 1's a CPU: still player 1 starts, and waits for their own roll.
        val viewModel = started(0)
        val game = assertNotNull(viewModel.game.value)
        assertEquals(0, game.state.current)
        assertEquals(0, game.first)
        assertEquals(TurnPhase.AwaitingRoll, game.state.phase)
        assertNull(game.state.lastEvent)
        assertTrue(game.state.players.all { player -> player.pawns.all { it == YARD_PROGRESS } })
        assertEquals("Player 1", game.players[0].name)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(game.state, viewModel.state)
    }

    @Test
    fun `a person rolls and moves - a move that isn't legal is ignored`() = runTest(dispatcher) {
        val viewModel = started(0, humans = true)
        // Roll until someone has a move: with every pawn in the yard, only a 6 gives one.
        repeat(200) { if (viewModel.state.phase == TurnPhase.AwaitingRoll) viewModel.roll() }
        val phase = assertIs<TurnPhase.AwaitingMove>(viewModel.state.phase)
        val before = viewModel.state
        viewModel.move(pawn = 99)
        assertEquals(before, viewModel.state)
        viewModel.move(phase.moves.first().pawn)
        assertIs<GameEvent.Moved>(viewModel.state.lastEvent)
    }

    @Test
    fun `nobody rolls until the last action has played out - the die's tumble and a pawn's move - while it's on screen`() = runTest(dispatcher) {
        val viewModel = started(noSixFirst, humans = true)
        viewModel.setPacing(GamePacing(thinkMillis = 700))
        assertTrue(viewModel.settled.value)
        // Player 1 rolls no 6, and the turn passes: player 2 waits for the die to settle.
        viewModel.roll()
        runCurrent()
        val missed = viewModel.state
        assertEquals(1, missed.current)
        viewModel.roll()
        assertEquals(missed, viewModel.state)
        viewModel.setForeground(false)
        advanceTimeBy(5_000)
        runCurrent()
        assertFalse(viewModel.settled.value)
        viewModel.setForeground(true)
        advanceTimeBy(DIE_TUMBLE_MILLIS - 1L)
        runCurrent()
        viewModel.roll()
        assertEquals(missed, viewModel.state)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(viewModel.settled.value)

        // On to a 6, and its pawn out: the roll it earned waits for the pawn.
        repeat(200) {
            if (viewModel.state.phase == TurnPhase.AwaitingRoll) viewModel.roll()
            advanceTimeBy(DIE_TUMBLE_MILLIS.toLong())
            runCurrent()
        }
        viewModel.move(assertIs<TurnPhase.AwaitingMove>(viewModel.state.phase).moves.first())
        runCurrent()
        val moved = viewModel.state
        assertTrue(moved.rollingAgain)
        advanceTimeBy(PAWN_STEP_MILLIS - 1L)
        runCurrent()
        viewModel.roll()
        assertEquals(moved, viewModel.state)
        advanceTimeBy(1)
        runCurrent()
        viewModel.roll()
        assertNotEquals(moved, viewModel.state)
    }

    @Test
    fun `with nothing animated the next player can roll at once`() = runTest(dispatcher) {
        val viewModel = started(noSixFirst, humans = true)
        viewModel.roll()
        assertTrue(viewModel.settled.value)
        val missed = viewModel.state
        viewModel.roll()
        assertNotEquals(missed, viewModel.state)
    }

    @Test
    fun `a CPU's turn plays itself a pause at a time - and a person can't roll for it`() = runTest(dispatcher) {
        val viewModel = started(noSixFirst)
        passToCpu(viewModel)
        val cpu = viewModel.state.current
        val waiting = viewModel.state
        viewModel.roll()
        assertEquals(waiting, viewModel.state)
        advanceTimeBy(699)
        runCurrent()
        assertEquals(waiting, viewModel.state)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(cpu, assertNotNull(viewModel.state.lastEvent).player)
    }

    @Test
    fun `a CPU's turn waits while the game is off screen or under a dialog`() = runTest(dispatcher) {
        val viewModel = started(noSixFirst)
        passToCpu(viewModel)
        viewModel.setForeground(false)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, viewModel.state.lastEvent?.player)
        viewModel.setForeground(true)
        viewModel.setHeld(true)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, viewModel.state.lastEvent?.player)
        // Back in front, the wait starts again.
        viewModel.setHeld(false)
        advanceTimeBy(701)
        runCurrent()
        assertEquals(1, viewModel.state.lastEvent?.player)
    }

    @Test
    fun `a game is saved as it starts and after every roll and move - and Continue brings it back as it was`() = runTest(dispatcher) {
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val viewModel = started(0, humans = true, saved = saved)
        assertEquals(viewModel.game.value, saved.load())
        repeat(200) { if (viewModel.state.phase == TurnPhase.AwaitingRoll) viewModel.roll() }
        assertIs<TurnPhase.AwaitingMove>(viewModel.state.phase)
        assertEquals(viewModel.game.value, saved.load())

        val resumed = GameViewModel(SettingsRepository(InMemoryPreferences()), saved, GameRecordRepository(InMemoryGameResultDao()), Random(0), elapsedMillis = { 0L })
        runCurrent()
        assertTrue(resumed.resumeGame())
        assertEquals(viewModel.game.value, resumed.game.value)
        // The move it was waiting for can still be made.
        resumed.move((resumed.state.phase as TurnPhase.AwaitingMove).moves.first().pawn)
        assertIs<GameEvent.Moved>(resumed.state.lastEvent)
        assertEquals(resumed.game.value, saved.load())
    }

    /**
     * A game of four people resumed from a save with the current player's pawns at [pawns], every other pawn in its yard,
     * and [roll] just rolled.
     */
    private suspend fun TestScope.resumedAt(pawns: List<Int>, roll: Int): GameViewModel {
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val game = assertNotNull(started(0, humans = true, saved = saved).game.value)
        val state = game.state.copy(players = game.state.players.mapIndexed { i, player -> if (i == game.state.current) player.copy(pawns = pawns) else player })
        saved.save(game.copy(state = LudoEngine.applyRoll(state, roll)))
        val resumed = GameViewModel(SettingsRepository(InMemoryPreferences()), saved, GameRecordRepository(InMemoryGameResultDao()), Random(0), elapsedMillis = { 0L })
        resumed.setPacing(GamePacing(thinkMillis = 700, pawnsAnimated = false, dieAnimated = false))
        runCurrent()
        assertTrue(resumed.resumeGame())
        return resumed
    }

    @Test
    fun `a person's move with no choice makes itself once the screen has caught up - while it's on screen`() = runTest(dispatcher) {
        val viewModel = resumedAt(listOf(10, Y, Y, Y), roll = 3)
        val player = viewModel.state.current
        assertEquals(Move(pawn = 0, from = 10, to = 13), viewModel.game.value?.automaticMove)
        viewModel.setForeground(false)
        advanceTimeBy(5_000)
        runCurrent()
        assertIs<TurnPhase.AwaitingMove>(viewModel.state.phase)
        viewModel.setForeground(true)
        advanceTimeBy(699)
        runCurrent()
        assertIs<TurnPhase.AwaitingMove>(viewModel.state.phase)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(13, Y, Y, Y), viewModel.state.players[player].pawns)
        assertEquals(GameEvent.Moved(player, 3, Move(pawn = 0, from = 10, to = 13), finished = false), viewModel.state.lastEvent)
    }

    @Test
    fun `a person may make the move with no choice first - it isn't made twice`() = runTest(dispatcher) {
        val viewModel = resumedAt(listOf(10, Y, Y, Y), roll = 3)
        val player = viewModel.state.current
        viewModel.move(pawn = 0)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(13, Y, Y, Y), viewModel.state.players[player].pawns)
    }

    @Test
    fun `a person brings a pawn out with every one in the yard - and their last pawn home - themselves`() = runTest(dispatcher) {
        for ((pawns, roll) in listOf(listOf(Y, Y, Y, Y) to 6, listOf(HOME, HOME, HOME, HOME - 3) to 3, listOf(10, 20, Y, Y) to 3)) {
            val viewModel = resumedAt(pawns, roll)
            assertNull(viewModel.game.value?.automaticMove)
            advanceTimeBy(5_000)
            runCurrent()
            assertIs<TurnPhase.AwaitingMove>(viewModel.state.phase, "$pawns with a $roll")
        }
    }

    @Test
    fun `a game has an id of its own - and counts each change - so a merge can tell how far on a copy is`() = runTest(dispatcher) {
        val viewModel = started(0, humans = true)
        val game = assertNotNull(viewModel.game.value)
        assertTrue(Regex("[0-9a-f]{32}").matches(assertNotNull(game.id)))
        val steps = game.steps
        viewModel.roll()
        assertEquals(steps + 1, viewModel.game.value?.steps)
        assertEquals(game.id, viewModel.game.value?.id)
        // Another game is another id.
        assertNotEquals(game.id, started(0, humans = true).game.value?.id)
    }

    @Test
    fun `with no saved game there is nothing to resume`() = runTest(dispatcher) {
        val viewModel = GameViewModel(SettingsRepository(InMemoryPreferences()), InProgressGameRepository(InMemoryPreferences(), backgroundScope), GameRecordRepository(InMemoryGameResultDao()))
        runCurrent()
        assertFalse(viewModel.resumeGame())
        assertNull(viewModel.game.value)
    }

    @Test
    fun `a resumed CPU's turn plays on by itself`() = runTest(dispatcher) {
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val first = started(noSixFirst, saved = saved)
        passToCpu(first)
        val resumed = GameViewModel(SettingsRepository(InMemoryPreferences()), saved, GameRecordRepository(InMemoryGameResultDao()), Random(1))
        resumed.setPacing(GamePacing(thinkMillis = 700, pawnsAnimated = false, dieAnimated = false))
        first.setForeground(false)
        runCurrent()
        assertTrue(resumed.resumeGame())
        advanceTimeBy(701)
        runCurrent()
        assertEquals(first.state.current, assertNotNull(resumed.state.lastEvent).player)
    }

    @Test
    fun `a finished game's save is cleared - and its people are recorded once`() = runTest(dispatcher) {
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val records = GameRecordRepository(InMemoryGameResultDao())
        val viewModel = started(0, saved = saved, records = records)
        assertNotNull(saved.load())
        // Player 1 plays its first move each time; the CPUs play themselves.
        while (!viewModel.state.isFinished) {
            if (viewModel.state.current != 0) {
                advanceTimeBy(1_000)
                runCurrent()
                continue
            }
            when (val phase = viewModel.state.phase) {
                is TurnPhase.AwaitingMove -> viewModel.move(phase.moves.first().pawn)
                else -> viewModel.roll()
            }
        }
        assertNotNull(viewModel.state.winner)
        // Every player placed has a finish time, the last too.
        assertEquals(viewModel.state.finishingOrder.toSet(), assertNotNull(viewModel.game.value).finishMillis.keys)
        runCurrent()
        // Gone, not left to be found unreadable: the menu stops offering Continue at once.
        assertFalse(saved.hasInProgressGame.first())
        assertNull(saved.load())
        // Player 1 is the one person: one row, at their place, with the game's figures.
        val game = assertNotNull(viewModel.game.value)
        val row = records.playerStatistics().single()
        assertEquals("Player 1", row.playerName)
        assertEquals(1, row.totals.gamesPlayed)
        assertEquals(game.state.winner == 0, row.totals.gamesWon == 1)
        assertEquals(game.state.players[0].captures, row.totals.captures)
        assertEquals(1, records.writes.value)
    }

    @Test
    fun `play time counts only while the game is on screen and not under a dialog`() = runTest(dispatcher) {
        var clock = 1_000L
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val viewModel = started(0, humans = true, saved = saved, clock = { clock })
        assertEquals(START, assertNotNull(viewModel.game.value).startedAtEpochMillis)
        clock += 4_000
        viewModel.roll()
        assertEquals(4_000, assertNotNull(viewModel.game.value).playMillis)
        // Off screen: not counted, and what was played is saved as it goes.
        clock += 1_000
        viewModel.setForeground(false)
        runCurrent()
        assertEquals(5_000, assertNotNull(saved.load()).playMillis)
        clock += 60_000
        viewModel.setForeground(true)
        // Under the leave dialog: not counted either.
        clock += 2_000
        viewModel.setHeld(true)
        clock += 30_000
        viewModel.setHeld(false)
        clock += 500
        repeat(200) { if (viewModel.state.phase == TurnPhase.AwaitingRoll) viewModel.roll() }
        assertEquals(7_500, assertNotNull(viewModel.game.value).playMillis)
    }

    @Test
    fun `a new game follows Settings' stop at first winner`() = runTest(dispatcher) {
        val settings = SettingsRepository(InMemoryPreferences())
        val viewModel = GameViewModel(settings, InProgressGameRepository(InMemoryPreferences(), backgroundScope), GameRecordRepository(InMemoryGameResultDao()))
        runCurrent()
        assertTrue(viewModel.startGame { "Player $it" })
        assertFalse(viewModel.state.rules.stopAtFirstWinner)
        settings.setStopAtFirstWinner(true)
        runCurrent()
        assertTrue(viewModel.startGame { "Player $it" })
        assertTrue(viewModel.state.rules.stopAtFirstWinner)
    }

    @Test
    fun `the setup form's player count plays that many on its board - spread round it - and comes back next time`() = runTest(dispatcher) {
        val prefs = InMemoryPreferences()
        val viewModel = GameViewModel(SettingsRepository(prefs), InProgressGameRepository(InMemoryPreferences(), backgroundScope), GameRecordRepository(InMemoryGameResultDao()))
        runCurrent()
        viewModel.setupForm.setPlayerCount(2)
        assertTrue(viewModel.startGame { "Player $it" })
        assertEquals(listOf(0, 2), viewModel.state.players.map { it.seat })
        assertEquals(BoardGeometry.CLASSIC, viewModel.state.board)
        viewModel.setupForm.setPlayerCount(6)
        assertTrue(viewModel.startGame { "Player $it" })
        assertEquals(6, assertNotNull(viewModel.game.value).players.size)
        assertEquals(BoardGeometry.star(6), viewModel.state.board)
        // Six different colours, the four new ones' first two among them.
        assertEquals(PlayerColour.entries.take(6), assertNotNull(viewModel.game.value).players.map { it.colour })
        runCurrent()
        val again = GameViewModel(SettingsRepository(prefs), InProgressGameRepository(InMemoryPreferences(), backgroundScope), GameRecordRepository(InMemoryGameResultDao()))
        runCurrent()
        assertEquals(6, again.setupForm.setup.value.playerCount)
    }

    @Test
    fun `the setup form's house rules make the game's rules - and come back next time - any no longer known dropped - locked until a game is finished`() = runTest(dispatcher) {
        val prefs = InMemoryPreferences()
        val viewModel = GameViewModel(SettingsRepository(prefs), InProgressGameRepository(InMemoryPreferences(), backgroundScope), GameRecordRepository(InMemoryGameResultDao(finishedGame = true)))
        runCurrent()
        viewModel.setupForm.setHouseRule(HouseRule.BLOCKADES, on = true)
        viewModel.setupForm.setHouseRule(HouseRule.MUST_CAPTURE, on = true)
        viewModel.setupForm.setHouseRule(HouseRule.MUST_CAPTURE, on = false)
        assertTrue(viewModel.startGame { "Player $it" })
        assertEquals(setOf(HouseRule.BLOCKADES), viewModel.state.rules.houseRules)
        assertTrue(viewModel.state.rules.blockades)
        runCurrent()
        val again = GameViewModel(SettingsRepository(prefs), InProgressGameRepository(InMemoryPreferences(), backgroundScope), GameRecordRepository(InMemoryGameResultDao(finishedGame = true)))
        runCurrent()
        assertEquals(setOf(HouseRule.BLOCKADES), again.setupForm.setup.value.houseRules)
        prefs.edit { it[stringSetPreferencesKey("house_rules")] = setOf("blockades", "retired_rule") }
        val dropped = GameViewModel(SettingsRepository(prefs), InProgressGameRepository(InMemoryPreferences(), backgroundScope), GameRecordRepository(InMemoryGameResultDao(finishedGame = true)))
        runCurrent()
        assertEquals(setOf(HouseRule.BLOCKADES), dropped.setupForm.setup.value.houseRules)

        // Until a first game is finished they're locked: kept, but not played.
        val locked = GameViewModel(SettingsRepository(prefs), InProgressGameRepository(InMemoryPreferences(), backgroundScope), GameRecordRepository(InMemoryGameResultDao()))
        runCurrent()
        assertFalse(locked.setupForm.setup.value.houseRulesUnlocked)
        assertEquals(setOf(HouseRule.BLOCKADES), locked.setupForm.setup.value.houseRules)
        assertTrue(locked.startGame { "Player $it" })
        assertEquals(emptySet(), locked.state.rules.houseRules)
        runCurrent()
        assertEquals(setOf(HouseRule.BLOCKADES), SettingsRepository(prefs).snapshot().houseRules)
    }

    @Test
    fun `a custom mode saved is picked - plays its base with its house rules under its name - is kept - and once deleted leaves its base`() = runTest(dispatcher) {
        val prefs = InMemoryPreferences()
        fun viewModel(finished: Boolean = true) =
            GameViewModel(SettingsRepository(prefs), InProgressGameRepository(InMemoryPreferences(), backgroundScope), GameRecordRepository(InMemoryGameResultDao(finishedGame = finished)))
        val viewModel = viewModel()
        runCurrent()
        val mode = CustomMode("custom_1", "Cut-Throat", GameMode.QUICK, setOf(HouseRule.MUST_CAPTURE))
        viewModel.setupForm.setHouseRule(HouseRule.BLOCKADES, on = true)
        viewModel.saveCustomMode(mode)
        assertEquals(mode, viewModel.setupForm.setup.value.customMode)
        assertTrue(viewModel.startGame { "Player $it" })
        val game = assertNotNull(viewModel.game.value)
        assertEquals(GameMode.QUICK, game.mode)
        assertEquals("Cut-Throat", game.customName)
        // Its own house rules, not those switched on for the built-in modes.
        assertEquals(setOf(HouseRule.MUST_CAPTURE), game.state.rules.houseRules)
        assertEquals(2, game.state.rules.pawnsPerPlayer)
        runCurrent()

        // Kept, and picked, next time.
        val again = viewModel()
        runCurrent()
        assertEquals(listOf(mode), again.setupForm.setup.value.customModes)
        assertEquals(mode, again.setupForm.setup.value.customMode)
        assertEquals(setOf(HouseRule.BLOCKADES), again.setupForm.setup.value.houseRules)
        // Locked, as house rules are until a game is finished: its base, plain.
        val locked = viewModel(finished = false)
        runCurrent()
        assertNull(locked.setupForm.setup.value.customMode)
        assertTrue(locked.startGame { "Player $it" })
        assertEquals(GameMode.QUICK, locked.state.mode)
        assertEquals(emptySet(), locked.state.rules.houseRules)
        assertNull(assertNotNull(locked.game.value).customName)

        again.deleteCustomMode("custom_1")
        runCurrent()
        assertEquals(GameMode.QUICK, again.setupForm.setup.value.gameMode)
        assertNull(again.setupForm.setup.value.customMode)
        assertEquals(emptyList(), SettingsRepository(prefs).snapshot().customModes)
    }

    @Test
    fun `colours saved for four slots come back - the four slots added take the colours left`() = runTest(dispatcher) {
        val prefs = InMemoryPreferences()
        val saved = listOf(PlayerColour.BLUE, PlayerColour.RED, PlayerColour.GREEN, PlayerColour.YELLOW)
        SettingsRepository(prefs).batch { saved.forEachIndexed { i, colour -> setPlayerColour(i + 1, colour) } }
        val viewModel = GameViewModel(SettingsRepository(prefs), InProgressGameRepository(InMemoryPreferences(), backgroundScope), GameRecordRepository(InMemoryGameResultDao()))
        runCurrent()
        val setup = viewModel.setupForm.setup.value
        assertEquals(saved + PlayerColour.entries.subList(4, 8), setup.allSlots.map { it.colour })
        assertEquals(4, setup.playerCount)
    }

    @Test
    fun `a colour swaps with whichever slot holds it - played or not - so all eight stay different - and one nobody holds is just taken`() = runTest(dispatcher) {
        val form = GameSetupForm()
        form.setPlayerCount(3)
        // Player 1 takes purple from slot 8, which isn't playing, and slot 8 takes red.
        form.setPlayerColour(1, PlayerColour.PURPLE)
        val colours = form.setup.value.allSlots.map { it.colour }
        assertEquals(PlayerColour.PURPLE, colours[0])
        assertEquals(PlayerColour.RED, colours[7])
        assertEquals(8, colours.toSet().size)
        assertEquals(3, form.setup.value.playerSlots.size)
        // Lime is one of the four no slot starts with: player 2 takes it, and nobody else changes.
        form.setPlayerColour(2, PlayerColour.LIME)
        assertEquals(listOf(PlayerColour.PURPLE, PlayerColour.LIME) + colours.drop(2), form.setup.value.allSlots.map { it.colour })
    }

    private companion object {
        /** When every test's game starts, by the wall clock. */
        const val START = 1_763_000_000_000L
    }
}
