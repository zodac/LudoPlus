package net.zodac.ludoplus.ui.style

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.ludoplus.ui.game.ClassicBoardLayout
import net.zodac.ludoplus.ui.game.inset

/**
 * A board whose yards are drawn the same on every layout: its outline ([drawYardGround]), the court in it
 * ([drawCourt]), and the pawns' spots ([drawSpot]), the classic yard's being a square round a rounded court.
 */
abstract class ShapedYardBoardStyle(id: String, swatch: Color) : LayeredBoardStyle(id, swatch) {
    /** A yard's whole [outline] in [colour], before its court. */
    protected open fun DrawScope.drawYardGround(outline: Path, unit: Float, colour: Color) {
        drawPath(outline, colour)
        drawPath(outline, line, style = Stroke(1.dp.toPx()))
    }

    /** A yard's [court], its seat's [colour] round it; it's drawn cut to its shape, and [seed] is its own. */
    protected open fun DrawScope.drawCourt(court: Path, bounds: Rect, unit: Float, colour: Color, seed: Int) = drawPath(court, this@ShapedYardBoardStyle.court)

    /** A pawn's spot in a yard, [radius] across from [centre]. */
    protected open fun DrawScope.drawSpot(centre: Offset, radius: Float, unit: Float, colour: Color) = drawCircle(colour.copy(alpha = 0.45f), radius, centre)

    final override fun DrawScope.drawYard(topLeft: Offset, unit: Float, colour: Color, spots: List<Offset>) {
        val side = ClassicBoardLayout.YARD_SIZE * unit
        val outline = Path().apply { addRect(Rect(topLeft, Size(side, side))) }
        val court = Path().apply { addRoundRect(RoundRect(Rect(topLeft + Offset(unit, unit), Size(4 * unit, 4 * unit)), CornerRadius(unit * 0.5f))) }
        drawWholeYard(outline, court, spots, CLASSIC_SPOT * unit, unit, colour)
    }

    final override fun DrawScope.drawYardShape(outline: List<Offset>, court: List<Offset>, spots: List<Offset>, spotRadius: Float, unit: Float, colour: Color) =
        drawWholeYard(outline.toPath(), court.toPath(), spots, CLASSIC_SPOT * unit * spotScale(spotRadius, unit), unit, colour)

    private fun DrawScope.drawWholeYard(outline: Path, court: Path, spots: List<Offset>, spotRadius: Float, unit: Float, colour: Color) {
        clipPath(outline) { drawYardGround(outline, unit, colour) }
        val bounds = court.getBounds()
        clipPath(court) { drawCourt(court, bounds, unit, colour, seed = (bounds.left / unit * 31 + bounds.top / unit * 17).toInt()) }
        for (spot in spots) drawSpot(spot, spotRadius, unit, colour)
    }

    private companion object {
        /** A classic yard's spot, in squares. */
        const val CLASSIC_SPOT = 0.58f
    }
}

/** Somewhere in [bounds]. */
private fun Random.within(bounds: Rect) = Offset(bounds.left + nextFloat() * bounds.width, bounds.top + nextFloat() * bounds.height)

