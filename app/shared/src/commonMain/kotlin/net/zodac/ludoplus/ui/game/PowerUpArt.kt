package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import net.zodac.ludoplus.game.PlayerState
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.ui.style.toPath
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

/** The power-ups in force for a player: how many of their rolls are still to count double, and how many Call Ups they have banked. */
internal data class InForce(val doubledRolls: Int, val callUps: Int) {
    constructor(side: PlayerState) : this(side.doubledRolls, side.callUps)

    val any: Boolean get() = doubledRolls > 0 || callUps > 0
}

/** Each power-up [inForce] for each player, and how many are left, on [places], each player's two in their yard ([BoardLayout.yardBadges]). */
internal fun DrawScope.drawInForce(places: List<List<YardBadge>>, unit: Float, inForce: List<InForce>, textMeasurer: TextMeasurer) {
    for ((badges, powerUps) in places.zip(inForce)) {
        val counts = listOf(PowerUp.DOUBLE to powerUps.doubledRolls, PowerUp.CALL_UP to powerUps.callUps)
        for ((badge, shown) in badges.zip(counts)) {
            val (powerUp, count) = shown
            if (count == 0) continue
            val radius = yardBadgeRadius(badge) * unit
            val centre = badge.centre * unit
            drawPowerUp(powerUp, centre, radius)
            drawCount(textMeasurer, count, centre + Offset(radius, -radius) * YARD_COUNT_OUT, max(radius * YARD_COUNT_SIZE, unit * PAWN_COUNT_RADIUS))
        }
    }
}

/**
 * How big a power-up in force is in a yard, across from its centre, in squares, and at most how much of the room there: less
 * than all, so its count is clear too.
 */
private const val YARD_BADGE_RADIUS = 0.62f
private const val YARD_BADGE_SHARE = 0.67f

/** How big a power-up in force is drawn at [badge], across from its centre, in squares. */
internal fun yardBadgeRadius(badge: YardBadge): Float = min(YARD_BADGE_RADIUS, badge.room * YARD_BADGE_SHARE)

/** Where the count of a power-up in force is, up and to the right of its centre, and how big, of its radius: never smaller than a pawn's. */
private const val YARD_COUNT_OUT = 0.74f
private const val YARD_COUNT_SIZE = 0.44f

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
        PowerUp.SHIELD -> drawShieldShape(centre, radius, powerUp.color)
        PowerUp.ROLL_AGAIN -> drawRoundArrow(centre, radius)
        PowerUp.CALL_UP -> {
            // A pawn coming up out of its yard.
            drawPawnSign(at(0f, -0.16f), radius * 0.56f)
            lines(-0.56f to 0.1f, -0.56f to 0.5f, 0.56f to 0.5f, 0.56f to 0.1f)
        }
        PowerUp.SWAP -> for (way in listOf(1f, -1f)) {
            // One each way, the same turned half round.
            drawLine(Color.White, at(-0.58f * way, -0.26f * way), at(0.16f * way, -0.26f * way), radius * 0.18f)
            drawHead(at(0.13f * way, -0.54f * way), at(0.13f * way, 0.02f * way), at(0.58f * way, -0.26f * way))
        }
    }
}

/** An arrow most of the way round [centre], clockwise, on a power-up of [radius]. */
private fun DrawScope.drawRoundArrow(centre: Offset, radius: Float) {
    val ring = radius * 0.46f
    drawArc(Color.White, ROUND_ARROW_START, ROUND_ARROW_SWEEP, false, centre - Offset(ring, ring), Size(ring * 2, ring * 2), style = Stroke(radius * 0.18f))
    val end = (ROUND_ARROW_START + ROUND_ARROW_SWEEP) * PI / 180
    val out = Offset(cos(end).toFloat(), sin(end).toFloat())
    val tip = centre + out * ring
    val half = radius * 0.26f
    drawHead(tip + out * half, tip - out * half, tip + Offset(-out.y, out.x) * (half * 1.35f))
}

private const val ROUND_ARROW_START = -40f
private const val ROUND_ARROW_SWEEP = 285f

/** An arrow's head, [a] and [b] its back corners, its point at [point]. */
private fun DrawScope.drawHead(a: Offset, b: Offset, point: Offset) = drawPath(listOf(a, b, point).toPath(), Color.White)

/** A pawn's outline, filled white, about [radius] from [centre]: a sign for one, whatever the player's pawn style. */
private fun DrawScope.drawPawnSign(centre: Offset, radius: Float) {
    fun at(x: Float, y: Float) = centre + Offset(x * radius, y * radius)
    drawCircle(Color.White, radius * 0.36f, at(0f, -0.5f))
    drawPath(listOf(at(-0.17f, -0.35f), at(0.17f, -0.35f), at(0.5f, 0.6f), at(-0.5f, 0.6f)).toPath(), Color.White)
    drawRoundRect(Color.White, at(-0.64f, 0.5f), Size(radius * 1.28f, radius * 0.34f), CornerRadius(radius * 0.12f))
}

/** A white shield on a power-up of [radius], its right half shaded towards [colour]. */
private fun DrawScope.drawShieldShape(centre: Offset, radius: Float, colour: Color) {
    fun at(x: Float, y: Float) = centre + Offset(x * radius, y * radius)
    val shield = Path().apply {
        at(0f, -0.55f).let { moveTo(it.x, it.y) }
        at(0.45f, -0.38f).let { lineTo(it.x, it.y) }
        (at(0.45f, 0.3f) to at(0f, 0.6f)).let { (bend, end) -> quadraticTo(bend.x, bend.y, end.x, end.y) }
        (at(-0.45f, 0.3f) to at(-0.45f, -0.38f)).let { (bend, end) -> quadraticTo(bend.x, bend.y, end.x, end.y) }
        close()
    }
    drawPath(shield, Color.White)
    val shade = Path().apply {
        at(0f, -0.4f).let { moveTo(it.x, it.y) }
        at(0.31f, -0.28f).let { lineTo(it.x, it.y) }
        (at(0.31f, 0.22f) to at(0f, 0.43f)).let { (bend, end) -> quadraticTo(bend.x, bend.y, end.x, end.y) }
        close()
    }
    drawPath(shade, lerp(colour, Color.White, 0.55f))
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
