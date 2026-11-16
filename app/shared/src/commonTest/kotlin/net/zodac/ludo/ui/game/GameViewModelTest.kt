package net.zodac.ludo.ui.game

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.ludo.data.InMemoryPreferences
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

    /** A started game whose die and roll-off come from [seed]; seats 2 to 4 are CPUs unless [humans]. */
    private fun TestScope.started(seed: Int, humans: Boolean = false): GameViewModel {
        val viewModel = GameViewModel(SettingsRepository(InMemoryPreferences()), Random(seed), cpuPauseMillis = 700)
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
}
