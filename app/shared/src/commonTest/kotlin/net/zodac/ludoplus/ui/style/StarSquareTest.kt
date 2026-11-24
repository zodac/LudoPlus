package net.zodac.ludoplus.ui.style

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.ui.game.ClassicBoardLayout
import net.zodac.ludoplus.ui.theme.color

private const val BOARD = "board"

/** A star square is shaded as well as marked, so it's told from a plain one with a pawn over its star. */
class StarSquareTest : UiTest() {

    @Test
    fun `every board shades its star squares - and not once its stars are gone`() = uiTest {
        var style by mutableStateOf<BoardStyle>(ClassicBoardStyle)
        var stars by mutableStateOf(true)
        val colours = List(4) { PlayerColour.defaultFor(it).color }
        compose.setContent {
            Canvas(Modifier.size(600.dp).testTag(BOARD)) {
                with(style) { drawBoard(ClassicBoardLayout, size.width / ClassicBoardLayout.size, colours, parts = BoardParts(stars = stars)) }
            }
        }
        fun pixels(): PixelMap {
            compose.waitForIdle()
            return compose.onNodeWithTag(BOARD).captureToImage().toPixelMap()
        }
        val geometry = ClassicBoardLayout.geometry
        val centre = ClassicBoardLayout.trackSquare(geometry.starOffset).centre
        for (board in BoardStyles.all) {
            style = board
            stars = true
            val marked = pixels()
            stars = false
            val bare = pixels()
            // Round the square's sides, outside every mark but the biggest's tips, and inside every shape of square.
            val unit = marked.width / ClassicBoardLayout.size
            val changed = listOf(Offset(-0.22f, -0.38f), Offset(0.22f, -0.38f), Offset(-0.22f, 0.38f), Offset(0.22f, 0.38f)).count { at ->
                val (x, y) = (centre + at) * unit
                difference(marked[x.toInt(), y.toInt()], bare[x.toInt(), y.toInt()]) > 0.15f
            }
            assertTrue(changed >= 3, "${board.id}: $changed of 4 points shaded")
        }
    }

    private fun difference(a: Color, b: Color): Float = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
}
