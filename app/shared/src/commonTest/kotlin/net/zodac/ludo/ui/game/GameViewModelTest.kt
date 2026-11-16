package net.zodac.ludo.ui.game

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
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.GameEvent
import net.zodac.ludo.game.RollOff
import net.zodac.ludo.game.TurnPhase
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
    ): GameViewModel {
        val viewModel = GameViewModel(SettingsRepository(InMemoryPreferences()), saved, Random(seed), elapsedMillis = clock, now = { START })
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

        val resumed = GameViewModel(SettingsRepository(InMemoryPreferences()), saved, Random(0), elapsedMillis = { 0L })
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
        val viewModel = GameViewModel(SettingsRepository(InMemoryPreferences()), InProgressGameRepository(InMemoryPreferences(), backgroundScope))
        runCurrent()
        assertFalse(viewModel.resumeGame())
        assertNull(viewModel.game.value)
    }

    @Test
    fun `a resumed CPU's turn plays on by itself`() = runTest(dispatcher) {
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val first = started(seedWhere { it != 0 }, saved = saved)
        val resumed = GameViewModel(SettingsRepository(InMemoryPreferences()), saved, Random(1))
        resumed.setPacing(GamePacing(thinkMillis = 700, pawnsAnimated = false, dieAnimated = false))
        first.setForeground(false)
        runCurrent()
        assertTrue(resumed.resumeGame())
        advanceTimeBy(701)
        runCurrent()
        assertEquals(first.state.current, assertNotNull(resumed.state.lastEvent).player)
    }

    @Test
    fun `a finished game's save is cleared`() = runTest(dispatcher) {
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val viewModel = started(seedWhere { true }, saved = saved)
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
        runCurrent()
        // Gone, not left to be found unreadable: the menu stops offering Continue at once.
        assertFalse(saved.hasInProgressGame.first())
        assertNull(saved.load())
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

    private companion object {
        /** When every test's game starts, by the wall clock. */
        const val START = 1_763_000_000_000L
    }
}