/** A point [radius] from [centre], [angle] radians round. */
private fun around(centre: Offset, radius: Float, angle: Double) = centre + Offset((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())

/** Dots of [colour], [width] across, at [points]: one draw, however many. */
private fun DrawScope.drawDots(points: List<Offset>, colour: Color, width: Float) = drawPoints(points, PointMode.Points, colour, width, StrokeCap.Round)

/** A [petals]-petalled flower [radius] out from [centre]. */
private fun DrawScope.drawFlower(centre: Offset, radius: Float, petals: Int, petal: Color, heart: Color, outline: Color? = null) {
    val spots = (0..<petals).map { around(centre, radius * 0.6f, 2 * PI * it / petals - PI / 2) }
    val size = radius * 0.4f
    if (outline != null) {
        for (spot in spots) drawCircle(outline, size + radius * 0.1f, spot)
        drawCircle(outline, radius * 0.5f, centre)
    }
    for (spot in spots) drawCircle(petal, size, spot)
    drawCircle(petal, radius * 0.4f, centre)
    drawCircle(heart, radius * 0.3f, centre)
}

// ---------------------------------------------------------------------------------------------
// Comic
// ---------------------------------------------------------------------------------------------

/**
 * A comic strip's page: thick [ink] panels on [paper], shaded in halftone dots of [dots], with a jagged burst of [burst]
 * on each safe square.
 */
class ComicBoardStyle(id: String, private val paper: Color, private val ink: Color, private val dots: Color, private val burst: Color) :
    ShapedYardBoardStyle(id, dots) {
    override val plain: Color = paper
    override val court: Color = paper
    override val line: Color = ink

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        drawRect(fill, topLeft, Size(unit, unit))
        // Halftone up from the bottom right corner, the dots shrinking away from it.
        val tone = if (seat) lerp(fill, Color.Black, 0.24f) else dots
        val step = unit / 5f
        for (diagonal in 0..3) {
            val points = (0..diagonal).map { topLeft + Offset(unit - step * (it + 0.6f), unit - step * (diagonal - it + 0.6f)) }
            drawDots(points, tone, step * (0.78f - diagonal * 0.14f))
        }
        drawRect(ink, topLeft, Size(unit, unit), style = Stroke(maxOf(1.dp.toPx(), unit * 0.06f)))
    }

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        val jag = starPath(centre, unit * 0.46f, points = 9, inner = 0.64f)
        drawPath(jag, burst)
        drawPath(jag, ink, style = Stroke(maxOf(1.dp.toPx(), unit * 0.05f), join = StrokeJoin.Round))
        drawPath(starPath(centre, unit * 0.2f), ink)
    }

    override fun DrawScope.drawYardGround(outline: Path, unit: Float, colour: Color) {
        drawPath(outline, colour)
        // Speed lines out from the middle.
        val bounds = outline.getBounds()
        val reach = bounds.maxDimension
        for (i in 0..<24) {
            val angle = 2 * PI * i / 24
            drawPath(listOf(bounds.center, around(bounds.center, reach, angle - 0.05), around(bounds.center, reach, angle + 0.05)).toPath(), lerp(colour, Color.White, 0.35f))
        }
        drawPath(outline, ink, style = Stroke(unit * 0.14f))
    }

    override fun DrawScope.drawCourt(court: Path, bounds: Rect, unit: Float, colour: Color, seed: Int) {
        drawPath(court, paper)
        val step = unit * 0.28f
        val rows = (bounds.height / step).toInt() + 1
        for (row in 0..rows) {
            // Bigger towards the foot.
            val width = step * (0.1f + 0.6f * row / rows)
            val shift = if (row % 2 == 0) 0f else step / 2
            val points = (0..(bounds.width / step).toInt() + 1).map { Offset(bounds.left + it * step + shift, bounds.top + row * step) }
            drawDots(points, dots.copy(alpha = 0.7f), width)
        }
        drawPath(court, ink, style = Stroke(unit * 0.14f))
    }

    override fun DrawScope.drawSpot(centre: Offset, radius: Float, unit: Float, colour: Color) {
        drawCircle(ink, radius, centre)
        drawCircle(colour, radius - unit * 0.07f, centre)
    }

    override fun DrawScope.drawHomeTriangle(points: List<Offset>, unit: Float, colour: Color) {
        val home = points.toPath()
        drawPath(home, colour)
        drawPath(home, ink, style = Stroke(unit * 0.08f, join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------------------------------------
// Patchwork
// ---------------------------------------------------------------------------------------------

/**
 * A quilt: patches of [cloth], some plain, some dotted, some striped in [print], each stitched down in [thread], with a
 * [button] sewn on each safe square.
 */
class PatchworkBoardStyle(id: String, private val cloth: Color, private val print: Color, private val thread: Color, private val button: Color) :
    ShapedYardBoardStyle(id, cloth) {
    override val plain: Color = cloth
    override val court: Color = lerp(cloth, Color.White, 0.2f)
    override val line: Color = lerp(thread, Color.Black, 0.2f)

    override fun DrawScope.ground(extent: Size) = drawRect(lerp(cloth, Color.Black, 0.3f), size = extent)

    private fun stitches(unit: Float, width: Float) = Stroke(width, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 0.1f, unit * 0.1f)))

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        val patch = squareRect(topLeft, unit).deflate(unit * 0.015f)
        drawRect(fill, patch.topLeft, patch.size)
        if (!seat) {
            // A plain, dotted, or striped patch, by where it is.
            val step = unit / 4f
            when (((topLeft.x / unit + 0.01f).toInt() + (topLeft.y / unit + 0.01f).toInt() * 2).mod(3)) {
                1 -> drawDots((0..8).map { patch.topLeft + Offset(step * (it % 3 + 1), step * (it / 3 + 1)) }, print, unit * 0.11f)
                2 -> for (i in 0..3) drawRect(print, patch.topLeft + Offset(step * (i + 0.25f), 0f), Size(step * 0.4f, patch.height))
            }
        }
        val seam = patch.deflate(unit * 0.09f)
        drawRect(if (seat) Color.White.copy(alpha = 0.85f) else thread, seam.topLeft, seam.size, style = stitches(unit, maxOf(1.dp.toPx(), unit * 0.04f)))
        // Puffed at the edge.
        drawRect(Color.Black.copy(alpha = 0.16f), patch.topLeft, patch.size, style = Stroke(unit * 0.05f))
    }

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        drawCircle(Color.Black.copy(alpha = 0.25f), unit * 0.31f, centre + Offset(unit * 0.02f, unit * 0.04f))
        drawCircle(button, unit * 0.3f, centre)
        drawCircle(lerp(button, Color.Black, 0.35f), unit * 0.3f, centre, style = Stroke(unit * 0.035f))
        drawCircle(lerp(button, Color.Black, 0.2f), unit * 0.21f, centre, style = Stroke(unit * 0.035f))
        val hole = unit * 0.075f
        drawDots(listOf(Offset(-hole, -hole), Offset(hole, -hole), Offset(-hole, hole), Offset(hole, hole)).map { centre + it }, lerp(button, Color.Black, 0.65f), unit * 0.075f)
        for (side in listOf(-1f, 1f)) drawLine(Color.White, centre + Offset(-hole, -hole * side), centre + Offset(hole, hole * side), unit * 0.035f)
    }

    override fun DrawScope.drawYardGround(outline: Path, unit: Float, colour: Color) {
        drawPath(outline, colour)
        drawPath(outline, Color.Black.copy(alpha = 0.16f), style = Stroke(unit * 0.16f))
        // The seam, in from the edge all round.
        scale(0.955f, outline.getBounds().center) { drawPath(outline, Color.White.copy(alpha = 0.85f), style = stitches(unit, maxOf(1.dp.toPx(), unit * 0.045f))) }
    }

    override fun DrawScope.drawCourt(court: Path, bounds: Rect, unit: Float, colour: Color, seed: Int) {
        drawPath(court, this@PatchworkBoardStyle.court)
        // Quilted in diamonds.
        val quilt = stitches(unit, maxOf(0.75.dp.toPx(), unit * 0.03f))
        val step = unit * 0.8f
        val lines = Path()
        var d = -bounds.height
        while (d < bounds.width) {
            lines.moveTo(bounds.left + d, bounds.bottom)
            lines.lineTo(bounds.left + d + bounds.height, bounds.top)
            lines.moveTo(bounds.left + d, bounds.top)
            lines.lineTo(bounds.left + d + bounds.height, bounds.bottom)
            d += step
        }
        drawPath(lines, thread.copy(alpha = 0.55f), style = quilt)
        drawPath(court, Color.Black.copy(alpha = 0.2f), style = Stroke(unit * 0.1f))
        drawPath(court, thread, style = stitches(unit, unit * 0.14f))
    }

    override fun DrawScope.drawSpot(centre: Offset, radius: Float, unit: Float, colour: Color) {
        drawCircle(colour, radius, centre)
        drawCircle(Color.White.copy(alpha = 0.9f), radius - unit * 0.09f, centre, style = stitches(unit, maxOf(1.dp.toPx(), unit * 0.045f)))
    }

    override fun DrawScope.drawHomeTriangle(points: List<Offset>, unit: Float, colour: Color) {
        val home = points.toPath()
        drawPath(home, colour)
        drawPath(home, Color.Black.copy(alpha = 0.2f), style = Stroke(unit * 0.05f))
        clipPath(home) {
            val seam = inset(points, unit * 0.12f).toPath()
            drawPath(seam, Color.White.copy(alpha = 0.85f), style = stitches(unit, maxOf(1.dp.toPx(), unit * 0.04f)))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Candy
// ---------------------------------------------------------------------------------------------

/**
 * A sweet shop's counter: boiled sweets of [sugar] on [icing], a swirled lollipop in [swirl] on each safe square, and
 * courts scattered with sprinkles.
 */
class CandyBoardStyle(id: String, private val icing: Color, private val sugar: Color, private val swirl: Color) : ShapedYardBoardStyle(id, icing) {
    private val edge = lerp(swirl, Color.Black, 0.35f)
    override val plain: Color = sugar
    override val court: Color = lerp(icing, Color.White, 0.6f)
    override val line: Color = edge

    override fun DrawScope.ground(extent: Size) = drawRect(icing, size = extent)

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        val sweet = squareRect(topLeft, unit).deflate(unit * 0.05f)
        val round = CornerRadius(unit * 0.28f)
        drawRect(icing, topLeft, Size(unit, unit))
        drawRoundRect(edge.copy(alpha = 0.55f), sweet.topLeft + Offset(0f, unit * 0.04f), sweet.size, round)
        drawRoundRect(
            Brush.verticalGradient(listOf(lerp(fill, Color.White, 0.25f), fill, lerp(fill, Color.Black, 0.1f)), startY = sweet.top, endY = sweet.bottom),
            sweet.topLeft, sweet.size, round,
        )
        drawRoundRect(edge.copy(alpha = 0.6f), sweet.topLeft, sweet.size, round, style = Stroke(maxOf(0.75.dp.toPx(), unit * 0.03f)))
        // The gloss.
        drawRoundRect(Color.White.copy(alpha = 0.7f), sweet.topLeft + Offset(unit * 0.14f, unit * 0.08f), Size(unit * 0.5f, unit * 0.11f), CornerRadius(unit * 0.06f))
    }

    override fun DrawScope.drawStartBorder(topLeft: Offset, unit: Float) {
        val sweet = squareRect(topLeft, unit).deflate(unit * 0.1f)
        drawRoundRect(StartBorder, sweet.topLeft, sweet.size, CornerRadius(unit * 0.23f), style = Stroke(unit * START_BORDER_WIDTH))
    }

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        val radius = unit * 0.34f
        drawCircle(Color.White, radius, centre)
        // Four curling stripes from the middle to the rim.
        val lolly = Path().apply { addOval(Rect(centre, radius)) }
        clipPath(lolly) {
            for (arm in 0..3) {
                val stripe = (0..8).map { around(centre, radius * 1.05f * it / 8, arm * PI / 2 + it * 0.2) } +
                    (8 downTo 0).map { around(centre, radius * 1.05f * it / 8, arm * PI / 2 + PI / 4 + it * 0.2) }
                drawPath(polygonPath(stripe), swirl)
            }
        }
        drawCircle(edge, radius, centre, style = Stroke(maxOf(1.dp.toPx(), unit * 0.045f)))
    }

    override fun DrawScope.drawYardGround(outline: Path, unit: Float, colour: Color) {
        drawPath(outline, colour)
        // Candy-cane stripes.
        val bounds = outline.getBounds()
        val step = unit * 0.9f
        var d = bounds.left - bounds.height
        while (d < bounds.right) {
            val stripe = listOf(Offset(d, bounds.bottom), Offset(d + step * 0.4f, bounds.bottom), Offset(d + step * 0.4f + bounds.height, bounds.top), Offset(d + bounds.height, bounds.top))
            drawPath(stripe.toPath(), lerp(colour, Color.White, 0.45f))
            d += step
        }
        drawPath(outline, edge.copy(alpha = 0.7f), style = Stroke(unit * 0.08f))
    }

    override fun DrawScope.drawCourt(court: Path, bounds: Rect, unit: Float, colour: Color, seed: Int) {
        drawPath(court, this@CandyBoardStyle.court)
        val random = Random(seed)
        val colours = listOf(Color(0xFFFF6FA5), Color(0xFF58C4F2), Color(0xFFFFC93C), Color(0xFF7BD88F), Color(0xFFB07CF0))
        repeat((bounds.width * bounds.height / (unit * unit) * 3).toInt()) { i ->
            val at = random.within(bounds)
            val tip = around(at, unit * 0.14f, random.nextDouble() * PI)
            drawLine(colours[i % colours.size], at, tip, unit * 0.06f, cap = StrokeCap.Round)
        }
        drawPath(court, edge.copy(alpha = 0.7f), style = Stroke(unit * 0.08f))
    }

    override fun DrawScope.drawSpot(centre: Offset, radius: Float, unit: Float, colour: Color) {
        drawCircle(edge.copy(alpha = 0.5f), radius, centre + Offset(0f, unit * 0.05f))
        drawCircle(colour, radius, centre)
        drawCircle(Color.White.copy(alpha = 0.6f), radius * 0.7f, centre, style = Stroke(unit * 0.07f))
        drawCircle(edge.copy(alpha = 0.6f), radius, centre, style = Stroke(unit * 0.035f))
    }
}

