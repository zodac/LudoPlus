package net.zodac.ludoplus

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
import net.zodac.ludoplus.app.AppContainer
import net.zodac.ludoplus.app.BuildInfo
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.achievements.AchievementsRepository
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.data.records.GameResultDao
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.online.OnlineSettingsRepository
import net.zodac.ludoplus.purchases.PurchasesRepository
import net.zodac.ludoplus.ui.common.DatabaseFailurePage
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(360, 800)

/**
 * Records that can't be opened - a migration missing or failing - are said so in place of the app, with the error to
 * report, rather than crashing the first page to read one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseFailureTest : UiTest() {

    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun TestScope.container(dao: GameResultDao) = AppContainer(
        gameRecordRepository = GameRecordRepository(dao),
        settingsRepository = SettingsRepository(InMemoryPreferences()),
        achievementsRepository = AchievementsRepository(InMemoryPreferences()),
        inProgressGameRepository = InProgressGameRepository(InMemoryPreferences()),
        onlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()),
        purchasesRepository = PurchasesRepository(InMemoryPreferences()),
        buildInfo = BuildInfo(versionName = "test", isDebug = false),
        scope = backgroundScope,
    )

    @Test
    fun `the container opens the records as it's built - and keeps the stack trace of why it couldn't`() = runTest {
        val opens = container(InMemoryGameResultDao())
        runCurrent()
        assertNull(opens.databaseFailure.value)

        val migration = IllegalStateException("A migration from 2 to 3 was required but not found.")
        val fails = container(object : GameResultDao by InMemoryGameResultDao() {
            override suspend fun count(): Int = throw migration
        })
        runCurrent()
        assertEquals(migration.stackTraceToString(), fails.databaseFailure.value)
    }

    @Test
    fun `the page names what happened under a heading - what to do - and the error to report`() = uiTest(SCREEN) {
        val error = "IllegalStateException: A migration from 2 to 3 was required but not found."
        compose.setContent { LudoPlusTheme { DatabaseFailurePage(details = error) } }
        compose.onNode(isHeading and hasText("Your games can't be opened")).assertIsDisplayed()
        listOf(
            "LudoPlus couldn't open the file that holds your Leaderboard and Statistics, so it has stopped here rather than risk changing it.",
            "Nothing has been deleted. Updating the app to its latest version usually puts this right.",
            "If it keeps happening, these details will help when you report it:",
            error,
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }
}
