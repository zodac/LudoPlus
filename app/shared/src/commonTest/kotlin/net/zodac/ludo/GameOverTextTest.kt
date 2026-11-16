package net.zodac.ludo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import net.zodac.ludo.game.GameEngine
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.model.ScoreCategory
import net.zodac.ludo.platform.LocalPlatformServices
import net.zodac.ludo.platform.SilentPlatformServices
import net.zodac.ludo.ui.game.GameOverScreen
import net.zodac.ludo.ui.theme.LudoPlusTheme

/** The results page's words: its title and buttons, the winner's card, and why a tie went the way it did. */
class GameOverTextTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun show(scores: List<Map<ScoreCategory, Int>>) {
        val configs = scores.indices.map { PlayerConfig(slot = it + 1, type = PlayerType.HUMAN, name = "Player${it + 1}") }
        val started = GameEngine.newGame(configs, GameMode.STANDARD)
        val state = started.copy(
            players = started.players.mapIndexed { index, player ->
                player.copy(scorecard = player.scorecard + scores[index].mapValues { listOf(it.value) })
            },
        )
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { GameOverScreen(state = state, onBackToMenu = {}, onPlayAgain = {}, onReviewScorecards = {}) }
            }
        }
    }

    @Test
    fun `the page is titled Game Over with its three buttons - the winner's card says Winner - and a tie on score says what decided it`() = uiTest {
        show(listOf(mapOf(ScoreCategory.CHANCE to 25), mapOf(ScoreCategory.CHANCE to 10)))
        listOf("Game Over", "Review Scorecards", "Main Menu", "Play Again").forEach { compose.onNodeWithText(it).assertExists() }
        // On the trophy as well.
        compose.onNodeWithText("Winner").assertExists()
        compose.onNodeWithContentDescription("Winner", useUnmergedTree = true).assertExists()

        // Same total, but the first player zeroed a box the second left empty: more zeroed categories wins the tie.
        show(listOf(mapOf(ScoreCategory.CHANCE to 20, ScoreCategory.ONES to 0), mapOf(ScoreCategory.CHANCE to 20)))
        compose.onNodeWithText("Won on more zeroed categories").assertExists()
    }
}
