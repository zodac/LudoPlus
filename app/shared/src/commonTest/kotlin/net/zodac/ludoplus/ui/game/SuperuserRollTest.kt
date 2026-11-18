package net.zodac.ludoplus.ui.game

import kotlin.random.Random
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.PlayerType

/** Debug builds' superuser mode in a game: a long press on the die turns it on, and the next sets the next roll. */
@OptIn(ExperimentalCoroutinesApi::class)
class SuperuserRollTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.started(debug: Boolean, humans: Boolean): GameViewModel {
        val viewModel = GameViewModel(
            SettingsRepository(InMemoryPreferences()),
            InProgressGameRepository(InMemoryPreferences(), backgroundScope),
            GameRecordRepository(InMemoryGameResultDao()),
            Random(3),
            elapsedMillis = { 0L },
            isDebugBuild = debug,
        )
        viewModel.setPacing(GamePacing(thinkMillis = 700, pawnsAnimated = false, dieAnimated = false))
        runCurrent()
        if (humans) (2..4).forEach { viewModel.setupForm.setPlayerType(it, PlayerType.HUMAN) }
        viewModel.startGame { "Player $it" }
        runCurrent()
        return viewModel
    }

    private val GameViewModel.lastRoll: Int?
        get() = when (val event = game.value?.state?.lastEvent) {
            is GameEvent.Rolled -> event.roll
            is GameEvent.NoMove -> event.roll
            is GameEvent.RollAgainLimit -> event.roll
            else -> null
        }

    @Test
    fun `a long press turns superuser mode on - the next offers the next roll - which a person's tap then rolls once`() = runTest(dispatcher) {
        val viewModel = started(debug = true, humans = true)
        viewModel.onDieLongPressed()
        assertTrue(viewModel.superuserModeActive.value)
        assertFalse(viewModel.rollPickerShown.value)
        viewModel.onDieLongPressed()
        assertTrue(viewModel.rollPickerShown.value)
        viewModel.pickNextRoll(4)
        assertFalse(viewModel.rollPickerShown.value)
        assertEquals(4, viewModel.nextRoll.value)

        viewModel.roll()
        assertEquals(4, viewModel.lastRoll)
        assertNull(viewModel.nextRoll.value, "used once")

        // A fair roll leaves nothing set.
        viewModel.onDieLongPressed()
        viewModel.pickNextRoll(null)
        assertNull(viewModel.nextRoll.value)
    }

    @Test
    fun `a CPU's roll is the picked one too`() = runTest(dispatcher) {
        val viewModel = started(debug = true, humans = false)
        // Wait for a CPU's turn to come round, with a roll still to make.
        var steps = 0
        while (true) {
            val state = assertNotNull(viewModel.game.value).state
            if (state.current != 0 && state.phase == TurnPhase.AwaitingRoll) break
            check(++steps < 400) { "No CPU's turn came round" }
            when (val phase = state.phase) {
                is TurnPhase.AwaitingMove -> if (state.current == 0) viewModel.move(phase.moves.first().pawn)
                else -> viewModel.roll()
            }
            advanceTimeBy(100)
            runCurrent()
        }
        viewModel.onDieLongPressed()
        viewModel.onDieLongPressed()
        viewModel.pickNextRoll(6)
        val cpu = assertNotNull(viewModel.game.value).state.current
        assertTrue(cpu != 0)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(6, viewModel.lastRoll)
    }

    @Test
    fun `a release build has no superuser mode`() = runTest(dispatcher) {
        val viewModel = started(debug = false, humans = true)
        viewModel.onDieLongPressed()
        viewModel.onDieLongPressed()
        assertFalse(viewModel.superuserModeActive.value)
        assertFalse(viewModel.rollPickerShown.value)
        viewModel.pickNextRoll(4)
        assertNull(viewModel.nextRoll.value)
    }
}