// ---------------------------------------------------------------------------------------------
// Obsidian
// ---------------------------------------------------------------------------------------------

/** Slabs of [rock], cracked and set in [glow]: a black volcano's floor, with a flame in [flame] on each safe square. */
class ObsidianBoardStyle(id: String, private val rock: Color, private val glow: Color, private val flame: Color) : ShapedYardBoardStyle(id, glow) {
    override val plain: Color = rock
    override val court: Color = lerp(rock, Color.Black, 0.35f)
    override val line: Color = glow

    /** Cooled rock round a disc's yards: only the squares' seams glow. */
    override fun DrawScope.ground(extent: Size) = drawRect(court, size = extent)

    /** A slab's outline: the square at [topLeft], its sides broken off true. */
    private fun slab(topLeft: Offset, unit: Float, random: Random): List<Offset> {
        fun jag() = unit * (0.02f + random.nextFloat() * 0.08f)
        val right = topLeft.x + unit
        val bottom = topLeft.y + unit
        fun along() = unit * (0.3f + random.nextFloat() * 0.4f)
        return listOf(
            Offset(topLeft.x + jag(), topLeft.y + jag()), Offset(topLeft.x + along(), topLeft.y + jag()), Offset(right - jag(), topLeft.y + jag()),
            Offset(right - jag(), topLeft.y + along()), Offset(right - jag(), bottom - jag()), Offset(topLeft.x + along(), bottom - jag()),
            Offset(topLeft.x + jag(), bottom - jag()), Offset(topLeft.x + jag(), topLeft.y + along()),
        )
    }

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        val random = Random((topLeft.x / unit * 131 + topLeft.y / unit * 37).toInt())
        drawRect(glow, topLeft, Size(unit, unit))
        val corners = slab(topLeft, unit, random)
        val path = corners.toPath()
        drawPath(path, fill)
        // Glassy: a facet catching the light, and the edge cooled dark.
        val glint = (if (seat) Color.White else lerp(glow, Color.White, 0.5f)).copy(alpha = if (seat) 0.28f else 0.14f)
        drawPath(listOf(corners[0], corners[1], corners[7]).toPath(), glint)
        drawPath(path, Color.Black.copy(alpha = 0.55f), style = Stroke(maxOf(0.75.dp.toPx(), unit * 0.04f), join = StrokeJoin.Round))
    }

    override fun DrawScope.drawStartBorder(topLeft: Offset, unit: Float) = drawLightStartBorder(squareRect(topLeft, unit).deflate(unit * 0.1f), unit)

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        fun tongue(scale: Float): Path {
            val s = unit * 0.42f * scale
            val foot = centre + Offset(0f, unit * 0.36f)
            return Path().apply {
                moveTo(foot.x, foot.y)
                cubicTo(foot.x - s * 0.95f, foot.y, foot.x - s * 0.75f, foot.y - s * 0.9f, foot.x - s * 0.25f, foot.y - s * 1.1f)
                cubicTo(foot.x - s * 0.2f, foot.y - s * 0.8f, foot.x + s * 0.1f, foot.y - s * 1.3f, foot.x + s * 0.02f, foot.y - s * 1.75f)
                cubicTo(foot.x + s * 0.8f, foot.y - s * 1.2f, foot.x + s * 0.95f, foot.y, foot.x, foot.y)
                close()
            }
        }
        drawSoftSpot(glow.copy(alpha = 0.6f), centre, unit * 0.5f)
        drawPath(tongue(1f), glow)
        drawPath(tongue(0.66f), flame)
        drawPath(tongue(0.34f), Color.White)
    }

    override fun DrawScope.drawYardGround(outline: Path, unit: Float, colour: Color) {
        drawPath(outline, colour)
        drawPath(outline, Color.Black.copy(alpha = 0.3f), style = Stroke(unit * 0.4f))
        drawPath(outline, glow, style = Stroke(unit * 0.12f))
    }

    override fun DrawScope.drawCourt(court: Path, bounds: Rect, unit: Float, colour: Color, seed: Int) {
        drawPath(court, this@ObsidianBoardStyle.court)
        // Cracks, glowing, wandering across from the edge.
        val random = Random(seed)
        repeat(5) {
            var at = random.within(bounds).let { if (random.nextBoolean()) it.copy(x = bounds.left) else it.copy(y = bounds.top) }
            var heading = if (at.x == bounds.left) 0.0 else PI / 2
            val crack = Path().apply {
                moveTo(at.x, at.y)
                repeat(12) {
                    heading += (random.nextDouble() - 0.5) * 1.5
                    at = around(at, unit * 0.45f, heading)
                    lineTo(at.x, at.y)
                }
            }
            drawPath(crack, glow.copy(alpha = 0.22f), style = Stroke(unit * 0.16f, join = StrokeJoin.Round))
            drawPath(crack, lerp(glow, flame, 0.3f).copy(alpha = 0.85f), style = Stroke(unit * 0.04f, join = StrokeJoin.Round))
        }
        drawPath(court, glow, style = Stroke(unit * 0.12f))
    }

    override fun DrawScope.drawSpot(centre: Offset, radius: Float, unit: Float, colour: Color) {
        drawCircle(rock, radius, centre)
        drawCircle(colour.copy(alpha = 0.4f), radius, centre)
        drawCircle(colour, radius, centre, style = Stroke(unit * 0.07f))
    }
}

