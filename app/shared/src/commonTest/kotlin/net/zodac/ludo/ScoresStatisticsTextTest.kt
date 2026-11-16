package net.zodac.ludo

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.scores.InMemoryScoreDao
import net.zodac.ludo.data.scores.ScoreEntry
import net.zodac.ludo.data.scores.ScoreRepository
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.ui.scores.ScoresScreen
import net.zodac.ludo.ui.scores.ScoresViewModel
import net.zodac.ludo.ui.statistics.StatisticsScreen
import net.zodac.ludo.ui.statistics.StatisticsViewModel
import net.zodac.ludo.ui.theme.LudoPlusTheme

/** The Leaderboard's and Statistics' words: titles, empty states, column headings, paging, and the delete question. */
class ScoresStatisticsTextTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun showScores(rows: List<ScoreEntry>) {
        val viewModel = ScoresViewModel(ScoreRepository(InMemoryScoreDao(rows)))
        showcase.show { LudoPlusTheme { ScoresScreen(viewModel = viewModel, onBack = {}) } }
    }

    @Test
    fun `the Leaderboard and Statistics are titled and say when they're empty - and the table has its column headings`() = uiTest {
        showScores(emptyList())
        compose.onNodeWithText("Leaderboard").assertExists()
        compose.onNodeWithText("No scores in Standard yet").assertExists()
        // The drop-down, on the board it opens on: one control, named and valued.
        compose.onNodeWithContentDescription("Leaderboard, Standard").assertExists()

        showScores(listOf(rowOf(1, 300, GameMode.STANDARD)))
        listOf("#", "Player", "5x", "Score").forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }

        val viewModel = StatisticsViewModel(ScoreRepository(InMemoryScoreDao(emptyList())), SettingsRepository(InMemoryPreferences()))
        showcase.show { LudoPlusTheme { StatisticsScreen(viewModel = viewModel, onBack = {}) } }
        compose.onNodeWithText("Statistics").assertExists()
        compose.onNodeWithText("No statistics yet - play a game!").assertExists()
    }
}
