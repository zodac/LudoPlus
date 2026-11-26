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
private val MODE_NAMES = listOf("Classic", "Colour Rush", "Two Dice", "Portals", "Survival", "Chaos", "Haunted House")

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
        val rows = (1..60).map { row("P$it", 1, 30 + it, 1, it.toLong()) } + (1..60).map { row("Q$it", 1, 20 + it, 1, it.toLong(), mode = GameMode.PORTALS) }
        val viewModel = LeaderboardViewModel(records(*rows.toTypedArray()))
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, P1, 31 turns, 1 capture")).fetchSemanticsNodes().isNotEmpty() }
        val top = field("Leaderboard", "Classic").fetchSemanticsNode().boundsInRoot.top
        val built = compose.onAllNodes(hasContentDescription(" turns, 1 capture", substring = true)).fetchSemanticsNodes().size
        assertTrue(built < 30, "$built rows built")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("60th, P60, 90 turns, 1 capture"))
        assertEquals(top, field("Leaderboard", "Classic").fetchSemanticsNode().boundsInRoot.top)
        compose.onNode(hasContentDescription("1st, P1", substring = true)).assertDoesNotExist()
        pickMode("Leaderboard", from = "Classic", name = "Portals")
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Q1, 21 turns, 1 capture")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Portals: fastest wins").assertIsDisplayed()
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
        val repository = records(row("Alex", 1, 38, 4, 1), row("Sam", 1, 22, 1, 2, mode = GameMode.PORTALS))
        val viewModel = LeaderboardViewModel(repository, MutableStateFlow(GameMode.PORTALS))
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Sam, 22 turns, 1 capture")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Portals: fastest wins").assertExists()
        pickMode("Leaderboard", from = "Portals", name = "Classic")
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Alex, 38 turns, 4 captures")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("1st, Sam", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `Colour Rush's Leaderboard ranks the most squares - then most captures - and its Statistics time no race but count pawns home`() = uiTest(TestScreen(411, 891)) {
        val rush = { name: String, captures: Int, squares: Int, endedAt: Long -> row(name, 1, 40, captures, endedAt, mode = GameMode.COLOUR_RUSH).copy(squares = squares) }
        val repository = records(rush("Alex", 9, 1, 1), rush("Sam", 2, 24, 2), rush("Jo", 4, 24, 3))
        val viewModel = LeaderboardViewModel(repository, MutableStateFlow(GameMode.COLOUR_RUSH))
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Jo, 24 squares, 4 captures")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("2nd, Sam, 24 squares, 2 captures")).assertExists()
        compose.onNode(hasContentDescription("3rd, Alex, 1 square, 9 captures")).assertExists()
        compose.onNodeWithText("Colour Rush: most squares").assertExists()

        val statistics = StatisticsViewModel(repository, MutableStateFlow(ModePage.Mode(GameMode.COLOUR_RUSH)))
        showcase.show { LudoPlusTheme { StatisticsScreen(statistics, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("Pawns home")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Fewest turns to win")).assertDoesNotExist()
    }

    @Test
    fun `Survival's Leaderboard ranks the furthest travelled - then most captures - and its Statistics have no race`() = uiTest(TestScreen(411, 891)) {
        val survival = { name: String, captures: Int, travelled: Int?, endedAt: Long -> row(name, 1, 80, captures, endedAt, mode = GameMode.SURVIVAL).copy(squaresMoved = travelled) }
        val repository = records(survival("Alex", 9, 1, 1), survival("Sam", 2, 412, 2), survival("Jo", 4, 412, 3), survival("Kim", 12, null, 4))
        val viewModel = LeaderboardViewModel(repository, MutableStateFlow(GameMode.SURVIVAL))
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Jo, 412 squares travelled, 4 captures")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("2nd, Sam, 412 squares travelled, 2 captures")).assertExists()
        compose.onNode(hasContentDescription("3rd, Alex, 1 square travelled, 9 captures")).assertExists()
        // A win from before the distance was kept: last, a dash for the eye.
        compose.onNode(hasContentDescription("4th, Kim, distance not recorded, 12 captures")).assertExists()
        compose.onNode(hasText("–", substring = false), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Survival: furthest travelled").assertExists()
        compose.onNodeWithText("Distance", useUnmergedTree = true).assertExists()

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
        val repository = records(row("Alex", 1, 38, 4, 1), row("Alex", 2, 30, 1, 2, mode = GameMode.PORTALS))
        val viewModel = StatisticsViewModel(repository)
        showcase.show { LudoPlusTheme { StatisticsScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("Games played") and hasText("1")).fetchSemanticsNodes().isNotEmpty() }
        pickMode("Statistics", from = "Classic", name = "All Modes")
        compose.waitUntil { compose.onAllNodes(hasText("Games played") and hasText("2")).fetchSemanticsNodes().isNotEmpty() }
        pickMode("Statistics", from = "All Modes", name = "Portals")
        compose.waitUntil { compose.onAllNodes(hasText("Games played") and hasText("1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Wins") and hasText("0")).assertExists()
        compose.onNode(hasContentDescription("6 players")).performClick()
        compose.waitUntil { compose.onAllNodes(hasText("No Portals games with 6 players yet.")).fetchSemanticsNodes().isNotEmpty() }
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

    @Test
    fun `in landscape the Leaderboard's counts share the drop-down's row - over a table at most 640dp wide`() = uiTest(TestScreen(891, 411)) {
        val viewModel = LeaderboardViewModel(records(row("Alex", 1, 38, 4, 1), row("Jo", 1, 42, 2, 2)))
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("Classic: fastest wins")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("1st, Alex, 38 turns, 4 captures")).assertIsDisplayed()
        val picker = field("Leaderboard", "Classic").fetchSemanticsNode().boundsInRoot
        val count = compose.onNode(hasContentDescription("4 players")).fetchSemanticsNode().boundsInRoot
        assertTrue(count.left >= picker.right, "the counts aren't beside the drop-down: $count, $picker")
        assertTrue(count.top < picker.bottom && count.bottom > picker.top, "the counts aren't on the drop-down's row: $count, $picker")
        val rowWidth = count.right - picker.left
        assertTrue(rowWidth <= 640f, "the row is ${rowWidth}dp wide")
    }

    @Test
    fun `in landscape Statistics has its counts on the drop-down's row - and two cards to a row, the odd one out on its own`() = uiTest(TestScreen(891, 411)) {
        val viewModel = StatisticsViewModel(records(row("Alex", 1, 38, 4, 1), row("Jo", 2, 42, 2, 1), row("Sam", 3, 50, 1, 1)))
        showcase.show { LudoPlusTheme { StatisticsScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("Alex")).fetchSemanticsNodes().isNotEmpty() }
        val picker = field("Statistics", "Classic").fetchSemanticsNode().boundsInRoot
        val all = compose.onNode(hasContentDescription("All games")).fetchSemanticsNode().boundsInRoot
        assertTrue(all.left >= picker.right && all.top < picker.bottom, "the counts aren't beside the drop-down: $all, $picker")
        val alex = compose.onNodeWithText("Alex").fetchSemanticsNode().boundsInRoot
        val jo = compose.onNodeWithText("Jo").fetchSemanticsNode().boundsInRoot
        assertEquals(alex.top, jo.top, "Alex's and Jo's cards aren't side by side")
        assertTrue(jo.left > alex.right, "Jo's card isn't right of Alex's")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Sam"))
        val sam = compose.onNodeWithText("Sam").fetchSemanticsNode().boundsInRoot
        assertEquals(alex.left, sam.left, "Sam's card isn't under Alex's")
    }
}
