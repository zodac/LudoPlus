package net.zodac.ludoplus.ui.style

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.ludoplus.ui.theme.BoardInk

/**
 * A pawn drawn on a grid of [radius] from [centre] (x and y each from -1 to 1, y down): its shadow, the ring when it can
 * be moved, then the family's own [drawBody]. Every family stands on the same footprint, so a pawn of any family sits
 * on its square the same way.
 */
abstract class GridPawnStyle(override val id: String, override val swatch: Color, override val lift: Float = 1f) : PawnStyle, Swatched {
    /** The pawn itself in [colour], on the grid of [g]. */
    protected abstract fun DrawScope.drawBody(g: PawnGrid, colour: Color)

    /** The shadow at its foot: an oval, unless its footprint is another shape. */
    protected open fun DrawScope.drawShadow(g: PawnGrid) = drawOval(Color.Black.copy(alpha = 0.25f), g.at(-0.8f, 0.72f), g.size(1.6f, 0.36f))

    final override fun DrawScope.drawPawn(centre: Offset, radius: Float, colour: Color, ringed: Boolean) {
        val g = PawnGrid(centre, radius)
        if (ringed) drawCircle(MovableRing, radius * 1.3f, g.at(0f, -0.04f), style = Stroke(radius * 0.2f))
        drawShadow(g)
        drawBody(g, colour)
    }
}

/** A pawn's drawing grid: [r] from [centre] is 1. */
class PawnGrid(val centre: Offset, val r: Float) {
    fun at(x: Float, y: Float) = Offset(centre.x + x * r, centre.y + y * r)
    fun size(width: Float, height: Float) = Size(width * r, height * r)

    /** A path through the grid points [xy] (x, y, x, y...), closed. */
    fun polygon(vararg xy: Float): Path = Path().apply {
        for (i in xy.indices step 2) at(xy[i], xy[i + 1]).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) }
        close()
    }

    /** A smooth curve through the grid points [xy] (x, y, x, y...), closed unless it's a line. */
    fun smooth(vararg xy: Float, closed: Boolean = true): Path = smoothPath(xy.toList().chunked(2) { at(it[0], it[1]) }, closed)

    /** The oval [width] by [height] across centred on ([x], [y]). */
    fun ovalTopLeft(x: Float, y: Float, width: Float, height: Float) = at(x - width / 2, y - height / 2)
}

/** The ink outline round a pawn of [g]'s size. */
internal fun DrawScope.outline(g: PawnGrid) = Stroke(width = maxOf(1.dp.toPx(), g.r * 0.09f), join = StrokeJoin.Round)

/** [colour], lit from the left across the pawn's width. */
internal fun lit(g: PawnGrid, colour: Color, light: Float = 0.35f, shade: Float = 0.28f): Brush = Brush.horizontalGradient(
    listOf(lerp(colour, Color.White, light), colour, lerp(colour, Color.Black, shade)),
    startX = g.centre.x - 0.75f * g.r,
    endX = g.centre.x + 0.75f * g.r,
)

/** The round base a figure stands on: its player's [colour], darker than the figure. */
internal fun DrawScope.drawPawnBase(g: PawnGrid, colour: Color) {
    val topLeft = g.at(-0.78f, 0.52f)
    val size = g.size(1.56f, 0.34f)
    drawRoundRect(lit(g, lerp(colour, Color.Black, 0.25f)), topLeft, size, CornerRadius(0.16f * g.r))
    drawRoundRect(BoardInk, topLeft, size, CornerRadius(0.16f * g.r), style = outline(g))
}

// ---------------------------------------------------------------------------------------------
// Counter
// ---------------------------------------------------------------------------------------------

