package net.zodac.ludo

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludo.game.GameEngine
import net.zodac.ludo.model.Die
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.ui.game.ScorecardReviewScreen
import net.zodac.ludo.ui.theme.LudoPlusTheme

/**
 * D20's scorecard review, the tallest card with a tray under it, scrolls on no phone as small as 372x827dp (a 1116x2480px
 * phone at 480dpi) once the status and navigation bars (52dp, with 16dp to spare) are taken off. Smaller screens may scroll.
 */
class ScorecardReviewFitTest : UiTest() {

    private fun scrollRange(widthDp: Int, heightDp: Int): List<Float> {
        var range = emptyList<Float>()
        uiTest(TestScreen(widthDp, heightDp - BARS_AND_MARGIN_DP, 3f)) {
            val base = GameEngine.newGame(listOf(PlayerConfig(slot = 1, type = PlayerType.HUMAN, name = "Tester")), GameMode.D20)
            val state = base.copy(players = base.players.map { it.copy(lastRoll = List(5) { index -> Die(index + 1) }) })
            Showcase { compose }.show { LudoPlusTheme { ScorecardReviewScreen(state = state, onBack = {}) } }
            range = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes()
                .map { it.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() }
        }
        return range
    }

    @Test
    fun theD20ReviewFitsWithoutScrollingOnA372By827Phone() {
        assertEquals(listOf(0f), scrollRange(372, 827))
    }

    @Test
    fun theD20ReviewFitsWithoutScrollingOnTallerPhones() {
        assertEquals(listOf(0f), scrollRange(411, 891))
    }

    private companion object {
        /** The status and navigation bars' share of a phone's height, and 16dp more so the fit isn't borderline. */
        const val BARS_AND_MARGIN_DP = 68
    }
}
