package net.zodac.ludo.ui.game

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.game.TutorialScript
import net.zodac.ludo.game.TutorialStep
import net.zodac.ludo.model.PlayerType

/** The tutorial played through the view model: only each step's own action is taken, and nothing is kept. */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorialGameTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class Started(val viewModel: GameViewModel, val saved: InProgressGameRepository, val records: InMemoryGameResultDao)

    private suspend fun TestScope.started(): Started {
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val records = InMemoryGameResultDao()
        val viewModel = GameViewModel(
            SettingsRepository(InMemoryPreferences()),
            saved,
            GameRecordRepository(records),
            elapsedMillis = { 0L },
            tutorial = TutorialScript.CLASSIC,
        )
        viewModel.setPacing(GamePacing(thinkMillis = 500))
        viewModel.startTutorial("Player 1", "Green")
        runCurrent()
        return Started(viewModel, saved, records)
    }

    private val GameViewModel.state get() = assertNotNull(game.value).state

    /** Waits out the screen's animations and the CPU's turn, until the next step is shown. */
    private fun TestScope.settle() = advanceUntilIdle()

    /** Takes the step shown, as the player would. */
    private fun GameViewModel.play() {
        when (val step = assertNotNull(tutorialStep.value)) {
            is TutorialStep.Roll -> roll()
            is TutorialStep.Move -> move(step.pawn)
            else -> error("The CPU's step $step is the CPU's to play")
        }
    }

    @Test
    fun `the tutorial starts as the player against Green with every pawn in the yard - and its first step shown`() = runTest(dispatcher) {
        val viewModel = started().viewModel
        val game = assertNotNull(viewModel.game.value)
        assertEquals(listOf("Player 1", "Green"), game.players.map { it.name })
        assertEquals(listOf(PlayerType.HUMAN, PlayerType.AI), game.players.map { it.type })
        assertEquals(TutorialScript.CLASSIC.start(0), game.state)
        assertEquals(TutorialScript.CLASSIC.parts[0].steps[0], viewModel.tutorialStep.value)
        assertEquals(0, viewModel.tutorialPart.value)
    }

    @Test
    fun `it plays through all four parts to the player's win - and keeps nothing`() = runTest(dispatcher) {
        val (viewModel, saved, records) = started().let { Triple(it.viewModel, it.saved, it.records) }
        val parts = mutableListOf<Int>()
        var guard = 0
        while (!viewModel.state.isFinished) {
            check(guard++ < 100) { "The tutorial never ended" }
            parts += viewModel.tutorialPart.value
            viewModel.play()
            settle()
        }
        assertEquals(listOf(0, 1, 2, 3), parts.distinct())
        assertEquals(TutorialScript.YOU, viewModel.state.winner)
        assertNull(saved.load(), "no saved game")
        assertEquals(0, records.count(), "no records")
    }

    @Test
    fun `only the step's own action is taken - and not before the step is shown`() = runTest(dispatcher) {
        val viewModel = started().viewModel
        // The first roll: a 3, as written, whatever the die would have said.
        viewModel.roll()
        assertEquals(3, (viewModel.state.lastEvent as? GameEvent.NoMove)?.roll)
        settle()
        // Green has played its 6 and its 2; the player's 6 is next.
        assertEquals(TutorialScript.YOU, viewModel.state.current)
        viewModel.roll()
        assertTrue(viewModel.state.phase is TurnPhase.AwaitingMove)
        val rolled = viewModel.state
        // Before the die has settled, the move step isn't shown, so no move is taken; nor a pawn the step didn't name.
        viewModel.move(0)
        assertEquals(rolled, viewModel.state)
        settle()
        viewModel.move(1)
        assertEquals(rolled, viewModel.state, "only the step's pawn moves")
        viewModel.roll()
        assertEquals(rolled, viewModel.state, "no roll at a move step")
        viewModel.move(0)
        assertEquals(listOf(0, -1, -1, -1), viewModel.state.players[TutorialScript.YOU].pawns)
    }

    @Test
    fun `back and forward go to the start of a part - back first to the start of this one`() = runTest(dispatcher) {
        val viewModel = started().viewModel
        viewModel.jumpToTutorialPart(1)
        assertEquals(1, viewModel.tutorialPart.value)
        assertEquals(TutorialScript.CLASSIC.start(1), viewModel.state)
        viewModel.play()
        settle()
        // Part way through: back goes to this part's start, then the one before.
        viewModel.jumpToTutorialPart(-1)
        assertEquals(1, viewModel.tutorialPart.value)
        assertEquals(TutorialScript.CLASSIC.start(1), viewModel.state)
        viewModel.jumpToTutorialPart(-1)
        assertEquals(0, viewModel.tutorialPart.value)
        // Nothing before the first, nor after the last.
        viewModel.jumpToTutorialPart(-1)
        assertEquals(0, viewModel.tutorialPart.value)
        repeat(5) { viewModel.jumpToTutorialPart(1) }
        assertEquals(3, viewModel.tutorialPart.value)
        assertEquals(TutorialScript.CLASSIC.parts[3].steps[0], viewModel.tutorialStep.value)
        assertFalse(viewModel.state.isFinished)
    }
}
