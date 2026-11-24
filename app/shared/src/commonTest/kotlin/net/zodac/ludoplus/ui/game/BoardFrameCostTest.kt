package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.ui.style.DiceStyle
import net.zodac.ludoplus.ui.style.GameVisualTheme
import net.zodac.ludoplus.ui.style.LocalArtFrozen
import net.zodac.ludoplus.ui.style.LocalGameVisualTheme
import net.zodac.ludoplus.ui.style.surfacesPaintedForTest
import net.zodac.ludoplus.ui.theme.BoardIvory
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import net.zodac.ludoplus.ui.theme.color

private const val BOARD = "board"

/** What a pawn move and a die's tumble redraw and recompose on their frames. */
class BoardFrameCostTest : UiTest() {

    /** Player 1's first pawn on its start square, and the same game once it has moved it three squares on. */
    private fun startAndMoved(): Pair<GameState, GameState> {
        var state = GameState.new(GameMode.CLASSIC, 4)
        state = LudoEngine.move(LudoEngine.applyRoll(state, 6), 0)
        // The 6 rolls again: a 3 takes the pawn on.
        state = LudoEngine.applyRoll(state, 3)
        return state to LudoEngine.move(state, 0)
    }

    @Test
    fun `the board is painted once, and a pawn's move draws the pawn on its way without painting the board again`() = uiTest {
        val (before, after) = startAndMoved()
        var state by mutableStateOf(before)
        compose.mainClock.autoAdvance = false
        val painted = surfacesPaintedForTest()
        compose.setContent {
            LudoPlusTheme {
                LudoBoard(state, List(4) { PlayerColour.defaultFor(it) }, emptyList(), {}, Modifier.size(300.dp).testTag(BOARD))
            }
        }
        compose.mainClock.advanceTimeByFrame()
        capture()
        assertEquals(1, surfacesPaintedForTest() - painted, "Shown: the board painted")

        val red = PlayerColour.defaultFor(0).color
        val seat = before.players[0].seat
        val layout = BoardLayout.of(before.board)
        fun square(steps: Int): Offset = layout.centreOf(seat, PawnPosition.of(steps, before.board), 0) / layout.size
        state = after
        // Its second square's hop down and the next's start (the move starts on the frame after it's shown): drawn by the move
        // alone, as the pawns standing still leave it out.
        // Drawn each frame, as a screen is: the move's clock starts on its first drawn frame.
        repeat(11) {
            capture()
            compose.mainClock.advanceTimeByFrame()
        }
        assertClose(red, capture().at(square(1)), "Under way: the pawn on its way")
        compose.mainClock.advanceTimeBy(3_000L)
        val landed = capture()
        assertClose(red, landed.at(square(3)), "Landed: the pawn on its square")
        assertClose(BoardIvory, landed.at(square(1)), "Landed: the square it passed empty")
        assertEquals(1, surfacesPaintedForTest() - painted, "The move's frames paint no board")
    }

    @Test
    fun `a tumbling die recomposes as each face flicks past, not every frame, with each face's own art held still`() = uiTest {
        val style = CountingDice()
        var roll by mutableIntStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalGameVisualTheme provides GameVisualTheme(diceStyle = style)) {
                LudoPlusTheme { GameDie(value = 4, roll = roll, canRoll = false, onRoll = {}, modifier = Modifier.size(64.dp)) }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        style.shown.clear()
        roll = 1
        compose.mainClock.advanceTimeBy(DIE_TUMBLE_MILLIS + 200L)
        val frames = (DIE_TUMBLE_MILLIS + 200) / 16
        assertTrue(style.shown.size <= 9, "Composed ${style.shown.size} times over ~$frames frames: ${style.shown}")
        assertTrue(style.shown.dropLast(1).all { it.second }, "Every face that flicks past is held still: ${style.shown}")
        assertEquals(4 to false, style.shown.last(), "Landed on its value, moving again")
    }

    private fun ComposeUiTest.capture() = onNodeWithTag(BOARD).captureToImage().toPixelMap()

    /** The colour at [point], a share of the board's width and height. */
    private fun androidx.compose.ui.graphics.PixelMap.at(point: Offset): Color =
        this[(point.x * width).toInt(), (point.y * height).toInt()]

    private fun assertClose(expected: Color, actual: Color, message: String) {
        val close = abs(expected.red - actual.red) < 0.12f && abs(expected.green - actual.green) < 0.12f && abs(expected.blue - actual.blue) < 0.12f
        assertTrue(close, "$message: expected $expected, was $actual")
    }

    private operator fun Offset.div(by: Float) = Offset(x / by, y / by)
}

/** A die that draws nothing, noting each face it composes and whether its art was held still. */
private class CountingDice : DiceStyle {
    override val id: String = "counting"
    val shown = mutableListOf<Pair<Int, Boolean>>()

    @Composable
    override fun Die(value: Int, modifier: Modifier) {
        shown += value to LocalArtFrozen.current
        Box(modifier)
    }
}