// ---------------------------------------------------------------------------------------------
// Disco
// ---------------------------------------------------------------------------------------------

/** A dance floor: panels of [dark] glass each lit from under in one of [lights], and a mirror ball on each safe square. */
class DiscoBoardStyle(id: String, private val dark: Color, private val lights: List<Color>) : ShapedYardBoardStyle(id, lights.first()) {
    private val chrome = Color(0xFFB9C0D8)
    override val plain: Color = dark
    override val court: Color = lerp(dark, Color.Black, 0.35f)
    override val line: Color = chrome

    override fun DrawScope.ground(extent: Size) = drawRect(Color(0xFF07060F), size = extent)

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        val panel = squareRect(topLeft, unit).deflate(unit * 0.04f)
        val round = CornerRadius(unit * 0.08f)
        if (seat) {
            drawRoundRect(
                Brush.radialGradient(listOf(lerp(fill, Color.White, 0.45f), fill, lerp(fill, Color.Black, 0.12f)), center = panel.center, radius = unit * 0.7f),
                panel.topLeft, panel.size, round,
            )
        } else {
            val light = lights[(topLeft.x / unit * 3 + topLeft.y / unit * 5).toInt().mod(lights.size)]
            drawRoundRect(
                // A star square's panel is white light, dimmed.
                if (fill == plain) {
                    Brush.radialGradient(listOf(lerp(dark, light, 0.62f), lerp(dark, light, 0.22f)), center = panel.center, radius = unit * 0.66f)
                } else {
                    Brush.radialGradient(listOf(lerp(fill, Color.White, 0.4f), fill), center = panel.center, radius = unit * 0.66f)
                },
                panel.topLeft, panel.size, round,
            )
        }
        drawRoundRect(chrome.copy(alpha = 0.5f), panel.topLeft, panel.size, round, style = Stroke(maxOf(0.75.dp.toPx(), unit * 0.03f)))
    }

    override fun DrawScope.drawStartBorder(topLeft: Offset, unit: Float) =
        drawLightStartBorder(squareRect(topLeft, unit).deflate(unit * 0.08f), unit, CornerRadius(unit * 0.08f))

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        val radius = unit * 0.34f
        drawSoftSpot(Color.White.copy(alpha = 0.45f), centre, unit * 0.5f)
        val ball = Path().apply { addOval(Rect(centre, radius)) }
        drawPath(ball, Brush.radialGradient(listOf(Color.White, chrome, Color(0xFF5A6180)), center = centre - Offset(radius * 0.35f, radius * 0.4f), radius = radius * 1.6f))
        clipPath(ball) {
            val groove = Stroke(maxOf(0.5.dp.toPx(), unit * 0.025f))
            val seam = Color(0xFF3C4260).copy(alpha = 0.7f)
            for (i in -2..2) drawLine(seam, centre + Offset(-radius, i * radius * 0.36f), centre + Offset(radius, i * radius * 0.36f), groove.width)
            for (share in listOf(0.25f, 0.65f)) drawOval(seam, centre - Offset(radius * share, radius), Size(2 * radius * share, 2 * radius), style = groove)
        }
        drawPath(Path().fourPointStar(centre + Offset(-radius * 0.4f, -radius * 0.45f), unit * 0.2f), Color.White)
    }

    override fun DrawScope.drawYardGround(outline: Path, unit: Float, colour: Color) {
        val bounds = outline.getBounds()
        drawPath(outline, Brush.radialGradient(listOf(lerp(colour, Color.White, 0.25f), lerp(colour, Color.Black, 0.12f)), center = bounds.center, radius = bounds.maxDimension * 0.7f))
        drawPath(outline, chrome, style = Stroke(unit * 0.1f))
    }

    override fun DrawScope.drawCourt(court: Path, bounds: Rect, unit: Float, colour: Color, seed: Int) {
        drawPath(court, this@DiscoBoardStyle.court)
        // Beams fanning down from the top, and the ball's flecks of light thrown about.
        val top = Offset(bounds.center.x, bounds.top - unit * 0.6f)
        for (i in -2..2) {
            val angle = PI / 2 + i * 0.34
            val beam = listOf(top, around(top, bounds.height * 1.5f, angle - 0.09), around(top, bounds.height * 1.5f, angle + 0.09))
            drawPath(beam.toPath(), lights[(i + 2) % lights.size].copy(alpha = 0.3f))
        }
        val random = Random(seed)
        repeat((bounds.width * bounds.height / (unit * unit) * 2).toInt()) {
            drawCircle(Color.White.copy(alpha = 0.25f + random.nextFloat() * 0.45f), unit * (0.02f + random.nextFloat() * 0.035f), random.within(bounds))
        }
        drawPath(court, chrome, style = Stroke(unit * 0.1f))
    }

    override fun DrawScope.drawSpot(centre: Offset, radius: Float, unit: Float, colour: Color) {
        drawSoftSpot(colour.copy(alpha = 0.8f), centre, radius * 1.15f)
        drawCircle(colour, radius, centre, style = Stroke(unit * 0.07f))
    }
}

