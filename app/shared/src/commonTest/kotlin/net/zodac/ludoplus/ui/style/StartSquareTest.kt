package net.zodac.ludoplus.ui.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.ui.game.ClassicBoardLayout
import net.zodac.ludoplus.ui.theme.color

private const val BOARD = "board"

/** A start square has a border, as it's safe. */
class StartSquareTest : UiTest() {

    private val start = ClassicBoardLayout.geometry.startSquare(0)
    private var style by mutableStateOf<BoardStyle>(ClassicBoardStyle)
    private var seats by mutableStateOf(listOf<Color?>(null))
    private var parts by mutableStateOf(BoardParts())

    private fun show() = compose.setContent {
        Canvas(Modifier.size(600.dp).testTag(BOARD)) {
            with(style) { drawBoard(ClassicBoardLayout, size.width / ClassicBoardLayout.size, seats, parts = parts) }
        }
    }

    /** Seat 0's start square, with [parts], at [points] (in squares from its centre). */
    private fun square(parts: BoardParts, points: List<Offset>): List<Color> {
        this.parts = parts
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag(BOARD).captureToImage().toPixelMap()
        val unit = pixels.width / ClassicBoardLayout.size
        return points.map { (ClassicBoardLayout.trackSquare(start).centre + it) * unit }.map { pixels[it.x.toInt(), it.y.toInt()] }
    }

    @Test
    fun `every board borders its start squares on every side - whatever the colour`() = uiTest {
        show()
        // Along each side, in from its edge by each tenth of a square that a border may be at.
        val side = (-3..3).flatMap { x -> (0..8).map { Offset(x * 0.05f, 0.5f - 0.02f * it) } }
        val sides = listOf(side, side.map { Offset(it.x, -it.y) }, side.map { Offset(it.y, it.x) }, side.map { Offset(-it.y, it.x) })
        for (board in BoardStyles.all) for (colour in PlayerColour.entries) {
            style = board
            seats = List(4) { colour.color }
            // One capture for all four sides.
            val bordered = square(BoardParts(), sides.flatten()).chunked(side.size)
            // The same square of the same colour, but not as a start.
            val bare = square(BoardParts(starts = false, claims = mapOf(start to colour.color)), sides.flatten()).chunked(side.size)
            sides.forEachIndexed { i, points ->
                val changed = points.indices.filter { difference(bordered[i][it], bare[i][it]) > 0.2f }.map { points[it] }
                val along = changed.map { if (i < 2) it.x else it.y }.distinct().size
                assertTrue(along >= 5, "${board.id}, $colour, side $i: the border shows at $along of 7 places")
            }
        }
    }

    @Test
    fun `an empty seat's start square has its border only while it's safe`() = uiTest {
        show()
        val grid = (-12..12).flatMap { x -> (-12..12).map { y -> Offset(x * 0.04f, y * 0.04f) } }
        for (board in BoardStyles.all) {
            style = board
            seats = listOf(null, PlayerColour.GREEN.color, null, PlayerColour.BLUE.color)
            val safe = square(BoardParts(), grid)
            val unsafe = square(BoardParts(emptyStartsSafe = false), grid)
            assertTrue(safe != unsafe, "${board.id}: the border is there, safe or not")
            seats = listOf(PlayerColour.RED.color, PlayerColour.GREEN.color, null, null)
            assertEquals(square(BoardParts(), grid), square(BoardParts(emptyStartsSafe = false), grid), "${board.id}: a player's own start is safe for them")
        }
    }

    private fun difference(a: Color, b: Color): Float = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
}
