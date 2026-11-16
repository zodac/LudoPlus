package net.zodac.ludo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.zodac.ludo.app.LocalAppContainer
import net.zodac.ludo.online.GameServices
import net.zodac.ludo.online.GameServicesState
import net.zodac.ludo.online.LocalGameServices
import net.zodac.ludo.online.NoGameServices
import net.zodac.ludo.online.OnlineAccount
import net.zodac.ludo.platform.LocalPlatformServices
import net.zodac.ludo.platform.SilentPlatformServices
import net.zodac.ludo.ui.settings.SettingsScreen
import net.zodac.ludo.ui.settings.SettingsViewModel
import net.zodac.ludo.ui.settings.settingsViewModel
import net.zodac.ludo.ui.theme.LudoPlusTheme

private class AchievementsUiServices(state: GameServicesState) : GameServices by NoGameServices {
    override val serviceName = "Test Games"
    override val state: StateFlow<GameServicesState> = MutableStateFlow(state)
    override val canShowAchievementsUi = state is GameServicesState.SignedIn
}

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(360, 800)

/**
 * Settings has no achievements row at all, and while signed in, Reset leaderboard's confirmation says the service's
 * boards keep their scores.
 */
class SettingsOnlineRowsTest : UiTest() {

    private fun show(services: GameServices) {
        val viewModel = settingsViewModel()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalGameServices provides services,
                LocalPlatformServices provides SilentPlatformServices,
            ) {
                LudoPlusTheme { SettingsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
    }

    @Test
    fun `signed in - Settings has neither Reset achievements nor a link to the service's achievements`() = uiTest(SCREEN) {
        show(AchievementsUiServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "k"))))
        compose.onNodeWithText("Reset leaderboard").performScrollTo()
        compose.onNodeWithText("Reset achievements").assertDoesNotExist()
        compose.onNodeWithText("View in Test Games").assertDoesNotExist()
    }

    @Test
    fun `signed out - Settings has no Reset achievements`() = uiTest(SCREEN) {
        show(AchievementsUiServices(GameServicesState.SignedOut()))
        compose.onNodeWithText("Reset leaderboard").performScrollTo()
        compose.onNodeWithText("Reset achievements").assertDoesNotExist()
    }

    private val resetMessage = "Every recorded score will be deleted, clearing the Leaderboard screen - and Statistics with it, since it's " +
        "calculated from the same scores. This can't be undone. Achievements and settings are not affected, though any achievement " +
        "progress measured against the leaderboard will start over."

    private fun resetLeaderboardMessage(services: GameServices): String {
        show(services)
        compose.onNodeWithText("Reset leaderboard").performScrollTo().performClick()
        compose.waitForIdle()
        return compose.onNodeWithText("Every recorded score", substring = true).fetchSemanticsNode()
            .config[SemanticsProperties.Text].joinToString("") { it.text }
    }

    @Test
    fun `signed in - Reset leaderboard's confirmation says the service's boards keep their scores`() = uiTest(SCREEN) {
        val message = resetLeaderboardMessage(AchievementsUiServices(GameServicesState.SignedIn(OnlineAccount("Ann", null, "k"))))
        assertEquals("$resetMessage Scores already sent to Test Games stay there.", message)
    }

    @Test
    fun `signed out - Reset leaderboard's confirmation as before`() = uiTest(SCREEN) {
        assertEquals(resetMessage, resetLeaderboardMessage(AchievementsUiServices(GameServicesState.SignedOut())))
    }
}