// ---------------------------------------------------------------------------------------------
// Groovy
// ---------------------------------------------------------------------------------------------

/**
 * A seventies carpet: squares of [cream] with quarter rings of [bands] that meet four to a roundel, courts with a ringed
 * sun rising from their foot, and a big daisy with a heart of [daisy] on each safe square.
 */
class GroovyBoardStyle(id: String, private val cream: Color, private val bands: List<Color>, private val daisy: Color) : ShapedYardBoardStyle(id, bands.first()) {
    private val ink = lerp(bands.last(), Color.Black, 0.4f)
    override val plain: Color = cream
    override val court: Color = cream
    override val line: Color = ink

    override fun DrawScope.ground(extent: Size) = drawRect(bands.last(), size = extent)

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        drawRect(fill, topLeft, Size(unit, unit))
        // Quarter rings from the corner on an even line each way, so four squares' arcs meet.
        val column = (topLeft.x / unit + 0.01f).toInt()
        val row = (topLeft.y / unit + 0.01f).toInt()
        val corner = topLeft + Offset(if (column % 2 == 0) 0f else unit, if (row % 2 == 0) 0f else unit)
        val square = Path().apply { addRect(squareRect(topLeft, unit)) }
        clipPath(square) {
            bands.forEachIndexed { i, band ->
                val tone = when {
                    seat -> lerp(fill, Color.White, 0.16f + 0.14f * i)
                    fill == plain -> lerp(band, cream, 0.35f)
                    // A star square's rings sink into its fill.
                    else -> lerp(band, fill, 0.8f)
                }
                drawCircle(tone, unit * (0.82f - 0.22f * i), corner)
            }
        }
        drawRect(ink.copy(alpha = 0.75f), topLeft, Size(unit, unit), style = Stroke(maxOf(1.dp.toPx(), unit * 0.035f)))
    }

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) = drawFlower(centre, unit * 0.46f, petals = 6, petal = Color.White, heart = daisy, outline = ink)

    override fun DrawScope.drawYardGround(outline: Path, unit: Float, colour: Color) {
        drawPath(outline, colour)
        val bounds = outline.getBounds()
        for (i in 0..2) drawCircle(lerp(colour, Color.White, 0.3f), bounds.maxDimension * (0.56f + 0.1f * i), bounds.center, style = Stroke(unit * 0.28f))
        drawPath(outline, ink, style = Stroke(unit * 0.1f))
    }

    override fun DrawScope.drawCourt(court: Path, bounds: Rect, unit: Float, colour: Color, seed: Int) {
        drawPath(court, cream)
        // A sun of rings off the court's foot.
        val sun = Offset(bounds.center.x, bounds.bottom)
        (bands + cream + bands).forEachIndexed { i, band ->
            drawCircle(lerp(band, cream, 0.3f), bounds.width * (0.72f - 0.1f * i).coerceAtLeast(0.05f), sun)
        }
        drawPath(court, ink, style = Stroke(unit * 0.1f))
    }

    override fun DrawScope.drawSpot(centre: Offset, radius: Float, unit: Float, colour: Color) =
        drawFlower(centre, radius * 1.02f, petals = 7, petal = colour, heart = lerp(colour, Color.White, 0.4f), outline = ink)
}

