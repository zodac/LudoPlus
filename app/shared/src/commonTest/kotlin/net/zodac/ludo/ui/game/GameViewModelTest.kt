package net.zodac.ludo.ui.game

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
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.game.InProgressGameRepository
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.records.InMemoryGameResultDao
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.GameEvent
import net.zodac.ludo.game.RollOff
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.model.BoardGeometry
import net.zodac.ludo.model.HouseRule
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.PlayerType

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /**
     * A started game whose die and roll-off come from [seed]; seats 2 to 4 are CPUs unless [humans]. It saves to [saved],
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

    /** The first seed whose roll-off [wanted] says yes to, so a test starts with the player it needs. */
    private fun seedWhere(wanted: (first: Int) -> Boolean): Int =
        generateSequence(0) { it + 1 }.first { wanted(RollOff.roll(4, 6, Random(it)).first) }

    @Test
    fun `a new game starts with the roll-off's winner to roll - every pawn in its yard`() = runTest(dispatcher) {
        val seed = seedWhere { true }
        val viewModel = started(seed, humans = true)
        val game = assertNotNull(viewModel.game.value)
        assertEquals(RollOff.roll(4, 6, Random(seed)).first, game.state.current)
        assertEquals(game.rollOff.first, game.state.current)
        assertEquals(TurnPhase.AwaitingRoll, game.state.phase)
        assertEquals(listOf("Player 1", "Player 2", "Player 3", "Player 4"), game.players.map { it.name })
    }

    @Test
    fun `a person rolls and moves - a move that isn't legal is ignored`() = runTest(dispatcher) {
        val viewModel = started(seedWhere { true }, humans = true)
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
    fun `a CPU's turn plays itself a pause at a time - and a person can't roll for it`() = runTest(dispatcher) {
        val viewModel = started(seedWhere { it != 0 })
        val cpu = viewModel.state.current
        assertNotEquals(0, cpu)
        viewModel.roll()
        assertEquals(null, viewModel.state.lastEvent)
        advanceTimeBy(699)
        runCurrent()
        assertEquals(null, viewModel.state.lastEvent)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(cpu, assertNotNull(viewModel.state.lastEvent).player)
    }

    @Test
    fun `a CPU's turn waits while the game is off screen or under a dialog`() = runTest(dispatcher) {
        val viewModel = started(seedWhere { it != 0 })
        viewModel.setForeground(false)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(null, viewModel.state.lastEvent)
        viewModel.setForeground(true)
        viewModel.setHeld(true)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(null, viewModel.state.lastEvent)
        // Back in front, the wait starts again.
        viewModel.setHeld(false)
        advanceTimeBy(701)
        runCurrent()
        assertNotNull(viewModel.state.lastEvent)
    }

    @Test
    fun `a game is saved as it starts and after every roll and move - and Continue brings it back as it was`() = runTest(dispatcher) {
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val viewModel = started(seedWhere { true }, humans = true, saved = saved)
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
        val first = started(seedWhere { it != 0 }, saved = saved)
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
        val viewModel = started(seedWhere { true }, saved = saved, records = records)
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
        val viewModel = started(seedWhere { true }, humans = true, saved = saved, clock = { clock })
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
