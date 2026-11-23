package net.zodac.ludoplus

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.GameResult
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.ui.records.LeaderboardScreen
import net.zodac.ludoplus.ui.records.LeaderboardViewModel
import net.zodac.ludoplus.ui.records.StatisticsScreen
import net.zodac.ludoplus.ui.records.StatisticsViewModel
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

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
        val viewModel = LeaderboardViewModel(repository) { GameMode.CLASSIC to 6 }
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Sam, 70 turns, 1 capture")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("1st, Alex", substring = true)).assertDoesNotExist()
        (2..8).forEach { compose.onNode(hasContentDescription("$it players")).assertExists() }
        compose.onNode(hasContentDescription("4 players")).performClick()
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Alex, 38 turns, 4 captures")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("1st, Sam", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `the Leaderboard starts at New Game's mode - each mode its own board - a segment a mode by its name`() = uiTest(TestScreen(411, 891)) {
        val repository = records(row("Alex", 1, 38, 4, 1), row("Sam", 1, 22, 1, 2, mode = GameMode.QUICK))
        val viewModel = LeaderboardViewModel(repository) { GameMode.QUICK to 4 }
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Sam, 22 turns, 1 capture")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Quick: fastest wins").assertExists()
        compose.onNode(hasContentDescription("Quick")).assertIsSelected()
        compose.onNode(hasContentDescription("Classic")).performClick()
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Alex, 38 turns, 4 captures")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("1st, Sam", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `Survival's Leaderboard ranks the most pawns left - then most captures - and its Statistics have no race`() = uiTest(TestScreen(411, 891)) {
        val survival = { name: String, captures: Int, lost: Int, endedAt: Long -> row(name, 1, 80, captures, endedAt, mode = GameMode.SURVIVAL).copy(timesCaptured = lost) }
        val repository = records(survival("Alex", 9, 5, 1), survival("Sam", 2, 1, 2), survival("Jo", 4, 1, 3))
        val viewModel = LeaderboardViewModel(repository) { GameMode.SURVIVAL to 4 }
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("1st, Jo, 5 pawns left, 4 captures")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("2nd, Sam, 5 pawns left, 2 captures")).assertExists()
        compose.onNode(hasContentDescription("3rd, Alex, 1 pawn left, 9 captures")).assertExists()
        compose.onNodeWithText("Survival: most pawns left").assertExists()

        val statistics = StatisticsViewModel(repository)
        showcase.show { LudoPlusTheme { StatisticsScreen(statistics, onBack = {}) } }
        // Among all modes, its wins set no fewest turns.
        compose.waitUntil { compose.onAllNodes(hasText("Fewest turns to win") and hasText("None")).fetchSemanticsNodes().isNotEmpty() }
        statistics.selectMode(GameMode.SURVIVAL)
        compose.waitUntil { compose.onAllNodes(hasText("Fewest turns to win")).fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasText("Pawns home")).assertDoesNotExist()
    }

    @Test
    fun `Statistics adds up every mode at first - or one mode's - All said as all modes - an empty mode said by name`() = uiTest(TestScreen(411, 891)) {
        val repository = records(row("Alex", 1, 38, 4, 1), row("Alex", 2, 30, 1, 2, mode = GameMode.QUICK))
        val viewModel = StatisticsViewModel(repository)
        showcase.show { LudoPlusTheme { StatisticsScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("Games played") and hasText("2")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("All modes")).assertIsSelected()
        compose.onNode(hasContentDescription("Quick")).performClick()
        compose.waitUntil { compose.onAllNodes(hasText("Games played") and hasText("1")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Wins") and hasText("0")).assertExists()
        compose.onNode(hasContentDescription("6 players")).performClick()
        compose.waitUntil { compose.onAllNodes(hasText("No Quick games with 6 players yet.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("All games")).performClick()
        viewModel.selectMode(GameMode.CLASSIC)
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
        val viewModel = StatisticsViewModel(repository)
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
    fun `empty Statistics asks for a game`() = uiTest(TestScreen(411, 891)) {
        val viewModel = StatisticsViewModel(records())
        showcase.show { LudoPlusTheme { StatisticsScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("No statistics yet. Play a game!")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Games played").assertDoesNotExist()
        viewModel.selectMode(GameMode.QUICK)
        compose.waitUntil { compose.onAllNodes(hasText("No Quick games yet.")).fetchSemanticsNodes().isNotEmpty() }
    }
}