// ---------------------------------------------------------------------------------------------
// Meadow
// ---------------------------------------------------------------------------------------------

/** A garden path: round stepping stones of [stone] through [grass], a daisy on each safe one, and courts of wild flowers. */
class MeadowBoardStyle(id: String, private val grass: Color, private val stone: Color) : ShapedYardBoardStyle(id, grass) {
    private val shade = lerp(grass, Color.Black, 0.45f)
    override val plain: Color = stone
    override val court: Color = lerp(grass, Color.White, 0.18f)
    override val line: Color = shade

    override fun DrawScope.ground(extent: Size) = drawRect(grass, size = extent)

    private fun DrawScope.tufts(bounds: Rect, unit: Float, count: Int, random: Random) {
        val blades = Path()
        repeat(count) {
            val at = random.within(bounds)
            for (lean in listOf(-0.07f, 0f, 0.07f)) {
                blades.moveTo(at.x, at.y)
                blades.lineTo(at.x + unit * lean, at.y - unit * (0.13f - lean * lean * 8))
            }
        }
        drawPath(blades, shade.copy(alpha = 0.55f), style = Stroke(maxOf(0.75.dp.toPx(), unit * 0.03f), cap = StrokeCap.Round))
    }

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        val random = Random((topLeft.x / unit * 71 + topLeft.y / unit * 13).toInt())
        drawRect(grass, topLeft, Size(unit, unit))
        tufts(squareRect(topLeft, unit).deflate(unit * 0.08f), unit, 4, random)
        val centre = topLeft + Offset(unit / 2, unit / 2)
        // A worn stone, off round.
        fun outline(at: Offset, reach: Float, wobble: List<Float>) = smoothPath(wobble.mapIndexed { i, w -> around(at, unit * reach * w, 2 * PI * i / wobble.size) }, closed = true)
        val wobble = List(8) { 0.93f + random.nextFloat() * 0.1f }
        drawPath(outline(centre + Offset(0f, unit * 0.05f), 0.46f, wobble), shade)
        drawPath(outline(centre, 0.46f, wobble), fill)
        drawPath(outline(centre - Offset(unit * 0.03f, unit * 0.04f), 0.36f, wobble), lerp(fill, Color.White, 0.22f))
        drawPath(outline(centre, 0.46f, wobble), lerp(fill, Color.Black, 0.45f), style = Stroke(maxOf(0.75.dp.toPx(), unit * 0.035f)))
    }

    /** A ring round the stone. */
    override fun DrawScope.drawStartBorder(topLeft: Offset, unit: Float) =
        drawCircle(StartBorder, unit * 0.39f, topLeft + Offset(unit / 2, unit / 2), style = Stroke(unit * START_BORDER_WIDTH))

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) =
        drawFlower(centre, unit * 0.38f, petals = 8, petal = Color.White, heart = Color(0xFFFFC107), outline = Color(0xFF5B6653))

    override fun DrawScope.drawYardGround(outline: Path, unit: Float, colour: Color) {
        drawPath(outline, colour)
        // A bed of its own flowers.
        val bounds = outline.getBounds()
        val random = Random((bounds.left / unit * 7 + bounds.top / unit * 11).toInt())
        repeat((bounds.width * bounds.height / (unit * unit) * 1.6f).toInt()) {
            drawFlower(random.within(bounds), unit * 0.17f, petals = 5, petal = lerp(colour, Color.White, 0.5f), heart = lerp(colour, Color.Black, 0.25f))
        }
        drawPath(outline, shade, style = Stroke(unit * 0.12f))
    }

    override fun DrawScope.drawCourt(court: Path, bounds: Rect, unit: Float, colour: Color, seed: Int) {
        drawPath(court, this@MeadowBoardStyle.court)
        val random = Random(seed)
        tufts(bounds, unit, (bounds.width * bounds.height / (unit * unit) * 2).toInt(), random)
        val petals = listOf(Color.White, Color(0xFFFFF176), Color(0xFFF8BBD0), Color(0xFFD1C4E9))
        repeat((bounds.width * bounds.height / (unit * unit)).toInt()) {
            drawFlower(random.within(bounds), unit * 0.14f, petals = 5, petal = petals[it % petals.size], heart = Color(0xFFFFA000))
        }
        drawPath(court, shade, style = Stroke(unit * 0.12f))
    }

    override fun DrawScope.drawSpot(centre: Offset, radius: Float, unit: Float, colour: Color) {
        drawCircle(shade, radius, centre + Offset(0f, unit * 0.05f))
        drawCircle(lerp(stone, colour, 0.55f), radius, centre)
        drawCircle(lerp(colour, Color.Black, 0.35f), radius, centre, style = Stroke(unit * 0.05f))
    }
}

