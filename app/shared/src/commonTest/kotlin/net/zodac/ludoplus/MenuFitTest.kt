package net.zodac.ludoplus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.ui.menu.MenuScreen
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/**
 * The menu fitted to the height it's given: its spacing tightened first, then its buttons and logo, and scrolled only once
 * those are at their least. At 1 pixel to a dp, so a size in pixels is one in dp.
 */
class MenuFitTest : UiTest() {

    private var pawnTaps = 0

    /** Draws the menu [height]dp tall: what an ad banner leaves of the screen. */
    private fun show(height: Int, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                LudoPlusTheme {
                    Box(Modifier.height(height.dp)) {
                        MenuScreen(
                            hasInProgressGame = true,
                            onContinue = {}, onNewGame = {}, onAchievements = {}, onLeaderboard = {}, onStatistics = {}, onStyles = {}, onRules = {}, onSettings = {},
                            onLogoPawnTapped = { pawnTaps++ },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** How far the menu scrolls: 0 when it fits. */
    private fun scroll(): Float = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    private fun node(label: String) = compose.onNode(hasText(label)).fetchSemanticsNode()

    private fun top(label: String): Int = node(label).positionInRoot.y.toInt()

    private fun heights(): List<Int> = LABELS.map { node(it).size.height }

    private fun logoScale(): Float = node("LudoPlus").let { it.boundsInRoot.width / it.size.width }

    private fun assertAllShown(height: Int) = LABELS.forEach {
        assertEquals(node(it).size.height.toFloat(), node(it).boundsInRoot.height, "$it is whole")
        assertTrue(node(it).boundsInRoot.bottom <= height, "$it is on the page")
    }

    @Test
    fun `with the height for it the menu is full size`() = uiTest(TestScreen(374, 827)) {
        show(height = 794)
        assertEquals(0f, scroll())
        assertEquals(List(LABELS.size) { 56 }, heights())
        assertEquals(56 + 12, top("Leaderboard") - top("Achievements"))
        assertEquals(1f, logoScale())
    }

    @Test
    fun `a little short - as under an ad on a tall phone - only the spacing tightens`() = uiTest(TestScreen(374, 827)) {
        show(height = 677)
        assertEquals(0f, scroll())
        assertAllShown(677)
        assertEquals(List(LABELS.size) { 56 }, heights())
        assertTrue(top("Leaderboard") - top("Achievements") in 56 + 8 until 56 + 12)
        assertEquals(1f, logoScale())
    }

    @Test
    fun `shorter still the buttons and logo shrink too - and it doesn't scroll`() = uiTest(TestScreen(360, 740)) {
        show(height = 555)
        assertEquals(0f, scroll())
        assertAllShown(555)
        assertTrue(heights().all { it in 48 until 56 }, "${heights()}")
        assertTrue(logoScale() in 0.6f..0.99f, "${logoScale()}")
    }

    @Test
    fun `the logo's pawn is still found where it's drawn when smaller`() = uiTest(TestScreen(360, 740)) {
        show(height = 555)
        // The pawn's middle, 62dp down the 112dp art, is 54dp above the wordmark's top (4dp under the art) at full size.
        val wordmark = node("LudoPlus").boundsInRoot
        compose.onRoot().performTouchInput { click(Offset(wordmark.center.x, wordmark.top - 54 * logoScale())) }
        compose.waitForIdle()
        assertEquals(1, pawnTaps)
    }

    @Test
    fun `with everything at its least and no room left it scrolls to Settings`() = uiTest(TestScreen(360, 640)) {
        show(height = 455)
        assertTrue(scroll() > 0f)
        assertEquals(List(LABELS.size) { 48 }, heights())
        assertEquals(48 + 8, top("Leaderboard") - top("Achievements"))
        assertEquals(0.6f, logoScale(), 0.01f)
        compose.onNodeWithText("Settings").performScrollTo()
        assertSettingsShown(455)
    }

    private fun assertSettingsShown(height: Int) {
        assertEquals(48f, node("Settings").boundsInRoot.height)
        assertTrue(node("Settings").boundsInRoot.bottom <= height)
    }

    @Test
    fun `in landscape over an ad no button is squashed - and the logo stays full size beside them`() = uiTest(TestScreen(827, 374)) {
        show(height = 275)
        assertEquals(0f, scroll())
        assertAllShown(275)
        assertTrue(heights().all { it == 56 }, "${heights()}")
        assertEquals(1f, logoScale())
    }

    @Test
    fun `in landscape with too little height it scrolls rather than squash a button`() = uiTest(TestScreen(640, 360)) {
        show(height = 180)
        assertTrue(scroll() > 0f)
        assertTrue(heights().all { it >= 48 }, "${heights()}")
        compose.onNodeWithText("Settings").performScrollTo()
        assertSettingsShown(180)
    }

    @Test
    fun `at a 200 percent font a button is never shorter than its words`() = uiTest(TestScreen(374, 827)) {
        show(height = 500, fontScale = 2f)
        assertTrue(scroll() > 0f)
        assertTrue(heights().all { it >= 56 }, "${heights()}")
    }
}

private val LABELS = listOf("New Game", "Continue", "Achievements", "Leaderboard", "Statistics", "Styles", "Rules", "Settings")
