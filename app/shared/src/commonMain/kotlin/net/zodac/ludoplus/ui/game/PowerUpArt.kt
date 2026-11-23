package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.ui.theme.BoardInk
import net.zodac.ludoplus.ui.theme.BoardSparkGold
import net.zodac.ludoplus.ui.theme.ShieldBlue
import net.zodac.ludoplus.ui.theme.color

/** How big a power-up is, across from its centre, in squares. */
internal const val POWER_UP_RADIUS = 0.44f

/** How big a pawn's shield is, of the pawn. */
private const val SHIELD_SIZE = 1.4f

/** Every one of [powerUps] (by track square) on [layout]'s board at [unit] a square. */
internal fun DrawScope.drawPowerUps(layout: BoardLayout, unit: Float, powerUps: Map<Int, PowerUp>) {
    for ((square, powerUp) in powerUps) drawPowerUp(powerUp, layout.trackSquare(square).centre * unit, unit * POWER_UP_RADIUS)
}

/** [powerUp] as a gold-edged hexagon in its colour, with its symbol in white: told by the symbol, never the colour alone. */
internal fun DrawScope.drawPowerUp(powerUp: PowerUp, centre: Offset, radius: Float) {
    fun hexagon(size: Float) = Path().apply {
        for (i in 0..5) {
            val angle = PI / 6 + i * PI / 3
            val point = centre + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * size
            if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
    drawPath(hexagon(radius * 1.16f), BoardInk)
    drawPath(hexagon(radius * 1.06f), BoardSparkGold)
    drawPath(hexagon(radius * 0.94f), powerUp.color)
    fun at(x: Float, y: Float) = centre + Offset(x * radius, y * radius)
    fun lines(vararg points: Pair<Float, Float>) {
        val path = Path()
        points.forEachIndexed { i, (x, y) -> at(x, y).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
        drawPath(path, Color.White, style = Stroke(radius * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    when (powerUp) {
        PowerUp.DOUBLE -> {
            // "x2", drawn: the same in every language and at any font size.
            lines(-0.6f to -0.12f, -0.2f to 0.32f)
            lines(-0.2f to -0.12f, -0.6f to 0.32f)
            lines(0.06f to -0.22f, 0.18f to -0.42f, 0.42f to -0.42f, 0.54f to -0.2f, 0.46f to 0f, 0.06f to 0.4f, 0.58f to 0.4f)
        }
        PowerUp.SHIELD -> drawShieldShape(centre, radius)
        PowerUp.TELEPORT -> {
            drawOval(Color.White, at(-0.42f, -0.55f), Size(radius * 0.84f, radius * 1.1f), style = Stroke(radius * 0.15f))
            drawCircle(Color.White, radius * 0.16f, centre)
        }
        PowerUp.ROLL_AGAIN -> {
            drawArc(Color.White, -50f, 290f, false, at(-0.42f, -0.42f), Size(radius * 0.84f, radius * 0.84f), style = Stroke(radius * 0.15f, cap = StrokeCap.Round))
            lines(0.02f to -0.62f, 0.3f to -0.36f, 0f to -0.12f)
        }
        PowerUp.CALL_UP -> {
            lines(0f to 0.5f, 0f to -0.45f)
            lines(-0.35f to -0.12f, 0f to -0.48f, 0.35f to -0.12f)
        }
        PowerUp.SWAP -> {
            lines(-0.45f to -0.2f, 0.45f to -0.2f, 0.2f to -0.45f)
            lines(0.45f to 0.22f, -0.45f to 0.22f, -0.2f to 0.47f)
        }
    }
}

private fun DrawScope.drawShieldShape(centre: Offset, radius: Float) {
    fun at(x: Float, y: Float) = centre + Offset(x * radius, y * radius)
    val shield = Path().apply {
        at(0f, -0.55f).let { moveTo(it.x, it.y) }
        at(0.45f, -0.38f).let { lineTo(it.x, it.y) }
        (at(0.45f, 0.3f) to at(0f, 0.6f)).let { (bend, end) -> quadraticTo(bend.x, bend.y, end.x, end.y) }
        (at(-0.45f, 0.3f) to at(-0.45f, -0.38f)).let { (bend, end) -> quadraticTo(bend.x, bend.y, end.x, end.y) }
        close()
    }
    drawPath(shield, Color.White)
}

/** The bubble round a shielded pawn [pawnRadius] across at [centre], under it. */
internal fun DrawScope.drawShieldBubble(centre: Offset, pawnRadius: Float) {
    val radius = pawnRadius * SHIELD_SIZE
    drawCircle(ShieldBlue.copy(alpha = 0.38f), radius, centre)
    drawCircle(BoardInk, radius, centre, style = Stroke(radius * 0.2f))
    drawCircle(ShieldBlue, radius, centre, style = Stroke(radius * 0.11f))
    drawArc(
        Color.White, 200f, 60f, false, centre - Offset(radius * 0.78f, radius * 0.78f), Size(radius * 1.56f, radius * 1.56f),
        style = Stroke(radius * 0.1f, cap = StrokeCap.Round),
    )
}
