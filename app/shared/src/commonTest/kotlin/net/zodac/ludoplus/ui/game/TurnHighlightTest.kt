package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import net.zodac.ludoplus.ui.theme.color

private const val ART = "art"

class TurnHighlightTest : UiTest() {

    @Test
    fun `the yard of the player whose turn it is has a white ring round its inside edge, until the game is over`() = uiTest {
        val red = PlayerColour.defaultFor(0).color
        var state by mutableStateOf(GameState.new(GameMode.CLASSIC, 4, first = 0))
        compose.setContent {
            LudoPlusTheme {
                LudoBoard(state, List(4) { PlayerColour.defaultFor(it) }, emptyList(), {}, Modifier.size(300.dp).testTag(ART))
            }
        }
        // Red's yard is the bottom-left six squares; this point is a tenth of a square in from its left edge.
        fun redYardEdge(): Color = pixel(0.1f / 15, 12f / 15)

        assertClose(Color.White, redYardEdge(), "Red's turn: the ring")
        state = state.copy(current = 1)
        assertClose(red, redYardEdge(), "Another's turn: red's yard as it is")
        state = state.copy(current = 0, phase = TurnPhase.Finished)
        assertClose(red, redYardEdge(), "Game over: no ring")
    }

    @Test
    fun `the die is ringed in the turn's colour - in full while it can be rolled, faintly the rest of the turn`() = uiTest {
        val green = PlayerColour.defaultFor(1).color
        var canRoll by mutableStateOf(true)
        var turnRing by mutableStateOf(true)
        compose.setContent {
            LudoPlusTheme {
                Box(Modifier.background(Color.Black).testTag(ART)) {
                    GameDie(value = 4, roll = null, canRoll = canRoll, onRoll = {}, ringColour = green, turnRing = turnRing, modifier = Modifier.size(64.dp))
                }
            }
        }
        // Halfway down the ring's left side.
        fun ring(): Color = pixel(1.5f / 64, 0.5f)

        assertClose(green, ring(), "Rollable: the ring in full")
        canRoll = false
        val faint = ring()
        assertTrue(faint.green in 0.15f..green.green - 0.2f, "Not rollable: a faint ring, was $faint")
        turnRing = false
        assertClose(Color.Black, ring(), "No turn ring: none")
    }

    /** The colour at ([x], [y]), each a share of the art's width and height. */
    private fun ComposeUiTest.pixel(x: Float, y: Float): Color {
        val map = compose.onNodeWithTag(ART).captureToImage().toPixelMap()
        return map[(x * map.width).toInt(), (y * map.height).toInt()]
    }

    private fun assertClose(expected: Color, actual: Color, message: String) {
        val close = abs(expected.red - actual.red) < 0.04f && abs(expected.green - actual.green) < 0.04f && abs(expected.blue - actual.blue) < 0.04f
        assertTrue(close, "$message: expected $expected, was $actual")
    }
}
