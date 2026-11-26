package net.zodac.ludoplus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.app.LocalAppContainer
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.achievements.AchievementsScreen
import net.zodac.ludoplus.ui.achievements.resetAchievementsScrollMemory
import net.zodac.ludoplus.ui.common.BACK_ARROW_OUTSET
import net.zodac.ludoplus.ui.common.ScreenScaffold
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/**
 * Where a page's app bar puts its back arrow, title and controls - lined up on the content's column in landscape. The
 * arrow is measured by its centre, as its button's bounds are smaller than its 48dp touch target.
 */
class PageHeaderLayoutTest : UiTest() {

    @BeforeTest
    fun openAtTheTop() = resetAchievementsScrollMemory()

    private fun showPage(wide: Boolean, scrollable: Boolean, landscapeWidth: Dp? = null) = compose.setContent {
        LudoPlusTheme {
            ScreenScaffold(title = "Page", onBack = {}, wide = wide, scrollable = scrollable, landscapeWidth = landscapeWidth) {
                Box(Modifier.fillMaxWidth().height(10.dp).testTag(CONTENT))
            }
        }
    }

    private fun assertNear(expected: Dp, actual: Dp, what: String) =
        assertEquals(expected.value, actual.value, 0.5f, what)

    @Test
    fun `in landscape the back arrow lines up with a wide page's content and the title centres on it`() = uiTest(TestScreen(1280, 800, 2f)) {
        showPage(wide = true, scrollable = false)
        val content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        val title = compose.onNodeWithText("Page").getBoundsInRoot()
        assertNear(content.left + ARROW_HALF, (back.left + back.right) / 2, "back arrow's centre")
        assertNear((content.left + content.right) / 2, (title.left + title.right) / 2, "title's centre")
    }

    @Test
    fun `in landscape a page's own width holds its content and its app bar - Dp Infinity the screen's whole width`() = uiTest(TestScreen(1280, 800, 2f)) {
        var width by mutableStateOf<Dp?>(640.dp)
        compose.setContent {
            LudoPlusTheme {
                ScreenScaffold(title = "Page", onBack = {}, landscapeWidth = width) { Box(Modifier.fillMaxWidth().height(10.dp).testTag(CONTENT)) }
            }
        }
        var content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        assertNear(640.dp, content.right - content.left, "content's width")
        assertNear(content.left + ARROW_HALF, (back.left + back.right) / 2, "back arrow's centre")
        width = Dp.Infinity
        compose.waitForIdle()
        content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
        assertTrue(content.right - content.left > 1200.dp, "the content isn't the screen's width: $content")
    }

    @Test
    fun `in landscape the back arrow lines up with a narrow scrolling page's content`() = uiTest(TestScreen(1280, 800, 2f)) {
        showPage(wide = false, scrollable = true)
        val content = compose.onNodeWithTag(CONTENT).getBoundsInRoot()
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        assertNear(content.left + ARROW_HALF, (back.left + back.right) / 2, "back arrow's centre")
    }

    @Test
    fun `in portrait the back arrow's 48dp target stays 4dp from the screen's edge`() = uiTest(TestScreen(411, 891, 3f)) {
        showPage(wide = true, scrollable = false)
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        assertNear(4.dp + BACK_ARROW_OUTSET + ARROW_HALF, (back.left + back.right) / 2, "back arrow's centre")
    }

    @Test
    fun `in landscape Achievements has its count left of the title and its switch right of it - both inside the list's column`() = uiTest(TestScreen(1280, 800, 2f)) {
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                LudoPlusTheme { AchievementsScreen(viewModel = achievementsViewModel(), onBack = {}) }
            }
        }
        compose.waitForIdle()
        val list = compose.onNode(hasScrollAction()).getBoundsInRoot()
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        val title = compose.onNodeWithText("Achievements").getBoundsInRoot()
        val count = compose.onNodeWithText(" earned", substring = true).getBoundsInRoot()
        val lockedOnly = compose.onNode(hasText("All") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .getBoundsInRoot()
        assertNear(list.left + ARROW_HALF, (back.left + back.right) / 2, "back arrow's centre")
        assertTrue(count.left >= back.right, "the count follows the back arrow")
        assertTrue(count.right < title.left, "the count is left of the title")
        assertTrue(lockedOnly.left > title.right, "The switch is right of the title")
        assertNear(list.right, lockedOnly.right, "The switch's end")
    }

    private companion object {
        const val CONTENT = "content"

        /** Half the 24dp arrow: lined up on the content's edge, its centre is this far inside it. */
        val ARROW_HALF = 12.dp
    }
}
