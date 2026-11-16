package net.zodac.ludo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.platform.LocalPlatformServices
import net.zodac.ludo.platform.SilentPlatformServices
import net.zodac.ludo.ui.game.GamePacing
import net.zodac.ludo.ui.game.GameScreen
import net.zodac.ludo.ui.game.GameViewModel
import net.zodac.ludo.ui.game.LeaveGameConfirmation
import net.zodac.ludo.ui.game.LeaveGameConfirmationDialog
import net.zodac.ludo.ui.game.LocalLeaveGameConfirmation
import net.zodac.ludo.ui.theme.LudoPlusTheme

/** Leaving a game: the back arrow asks first while the game is on, unless Settings says not to. */
class LeaveGameTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun show(viewModel: GameViewModel, onLeft: () -> Unit) {
        val confirmation = LeaveGameConfirmation()
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices, LocalLeaveGameConfirmation provides confirmation) {
                LudoPlusTheme {
                    GameScreen(viewModel, onBackToMenu = onLeft)
                    LeaveGameConfirmationDialog(confirmation)
                }
            }
        }
    }

    private fun startedGame(confirm: Boolean): GameViewModel {
        val settings = SettingsRepository(InMemoryPreferences())
        runBlocking { settings.setConfirmBeforeLeavingGame(confirm) }
        val viewModel = gameViewModel(settings)
        viewModel.setPacing(GamePacing(thinkMillis = 60_000))
        compose.waitUntil { viewModel.setupForm.setupRestored.value }
        assertTrue(viewModel.startGame { "Player $it" })
        compose.waitUntil { viewModel.confirmBeforeLeavingGame.value == confirm }
        return viewModel
    }

    @Test
    fun `back asks before leaving a game - Cancel stays - Leave goes to the menu`() = uiTest(TestScreen(411, 891)) {
        var left = 0
        show(startedGame(confirm = true)) { left++ }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Leave game?").assertExists()
        compose.onNodeWithText("Your progress is saved, so you can continue this game later.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Leave game?").assertDoesNotExist()
        assertEquals(0, left)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Leave").performClick()
        assertEquals(1, left)
    }

    @Test
    fun `with confirming switched off back leaves straight away`() = uiTest(TestScreen(411, 891)) {
        var left = 0
        show(startedGame(confirm = false)) { left++ }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Leave game?").assertDoesNotExist()
        assertEquals(1, left)
    }
}
