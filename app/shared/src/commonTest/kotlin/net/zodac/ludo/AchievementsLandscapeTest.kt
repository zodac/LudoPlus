package net.zodac.ludo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludo.app.LocalAppContainer
import net.zodac.ludo.model.Achievement
import net.zodac.ludo.model.AchievementVisibility
import net.zodac.ludo.navigation.AchievementScrollRequest
import net.zodac.ludo.navigation.AchievementScrollRequests
import net.zodac.ludo.platform.LocalPlatformServices
import net.zodac.ludo.platform.SilentPlatformServices
import net.zodac.ludo.ui.achievements.AchievementsScreen
import net.zodac.ludo.ui.achievements.resetAchievementsScrollMemory
import net.zodac.ludo.ui.theme.LudoPlusTheme
import org.jetbrains.compose.resources.getString

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(891, 411)

/** On a landscape page the list has two achievements to a row - and a banner's long press still finds its row. */
class AchievementsLandscapeTest : UiTest() {

    private fun showAchievements() {
        // The screen remembers where it was scrolled to for the whole process: start every test at the top.
        resetAchievementsScrollMemory()
        val viewModel = achievementsViewModel()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                LudoPlusTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        compose.waitUntil(timeoutMillis = 10_000) { shows("earned") }
    }

    private fun shows(text: String) = compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the first two achievements share a row - side by side`() = uiTest(SCREEN) {
        showAchievements()
        val first = compose.onNodeWithText("The Journey Begins").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithText("Let's Finish This").getUnclippedBoundsInRoot()
        assertEquals(first.top, second.top)
        assertTrue(second.left > first.right, "The second is beside the first, not under it")
    }

    @Test
    fun `a banner's long press scrolls to its achievement - counted in rows of two`() = uiTest(SCREEN) {
        // The last achievement shown by name from the start, far down the list.
        val target = Achievement.entries.last { it.visibility == AchievementVisibility.NORMAL }
        val title = getString(target.title)
        showAchievements()
        compose.onNodeWithText(title).assertDoesNotExist()
        AchievementScrollRequests.request(AchievementScrollRequest(target.id, animate = false))
        compose.waitUntil(timeoutMillis = 10_000) { shows(title) }
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