// ---------------------------------------------------------------------------------------------
// Circuit
// ---------------------------------------------------------------------------------------------

/** A printed circuit: [mask] with pads and traces of [copper], the seats' squares lit, and a chip on each safe square. */
class CircuitBoardStyle(id: String, private val mask: Color, private val copper: Color) : ShapedYardBoardStyle(id, copper) {
    private val chip = Color(0xFF15181C)
    private val pad = lerp(mask, Color.White, 0.1f)
    override val plain: Color = pad
    override val court: Color = lerp(mask, Color.Black, 0.25f)
    override val line: Color = copper

    override fun DrawScope.ground(extent: Size) = drawRect(mask, size = extent)

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        drawRect(mask, topLeft, Size(unit, unit))
        val centre = topLeft + Offset(unit / 2, unit / 2)
        val wire = maxOf(1.dp.toPx(), unit * 0.05f)
        // The wires on to the squares next to it, then the pad over them.
        drawLine(copper, topLeft + Offset(0f, unit / 2), topLeft + Offset(unit, unit / 2), wire)
        drawLine(copper, topLeft + Offset(unit / 2, 0f), topLeft + Offset(unit / 2, unit), wire)
        val inner = squareRect(topLeft, unit).deflate(unit * 0.09f)
        val round = CornerRadius(unit * 0.14f)
        if (seat) {
            drawRoundRect(
                Brush.radialGradient(listOf(lerp(fill, Color.White, 0.5f), fill), center = centre, radius = unit * 0.5f),
                inner.topLeft, inner.size, round,
            )
        } else {
            drawRoundRect(fill, inner.topLeft, inner.size, round)
            drawDots(listOf(Offset(0.22f, 0.22f), Offset(0.78f, 0.22f), Offset(0.22f, 0.78f), Offset(0.78f, 0.78f)).map { topLeft + it * unit }, copper.copy(alpha = 0.8f), unit * 0.09f)
        }
        drawRoundRect(copper, inner.topLeft, inner.size, round, style = Stroke(wire))
    }

    override fun DrawScope.drawStartBorder(topLeft: Offset, unit: Float) =
        drawLightStartBorder(squareRect(topLeft, unit).deflate(unit * 0.1f), unit, CornerRadius(unit * 0.14f))

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        val half = unit * 0.24f
        for (i in -1..1) {
            val across = i * half * 0.6f
            for (side in listOf(-1f, 1f)) {
                drawLine(Color(0xFFD9DEE5), centre + Offset(side * half, across), centre + Offset(side * half * 1.45f, across), unit * 0.06f)
                drawLine(Color(0xFFD9DEE5), centre + Offset(across, side * half), centre + Offset(across, side * half * 1.45f), unit * 0.06f)
            }
        }
        drawRoundRect(chip, centre - Offset(half, half), Size(half * 2, half * 2), CornerRadius(unit * 0.04f))
        drawPath(starPath(centre, half * 0.75f), Color(0xFFE6EAF0))
    }

    override fun DrawScope.drawYardGround(outline: Path, unit: Float, colour: Color) {
        drawPath(outline, colour)
        drawPath(outline, copper, style = Stroke(unit * 0.16f))
    }

    override fun DrawScope.drawCourt(court: Path, bounds: Rect, unit: Float, colour: Color, seed: Int) {
        drawPath(court, this@CircuitBoardStyle.court)
        // Traces in straight runs and 45-degree turns, each ending in a drilled pad.
        val random = Random(seed)
        val cell = unit * 0.4f
        val traces = Path()
        val ends = mutableListOf<Offset>()
        repeat(14) {
            var at = bounds.topLeft + Offset(random.nextInt((bounds.width / cell).toInt() + 1) * cell, random.nextInt((bounds.height / cell).toInt() + 1) * cell)
            var turn = random.nextInt(8)
            ends += at
            traces.moveTo(at.x, at.y)
            repeat(2 + random.nextInt(4)) {
                val (dx, dy) = Directions[turn.mod(8)]
                at += Offset(dx * cell, dy * cell)
                traces.lineTo(at.x, at.y)
                turn += random.nextInt(3) - 1
            }
            ends += at
        }
        drawPath(traces, copper.copy(alpha = 0.6f), style = Stroke(maxOf(0.75.dp.toPx(), unit * 0.05f), join = StrokeJoin.Round))
        drawDots(ends, copper, unit * 0.17f)
        drawDots(ends, this@CircuitBoardStyle.court, unit * 0.07f)
        drawPath(court, copper, style = Stroke(unit * 0.12f))
    }

    override fun DrawScope.drawSpot(centre: Offset, radius: Float, unit: Float, colour: Color) {
        drawCircle(mask, radius, centre)
        drawCircle(colour.copy(alpha = 0.45f), radius, centre)
        drawCircle(copper, radius, centre, style = Stroke(unit * 0.09f))
    }

    private companion object {
        /** The eight ways a trace runs, a 45-degree turn apart. */
        val Directions = listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
    }
}

