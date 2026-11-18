package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.rules.RulesScreen
import net.zodac.ludoplus.ui.rules.resetRulesPageMemory
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import org.robolectric.annotation.Config

/** A page's scrollbar thumb, on a real page: Rules' Your Turn, whose bar is in a margin narrower than a finger. */
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class RulesScrollbarThumbTest : UiTest() {

    private val scrolls = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    private fun ComposeUiTest.place() = onNode(scrolls).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]

    private fun ComposeUiTest.showRules() {
        resetRulesPageMemory()
        setContent { CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) { LudoPlusTheme { RulesScreen(onBack = {}) } } }
        waitForIdle()
        onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasText("Your Turn")).performClick()
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        // The thumb's place comes from its drawing, which Robolectric does only for a capture.
        onRoot().captureToImage()
        assertTrue(place().maxValue() > 0f, "the page has to be too long to fit")
    }

    @Test
    fun `the thumb is pulled from over the page, past its margin, the length of the page`() = uiTest {
        showRules()
        val top = onNode(scrolls).fetchSemanticsNode().boundsInRoot.top
        onRoot().performTouchInput {
            // 8dp in from the 20dp margin, where only the thumb's handle reaches.
            down(Offset(width - 28.dp.toPx(), top + 5.dp.toPx()))
            moveBy(Offset(0f, height.toFloat()))
            up()
        }
        waitForIdle()
        assertEquals(place().maxValue(), place().value())
    }
}
