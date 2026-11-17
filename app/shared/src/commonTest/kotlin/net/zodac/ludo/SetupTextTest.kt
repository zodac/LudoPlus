package net.zodac.ludo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.platform.LocalPlatformServices
import net.zodac.ludo.platform.SilentPlatformServices
import net.zodac.ludo.ui.game.GameViewModel
import net.zodac.ludo.ui.setup.GameSetupScreen
import net.zodac.ludo.ui.theme.LudoPlusTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(411, 891)

/**
 * The New Game form's words - and that an empty name field shows its seat's default as a hint, not as text that was typed.
 * On a wide phone: at 360dp a computer player's difficulties are their initials (see DifficultySelector).
 */
class SetupTextTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun show(viewModel: GameViewModel = gameViewModel()) = showcase.show {
        CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
            LudoPlusTheme { GameSetupScreen(viewModel = viewModel, onStartGame = {}, onBack = {}) }
        }
    }

    @Test
    fun `the form's words - its title and section - a seat's default as a hint - a clash with it - and the seat chips and difficulties`() = uiTest(SCREEN) {
        val viewModel = gameViewModel()
        show(viewModel)
        listOf("New Game", "Game Mode", "Start Game").forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }

        // An empty name field shows its seat's default as a hint - and a name typed over it replaces it.
        compose.onNodeWithText("Player 1").assertExists()
        viewModel.setupForm.setPlayerName(1, "Wolfgang")
        compose.waitForIdle()
        compose.onNodeWithText("Wolfgang").assertExists()
        compose.onAllNodesWithText("Player 1").assertCountEquals(0)
        compose.onNodeWithContentDescription("Player 1 name").assertExists()

        // A seat's default clashes with another seat that typed the same words.
        val clash = gameViewModel()
        clash.setupForm.setPlayerType(2, PlayerType.HUMAN)
        clash.setupForm.setPlayerType(3, PlayerType.HUMAN)
        clash.setupForm.setPlayerName(2, "player 3")
        show(clash)
        compose.onNodeWithText("Names must be unique").assertExists()

        // The seat chips and a computer player's difficulties are named: seats 2 to 4 are computer players by default.
        show(gameViewModel())
        compose.onAllNodesWithText("CPU").assertCountEquals(3)
        listOf("Easy", "Medium", "Hard").forEach { compose.onAllNodesWithText(it).assertCountEquals(3) }
    }

    @Test
    fun `the tutorial link shows until a first game is finished - and opens the tutorial`() = uiTest(SCREEN) {
        var opened = 0
        val viewModel = gameViewModel()
        var link by mutableStateOf(true)
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { GameSetupScreen(viewModel = viewModel, onStartGame = {}, onBack = {}, showTutorialLink = link, onTutorial = { opened++ }) }
            }
        }
        compose.onNodeWithText("New to the game? Play the tutorial").performClick()
        assertEquals(1, opened)
        link = false
        compose.waitForIdle()
        compose.onAllNodesWithText("New to the game? Play the tutorial").assertCountEquals(0)
    }

    @Test
    fun `the Players row - each number said as a count of players - picking one shows that many players`() = uiTest(SCREEN) {
        val viewModel = gameViewModel()
        show(viewModel)
        compose.onAllNodesWithText("Players").assertCountEquals(1)
        (2..8).forEach { compose.onNodeWithContentDescription("$it players").assertExists() }
        compose.onAllNodesWithContentDescription("Player 5 colour", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("8 players").performClick()
        compose.waitForIdle()
        assertEquals(8, viewModel.setupForm.setup.value.playerCount)
        compose.onAllNodesWithContentDescription("Player 8 colour, Purple").assertCountEquals(1)
    }
}
