package net.zodac.ludo.ui.style

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.ludo.data.settings.DefaultStyleIds
import net.zodac.ludo.ui.game.BoardLayout
import net.zodac.ludo.ui.game.Cell
import net.zodac.ludo.ui.theme.BoardEmptySeat
import net.zodac.ludo.ui.theme.BoardInk
import net.zodac.ludo.ui.theme.BoardIvory

/** The default [BoardStyle]: ivory squares ruled in ink, the seats' colours, and ink stars on the safe squares. */
object ClassicBoardStyle : BoardStyle, Swatched {
    override val id: String = DefaultStyleIds.BOARD
    override val swatch: Color = BoardIvory

    /** The board itself, no pawns: squares, yards, start squares, stars, home columns, and the centre. */
    override fun DrawScope.drawBoard(unit: Float, seatColours: List<Color?>) {
        val board = BoardLayout.geometry
        val line = BoardInk.copy(alpha = 0.45f)
        val lineWidth = 1.dp.toPx()
        drawRect(BoardIvory, size = Size(unit * BoardLayout.SIZE, unit * BoardLayout.SIZE))
        fun square(cell: Cell, fill: Color) {
            val topLeft = Offset(cell.column * unit, cell.row * unit)
            drawRect(fill, topLeft, Size(unit, unit))
            drawRect(line, topLeft, Size(unit, unit), style = Stroke(lineWidth))
        }
        for (square in 0..<board.trackLength) {
            val cell = BoardLayout.trackCell(square)
            val startOf = if (square % board.seatSpacing == 0) square / board.seatSpacing else null
            square(cell, startOf?.let { seatColours.colourOf(it) } ?: BoardIvory)
            if (square % board.seatSpacing == board.starOffset) drawStar(cell.centre * unit, unit * 0.36f, BoardInk.copy(alpha = 0.55f))
        }
        for (seat in 0..<board.seats) {
            val colour = seatColours.colourOf(seat)
            for (index in 0..<board.homeColumnLength) square(BoardLayout.homeColumnCell(seat, index), colour)
            // The yard: a block of the seat's colour round an ivory court with a spot for each pawn.
            val origin = BoardLayout.yardOrigin(seat)
            val yardTopLeft = Offset(origin.column * unit, origin.row * unit)
            val yardSize = Size(BoardLayout.YARD_SIZE * unit, BoardLayout.YARD_SIZE * unit)
            drawRect(colour, yardTopLeft, yardSize)
            drawRect(line, yardTopLeft, yardSize, style = Stroke(lineWidth))
            drawRoundRect(
                BoardIvory,
                yardTopLeft + Offset(unit, unit),
                Size(4 * unit, 4 * unit),
                CornerRadius(unit * 0.5f),
            )
            for (slot in 0..3) {
                drawCircle(colour.copy(alpha = 0.45f), unit * 0.62f, BoardLayout.yardSpot(seat, slot) * unit)
            }
            // Home: the seat's triangle of the centre.
            val triangle = BoardLayout.homeTriangle(seat).map { it * unit }
            val path = Path().apply {
                moveTo(triangle[0].x, triangle[0].y)
                triangle.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, colour)
            drawPath(path, line, style = Stroke(lineWidth))
        }
    }

    /** The ivory spot a pawn that is home stands on, ringed in ink: it lifts the pawn off its own colour's triangle. */
    override fun DrawScope.drawHomeSpot(centre: Offset, radius: Float) {
        val spot = centre + Offset(0f, -0.05f * radius)
        drawCircle(BoardIvory, radius * 1.12f, spot)
        drawCircle(BoardInk.copy(alpha = 0.6f), radius * 1.12f, spot, style = Stroke(maxOf(0.75.dp.toPx(), radius * 0.07f)))
    }
}

private fun List<Color?>.colourOf(seat: Int): Color = getOrNull(seat) ?: BoardEmptySeat

/** A five-pointed star, point up, its points [radius] from [centre]: a safe square's mark. */
private fun DrawScope.drawStar(centre: Offset, radius: Float, colour: Color) {
    val path = Path()
    for (i in 0..9) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val angle = -PI / 2 + i * PI / 5
        val point = centre + Offset((r * cos(angle)).toFloat(), (r * sin(angle)).toFloat())
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, colour)
}
