package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LogEntry
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.MoveLog
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.game.GameScreen
import net.zodac.ludoplus.ui.game.GameViewModel
import net.zodac.ludoplus.ui.game.StartedGame
import net.zodac.ludoplus.ui.game.portraitBoardSide
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** The room the move log gets in portrait, however many are playing: two lines where it can, one line at least, and the rest a scroll away. */
class MoveLogRoomTest : UiTest() {

    private val showcase = Showcase { compose }

    /** A game of [players], all at this phone, some way in. */
    private fun game(players: Int): StartedGame {
        val names = listOf("Alex", "Sam", "Jo", "Kim", "Max", "Zoe", "Leo", "Bo")
        val random = Random(3)
        val start = GameState.new(GameMode.CLASSIC, players, rules = GameMode.CLASSIC.rules.forPlayers(players))
        val states = generateSequence(start) { state ->
            when (val phase = state.phase) {
                is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.first().pawn, phase.moves.first().owner)
                else -> LudoEngine.roll(state, random)
            }
        }.take(120).toList()
        // Up to a player about to roll, so nothing moves itself on.
        val shown = states.take(states.indexOfLast { it.phase == TurnPhase.AwaitingRoll } + 1)
        val log = shown.zipWithNext().fold(emptyList<LogEntry>()) { log, (before, after) -> MoveLog.next(log, before, after, 0) }
        return StartedGame(
            mode = GameMode.CLASSIC,
            players = List(players) { PlayerConfig(it + 1, PlayerType.HUMAN, names[it]) },
            state = shown.last(),
            first = 0,
            id = "a".repeat(32),
            log = log,
        )
    }

    private suspend fun show(game: StartedGame, fontScale: Float = 1f) {
        val inProgress = InProgressGameRepository(InMemoryPreferences())
        inProgress.save(game)
        val viewModel = GameViewModel(SettingsRepository(InMemoryPreferences()), inProgress, GameRecordRepository(InMemoryGameResultDao()))
        assertTrue(viewModel.resumeGame())
        showcase.show {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices, LocalDensity provides Density(density.density, fontScale)) {
                LudoPlusTheme { GameScreen(viewModel, onBackToMenu = {}) }
            }
        }
    }

    private val isLog = SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo) and
        SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private val isLine = SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionItemInfo)

    /** How many of the log's lines are on screen in full. */
    private fun fullLines(): Int {
        val log = compose.onNode(isLog).fetchSemanticsNode().boundsInRoot
        return compose.onAllNodes(isLine).fetchSemanticsNodes().count { line ->
            val bounds = Rect(line.positionInRoot, Size(line.size.width.toFloat(), line.size.height.toFloat()))
            bounds.top >= log.top - 0.5f && bounds.bottom <= log.bottom + 0.5f
        }
    }

    private fun assertLines(screen: TestScreen, players: Int, atLeast: Int, fontScale: Float = 1f) {
        val lines = fullLines()
        assertTrue(lines >= atLeast, "$players players on ${screen.widthDp}x${screen.heightDp} at $fontScale: $lines lines, not $atLeast")
    }

    private fun linesOn(screen: TestScreen, players: Int, atLeast: Int, fontScale: Float = 1f) = uiTest(screen) {
        show(game(players), fontScale)
        assertLines(screen, players, atLeast, fontScale)
    }

    @Test fun `eight players leave the log two lines on a 16 by 9 phone`() = linesOn(TestScreen(411, 731), players = 8, atLeast = 2)

    @Test fun `and one on a small one`() = linesOn(TestScreen(360, 640), players = 8, atLeast = 1)

    @Test fun `and one line on a small one less its system bars`() = linesOn(TestScreen(360, 568), players = 8, atLeast = 1)

    @Test fun `eight leave two on a taller phone`() = linesOn(TestScreen(360, 800), players = 8, atLeast = 2)

    @Test fun `and on a wider one`() = linesOn(TestScreen(411, 891), players = 8, atLeast = 2)

    @Test fun `eight leave one at a large font`() = linesOn(TestScreen(411, 731), players = 8, atLeast = 1, fontScale = 1.3f)

    @Test fun `four players keep theirs`() = linesOn(TestScreen(360, 640), players = 4, atLeast = 2)

    @Test
    fun `the log scrolls back to who went first where it shows only a line or two`() = uiTest(TestScreen(411, 731)) {
        val game = game(8)
        show(game)
        compose.onNode(isLog).performScrollToIndex(game.log.size)
        compose.onNode(hasContentDescription("Alex goes first.")).assertExists()
    }

    @Test
    fun `the log has its title where there's room for it and two lines - and not where there isn't`() = uiTest(TestScreen(411, 731)) {
        show(game(4))
        compose.onNode(hasText("Moves") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        show(game(8))
        compose.onNode(hasText("Moves") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertDoesNotExist()
    }

    @Test
    fun `the board is the screen's width where the log has room for two lines beside it`() {
        assertEquals(400, portraitBoardSide(width = 400, room = 456, oneLine = 32, twoLines = 56))
        assertEquals(400, portraitBoardSide(width = 400, room = 900, oneLine = 32, twoLines = 56))
    }

    @Test
    fun `the board gives up to a tenth for the log's second line and more only for its first`() {
        assertEquals(384, portraitBoardSide(width = 400, room = 440, oneLine = 32, twoLines = 56))
        assertEquals(360, portraitBoardSide(width = 400, room = 416, oneLine = 32, twoLines = 56))
        // A second line would take more: the log keeps its first.
        assertEquals(360, portraitBoardSide(width = 400, room = 400, oneLine = 32, twoLines = 56))
        assertEquals(328, portraitBoardSide(width = 400, room = 360, oneLine = 32, twoLines = 56))
        // And never under its smallest.
        assertEquals(240, portraitBoardSide(width = 400, room = 100, oneLine = 32, twoLines = 56))
    }
}
