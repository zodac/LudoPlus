package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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

/** The room the move log gets in portrait, however many are playing: its newest round's heading and a line at least, and the rest a scroll away. */
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

    /** The newest round in the log shown. */
    private var newestRound = 0

    private suspend fun show(game: StartedGame, fontScale: Float = 1f, showLog: Boolean? = null) {
        newestRound = game.log.last().turn!!
        val inProgress = InProgressGameRepository(InMemoryPreferences())
        inProgress.save(game)
        val settings = SettingsRepository(InMemoryPreferences())
        showLog?.let { settings.setShowMoveLog(it) }
        val viewModel = GameViewModel(settings, inProgress, GameRecordRepository(InMemoryGameResultDao()))
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
        val heading = compose.onNode(hasText("Round $newestRound") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNode()
        val log = compose.onNode(isLog).fetchSemanticsNode().boundsInRoot
        assertTrue(heading.boundsInRoot.top >= log.top && heading.positionInRoot.y + heading.size.height <= log.bottom, "$players players on ${screen.widthDp}x${screen.heightDp} at $fontScale: no heading")
        assertTrue(lines >= atLeast, "$players players on ${screen.widthDp}x${screen.heightDp} at $fontScale: $lines lines, not $atLeast")
    }

    private fun linesOn(screen: TestScreen, players: Int, atLeast: Int, fontScale: Float = 1f) = uiTest(screen) {
        show(game(players), fontScale)
        assertLines(screen, players, atLeast, fontScale)
    }

    @Test fun `eight players leave the log a heading and a line on a 16 by 9 phone`() = linesOn(TestScreen(411, 731), players = 8, atLeast = 1)

    @Test fun `and on a small one`() = linesOn(TestScreen(360, 640), players = 8, atLeast = 1)

    @Test fun `and on a small one less its system bars`() = linesOn(TestScreen(360, 568), players = 8, atLeast = 1)

    @Test fun `eight leave two on a taller phone`() = linesOn(TestScreen(360, 800), players = 8, atLeast = 2)

    @Test fun `and on a wider one`() = linesOn(TestScreen(411, 891), players = 8, atLeast = 2)

    @Test fun `eight leave one at a large font`() = linesOn(TestScreen(411, 731), players = 8, atLeast = 1, fontScale = 1.3f)

    @Test fun `four players have a line too on a small one`() = linesOn(TestScreen(360, 640), players = 4, atLeast = 1)

    @Test
    fun `the log scrolls back to who went first where it shows only a line or two`() = uiTest(TestScreen(411, 731)) {
        val game = game(8)
        show(game)
        // Every line, and a heading over each round and the start.
        compose.onNode(isLog).performScrollToIndex(game.log.size + newestRound + 1)
        compose.onNode(hasContentDescription("Alex goes first.")).assertExists()
        compose.onNode(hasText("Start") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
    }

    @Test
    fun `each round's lines are under its heading - newest first - and the headings aren't lines of the list`() = uiTest(TestScreen(411, 2400)) {
        val game = game(4).let { it.copy(log = it.log.take(14)) }
        show(game)
        val top = { matcher: SemanticsMatcher -> compose.onNode(matcher).fetchSemanticsNode().positionInRoot.y }
        val heading = { text: String -> top(hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)) }
        val rounds = game.log.map { it.turn!! }.distinct()
        val headings = rounds.map { heading("Round $it") }
        assertEquals(headings.sortedDescending(), headings)
        assertTrue(heading("Start") > headings.first() && top(hasContentDescription("Alex goes first.")) > heading("Start"))
        val lines = compose.onAllNodes(isLine).fetchSemanticsNodes().associate { it.config[SemanticsProperties.CollectionItemInfo].rowIndex to it.positionInRoot.y }
        assertEquals((0..game.log.size).toSet(), lines.keys)
        assertEquals(game.log.size + 1, compose.onNode(isLog).fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].rowCount)
        game.log.asReversed().forEachIndexed { index, entry ->
            val y = lines.getValue(index)
            // The nearest heading over the line.
            assertEquals(entry.turn, rounds.filter { heading("Round $it") < y }.maxBy { heading("Round $it") }, "line $index")
        }
    }

    /** How tall the board is: from the top of its highest place to the foot of its lowest. */
    private fun boardHeight(): Float {
        val places = compose.onAllNodes(hasContentDescription("Square ", substring = true)).fetchSemanticsNodes().map { it.boundsInRoot }
        return places.maxOf { it.bottom } - places.minOf { it.top }
    }

    @Test
    fun `with the log off in Settings nothing of it is on screen - its newest line still heard - and the board has its room`() = uiTest(TestScreen(360, 640)) {
        val game = game(8)
        show(game, showLog = true)
        compose.onNode(isLog).assertExists()
        val withLog = boardHeight()
        show(game, showLog = false)
        compose.onNode(isLog).assertDoesNotExist()
        compose.onNode(hasText("Round $newestRound")).assertDoesNotExist()
        assertTrue(boardHeight() > withLog, "the board is $withLog tall with the log and ${boardHeight()} without")
        // The newest line, as a polite live region, and none of the others.
        val heard = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).fetchSemanticsNodes()
        assertTrue(heard.any { node -> node.config.getOrNull(SemanticsProperties.ContentDescription)?.single()?.contains("'s Pawn ") == true })
        compose.onNode(hasContentDescription("Alex goes first.")).assertDoesNotExist()
    }

    @Test
    fun `the log starts at its newest round's heading - with no title over it - however much room it has`() = uiTest(TestScreen(411, 891)) {
        show(game(4))
        compose.onNodeWithText("Moves").assertDoesNotExist()
        val heading = compose.onNode(hasText("Round $newestRound") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNode()
        val log = compose.onNode(isLog).fetchSemanticsNode()
        // 4dp over the first row, and a heading's own 4dp.
        assertEquals(with(compose.density) { 8.dp.toPx() }, heading.positionInRoot.y - log.positionInRoot.y, 1f)
    }

    @Test
    fun `the board is the screen's width where the log has its room beside it`() {
        assertEquals(400, portraitBoardSide(width = 400, room = 456, logHeight = 56))
        assertEquals(400, portraitBoardSide(width = 400, room = 900, logHeight = 56))
    }

    @Test
    fun `the board gives up what the log needs - and never goes under its smallest`() {
        assertEquals(384, portraitBoardSide(width = 400, room = 440, logHeight = 56))
        assertEquals(304, portraitBoardSide(width = 400, room = 360, logHeight = 56))
        assertEquals(240, portraitBoardSide(width = 400, room = 100, logHeight = 56))
    }
}
