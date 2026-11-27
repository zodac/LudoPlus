package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.zodac.ludoplus.app.LocalAppContainer
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.online.GameServices
import net.zodac.ludoplus.online.GameServicesState
import net.zodac.ludoplus.online.LocalGameServices
import net.zodac.ludoplus.online.NoGameServices
import net.zodac.ludoplus.online.OnlineAccount
import net.zodac.ludoplus.ui.records.LeaderboardScreen
import net.zodac.ludoplus.ui.records.LeaderboardViewModel
import net.zodac.ludoplus.ui.settings.AboutDialog
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** A service in [state], noting each leaderboard it was asked to open. */
private class LinkServices(state: GameServicesState) : GameServices by NoGameServices {
    override val serviceName = "Test Games"
    override val state: StateFlow<GameServicesState> = MutableStateFlow(state)
    override val canShowLeaderboardUi: Boolean = state is GameServicesState.SignedIn
    val opened = mutableListOf<String?>()
    override fun showLeaderboardUi(leaderboardKey: String?) {
        opened += leaderboardKey
    }
}

private val SIGNED_IN = GameServicesState.SignedIn(OnlineAccount("Ann", null, "key"))

/** The ways out to the online service: the Leaderboard's global board, and About's privacy line. */
class OnlineLinksTest : UiTest() {

    private fun showLeaderboard(services: GameServices, mode: GameMode = GameMode.CLASSIC, players: Int = 4) {
        val viewModel = LeaderboardViewModel(GameRecordRepository(InMemoryGameResultDao()), MutableStateFlow(mode)) { players }
        compose.setContent {
            CompositionLocalProvider(LocalGameServices provides services) { LudoPlusTheme { LeaderboardScreen(viewModel, onBack = {}) } }
        }
        compose.waitUntil { compose.onAllNodes(hasContentDescription("Leaderboard, ", substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `signed in - the Leaderboard opens the same board on the service - spoken with the mode and count`() = uiTest(TestScreen(411, 891)) {
        val services = LinkServices(SIGNED_IN)
        showLeaderboard(services, GameMode.TWO_DICE, players = 6)
        compose.onNode(hasContentDescription("Global leaderboard for Two Dice, 6 players, on Test Games") and hasClickAction()).performClick()
        assertEquals(listOf<String?>("two_dice_6p"), services.opened)
    }

    @Test
    fun `signed out - or with no service - there is no global board`() = uiTest(TestScreen(411, 891)) {
        showLeaderboard(LinkServices(GameServicesState.SignedOut()))
        compose.onNode(hasText("Global leaderboard")).assertDoesNotExist()
    }

    @Test
    fun `About's privacy line names the service where there is one - and not where there isn't`() = uiTest(TestScreen(411, 891)) {
        var services: GameServices by mutableStateOf(LinkServices(GameServicesState.SignedOut()))
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides inMemoryAppContainer(), LocalGameServices provides services) {
                LudoPlusTheme { AboutDialog(onDismissRequest = {}) }
            }
        }
        compose.onNode(
            hasText(
                "LudoPlus holds no user data. Your games, achievements, and settings stay on this device, unless you sign in to Test Games. " +
                    "There are no ads, analytics, or tracking.",
            ),
            useUnmergedTree = true,
        ).assertExists()
        services = NoGameServices
        compose.onNode(hasText("unless you sign in", substring = true), useUnmergedTree = true).assertDoesNotExist()
    }
}
