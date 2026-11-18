package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import kotlin.test.Test
import net.zodac.ludoplus.app.LocalAppContainer
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementVisibility
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.achievements.AchievementsScreen
import net.zodac.ludoplus.ui.achievements.resetAchievementsScrollMemory
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import org.jetbrains.compose.resources.getString

/** Leaving the Achievements page and coming back opens it where it was left, not at the top. */
class AchievementsScrollMemoryTest : UiTest() {

    private fun shows(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `coming back to the page opens it where it was left`() = uiTest {
        // The last achievement shown by name from the start, far down the list.
        val target = Achievement.entries.last { it.visibility == AchievementVisibility.NORMAL }
        val title = getString(target.title)
        resetAchievementsScrollMemory()
        val container = inMemoryAppContainer()
        var shown by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides container,
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                // A fresh ViewModel on each visit, as leaving the page discards it.
                if (shown) LudoPlusTheme { AchievementsScreen(viewModel = achievementsViewModel(), onBack = {}) }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) { shows("earned") }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(title))
        compose.onNodeWithText(title).assertIsDisplayed()

        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitUntil(timeoutMillis = 10_000) { shows("earned") }
        compose.waitForIdle()
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
