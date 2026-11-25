package net.zodac.ludoplus.ui.style

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.ui.common.drawPawnWatermark
import net.zodac.ludoplus.ui.game.BoardLayout
import net.zodac.ludoplus.ui.theme.color

/**
 * Every board and pawn, drawn: a pawn is mostly its player's colour and keeps to its footprint, and a board shows each
 * seat's colour on every layout and draws the same every time.
 */
class BoardAndPawnArtTest : UiTest() {

    private fun pixels(side: Int, draw: DrawScope.() -> Unit): IntArray {
        val image = ImageBitmap(side, side)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(side.toFloat(), side.toFloat()), draw)
        return IntArray(side * side).also { image.readPixels(it) }
    }

    /** [style]'s pawn, 40 across from the middle of 120 square. */
    private fun pawn(style: PawnStyle, colour: PlayerColour) = pixels(PAWN_SIDE) {
        with(style) { drawPawn(Offset(PAWN_SIDE / 2f, PAWN_SIDE / 2f), PAWN_RADIUS, colour.color, ringed = false) }
    }

    @Test
    fun `every pawn is mostly its player's colour`() = uiTest {
        for (style in PawnStyles.all) {
            val red = pawn(style, PlayerColour.RED)
            val blue = pawn(style, PlayerColour.BLUE)
            // Solid pixels: not the shadow, nor the edges' blending.
            val solid = red.indices.filter { red[it] ushr 24 > 0x90 }
            val coloured = solid.count { red[it] != blue[it] }
            assertTrue(coloured > solid.size * 0.4, "${style.id}: $coloured of ${solid.size} pixels are its player's colour")
        }
    }

    @Test
    fun `every pawn keeps to its footprint`() = uiTest {
        for (style in PawnStyles.all) {
            val drawn = pawn(style, PlayerColour.RED)
            for (i in drawn.indices) {
                if (drawn[i] ushr 24 < 0x40) continue
                val x = (i % PAWN_SIDE - PAWN_SIDE / 2f) / PAWN_RADIUS
                val y = (i / PAWN_SIDE - PAWN_SIDE / 2f) / PAWN_RADIUS
                assertTrue(x in -1.06f..1.06f && y in -1.1f..1.12f, "${style.id} draws at ($x, $y)")
            }
        }
    }

    @Test
    fun `every pawn is in the middle of a square on its corner - or no higher than one wide at its top can be`() = uiTest {
        // The eight-seat board's second arm is turned an eighth.
        val square = BoardLayout.of(BoardGeometry.forPlayers(8)).homeColumnSquare(1, 2)
        for (style in PawnStyles.all) {
            val lift = (square.centre.y - square.stand(style.lift).y) / LONE_PAWN_RADIUS
            val drawn = pawn(style, PlayerColour.RED)
            // How far it reaches towards the square's lower sides, and its upper ones, in its radii: its shadow aside.
            var under = 0f
            var over = 0f
            for (i in drawn.indices) {
                if (drawn[i] ushr 24 <= 0x90) continue
                val x = abs(i % PAWN_SIDE + 0.5f - PAWN_SIDE / 2f) / PAWN_RADIUS
                val y = (i / PAWN_SIDE + 0.5f - PAWN_SIDE / 2f) / PAWN_RADIUS - lift
                under = maxOf(under, x + y)
                over = maxOf(over, x - y)
            }
            val inside = sqrt(0.5f) / LONE_PAWN_RADIUS
            assertTrue(under < inside && over < inside, "${style.id} is off its square: $under under, $over over, of $inside")
            if (style.lift < 1f) assertTrue(under <= over + 0.12f, "${style.id} is too low: $under under, $over over")
            if (style.lift > 0f) assertTrue(over <= under + 0.12f, "${style.id} is too high: $under under, $over over")
        }
    }

    @Test
    fun `the menu's drifting pawn is the classic pawn's shape`() = uiTest {
        val classic = pawn(ClassicPawnStyle, PlayerColour.RED)
        // The square a watermark stands its pawn in, sized and placed for a pawn of the same radius at the same centre.
        val extent = PAWN_RADIUS / 0.46f
        val middle = Offset(PAWN_SIDE / 2f, PAWN_SIDE / 2f) + ClassicPawnShape.MIDDLE * PAWN_RADIUS
        val drifting = pixels(PAWN_SIDE) {
            drawPawnWatermark(middle - Offset(extent / 2f, extent / 2f), extent, Color.Red, Color.Red)
        }
        // Solid pixels: not the classic pawn's shadow. Their outlines differ a little in width, so the edges may.
        val drawn = classic.indices.filter { classic[it] ushr 24 > 0x90 }
        val differ = classic.indices.count { (classic[it] ushr 24 > 0x90) != (drifting[it] ushr 24 > 0x90) }
        assertTrue(differ < drawn.size * 0.04, "$differ of ${drawn.size} pixels differ")
    }

    @Test
    fun `a drifting pawn's lines and fill are each one strength throughout`() = uiTest {
        val drifting = pixels(PAWN_SIDE) {
            drawPawnWatermark(Offset.Zero, PAWN_SIDE.toFloat(), Color.Transparent, Color.Red.copy(alpha = 0.5f))
        }
        assertTrue(drifting.all { it ushr 24 <= 0x81 }, "the fill doubles up where the pawn's parts overlap")
        val lines = pixels(PAWN_SIDE) {
            drawPawnWatermark(Offset.Zero, PAWN_SIDE.toFloat(), Color.Red.copy(alpha = 0.5f), Color.Transparent)
        }
        assertTrue(lines.all { it ushr 24 <= 0x81 }, "the lines double up where the pawn's parts meet")
    }

    @Test
    fun `every board shows each seat's colour on every layout`() = uiTest {
        for (style in BoardStyles.all) for (players in listOf(2, 3, 5, 6, 7, 8)) {
            val layout = BoardLayout.of(BoardGeometry.forPlayers(players))
            val colours = PlayerColour.entries.take(layout.geometry.seats).map { it.color }
            val board = board(style, layout, colours)
            for (seat in colours.indices) {
                // Another colour for the seat changes its home column's squares, all but their lines and marks.
                val recoloured = board(style, layout, colours.toMutableList().also { it[seat] = PlayerColour.MAGENTA.color })
                val unit = BOARD_SIDE / layout.size
                for (index in 0..<layout.geometry.homeColumnLength) {
                    val centre = layout.homeColumnSquare(seat, index).centre * unit
                    val reach = (unit * 0.25f).toInt()
                    val spots = (-reach..reach).flatMap { dy -> (-reach..reach).map { dx -> (centre.y.toInt() + dy) * BOARD_SIDE + centre.x.toInt() + dx } }
                    val changed = spots.count { board[it] != recoloured[it] }
                    assertTrue(changed > spots.size * 0.6, "${style.id}, $players players, seat $seat's square $index: $changed of ${spots.size}")
                }
            }
        }
    }

    @Test
    fun `every board draws the same every time`() = uiTest {
        for (style in BoardStyles.all) for (players in listOf(4, 5)) {
            val layout = BoardLayout.of(BoardGeometry.forPlayers(players))
            val colours = PlayerColour.entries.take(layout.geometry.seats).map { it.color }
            assertContentEquals(board(style, layout, colours), board(style, layout, colours), "${style.id}, $players players")
        }
    }

    @Test
    fun `a family's shapes are each drawn their own way`() = uiTest {
        for (family in PawnStyles.families.filter { it.colours.size > 1 }) {
            val drawn = family.colours.map { pawn(it.style, PlayerColour.RED).toList() }
            assertEquals(drawn.size, drawn.distinct().size, family.name.key)
        }
    }

    private fun board(style: BoardStyle, layout: BoardLayout, colours: List<Color>) = pixels(BOARD_SIDE) {
        with(style) { drawBoard(layout, BOARD_SIDE / layout.size, colours) }
    }

    private companion object {
        const val PAWN_SIDE = 120
        const val PAWN_RADIUS = 40f

        /** A lone pawn's on the board, in squares. */
        const val LONE_PAWN_RADIUS = 0.38f
        const val BOARD_SIDE = 360
    }
}
