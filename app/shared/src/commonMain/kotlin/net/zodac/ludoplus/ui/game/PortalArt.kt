package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.Portal
import net.zodac.ludoplus.model.portals
import net.zodac.ludoplus.ui.theme.BoardInk
import net.zodac.ludoplus.ui.theme.BoardIvory
import net.zodac.ludoplus.ui.theme.PortalColours

/** How big a portal is, across from its centre, in squares. */
private const val PORTAL_RADIUS = 0.46f

/** How far from a long portal's middle the lock of the seat it's shut to sits, in squares, and how big it is. */
private const val LOCK_OFFSET = 0.33f
private const val LOCK_RADIUS = 0.2f

/** How many symbols a portal's pair may be told by: a symbol and a form (a long pair's ring is split) name a pair. */
private const val SYMBOLS = 8

/** Which symbol and colour [portal]'s pair has on [board]: its own, but that past four seats a long pair shares a short one's. */
internal fun portalSymbol(board: BoardGeometry, portal: Portal): Int =
    (portal.seat + if (portal.long && board.seats <= SYMBOLS / 2) SYMBOLS / 2 else 0) % SYMBOLS

/** The seat [portal] is shut to on [board]: the one whose start square it would take a pawn past. Null for a short pair. */
internal fun portalShutTo(board: BoardGeometry, portal: Portal): Int? = if (portal.long) (portal.seat + 1) % board.seats else null

/**
 * Every portal of [layout]'s board at [unit] a square: a swirl in its pair's colour round its symbol, a long pair's with a
 * split ring, and on each of a long pair's two a lock in the colour of the seat it's shut to (none where nobody sits).
 */
internal fun DrawScope.drawPortals(layout: BoardLayout, unit: Float, seatColours: List<Color?>) {
    val board = layout.geometry
    for (portal in board.portals) {
        val symbol = portalSymbol(board, portal)
        val lock = portalShutTo(board, portal)?.let(seatColours::getOrNull)
        for (square in listOf(portal.first, portal.second)) {
            val centre = layout.trackSquare(square).centre * unit
            drawPortal(centre, unit * PORTAL_RADIUS, symbol, portal.long)
            if (lock != null) drawLock(centre + Offset(LOCK_OFFSET, LOCK_OFFSET) * unit, unit * LOCK_RADIUS, lock)
        }
    }
}

private fun DrawScope.drawPortal(centre: Offset, radius: Float, symbol: Int, long: Boolean) {
    val tone = PortalColours[symbol]
    drawCircle(Color.White, radius * 1.06f, centre)
    drawCircle(BoardInk, radius, centre)
    drawCircle(Brush.radialGradient(listOf(lerp(tone, Color.Black, 0.75f), tone), centre, radius * 0.92f), radius * 0.92f, centre)
    if (long) {
        // A ring in eight parts: a long pair, whatever its colour and symbol.
        val part = (2 * PI * radius * 0.78 / 16).toFloat()
        drawCircle(Color.White, radius * 0.78f, centre, style = Stroke(radius * 0.18f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(part, part))))
    } else {
        for (arm in 0..<SWIRL_ARMS) {
            val swirl = Path()
            for (i in 0..SWIRL_STEPS) {
                val along = i / SWIRL_STEPS.toFloat()
                val point = centre + direction(arm * 2 * PI / SWIRL_ARMS + along * SWIRL_TURN) * (radius * (0.42f + 0.48f * along))
                if (i == 0) swirl.moveTo(point.x, point.y) else swirl.lineTo(point.x, point.y)
            }
            drawPath(swirl, Color.White.copy(alpha = 0.75f), style = Stroke(radius * 0.09f, cap = StrokeCap.Round))
        }
    }
    drawCircle(BoardInk, radius * 0.52f, centre)
    drawSymbol(symbol, centre, radius * 0.36f)
}

private const val SWIRL_ARMS = 3
private const val SWIRL_STEPS = 24
private const val SWIRL_TURN = 2.2

private fun direction(angle: Double) = Offset(cos(angle).toFloat(), sin(angle).toFloat())

/** A pair's symbol, white, [radius] across from [centre]: a disc, triangle, square, diamond, cross, ring, hexagon, or bars. */
private fun DrawScope.drawSymbol(symbol: Int, centre: Offset, radius: Float) {
    fun polygon(sides: Int, start: Double, size: Float) {
        val path = Path()
        for (i in 0..<sides) {
            val point = centre + direction(start + 2 * PI * i / sides) * size
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        path.close()
        drawPath(path, Color.White)
    }
    val across = Offset(radius * 0.8f, 0f)
    when (symbol) {
        0 -> drawCircle(Color.White, radius * 0.75f, centre)
        1 -> polygon(3, -PI / 2, radius)
        2 -> polygon(4, PI / 4, radius)
        3 -> polygon(4, 0.0, radius)
        4 -> {
            drawLine(Color.White, centre - across, centre + across, radius * 0.5f)
            drawLine(Color.White, centre - Offset(0f, across.x), centre + Offset(0f, across.x), radius * 0.5f)
        }
        5 -> drawCircle(Color.White, radius * 0.65f, centre, style = Stroke(radius * 0.35f))
        6 -> polygon(6, 0.0, radius * 0.9f)
        else -> for (y in listOf(-0.5f, 0.5f)) {
            val row = centre + Offset(0f, radius * y)
            drawLine(Color.White, row - across, row + across, radius * 0.4f)
        }
    }
}

/** A padlock on a disc of [colour]. */
private fun DrawScope.drawLock(centre: Offset, radius: Float, colour: Color) {
    drawCircle(BoardInk, radius, centre)
    drawCircle(colour, radius * 0.86f, centre)
    val body = Size(radius * 0.9f, radius * 0.62f)
    drawArc(BoardInk, 180f, 180f, false, centre + Offset(-radius * 0.28f, -radius * 0.56f), Size(radius * 0.56f, radius * 0.9f), style = Stroke(radius * 0.18f))
    drawRoundRect(BoardInk, centre + Offset(-body.width / 2, -radius * 0.08f), body, CornerRadius(radius * 0.12f))
}

/** The way a pawn is taken from portal [from] to its pair [to], fading as it comes out: [progress] (0 to 1) through. */
internal fun DrawScope.drawWarpTrail(from: Offset, to: Offset, unit: Float, progress: Float, colour: Color) {
    val fade = if (progress < 0.5f) 1f else 1f - (progress - 0.5f) * 2f
    val dash = PathEffect.dashPathEffect(floatArrayOf(unit * 0.3f, unit * 0.24f))
    drawLine(BoardInk.copy(alpha = fade), from, to, unit * 0.22f, StrokeCap.Round, dash)
    drawLine(lerp(colour, BoardIvory, 0.3f).copy(alpha = fade), from, to, unit * 0.13f, StrokeCap.Round, dash)
}