/** A flat round counter, like a draughts piece, seen from a little above: a ridged rim under a grooved top. */
class CounterPawnStyle(id: String) : GridPawnStyle(id, Color(0xFFD9534F)) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val ink = BoardInk
        val stroke = outline(g)
        val width = 1.6f
        val top = 0.12f
        val bottom = 0.5f
        val depth = 0.62f
        // The rim: the band between the top's oval and the bottom's.
        // The rim: down the left side, round the front of the bottom's oval, and up the right.
        val rim = Path().apply {
            g.at(-width / 2, top).let { moveTo(it.x, it.y) }
            g.at(-width / 2, bottom).let { lineTo(it.x, it.y) }
            arcTo(Rect(g.ovalTopLeft(0f, bottom, width, depth), g.size(width, depth)), 180f, -180f, false)
            g.at(width / 2, top).let { lineTo(it.x, it.y) }
            close()
        }
        drawPath(rim, lit(g, colour, shade = 0.42f))
        clipPath(rim) {
            for (i in 1..13) {
                val x = -width / 2 + i * width / 14
                drawLine(lerp(colour, Color.Black, 0.4f), g.at(x, top), g.at(x, bottom + 0.3f * (1 - (2 * x / width) * (2 * x / width))), g.r * 0.05f)
            }
        }
        drawPath(rim, ink, style = stroke)
        // The top, with its groove.
        drawOval(lit(g, colour, light = 0.45f, shade = 0.15f), g.ovalTopLeft(0f, top, width, depth), g.size(width, depth))
        drawOval(lerp(colour, Color.Black, 0.3f), g.ovalTopLeft(0f, top, width * 0.66f, depth * 0.66f), g.size(width * 0.66f, depth * 0.66f), style = Stroke(g.r * 0.07f))
        drawOval(ink, g.ovalTopLeft(0f, top, width, depth), g.size(width, depth), style = stroke)
        drawOval(Color.White.copy(alpha = 0.45f), g.ovalTopLeft(-0.4f, top - 0.12f, 0.36f, 0.12f), g.size(0.36f, 0.12f))
    }
}

// ---------------------------------------------------------------------------------------------
// Cone
// ---------------------------------------------------------------------------------------------