// ---------------------------------------------------------------------------------------------
// Bestagons
// ---------------------------------------------------------------------------------------------

/** A honeycomb: every square a hexagon of [cell] set in [wax], the spots and the courts' comb too, and a bee on each safe one. */
class BestagonBoardStyle(id: String, private val cell: Color, private val wax: Color, private val comb: Color, private val edge: Color) :
    ShapedYardBoardStyle(id, comb) {
    override val plain: Color = cell
    override val court: Color = comb
    override val line: Color = edge

    override fun DrawScope.ground(extent: Size) = drawRect(wax, size = extent)

    /** A hexagon as tall as it's wide, so it fills its square. */
    private fun hexagon(centre: Offset, reach: Float): Path = hexagonPoints(Offset.Zero, reach).map { centre + Offset(it.x, it.y * HEXAGON_STRETCH) }.toPath()

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        drawRect(wax, topLeft, Size(unit, unit))
        val centre = topLeft + Offset(unit / 2, unit / 2)
        drawPath(hexagon(centre, unit * 0.48f), fill)
        drawPath(hexagon(centre, unit * 0.36f), lerp(fill, Color.White, 0.2f))
        drawPath(hexagon(centre, unit * 0.48f), edge, style = Stroke(maxOf(1.dp.toPx(), unit * 0.045f), join = StrokeJoin.Round))
    }

    override fun DrawScope.drawStartBorder(topLeft: Offset, unit: Float) =
        drawPath(hexagon(topLeft + Offset(unit / 2, unit / 2), unit * 0.4f), StartBorder, style = Stroke(unit * START_BORDER_WIDTH, join = StrokeJoin.Round))

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        val ink = Color(0xFF2B1B0A)
        // The wings up behind, then the striped body, and the head to the right.
        for (x in listOf(-0.08f, 0.08f)) {
            val wing = Rect(centre + Offset(unit * x, -unit * 0.18f), unit * 0.13f)
            drawOval(Color.White.copy(alpha = 0.9f), wing.topLeft, wing.size)
            drawOval(ink, wing.topLeft, wing.size, style = Stroke(maxOf(0.5.dp.toPx(), unit * 0.025f)))
        }
        val body = Path().apply { addOval(Rect(centre.x - unit * 0.28f, centre.y - unit * 0.12f, centre.x + unit * 0.2f, centre.y + unit * 0.22f)) }
        drawCircle(ink, unit * 0.12f, centre + Offset(unit * 0.22f, unit * 0.03f))
        drawPath(body, Color(0xFFFFC928))
        clipPath(body) {
            for (x in listOf(-0.16f, -0.02f, 0.12f)) drawRect(ink, centre + Offset(unit * x, -unit * 0.2f), Size(unit * 0.07f, unit * 0.5f))
        }
        drawPath(body, ink, style = Stroke(maxOf(0.75.dp.toPx(), unit * 0.035f)))
        drawCircle(Color.White, unit * 0.035f, centre + Offset(unit * 0.26f, 0f))
    }

    override fun DrawScope.drawYardGround(outline: Path, unit: Float, colour: Color) {
        drawPath(outline, colour)
        drawComb(outline.getBounds(), unit, lerp(colour, Color.White, 0.32f))
        drawPath(outline, edge, style = Stroke(unit * 0.12f))
    }

    override fun DrawScope.drawCourt(court: Path, bounds: Rect, unit: Float, colour: Color, seed: Int) {
        drawPath(court, comb)
        drawComb(bounds, unit, lerp(comb, edge, 0.4f))
        drawPath(court, edge, style = Stroke(unit * 0.12f))
    }

    /** A honeycomb's lines over [bounds]. */
    private fun DrawScope.drawComb(bounds: Rect, unit: Float, colour: Color) {
        val radius = unit * 0.42f
        val columnStep = radius * 1.5f
        val rowStep = radius * 1.732f
        val lines = Path()
        for (column in 0..(bounds.width / columnStep).toInt() + 1) {
            for (row in 0..(bounds.height / rowStep).toInt() + 1) {
                val centre = bounds.topLeft + Offset(column * columnStep, row * rowStep + if (column % 2 == 0) 0f else rowStep / 2)
                lines.addPath(hexagonPoints(centre, radius).toPath())
            }
        }
        drawPath(lines, colour, style = Stroke(maxOf(0.75.dp.toPx(), unit * 0.05f), join = StrokeJoin.Round))
    }

    override fun DrawScope.drawSpot(centre: Offset, radius: Float, unit: Float, colour: Color) {
        val hexagon = hexagonPoints(centre, radius * 1.06f).toPath()
        drawPath(hexagon, colour)
        drawPath(hexagon, edge, style = Stroke(unit * 0.06f, join = StrokeJoin.Round))
    }

    private companion object {
        const val HEXAGON_STRETCH = 1.12f
    }
}
