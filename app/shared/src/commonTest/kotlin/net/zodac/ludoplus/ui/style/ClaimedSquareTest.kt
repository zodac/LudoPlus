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
import kotlin.test.assertTrue
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.ui.game.ClassicBoardLayout
import net.zodac.ludoplus.ui.theme.color

private const val BOARD = "board"

/** A square claimed in Colour Rush looks as its player's home column does. */
class ClaimedSquareTest : UiTest() {

    @Test
    fun `on every board a claimed square is drawn as its player's home column is`() = uiTest {
        var style by mutableStateOf<BoardStyle>(ClassicBoardStyle)
        val colours = List(4) { PlayerColour.defaultFor(it).color }
        // Beside red's home column, level with its second square.
        val claimed = 2
        compose.setContent {
            Canvas(Modifier.size(600.dp).testTag(BOARD)) {
                with(style) { drawBoard(ClassicBoardLayout, size.width / ClassicBoardLayout.size, colours, parts = BoardParts(claims = mapOf(claimed to colours[0], claimed + 1 to colours[2]))) }
            }
        }
        val centre = ClassicBoardLayout.trackSquare(claimed).centre
        val column = (0..<ClassicBoardLayout.geometry.homeColumnLength).map { ClassicBoardLayout.homeColumnSquare(0, it).centre }
        val rival = ClassicBoardLayout.trackSquare(claimed + 1).centre
        val rivalColumn = (0..<ClassicBoardLayout.geometry.homeColumnLength).map { ClassicBoardLayout.homeColumnSquare(2, it).centre }
        for (board in BoardStyles.all) {
            style = board
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag(BOARD).captureToImage().toPixelMap()
            val unit = pixels.width / ClassicBoardLayout.size
            fun average(at: Offset): Color {
                val points = (-3..3).flatMap { x -> (-3..3).map { y -> (at + Offset(x * 0.1f, y * 0.1f)) * unit } }.map { pixels[it.x.toInt(), it.y.toInt()] }
                return Color(points.map { it.red }.average().toFloat(), points.map { it.green }.average().toFloat(), points.map { it.blue }.average().toFloat())
            }
            for ((square, home) in listOf(centre to column, rival to rivalColumn)) {
                val off = home.minOf { difference(average(square), average(it)) }
                assertTrue(off < 0.15f, "${board.id}: $off off its home column")
            }
            assertTrue(difference(average(centre), average(rival)) > 0.15f, "${board.id}: two players' squares look alike")
        }
    }

    @Test
    fun `on every board a claimed star square shows its player's colour and its mark - whatever the colour`() = uiTest {
        var style by mutableStateOf<BoardStyle>(ClassicBoardStyle)
        var stars by mutableStateOf(true)
        var claimedBy by mutableStateOf(PlayerColour.RED)
        val seats = List(4) { PlayerColour.defaultFor(it).color }
        val star = ClassicBoardLayout.geometry.starOffset
        compose.setContent {
            Canvas(Modifier.size(600.dp).testTag(BOARD)) {
                with(style) { drawBoard(ClassicBoardLayout, size.width / ClassicBoardLayout.size, seats, parts = BoardParts(stars = stars, claims = mapOf(star to claimedBy.color))) }
            }
        }
        val grid = (-15..15).flatMap { x -> (-15..15).map { y -> Offset(x * 0.03f, y * 0.03f) } }
        fun square(): List<Color> {
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag(BOARD).captureToImage().toPixelMap()
            val unit = pixels.width / ClassicBoardLayout.size
            return grid.map { (ClassicBoardLayout.trackSquare(star).centre + it) * unit }.map { pixels[it.x.toInt(), it.y.toInt()] }
        }
        for (board in BoardStyles.all) for (colour in PlayerColour.entries) {
            style = board
            claimedBy = colour
            stars = true
            val marked = square()
            stars = false
            val share = marked.zip(square()).count { (a, b) -> difference(a, b) > 0.2f }.toFloat() / grid.size
            assertTrue(share > 0.04f, "${board.id}, $colour: the star's mark doesn't show ($share)")
            assertTrue(share < 0.5f, "${board.id}, $colour: the star's mark covers its player's colour ($share)")
        }
    }

    private fun difference(a: Color, b: Color): Float = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
}