/** A painted cone on a round foot, with a ball at its tip. */
class ConePawnStyle(id: String) : GridPawnStyle(id, Color(0xFF4CAF50), lift = 0.4f) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val ink = BoardInk
        val stroke = outline(g)
        val cone = Path().apply {
            g.at(0f, -0.62f).let { moveTo(it.x, it.y) }
            g.at(0.62f, 0.62f).let { lineTo(it.x, it.y) }
            g.at(0f, 0.86f).let { c -> g.at(-0.62f, 0.62f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            close()
        }
        drawPath(cone, lit(g, colour))
        drawPath(cone, ink, style = stroke)
        // A painted band round it.
        clipPath(cone) {
            val band = Path().apply {
                g.at(-0.7f, 0.2f).let { moveTo(it.x, it.y) }
                g.at(0f, 0.38f).let { c -> g.at(0.7f, 0.2f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
                g.at(0.7f, 0.32f).let { lineTo(it.x, it.y) }
                g.at(0f, 0.5f).let { c -> g.at(-0.7f, 0.32f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
                close()
            }
            drawPath(band, Color.White.copy(alpha = 0.75f))
        }
        val ball = g.at(0f, -0.68f)
        drawCircle(lit(g, colour), 0.26f * g.r, ball)
        drawCircle(ink, 0.26f * g.r, ball, style = stroke)
        drawCircle(Color.White.copy(alpha = 0.55f), 0.08f * g.r, g.at(-0.09f, -0.78f))
    }
}

// ---------------------------------------------------------------------------------------------
// Peg
// ---------------------------------------------------------------------------------------------

/** A turned wooden peg, stained in its player's colour with the grain showing: a rounded dome on a foot, like a Pachisi piece. */
class PegPawnStyle(id: String) : GridPawnStyle(id, Color(0xFFB07A3E), lift = 0.8f) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val ink = BoardInk
        val stroke = outline(g)
        val stain = lerp(colour, Color(0xFF6B4423), 0.22f)
        val dome = Path().apply {
            g.at(-0.5f, 0.6f).let { moveTo(it.x, it.y) }
            g.at(-0.62f, -0.2f).let { c -> g.at(-0.3f, -0.72f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            g.at(0f, -1f).let { c -> g.at(0.3f, -0.72f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            g.at(0.62f, -0.2f).let { c -> g.at(0.5f, 0.6f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            close()
        }
        drawPath(dome, lit(g, stain))
        clipPath(dome) {
            for (i in -3..3) {
                val x = i * 0.16f
                val path = Path().apply {
                    g.at(x, 0.7f).let { moveTo(it.x, it.y) }
                    g.at(x * 1.6f, -0.2f).let { c -> g.at(x * 0.4f, -1f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
                }
                drawPath(path, Color(0xFF3E2410).copy(alpha = 0.22f), style = Stroke(g.r * 0.04f))
            }
        }
        drawPath(dome, ink, style = stroke)
        // The foot, and the turned ring at its top.
        val footTopLeft = g.at(-0.7f, 0.5f)
        drawRoundRect(lit(g, stain), footTopLeft, g.size(1.4f, 0.3f), CornerRadius(0.15f * g.r))
        drawRoundRect(ink, footTopLeft, g.size(1.4f, 0.3f), CornerRadius(0.15f * g.r), style = stroke)
        drawLine(lerp(stain, Color.Black, 0.35f), g.at(-0.56f, 0.12f), g.at(0.56f, 0.12f), g.r * 0.08f, cap = StrokeCap.Round)
        drawOval(Color.White.copy(alpha = 0.4f), g.at(-0.36f, -0.6f), g.size(0.18f, 0.4f))
    }
}

// ---------------------------------------------------------------------------------------------
// Gems
// ---------------------------------------------------------------------------------------------

/** A cut gem in its player's colour, seen from a little above: flat facet tones round a table, outlined in ink, with a crisp glint. */
class GemPawnStyle(id: String) : GridPawnStyle(id, Color(0xFF3F7FD9), lift = 0f) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val ink = BoardInk
        val stroke = outline(g)
        // The girdle (outer octagon) and the table (inner), squashed for the view from above.
        fun ring(radius: Float, cy: Float, squash: Float) = (0..7).map { i ->
            val angle = PI / 8 + i * PI / 4
            Offset((radius * cos(angle)).toFloat(), cy + (radius * squash * sin(angle)).toFloat())
        }
        val girdle = ring(0.86f, -0.05f, 0.62f)
        val table = ring(0.45f, -0.2f, 0.62f)
        val tones = listOf(0.35f, 0.15f, -0.1f, -0.3f, -0.4f, -0.2f, 0.05f, 0.25f)
        fun tone(t: Float) = if (t >= 0) lerp(colour, Color.White, t) else lerp(colour, Color.Black, -t)
        // The pavilion, below the girdle: a point under the middle.
        val tip = Offset(0f, 0.72f)
        for (i in 3..6) {
            val a = girdle[i]
            val b = girdle[(i + 1) % 8]
            if (a.y < -0.05f && b.y < -0.05f) continue
            drawPath(g.polygon(a.x, a.y, b.x, b.y, tip.x, tip.y), tone(tones[i] - 0.15f))
        }
        val pavilion = g.polygon(girdle[0].x, girdle[0].y, tip.x, tip.y, girdle[3].x, girdle[3].y, girdle[2].x, girdle[2].y, girdle[1].x, girdle[1].y)
        drawPath(g.polygon(girdle[7].x, girdle[7].y, girdle[0].x, girdle[0].y, tip.x, tip.y, girdle[3].x, girdle[3].y, girdle[4].x, girdle[4].y), tone(-0.25f))
        drawPath(pavilion, tone(-0.3f))
        drawLine(ink.copy(alpha = 0.5f), g.at(0f, 0.12f), g.at(tip.x, tip.y), g.r * 0.04f)
        drawPath(g.polygon(girdle[0].x, girdle[0].y, tip.x, tip.y, girdle[3].x, girdle[3].y), ink, style = stroke)
        // The crown: a facet between each edge of the girdle and the table.
        for (i in 0..7) {
            val j = (i + 1) % 8
            drawPath(g.polygon(girdle[i].x, girdle[i].y, girdle[j].x, girdle[j].y, table[j].x, table[j].y, table[i].x, table[i].y), tone(tones[i]))
        }
        val tablePath = g.polygon(*table.flatMap { listOf(it.x, it.y) }.toFloatArray())
        drawPath(tablePath, tone(0.2f))
        for (i in 0..7) drawLine(ink.copy(alpha = 0.5f), g.at(girdle[i].x, girdle[i].y), g.at(table[i].x, table[i].y), g.r * 0.04f)
        drawPath(tablePath, ink.copy(alpha = 0.6f), style = Stroke(g.r * 0.045f))
        drawPath(g.polygon(*girdle.flatMap { listOf(it.x, it.y) }.toFloatArray()), ink, style = stroke)
        drawPath(g.polygon(-0.3f, -0.36f, -0.08f, -0.36f, -0.2f, -0.2f), Color.White.copy(alpha = 0.85f))
    }
}

// ---------------------------------------------------------------------------------------------
// Glass
// ---------------------------------------------------------------------------------------------

/** A glass pawn, the classic shape tinted in its player's colour, the board showing faintly through, with bright edges and a streak of light. */
class GlassPawnStyle(id: String) : GridPawnStyle(id, Color(0xFFB3E5FC)) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val edge = lerp(colour, Color.Black, 0.35f)
        val stroke = Stroke(width = maxOf(1.dp.toPx(), g.r * 0.08f))
        val glass = Brush.horizontalGradient(
            listOf(lerp(colour, Color.White, 0.55f).copy(alpha = 0.85f), colour.copy(alpha = 0.6f), lerp(colour, Color.Black, 0.2f).copy(alpha = 0.85f)),
            startX = g.centre.x - 0.7f * g.r,
            endX = g.centre.x + 0.7f * g.r,
        )
        val body = Path().apply {
            g.at(-0.3f, -0.18f).let { moveTo(it.x, it.y) }
            g.at(-0.36f, 0.3f).let { c -> g.at(-0.66f, 0.62f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            g.at(0.66f, 0.62f).let { lineTo(it.x, it.y) }
            g.at(0.36f, 0.3f).let { c -> g.at(0.3f, -0.18f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            close()
        }
        drawPath(body, glass)
        drawPath(body, edge, style = stroke)
        val base = g.at(-0.82f, 0.56f)
        drawRoundRect(glass, base, g.size(1.64f, 0.36f), CornerRadius(0.16f * g.r))
        drawRoundRect(edge, base, g.size(1.64f, 0.36f), CornerRadius(0.16f * g.r), style = stroke)
        val head = g.at(0f, -0.56f)
        drawCircle(glass, 0.42f * g.r, head)
        drawCircle(edge, 0.42f * g.r, head, style = stroke)
        // The light: a streak down the body's left, a glint on the head, and a bright rim on its right.
        drawLine(Color.White.copy(alpha = 0.8f), g.at(-0.2f, -0.05f), g.at(-0.38f, 0.5f), g.r * 0.08f, cap = StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = 0.9f), 0.1f * g.r, g.at(-0.16f, -0.72f))
        drawArc(Color.White.copy(alpha = 0.6f), -20f, 70f, false, g.at(-0.34f, -0.9f), g.size(0.68f, 0.68f), style = Stroke(g.r * 0.05f, cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------------------------------------
// Animal
// ---------------------------------------------------------------------------------------------

/** Which animal an [AnimalPawnStyle] is. */
enum class PawnAnimal(val swatch: Color, val lift: Float) {
    CAT(Color(0xFFFFB74D), lift = 0.3f),
    DUCK(Color(0xFFFFEB3B), lift = 0.5f),
    FROG(Color(0xFF7CB342), lift = 0.3f),
    PENGUIN(Color(0xFF455A64), lift = 0.8f),
}

/** A little animal sitting on a round base, in its player's colour, outlined in ink. */
class AnimalPawnStyle(id: String, private val animal: PawnAnimal) : GridPawnStyle(id, animal.swatch, animal.lift) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val ink = BoardInk
        val stroke = outline(g)
        val fill = lit(g, colour)
        drawPawnBase(g, colour)
        when (animal) {
            PawnAnimal.CAT -> drawCat(g, colour, fill, ink, stroke)
            PawnAnimal.DUCK -> drawDuck(g, fill, ink, stroke)
            PawnAnimal.FROG -> drawFrog(g, colour, fill, ink, stroke)
            PawnAnimal.PENGUIN -> drawPenguin(g, colour, fill, ink, stroke)
        }
    }

    private fun DrawScope.drawCat(g: PawnGrid, colour: Color, fill: Brush, ink: Color, stroke: Stroke) {
        // The tail curling up the right, behind the body.
        val tail = Path().apply {
            g.at(0.3f, 0.5f).let { moveTo(it.x, it.y) }
            g.at(0.95f, 0.45f).let { c -> g.at(0.72f, -0.1f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
        }
        drawPath(tail, ink, style = Stroke(g.r * 0.26f, cap = StrokeCap.Round))
        drawPath(tail, colour, style = Stroke(g.r * 0.14f, cap = StrokeCap.Round))
        drawOval(fill, g.ovalTopLeft(0f, 0.2f, 1.1f, 0.84f), g.size(1.1f, 0.84f))
        drawOval(ink, g.ovalTopLeft(0f, 0.2f, 1.1f, 0.84f), g.size(1.1f, 0.84f), style = stroke)
        val ears = listOf(g.polygon(-0.46f, -0.5f, -0.4f, -0.98f, -0.1f, -0.68f), g.polygon(0.46f, -0.5f, 0.4f, -0.98f, 0.1f, -0.68f))
        for (ear in ears) {
            drawPath(ear, fill)
            drawPath(ear, ink, style = stroke)
        }
        val head = g.at(0f, -0.46f)
        drawCircle(fill, 0.44f * g.r, head)
        drawCircle(ink, 0.44f * g.r, head, style = stroke)
        for (x in listOf(-0.17f, 0.17f)) drawCircle(ink, 0.065f * g.r, g.at(x, -0.5f))
        drawPath(g.polygon(-0.06f, -0.36f, 0.06f, -0.36f, 0f, -0.29f), ink)
    }

    private fun DrawScope.drawDuck(g: PawnGrid, fill: Brush, ink: Color, stroke: Stroke) {
        val body = Path().apply {
            g.at(-0.5f, 0.2f).let { moveTo(it.x, it.y) }
            g.at(-0.78f, -0.02f).let { lineTo(it.x, it.y) }
            g.at(-0.6f, 0.62f).let { c -> g.at(0.1f, 0.62f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            g.at(0.72f, 0.6f).let { c -> g.at(0.62f, 0.05f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            g.at(0f, -0.05f).let { c -> g.at(-0.5f, 0.2f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            close()
        }
        drawPath(body, fill)
        drawPath(body, ink, style = stroke)
        val head = g.at(0.22f, -0.42f)
        val beak = g.polygon(0.5f, -0.5f, 0.9f, -0.38f, 0.5f, -0.28f)
        drawPath(beak, Color(0xFFFF9800))
        drawPath(beak, ink, style = stroke)
        drawCircle(fill, 0.38f * g.r, head)
        drawCircle(ink, 0.38f * g.r, head, style = stroke)
        drawCircle(ink, 0.065f * g.r, g.at(0.32f, -0.52f))
        val wing = Path().apply {
            g.at(-0.4f, 0.22f).let { moveTo(it.x, it.y) }
            g.at(0f, 0.55f).let { c -> g.at(0.3f, 0.2f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
        }
        drawPath(wing, ink, style = Stroke(g.r * 0.07f, cap = StrokeCap.Round))
    }

    private fun DrawScope.drawFrog(g: PawnGrid, colour: Color, fill: Brush, ink: Color, stroke: Stroke) {
        // The haunches either side, then the body, the eyes' bulges, and the wide head over them.
        for (side in listOf(-1f, 1f)) {
            drawOval(fill, g.ovalTopLeft(side * 0.5f, 0.36f, 0.56f, 0.5f), g.size(0.56f, 0.5f))
            drawOval(ink, g.ovalTopLeft(side * 0.5f, 0.36f, 0.56f, 0.5f), g.size(0.56f, 0.5f), style = stroke)
        }
        drawOval(fill, g.ovalTopLeft(0f, 0.24f, 0.84f, 0.8f), g.size(0.84f, 0.8f))
        drawOval(ink, g.ovalTopLeft(0f, 0.24f, 0.84f, 0.8f), g.size(0.84f, 0.8f), style = stroke)
        drawOval(lerp(colour, Color.White, 0.6f), g.ovalTopLeft(0f, 0.34f, 0.5f, 0.48f), g.size(0.5f, 0.48f))
        for (side in listOf(-1f, 1f)) {
            val bulge = g.at(side * 0.4f, -0.62f)
            drawCircle(fill, 0.27f * g.r, bulge)
            drawCircle(ink, 0.27f * g.r, bulge, style = stroke)
        }
        drawOval(fill, g.ovalTopLeft(0f, -0.26f, 1.36f, 0.84f), g.size(1.36f, 0.84f))
        drawOval(ink, g.ovalTopLeft(0f, -0.26f, 1.36f, 0.84f), g.size(1.36f, 0.84f), style = stroke)
        for (side in listOf(-1f, 1f)) {
            drawCircle(fill, 0.22f * g.r, g.at(side * 0.4f, -0.6f))
            drawCircle(Color.White, 0.17f * g.r, g.at(side * 0.4f, -0.64f))
            drawCircle(ink, 0.09f * g.r, g.at(side * 0.38f, -0.63f))
        }
        drawPath(g.smooth(-0.46f, -0.2f, -0.24f, -0.06f, 0f, -0.02f, 0.24f, -0.06f, 0.46f, -0.2f, closed = false), ink, style = Stroke(g.r * 0.08f, cap = StrokeCap.Round))
    }

    private fun DrawScope.drawPenguin(g: PawnGrid, colour: Color, fill: Brush, ink: Color, stroke: Stroke) {
        val orange = Color(0xFFFF9800)
        for (side in listOf(-1f, 1f)) {
            val flipper = g.smooth(side * 0.46f, -0.2f, side * 0.82f, 0.24f, side * 0.72f, 0.4f, side * 0.44f, 0.24f)
            drawPath(flipper, lerp(colour, Color.Black, 0.2f))
            drawPath(flipper, ink, style = stroke)
        }
        val body = g.smooth(0f, -0.96f, 0.4f, -0.74f, 0.5f, -0.2f, 0.58f, 0.3f, 0.36f, 0.6f, -0.36f, 0.6f, -0.58f, 0.3f, -0.5f, -0.2f, -0.4f, -0.74f)
        drawPath(body, fill)
        drawPath(body, ink, style = stroke)
        // The white front: two rounds for the face, one for the belly.
        for (x in listOf(-0.15f, 0.15f)) drawCircle(Color.White, 0.2f * g.r, g.at(x, -0.5f))
        drawOval(Color.White, g.ovalTopLeft(0f, 0.12f, 0.72f, 0.88f), g.size(0.72f, 0.88f))
        for (x in listOf(-0.15f, 0.15f)) drawCircle(ink, 0.07f * g.r, g.at(x, -0.54f))
        val beak = g.polygon(-0.13f, -0.42f, 0.13f, -0.42f, 0f, -0.22f)
        drawPath(beak, orange)
        drawPath(beak, ink, style = Stroke(stroke.width * 0.7f, join = StrokeJoin.Round))
        for (x in listOf(-0.24f, 0.24f)) {
            drawOval(orange, g.ovalTopLeft(x, 0.62f, 0.36f, 0.18f), g.size(0.36f, 0.18f))
            drawOval(ink, g.ovalTopLeft(x, 0.62f, 0.36f, 0.18f), g.size(0.36f, 0.18f), style = Stroke(stroke.width * 0.7f))
        }
    }
}
