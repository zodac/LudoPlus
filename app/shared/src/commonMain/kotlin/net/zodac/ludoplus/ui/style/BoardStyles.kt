package net.zodac.ludoplus.ui.style

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
import net.zodac.ludoplus.data.settings.DefaultStyleIds
import net.zodac.ludoplus.ui.game.BoardLayout
import net.zodac.ludoplus.ui.game.ClassicBoardLayout
import net.zodac.ludoplus.ui.theme.BoardEmptySeat
import net.zodac.ludoplus.ui.theme.BoardInk
import net.zodac.ludoplus.ui.theme.BoardIvory

/** The default [BoardStyle]: ivory squares ruled in ink, the seats' colours, and ink stars on the safe squares. */
object ClassicBoardStyle : BoardStyle, Swatched {
    override val id: String = DefaultStyleIds.BOARD
    override val swatch: Color = BoardIvory

    /** The board itself, no pawns: squares, yards, start squares, stars, home columns, and the centre. */
    override fun DrawScope.drawBoard(layout: BoardLayout, unit: Float, seatColours: List<Color?>, pawns: Int, parts: BoardParts) {
        val board = layout.geometry
        val line = BoardInk.copy(alpha = 0.45f)
        val lineWidth = 1.dp.toPx()
        val extent = Size(unit * layout.size, unit * layout.size)
        if (layout.round) drawOval(BoardIvory, size = extent) else drawRect(BoardIvory, size = extent)
        fun shape(points: List<Offset>, fill: Color) {
            val path = points.map { it * unit }.toPath()
            drawPath(path, fill)
            drawPath(path, line, style = Stroke(lineWidth))
        }
        for (square in 0..<board.trackLength) {
            val cell = layout.trackSquare(square)
            val startOf = if (parts.starts && square % board.seatSpacing == 0) square / board.seatSpacing else null
            shape(cell.corners, startOf?.let { seatColours.colourOf(it) } ?: BoardIvory)
            if (parts.stars && square % board.seatSpacing == board.starOffset) drawStar(cell.centre * unit, unit * 0.36f, BoardInk.copy(alpha = 0.55f))
        }
        for (seat in 0..<board.seats) {
            val colour = seatColours.colourOf(seat)
            if (parts.homes) for (index in 0..<board.homeColumnLength) shape(layout.homeColumnSquare(seat, index).corners, colour)
            // The yard: a block of the seat's colour round an ivory court with a spot for each pawn.
            shape(layout.yard(seat), colour)
            if (layout is ClassicBoardLayout) {
                val court = layout.yardCourt(seat)
                drawRoundRect(BoardIvory, court[0] * unit, Size(4 * unit, 4 * unit), CornerRadius(unit * 0.5f))
            } else {
                drawPath(layout.yardCourt(seat).map { it * unit }.toPath(), BoardIvory)
            }
            for (pawn in 0..<pawns) {
                drawCircle(colour.copy(alpha = 0.45f), unit * layout.yardSpotRadius, layout.yardSpotOf(seat, pawn, pawns) * unit)
            }
            // Home: the seat's part of the centre.
            if (parts.homes) shape(layout.homeArea(seat), colour)
        }
        if (layout.round) drawOval(BoardInk.copy(alpha = 0.6f), size = extent, style = Stroke(1.5.dp.toPx()))
    }

    /** The ivory spot a pawn that is home stands on, ringed in ink: it lifts the pawn off its own colour's triangle. */
    override fun DrawScope.drawHomeSpot(centre: Offset, radius: Float) {
        val spot = centre + Offset(0f, -0.05f * radius)
        drawCircle(BoardIvory, radius * 1.12f, spot)
        drawCircle(BoardInk.copy(alpha = 0.6f), radius * 1.12f, spot, style = Stroke(maxOf(0.75.dp.toPx(), radius * 0.07f)))
    }
}

private fun List<Color?>.colourOf(seat: Int): Color = getOrNull(seat) ?: BoardEmptySeat

private fun List<Offset>.toPath(): Path = Path().apply {
    moveTo(first().x, first().y)
    drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

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
