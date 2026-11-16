package net.zodac.ludo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludo.game.GameEngine
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.ui.game.GameBoard
import net.zodac.ludo.ui.theme.LudoPlusTheme

/**
 * Score boxes only take a tap once a roll has fully played out - not while the cup is shaking, and
 * not while the dice it produced are still tumbling to a stop on the mat.
 */
class GameBoardScoringGateTest : UiTest() {

    private val rolled = GameEngine.rollDice(
        GameEngine.newGame(listOf(PlayerConfig(slot = 0, type = PlayerType.HUMAN, name = "Alex"))),
        Random(7),
    )

    private var rolling by mutableStateOf(false)
    private var diceSettling by mutableStateOf(false)

    private fun showBoard() {
        compose.setContent {
            LudoPlusTheme {
                GameBoard(
                    state = rolled,
                    rolling = rolling,
                    diceSettling = diceSettling,
                    canUndo = false,
                    onScoreCategory = {},
                    onCupTap = {},
                    onUndo = {},
                )
            }
        }
    }

    private fun tappableCount(rolling: Boolean, diceSettling: Boolean): Int {
        this.rolling = rolling
        this.diceSettling = diceSettling
        compose.waitForIdle()
        return compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
    }

    @Test
    fun scoresCanBeTappedOnlyOnceTheDiceHaveSettledAndSettlingDiceOfferNoMoreThanAShakingCup() = uiTest {
        showBoard()
        val settled = tappableCount(rolling = false, diceSettling = false)
        val settling = tappableCount(rolling = false, diceSettling = true)
        assertTrue(settled > settling, "settled dice should offer score boxes to tap ($settled) that settling ones don't ($settling)")
        assertEquals(tappableCount(rolling = true, diceSettling = false), settling)
    }
}
