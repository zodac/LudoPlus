package net.zodac.ludoplus

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.GameResult
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.ModePage
import net.zodac.ludoplus.ui.records.LeaderboardScreen
import net.zodac.ludoplus.ui.records.LeaderboardViewModel
import net.zodac.ludoplus.ui.records.StatisticsScreen
import net.zodac.ludoplus.ui.records.StatisticsViewModel
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** Every mode's name, in the drop-downs' order. */
private val MODE_NAMES = listOf("Classic", "Quick", "Two Dice", "Portals", "Survival", "Chaos", "Haunted House")

/** The Leaderboard and Statistics pages: what they say, to the eye and to a screen reader, full and empty. */
class RecordsScreensTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun records(vararg rows: GameResult): GameRecordRepository {
        val dao = InMemoryGameResultDao()
        runBlocking { dao.insertGame(rows.toList()) }
        return GameRecordRepository(dao)
    }

    private fun row(name: String, place: Int, turns: Int, captures: Int, endedAt: Long, players: Int = 4, mode: GameMode = GameMode.CLASSIC) = GameResult(
        playerName = name, playerKey = name.lowercase(), isPrimaryPlayer = false, gameModeId = mode.id, playerCount = players,
        endedAtEpochMillis = endedAt, playMillis = 1, place = place, won = place == 1, finishMillis = 1, turns = turns,
        captures = captures, timesCaptured = 2, pawnsHome = if (place == 4) 1 else 4,
    )

    /** The drop-down's field, showing [name], on the page called [title]. */
    private fun field(title: String, name: String) = compose.onNode(hasContentDescription("$title, $name") and hasClickAction())

    private fun option(name: String) = compose.onNode(hasContentDescription(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    /** Opens the drop-down of the page called [title], showing [from], and picks [name]. */
    private fun pickMode(title: String, from: String, name: String) {
        field(title, from).performClick()
        compose.waitForIdle()
        option(name).performClick()
        compose.waitForIdle()
    }

    /** What a screen reader hears of the drop-down, closed and open, its menu listing [names] with [selected] selected. */
    private fun checkDropDown(title: String, selected: String, names: List<String>) {
        val closed = field(title, selected).fetchSemanticsNode().config
        assertEquals(Role.DropdownList, closed.getOrNull(SemanticsProperties.Role))
        assertEquals("Collapsed", closed.getOrNull(SemanticsProperties.StateDescription))
        field(title, selected).performClick()
        compose.waitForIdle()
        assertEquals("Expanded", field(title, selected).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        names.forEachIndexed { index, name ->
            val item = option(name).fetchSemanticsNode().config
            assertEquals(name == selected, item.getOrNull(SemanticsProperties.Selected), name)
            assertEquals("Selected".takeIf { name == selected }, item.getOrNull(SemanticsProperties.StateDescription), name)
            assertEquals(index, item.getOrNull(SemanticsProperties.CollectionItemInfo)!!.rowIndex, name)
        }
        assertEquals(
            names.size,
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionItemInfo) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun `the Leaderboard's drop-down is one control with its value and state - its menu every mode, the one on view selected`() = uiTest(TestScreen(411, 891)) {
        val viewModel = LeaderboardViewModel(records())
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("Classic: fastest wins")).fetchSemanticsNodes().isNotEmpty() }
        checkDropDown("Leaderboard", "Classic", MODE_NAMES)
    }

    @Test
    fun `Statistics' drop-down has All Modes last`() = uiTest(TestScreen(411, 891)) {
        val viewModel = StatisticsViewModel(records(row("Alex", 1, 38, 4, 1)))
        showcase.show { LudoPlusTheme { StatisticsScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("Games played")).fetchSemanticsNodes().isNotEmpty() }
        checkDropDown("Statistics", "Classic", MODE_NAMES + "All Modes")
    }

    @Test
    fun `the drop-down stays at the top while a long board scrolls - only the rows on screen built - and another mode's starts at its top`() = uiTest(TestScreen(411, 891)) {
        val rows = (1..60).map { row("P$it", 1, 30 + it, 1, it.toLong()) } + (1..60).map { row("Q$it", 1, 20 + it, 1, it.toLong(), mode = GameMode.QUICK) }
        val viewModel = LeaderboardViewModel(records(*rows.toTypedArray()))
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, P1, 31 turns, 1 capture")).fetchSemanticsNodes().isNotEmpty() }
        val top = field("Leaderboard", "Classic").fetchSemanticsNode().boundsInRoot.top
        val built = compose.onAllNodes(hasContentDescription(" turns, 1 capture", substring = true)).fetchSemanticsNodes().size
        assertTrue(built < 30, "$built rows built")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("60th, P60, 90 turns, 1 capture"))
        assertEquals(top, field("Leaderboard", "Classic").fetchSemanticsNode().boundsInRoot.top)
        compose.onNode(hasContentDescription("1st, P1", substring = true)).assertDoesNotExist()
        pickMode("Leaderboard", from = "Classic", name = "Quick")
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Q1, 21 turns, 1 capture")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Quick: fastest wins").assertIsDisplayed()
    }

    @Test
    fun `the Leaderboard reads each win as one row - rank, name, turns, and captures`() = uiTest(TestScreen(411, 891)) {
        val repository = records(row("Alex", 1, 38, 4, 1), row("Sam", 1, 41, 1, 2), row("Sam", 2, 30, 9, 3))
        val viewModel = LeaderboardViewModel(repository)
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("Classic: fastest wins")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("1st, Alex, 38 turns, 4 captures")).assertExists()
        compose.onNode(hasContentDescription("2nd, Sam, 41 turns, 1 capture")).assertExists()
        // Sam's loss isn't a win: two rows.
        compose.onNode(hasContentDescription("3rd", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `an empty Leaderboard says how to get on it - for the player count picked`() = uiTest(TestScreen(411, 891)) {
        val viewModel = LeaderboardViewModel(records())
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("No wins in Classic with 4 players yet. Win a game to set a time.")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `the Leaderboard starts at New Game's count - each count its own board - each number said as players`() = uiTest(TestScreen(411, 891)) {
        val repository = records(row("Alex", 1, 38, 4, 1), row("Sam", 1, 70, 1, 2, players = 6))
        val viewModel = LeaderboardViewModel(repository) { 6 }
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Sam, 70 turns, 1 capture")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("1st, Alex", substring = true)).assertDoesNotExist()
        (2..8).forEach { compose.onNode(hasContentDescription("$it players")).assertExists() }
        compose.onNode(hasContentDescription("4 players")).performClick()
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Alex, 38 turns, 4 captures")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("1st, Sam", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `the Leaderboard starts at the mode last picked there - each mode its own board`() = uiTest(TestScreen(411, 891)) {
        val repository = records(row("Alex", 1, 38, 4, 1), row("Sam", 1, 22, 1, 2, mode = GameMode.QUICK))
        val viewModel = LeaderboardViewModel(repository, MutableStateFlow(GameMode.QUICK))
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Sam, 22 turns, 1 capture")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Quick: fastest wins").assertExists()
        pickMode("Leaderboard", from = "Quick", name = "Classic")
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Alex, 38 turns, 4 captures")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("1st, Sam", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `Survival's Leaderboard ranks the most pawns left - then most captures - and its Statistics have no race`() = uiTest(TestScreen(411, 891)) {
        val survival = { name: String, captures: Int, lost: Int, endedAt: Long -> row(name, 1, 80, captures, endedAt, mode = GameMode.SURVIVAL).copy(timesCaptured = lost) }
        val repository = records(survival("Alex", 9, 5, 1), survival("Sam", 2, 1, 2), survival("Jo", 4, 1, 3))
        val viewModel = LeaderboardViewModel(repository, MutableStateFlow(GameMode.SURVIVAL))
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Jo, 5 pawns left, 4 captures")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("2nd, Sam, 5 pawns left, 2 captures")).assertExists()
        compose.onNode(hasContentDescription("3rd, Alex, 1 pawn left, 9 captures")).assertExists()
        compose.onNodeWithText("Survival: most pawns left").assertExists()

        val statistics = StatisticsViewModel(repository, MutableStateFlow(ModePage.AllModes))
        showcase.show { LudoPlusTheme { StatisticsScreen(statistics, onBack = {}) } }
        // Among all modes, its wins set no fewest turns.
        compose.waitUntil { compose.onAllNodes(hasText("Fewest turns to win") and hasText("None")).fetchSemanticsNodes().isNotEmpty() }
        statistics.selectPage(ModePage.Mode(GameMode.SURVIVAL))
        compose.waitUntil { compose.onAllNodes(hasText("Fewest turns to win")).fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasText("Pawns home")).assertDoesNotExist()
    }

    @Test
    fun `Statistics adds up one mode - or every mode - an empty mode said by name`() = uiTest(TestScreen(411, 891)) {
        val repository = records(row("Alex", 1, 38, 4, 1), row("Alex", 2, 30, 1, 2, mode = GameMode.QUICK))
        val viewModel = StatisticsViewModel(repository)
        showcase.show { LudoPlusTheme { StatisticsScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("Games played") and hasText("1")).fetchSemanticsNodes().isNotEmpty() }
        pickMode("Statistics", from = "Classic", name = "All Modes")
        compose.waitUntil { compose.onAllNodes(hasText("Games played") and hasText("2")).fetchSemanticsNodes().isNotEmpty() }
        pickMode("Statistics", from = "All Modes", name = "Quick")
        compose.waitUntil { compose.onAllNodes(hasText("Games played") and hasText("1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Wins") and hasText("0")).assertExists()
        compose.onNode(hasContentDescription("6 players")).performClick()
        compose.waitUntil { compose.onAllNodes(hasText("No Quick games with 6 players yet.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("All games")).performClick()
        viewModel.selectPage(ModePage.Mode(GameMode.CLASSIC))
        compose.waitUntil { compose.onAllNodes(hasText("Games played") and hasText("1")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `Statistics gives each player's figures with their labels`() = uiTest(TestScreen(411, 891)) {
        val repository = records(row("Alex", 1, 38, 4, 1), row("Alex", 1, 41, 1, 2), row("Alex", 4, 60, 0, 3), row("Sam", 2, 50, 2, 1))
        val viewModel = StatisticsViewModel(repository)
        showcase.show { LudoPlusTheme { StatisticsScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("Alex")).fetchSemanticsNodes().isNotEmpty() }
        // Each figure is one node, label then value.
        listOf(
            "Games played" to "3", "Wins" to "2", "Win rate" to "67%", "Current win streak" to "0", "Best win streak" to "2",
            "Fewest turns to win" to "38", "Captures" to "5", "Times captured" to "6", "Pawns home" to "9",
        ).forEach { (label, value) -> compose.onAllNodes(hasText(label) and hasText(value))[0].assertExists() }
        // Sam never won.
        compose.onNode(hasText("Fewest turns to win") and hasText("None")).assertExists()
        compose.onNode(hasText("Win rate") and hasText("0%")).assertExists()
    }

    @Test
    fun `Statistics adds up every game at first - or one player count's - All said as all games`() = uiTest(TestScreen(411, 891)) {
        val repository = records(row("Alex", 1, 38, 4, 1), row("Alex", 2, 90, 1, 2, players = 8))
        val viewModel = StatisticsViewModel(repository, MutableStateFlow(ModePage.AllModes))
        showcase.show { LudoPlusTheme { StatisticsScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("Games played") and hasText("2")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("All games")).assertIsSelected()
        compose.onNode(hasContentDescription("8 players")).performClick()
        compose.waitUntil { compose.onAllNodes(hasText("Games played") and hasText("1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Wins") and hasText("0")).assertExists()
        compose.onNode(hasContentDescription("3 players")).performClick()
        compose.waitUntil { compose.onAllNodes(hasText("No games with 3 players yet.")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `empty Statistics asks for a game - with nothing to pick from`() = uiTest(TestScreen(411, 891)) {
        val viewModel = StatisticsViewModel(records())
        showcase.show { LudoPlusTheme { StatisticsScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("No statistics yet. Play a game!")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Games played").assertDoesNotExist()
        field("Statistics", "Classic").assertDoesNotExist()
        compose.onNode(hasContentDescription("All games")).assertDoesNotExist()
    }
}
