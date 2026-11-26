package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.rules.RulesScreen
import net.zodac.ludoplus.ui.rules.resetRulesPageMemory
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** The Rules on a phone on its side: the group tabs fit their row, and a page's picture is beside its text, all on screen. */
class RulesLandscapeTest : UiTest() {

    private val phone = TestScreen(891, 411, density = 3f)

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    private val isPicture = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)

    private fun showRules() = compose.setContent {
        resetRulesPageMemory()
        CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
            LudoPlusTheme { RulesScreen(onBack = {}) }
        }
    }

    @Test
    fun `the group tabs' row has room for its labels - it doesn't scroll`() = uiTest(phone) {
        showRules()
        compose.waitForIdle()
        val gameplay = compose.onNode(isTab and hasText("Gameplay")).fetchSemanticsNode()
        val row = generateSequence(gameplay.parent) { it.parent }.first { SemanticsProperties.HorizontalScrollAxisRange in it.config }
        assertEquals(0f, row.config[SemanticsProperties.HorizontalScrollAxisRange].maxValue(), "the group row scrolls")
    }

    @Test
    fun `a page's diagram and a mode's board are beside the text - wholly on screen`() = uiTest(phone) {
        showRules()
        for ((tab, title) in listOf("The Board" to "The Board", "Game Modes" to "Game Modes", "Colour Rush" to "Colour Rush")) {
            compose.onNode(isTab and hasText(tab)).performClick()
            compose.waitForIdle()
            if (tab == "Game Modes") continue
            val heading = compose.onNode(isHeading and hasText(title)).fetchSemanticsNode().boundsInRoot
            val picture = compose.onNode(isPicture).fetchSemanticsNode().boundsInRoot
            val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue(picture.left > heading.right, "$title's picture isn't beside its text: $picture, heading $heading")
            assertTrue(picture.top < heading.bottom, "$title's picture starts under its heading: $picture, heading $heading")
            assertTrue(picture.bottom <= screen.bottom && picture.right <= screen.right, "$title's picture runs off the screen: $picture")
        }
    }
}
