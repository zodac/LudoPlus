package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludoplus.app.LocalAppContainer
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.settings.SettingsScreen
import net.zodac.ludoplus.ui.settings.settingsViewModel
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(360, 800)

/**
 * Each on/off setting is one toggle the width of its row - its label and switch a single target, for
 * a tap and for a screen reader - rather than a lone switch beside an unconnected label; Graphics is a
 * button row opening a page of its own (GraphicsScreenTest).
 */
class SettingsRowsTest : UiTest() {

    @Test
    fun `every setting is named - an on-off one a single toggle - Graphics a button opening its page`() = uiTest(SCREEN) {
        val viewModel = settingsViewModel()
        var graphicsOpened = 0
        compose.setContent {
            // Only for the version line at the foot of the page.
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                LudoPlusTheme { SettingsScreen(viewModel = viewModel, onBack = {}, onGraphics = { graphicsOpened++ }) }
            }
        }
        // The page names every switch - and its version and two links.
        listOf("Settings", "Sound effects", "Vibration", "Confirm leaving game", "About", "Licences").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNode(hasText("Version ", substring = true)).assertExists()

        // Compact rows: a switch row is the 48dp touch target's height, where a ListItem would make it 56dp.
        compose.onNode(hasText("Sound effects") and isToggleable()).assertHeightIsEqualTo(48.dp)

        // Tapping a setting's label flips it - label and switch are one toggle.
        val vibration = compose.onNode(hasText("Vibration") and isToggleable())
        vibration.assertIsOn()
        vibration.performClick()
        vibration.assertIsOff()

        // Graphics - the Orientation, Animations and Advanced graphics settings - is one button row, the 48dp touch target's height,
        // opening its page; none of those settings is on this one.
        val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        val graphics = compose.onNode(hasText("Graphics") and hasClickAction() and isButton)
        graphics.performScrollTo().assertHeightIsEqualTo(48.dp)
        graphics.performClick()
        assertEquals(1, graphicsOpened)
        listOf("Animations", "Orientation", "Advanced graphics settings").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }
}
