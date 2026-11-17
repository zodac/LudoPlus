package net.zodac.ludo

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.records.GameResult
import net.zodac.ludo.data.records.InMemoryGameResultDao
import net.zodac.ludo.ui.records.LeaderboardScreen
import net.zodac.ludo.ui.records.LeaderboardViewModel
import net.zodac.ludo.ui.records.StatisticsScreen
import net.zodac.ludo.ui.records.StatisticsViewModel
import net.zodac.ludo.ui.theme.LudoPlusTheme

/** The Leaderboard and Statistics pages: what they say, to the eye and to a screen reader, full and empty. */
class RecordsScreensTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun records(vararg rows: GameResult): GameRecordRepository {
        val dao = InMemoryGameResultDao()
        runBlocking { dao.insertGame(rows.toList()) }
        return GameRecordRepository(dao)
    }

    private fun row(name: String, place: Int, turns: Int, captures: Int, endedAt: Long) = GameResult(
        playerName = name, playerKey = name.lowercase(), isPrimaryPlayer = false, gameModeId = "classic", playerCount = 4,
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
    fun `an empty Leaderboard says how to get on it`() = uiTest(TestScreen(411, 891)) {
        val viewModel = LeaderboardViewModel(records())
        showcase.show { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("No wins in Classic yet. Win a game to set a time.")).fetchSemanticsNodes().isNotEmpty() }
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
    fun `empty Statistics asks for a game`() = uiTest(TestScreen(411, 891)) {
        val viewModel = StatisticsViewModel(records())
        showcase.show { LudoPlusTheme { StatisticsScreen(viewModel, onBack = {}) } }
        compose.waitUntil { compose.onAllNodes(hasText("No statistics yet. Play a game!")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Games played").assertDoesNotExist()
    }
}
