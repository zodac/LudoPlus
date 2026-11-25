package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import net.zodac.ludoplus.ui.theme.color

private const val BOARD = "board"
private const val Y = -1
private const val FRAME = 16

/** The Double power-up on the yard of a player whose rolls count double. */
class YardDoubleTest : UiTest() {

    private fun game(vararg doubledRolls: Int, powerUps: Map<Int, PowerUp> = emptyMap()): GameState {
        val new = GameState.new(GameMode.CHAOS, 4)
        return new.copy(
            current = 0,
            powerUps = powerUps,
            // None to come while a test plays.
            turnsToPowerUp = 5,
            players = new.players.mapIndexed { i, side -> side.copy(pawns = listOf(1, Y, Y, Y), doubledRolls = doubledRolls.getOrElse(i) { 0 }) },
        )
    }

    @Test
    fun `it's on the yards of those with rolls left to count double - and only theirs`() = uiTest {
        val state = show(game(0, 2, 0, 1))
        assertEquals(listOf(1, 3), yardsShown())
        state.value = game(3, 0, 0, 1)
        assertEquals(listOf(0, 3), frames(5 * FRAME).last())
    }

    @Test
    fun `it comes as the pawn lands on the Double - not before`() = uiTest {
        val rolled = LudoEngine.applyRoll(game(powerUps = mapOf(3 to PowerUp.DOUBLE)), 2)
        val state = show(rolled)
        state.value = LudoEngine.move(rolled, 0)
        val along = frames(2 * PAWN_STEP_MILLIS + 100)
        assertTrue(along.indexOfFirst { it.isNotEmpty() } * FRAME >= 2 * PAWN_STEP_MILLIS - FRAME, "Too soon: $along")
        assertEquals(listOf(0), along.last())
    }

    @Test
    fun `it stays while the pawn its last doubled roll moves is on its way`() = uiTest {
        val rolled = LudoEngine.applyRoll(game(1), 2)
        val state = show(rolled)
        state.value = LudoEngine.move(rolled, 0)
        val along = frames(4 * PAWN_STEP_MILLIS + 100)
        assertTrue(along.indexOfFirst { it.isEmpty() } * FRAME >= 4 * PAWN_STEP_MILLIS - FRAME, "Too soon: $along")
        assertEquals(emptyList(), along.last())
    }

    private fun ComposeUiTest.show(start: GameState): MutableState<GameState> {
        val state = mutableStateOf(start)
        mainClock.autoAdvance = false
        setContent {
            LudoPlusTheme {
                LudoBoard(state.value, List(4) { PlayerColour.defaultFor(it) }, emptyList(), {}, Modifier.size(330.dp).testTag(BOARD))
            }
        }
        mainClock.advanceTimeByFrame()
        yardsShown()
        return state
    }

    /** The yards it's on, on each frame of the next [millis]. */
    private fun ComposeUiTest.frames(millis: Int): List<List<Int>> = List(millis / FRAME) {
        // Drawn each frame, as a screen is: the move's clock starts on its first drawn frame.
        yardsShown().also { mainClock.advanceTimeByFrame() }
    }

    /** The seats whose yards it's drawn on: its colour under its symbol. */
    private fun ComposeUiTest.yardsShown(): List<Int> {
        val pixels = onNodeWithTag(BOARD).captureToImage().toPixelMap()
        val unit = pixels.width / ClassicBoardLayout.size
        return (0..3).filter { seat ->
            val at = (ClassicBoardLayout.yardBadge(seat, 4, YARD_BADGE_ROOM).centre + Offset(0f, YARD_BADGE_RADIUS * 0.65f)) * unit
            pixels[at.x.toInt(), at.y.toInt()] == PowerUp.DOUBLE.color
        }
    }
}
