package net.zodac.ludoplus.ui.game

import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.ludoplus.app.Breadcrumbs
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.PlayerType

/** A game's steps are left as breadcrumbs, for a crash report to say what led up to it: ids and numbers, never names. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameBreadcrumbsTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(saved: InProgressGameRepository, seed: Int = 1): GameViewModel =
        GameViewModel(SettingsRepository(InMemoryPreferences()), saved, GameRecordRepository(InMemoryGameResultDao()), Random(seed)).also {
            it.setPacing(GamePacing(pawnsAnimated = false, dieAnimated = false))
            runCurrent()
        }

    @Test
    fun `starting - rolling - moving and resuming each leave a breadcrumb - with no player's name`() = runTest(dispatcher) {
        val saved = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val viewModel = viewModel(saved)
        (2..4).forEach { viewModel.setupForm.setPlayerType(it, PlayerType.HUMAN) }
        viewModel.startGame { "Alexandra $it" }
        runCurrent()
        assertEquals("Started classic: HUMAN, HUMAN, HUMAN, HUMAN", Breadcrumbs.recent.last())

        viewModel.roll()
        runCurrent()
        assertTrue(Breadcrumbs.recent.last().let { it.startsWith("Rolled ") && it.endsWith(", player 1") }, Breadcrumbs.recent.last())

        // On to a move: with every pawn in the yard, only a 6 gives one.
        repeat(200) { if (viewModel.game.value?.state?.phase == TurnPhase.AwaitingRoll) viewModel.roll() }
        val phase = assertIs<TurnPhase.AwaitingMove>(viewModel.game.value?.state?.phase)
        val player = checkNotNull(viewModel.game.value).state.current + 1
        assertEquals("Rolled 6, player $player", Breadcrumbs.recent.last())
        viewModel.move(phase.moves.first())
        runCurrent()
        assertEquals("Moved pawn ${phase.moves.first().pawn + 1} from -1 to 0, player $player", Breadcrumbs.recent.last())

        assertTrue(viewModel(saved).resumeGame())
        assertEquals("Resumed classic: HUMAN, HUMAN, HUMAN, HUMAN", Breadcrumbs.recent.last())
        assertTrue(Breadcrumbs.recent.none { "Alexandra" in it })
    }

    @Test
    fun `a move says whose pawn it was - the portal it took and what it captured`() {
        val move = Move(pawn = 1, from = 3, to = 17, captures = listOf(PawnRef(2, 0)), owner = 3, via = 10)
        GameBreadcrumbs.event(before = null, event = GameEvent.Moved(player = 1, roll = 4, move = move, finished = false))
        assertEquals("Moved pawn 2 from 3 to 17, player 2, for player 4, by the portal at 10, captured 1", Breadcrumbs.recent.last())
    }

    @Test
    fun `two dice are both said - and an event already there isn't left again`() {
        val rolled = GameEvent.NoMove(player = 0, roll = 2, second = 5)
        GameBreadcrumbs.event(before = null, event = rolled)
        assertEquals("Rolled 2 and 5 with no move, player 1", Breadcrumbs.recent.last())
        val before = Breadcrumbs.recent
        GameBreadcrumbs.event(before = rolled, event = rolled)
        GameBreadcrumbs.event(before = rolled, event = null)
        assertEquals(before, Breadcrumbs.recent)
    }
}
