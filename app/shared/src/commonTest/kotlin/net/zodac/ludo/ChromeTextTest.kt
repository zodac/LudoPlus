package net.zodac.ludo

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludo.ui.common.ChoicePicker
import net.zodac.ludo.ui.common.ScreenScaffold
import net.zodac.ludo.ui.menu.MenuScreen
import net.zodac.ludo.ui.settings.AboutDialog
import net.zodac.ludo.ui.theme.LudoPlusTheme

/** The words of the shared chrome - the title bar's back arrow, a picker field, the main menu and the About dialog - as a player and TalkBack get them. */
class ChromeTextTest : UiTest() {

    @Test
    fun `the shared chrome's words - Back - a picker's choice and Choose - every menu destination - and the About dialog`() = uiTest {
        val showcase = Showcase { compose }
        showcase.show { LudoPlusTheme { ScreenScaffold(title = "Page", onBack = {}) {} } }
        compose.onNodeWithContentDescription("Back").assertExists()

        // A picker field says its choice after its title - and its tap is called Choose.
        showcase.show {
            LudoPlusTheme {
                ChoicePicker(title = "Thing", options = listOf("A", "B"), selected = "A", onSelect = {}, label = { "Pick $it" }, description = { "About $it" })
            }
        }
        val field = compose.onNodeWithContentDescription("Thing").fetchSemanticsNode().config
        assertEquals("Pick A. About A", field[SemanticsProperties.StateDescription])
        assertEquals("Choose Thing", field[SemanticsActions.OnClick].label)

        // The main menu names every destination - and splits Play when a game is saved.
        for (hasInProgressGame in listOf(true, false)) {
            showcase.show {
                LudoPlusTheme {
                    MenuScreen(
                        hasInProgressGame = hasInProgressGame,
                        onContinue = {}, onNewGame = {}, onAchievements = {}, onLeaderboard = {}, onStatistics = {}, onStyles = {}, onRules = {}, onSettings = {},
                    )
                }
            }
            if (hasInProgressGame) {
                listOf("Leaderboard", "Statistics", "Styles", "Rules", "Settings", "New Game", "Continue").forEach { compose.onNodeWithText(it).assertExists() }
                compose.onAllNodesWithText("Play").assertCountEquals(0)
            } else {
                compose.onNodeWithText("Play").assertExists()
                compose.onAllNodesWithText("Continue").assertCountEquals(0)
            }
        }

        // The About dialog says who made the app - and closes with its OK button.
        var closed = 0
        showcase.show { LudoPlusTheme { AboutDialog(onDismissRequest = { closed++ }) } }
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, closed)
        compose.onAllNodesWithText("About").assertCountEquals(1)
        listOf("Author", "The game", "Privacy", "Source code on GitHub", "Ludo on Wikipedia", "Full privacy policy").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("Ludo Plus is created by zodac.").assertExists()
        compose.onNodeWithText("Ludo Plus plays the rules of Ludo, the race game that grew from the ancient Indian game Pachisi.").assertExists()
        compose.onNodeWithText("Ludo Plus holds no user data. Your games, scores, and settings stay on this device, and there are no ads, analytics, or tracking.")
            .assertExists()
    }
}
