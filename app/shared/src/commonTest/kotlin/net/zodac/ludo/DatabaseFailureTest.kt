package net.zodac.ludo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.app.BuildInfo
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.achievements.AchievementsRepository
import net.zodac.ludo.data.game.InProgressGameRepository
import net.zodac.ludo.data.scores.InMemoryScoreDao
import net.zodac.ludo.data.scores.ScoreDao
import net.zodac.ludo.data.scores.ScoreRepository
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.online.OnlineSettingsRepository
import net.zodac.ludo.purchases.PurchasesRepository
import net.zodac.ludo.ui.common.DatabaseFailurePage
import net.zodac.ludo.ui.theme.LudoPlusTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(360, 800)

/**
 * Scores that can't be opened - a migration missing or failing - are said so in place of the app, with the error to
 * report, rather than crashing the first page to read one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseFailureTest : UiTest() {

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun TestScope.container(dao: ScoreDao) = AppContainer(
        scoreRepository = ScoreRepository(dao),
        settingsRepository = SettingsRepository(InMemoryPreferences()),
        achievementsRepository = AchievementsRepository(InMemoryPreferences()),
        inProgressGameRepository = InProgressGameRepository(InMemoryPreferences()),
        onlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()),
        purchasesRepository = PurchasesRepository(InMemoryPreferences()),
        buildInfo = BuildInfo(versionName = "test", isDebug = false),
        scope = backgroundScope,
    )

    @Test
    fun `the container opens the scores as it's built - and keeps the stack trace of why it couldn't`() = runTest {
        val opens = container(InMemoryScoreDao())
        runCurrent()
        assertNull(opens.databaseFailure.value)

        val migration = IllegalStateException("A migration from 2 to 3 was required but not found.")
        val fails = container(object : ScoreDao by InMemoryScoreDao() {
            override suspend fun count(): Int = throw migration
        })
        runCurrent()
        assertEquals(migration.stackTraceToString(), fails.databaseFailure.value)
    }

    @Test
    fun `the page names what happened under a heading - what to do - and the error to report`() = uiTest(SCREEN) {
        val error = "IllegalStateException: A migration from 2 to 3 was required but not found."
        compose.setContent { LudoPlusTheme { DatabaseFailurePage(details = error) } }
        compose.onNode(isHeading and hasText("Your scores can't be opened")).assertIsDisplayed()
        listOf(
            "Ludo Plus couldn't open the file that holds your Leaderboard and Statistics, so it has stopped here rather than risk changing it.",
            "Nothing has been deleted. Updating the app to its latest version usually puts this right.",
            "If it keeps happening, these details will help when you report it:",
            error,
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }
}
