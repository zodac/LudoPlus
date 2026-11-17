package net.zodac.ludo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludo.platform.LocalPlatformServices
import net.zodac.ludo.platform.SilentPlatformServices
import net.zodac.ludo.ui.common.LocalReduceMotion
import net.zodac.ludo.ui.rules.RulesScreen
import net.zodac.ludo.ui.rules.resetRulesPageMemory
import net.zodac.ludo.ui.theme.LudoPlusTheme

/**
 * The Rules page row scrolls, and nothing on screen says how far - so it's the semantics that tell a TalkBack user there
 * are more tabs than the ones in view, as "Tab, 1 of 5" rather than just "Tab". Tests run on a 360dp phone, where the
 * row doesn't fit.
 */
class RulesScreenAccessibilityTest : UiTest() {

    private val phone = TestScreen(360, 800)

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    /** A page's title - which can read the same as its tab. */
    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private val showcase = Showcase { compose }

    /** A fresh Rules screen - each call starts again on the first page. */
    private fun showRules(reduceMotion: Boolean = false) = showcase.show {
        resetRulesPageMemory()
        CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices, LocalReduceMotion provides reduceMotion) {
            LudoPlusTheme { RulesScreen(onBack = {}) }
        }
    }

    private fun openPage(name: String) {
        compose.onNode(isTab and hasText(name)).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun underReducedMotionATabChevronMovesTheRowAtOnce() = uiTest(phone) {
        showRules(reduceMotion = true)
        compose.waitForIdle()
        fun firstTabLeft() = compose.onNode(isTab and hasText("How to Play")).fetchSemanticsNode().boundsInRoot.left
        val start = firstTabLeft()
        compose.mainClock.autoAdvance = false
        compose.onNode(hasAnyChild(isTab and hasText("How to Play"))).performTouchInput { click(centerRight - Offset(24.dp.toPx(), 0f)) }
        compose.mainClock.advanceTimeBy(48)
        val moved = firstTabLeft()
        compose.mainClock.advanceTimeBy(2000)
        assertTrue(moved < start, "the row stayed where it was")
        assertEquals(firstTabLeft(), moved, "the row was still moving after the tap's frames")
    }

    @Test
    fun theTabRowSaysHowManyTabsItHasAndWhereEachTabIsAndEveryPageCanBeReachedByTabSwipeOrChevron() = uiTest(phone) {
        showRules()
        // The edge chevrons are not extra TalkBack stops: every clickable node is a tab, the app bar's back arrow, or How to
        // Play's Play the Tutorial. The chevrons only repeat what the tabs (and their "1 of 5") already give TalkBack, so
        // they're kept out of it, and the diagrams are pictures, not buttons.
        val tabs = compose.onAllNodes(isTab).fetchSemanticsNodes()
        assertEquals(5, tabs.size)
        assertEquals(tabs.size + 2, compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes().size)

        // The row says how many tabs it has, and each tab its place in it.
        val row = generateSequence(tabs.first().parent) { it.parent }.first { SemanticsProperties.CollectionInfo in it.config }
        assertEquals(5, row.config[SemanticsProperties.CollectionInfo].columnCount)
        assertEquals(tabs.indices.toList(), tabs.map { it.config[SemanticsProperties.CollectionItemInfo].columnIndex })

        // With one group of pages, there's no group row.
        assertEquals(0, compose.onAllNodes(isTab and hasText("Gameplay")).fetchSemanticsNodes().size)

        // The footer says which page, and is announced when it changes.
        compose.onNodeWithText("1 of 5").assertIsDisplayed()
        assertEquals(
            LiveRegionMode.Polite,
            compose.onNodeWithContentDescription("Gameplay, page 1 of 5").fetchSemanticsNode().config[SemanticsProperties.LiveRegion],
        )

        // A swipe on the page moves to the next one.
        compose.onNode(hasScrollAction() and hasAnyChild(isHeading and hasText("How to Play Ludo Plus"))).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNode(isTab and hasText("The Board")).assertIsSelected()
        compose.onNode(hasContentDescription("Gameplay, page 2 of 5")).assertExists()

        // Tapping the end chevron scrolls the tabs. It has no semantics node of its own, so it's tapped where it sits: over
        // the row's end.
        compose.onNode(isTab and hasText("Winning")).assertIsNotDisplayed()
        compose.onNode(hasAnyChild(isTab and hasText("How to Play"))).performTouchInput { click(centerRight - Offset(24.dp.toPx(), 0f)) }
        compose.waitForIdle()
        compose.onNode(isTab and hasText("How to Play")).assertIsNotDisplayed()
        // Still on the same page: the chevron took the tap and scrolled the tabs, rather than the tap falling through to
        // a tab under it.
        compose.onNode(isTab and hasText("The Board")).assertIsSelected()

        // An offscreen tab can be reached and opens its page - the tab, not the page's title, which reads the same.
        showRules()
        openPage("Winning")
        compose.onNode(isTab and hasText("Winning")).assertIsSelected()
        compose.onNode(isHeading and hasText("Winning")).assertIsDisplayed()
        compose.onNode(hasContentDescription("Gameplay, page 5 of 5")).assertExists()
    }

    @Test
    fun aLongPagesDiagramScrollsClearOfTheFooter() = uiTest(phone) {
        showRules()
        // Scrolled right to the end, as a player would, the diagram that ends the page must finish above the footer that
        // floats over the pages. (Not performScrollTo, which stops as soon as it's inside the scroll area - and that area
        // deliberately runs on under the footer.)
        openPage("Your Turn")
        val page = compose.onNode(hasScrollAction() and hasAnyChild(isHeading and hasText("Your Turn")))
        repeat(5) { page.performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val diagramBottom = compose.onNode(hasContentDescription("Diagram: the die shows 6", substring = true)).fetchSemanticsNode().boundsInRoot.bottom
        val footerTop = compose.onNode(hasContentDescription(", page ", substring = true)).fetchSemanticsNode().boundsInRoot.top
        assertTrue(diagramBottom <= footerTop, "the diagram ends at $diagramBottom, the footer starts at $footerTop")
    }

    /**
     * A tab row that scrolls itself on every selection (as Material's re-centring did) takes a tap while it's scrolling as
     * "stop", not as a tap on the tab - so a second tap soon after the first went nowhere. Real touches, 50ms apart.
     */
    @Test
    fun aSecondPageTapSoonAfterTheFirstStillLands() = uiTest(phone) {
        compose.mainClock.autoAdvance = false
        showRules()
        compose.mainClock.advanceTimeBy(500)
        compose.tapTab(compose.onNode(isTab and hasText("Your Turn")))
        compose.mainClock.advanceTimeBy(50)
        compose.tapTab(compose.onNode(isTab and hasText("The Board")))
        compose.mainClock.advanceTimeBy(2_000)

        compose.onNode(isTab and hasText("The Board")).assertIsSelected()
    }
}
