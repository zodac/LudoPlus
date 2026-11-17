package net.zodac.ludo.ui.style

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.ludo.ui.game.BoardLayout
import net.zodac.ludo.ui.game.Cell
import net.zodac.ludo.ui.theme.BoardEmptySeat

/**
 * A board drawn in layers, each a family's own: the [ground] under it all, every square ([drawSquare]), the safe
 * squares' marks ([drawSafeMark]), the yards ([drawYard]), the home triangles ([drawHomeTriangle]), and a [drawFinish]
 * over the lot (grain, speckle). A seat's colour is drawn as [seatTone] of it, which may tint it towards the board's
 * material but never so far that the seats can't be told apart.
 */
abstract class LayeredBoardStyle(override val id: String, override val swatch: Color) : BoardStyle, Swatched {
    /** The colour of a plain track square. */
    protected abstract val plain: Color

    /** The inside of a yard, round its pawns' spots. */
    protected abstract val court: Color

    /** The lines between squares, and the ring of a home pawn's spot. */
    protected abstract val line: Color

    /** A seat's colour as the board shows it. */
    protected open fun seatTone(colour: Color): Color = colour

    /** Under everything, [extent] wide. */
    protected open fun DrawScope.ground(extent: Size) = drawRect(plain, size = extent)

    /** One square, [unit] across from [topLeft], in [fill]: a plain square's, or a seat's when [seat]. */
    protected abstract fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean)

    /** A safe square's mark, on the square centred on [centre]. */
    protected abstract fun DrawScope.drawSafeMark(centre: Offset, unit: Float)

    /** A seat's yard in [colour], [unit] a square from [topLeft], round a court with a spot at each of [spots]. */
    protected open fun DrawScope.drawYard(topLeft: Offset, unit: Float, colour: Color, spots: List<Offset>) {
        drawRect(colour, topLeft, Size(BoardLayout.YARD_SIZE * unit, BoardLayout.YARD_SIZE * unit))
        drawRoundRectCourt(topLeft, unit)
        for (spot in spots) drawCircle(colour.copy(alpha = 0.45f), unit * 0.62f, spot)
    }

    /** A yard's court: the inner four squares, rounded. */
    protected fun DrawScope.drawRoundRectCourt(topLeft: Offset, unit: Float, colour: Color = court) =
        drawRoundRect(colour, topLeft + Offset(unit, unit), Size(4 * unit, 4 * unit), CornerRadius(unit * 0.5f))

    /** A seat's triangle of the centre, its three [points], in [colour]. */
    protected open fun DrawScope.drawHomeTriangle(points: List<Offset>, unit: Float, colour: Color) {
        val triangle = points.toPath()
        drawPath(triangle, colour)
        drawPath(triangle, line, style = Stroke(1.dp.toPx()))
    }

    /** Over everything, [extent] wide. */
    protected open fun DrawScope.drawFinish(extent: Size, unit: Float) = Unit

    final override fun DrawScope.drawBoard(unit: Float, seatColours: List<Color?>) {
        val board = BoardLayout.geometry
        val extent = Size(unit * BoardLayout.SIZE, unit * BoardLayout.SIZE)
        fun seat(seat: Int) = seatTone(seatColours.getOrNull(seat) ?: BoardEmptySeat)
        fun Cell.topLeft() = Offset(column * unit, row * unit)
        ground(extent)
        for (square in 0..<board.trackLength) {
            val startOf = if (square % board.seatSpacing == 0) square / board.seatSpacing else null
            drawSquare(BoardLayout.trackCell(square).topLeft(), unit, startOf?.let(::seat) ?: plain, seat = startOf != null)
        }
        for (s in 0..<board.seats) {
            for (index in 0..<board.homeColumnLength) drawSquare(BoardLayout.homeColumnCell(s, index).topLeft(), unit, seat(s), seat = true)
            drawYard(BoardLayout.yardOrigin(s).topLeft(), unit, seat(s), (0..3).map { BoardLayout.yardSpot(s, it) * unit })
            drawHomeTriangle(BoardLayout.homeTriangle(s).map { it * unit }, unit, seat(s))
        }
        for (square in 0..<board.trackLength) {
            if (square % board.seatSpacing == board.starOffset) drawSafeMark(BoardLayout.trackCell(square).centre * unit, unit)
        }
        drawFinish(extent, unit)
    }

    override fun DrawScope.drawHomeSpot(centre: Offset, radius: Float) {
        val spot = centre + Offset(0f, -0.05f * radius)
        drawCircle(court, radius * 1.12f, spot)
        drawCircle(line, radius * 1.12f, spot, style = Stroke(maxOf(0.75.dp.toPx(), radius * 0.07f)))
    }
}

private fun List<Offset>.toPath(): Path = Path().apply {
    moveTo(first().x, first().y)
    drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

/** A star of [points] points, point up, its points [radius] from [centre] and its inner corners [inner] of that. */
internal fun starPath(centre: Offset, radius: Float, points: Int = 5, inner: Float = 0.45f): Path = Path().apply {
    for (i in 0..<points * 2) {
        val r = if (i % 2 == 0) radius else radius * inner
        val angle = -PI / 2 + i * PI / points
        val point = centre + Offset((r * cos(angle)).toFloat(), (r * sin(angle)).toFloat())
        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

/** The square [unit] across from [topLeft]. */
private fun squareRect(topLeft: Offset, unit: Float) = Rect(topLeft, Size(unit, unit))

// ---------------------------------------------------------------------------------------------
// Wood
// ---------------------------------------------------------------------------------------------

/**
 * A wooden board: squares of [plank] wood cut by dark grooves, the seats' colours inlaid as stained wood, and grain over
 * it all. The safe squares' stars are burnt in.
 */
class WoodBoardStyle(id: String, private val plank: Color, private val groove: Color, private val grain: Color) :
    LayeredBoardStyle(id, plank) {
    override val plain: Color = plank
    override val court: Color = lerp(plank, Color.White, 0.18f)
    override val line: Color = groove
    override fun seatTone(colour: Color): Color = lerp(colour, groove, 0.14f)

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        drawRect(fill, topLeft, Size(unit, unit))
        drawLine(Color.White.copy(alpha = 0.22f), topLeft + Offset(1.5f, 1.5f), topLeft + Offset(unit - 1.5f, 1.5f), 1.dp.toPx())
        drawRect(groove.copy(alpha = 0.7f), topLeft, Size(unit, unit), style = Stroke(1.25.dp.toPx()))
    }

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        drawPath(starPath(centre + Offset(0f, unit * 0.03f), unit * 0.36f), Color.White.copy(alpha = 0.3f))
        drawPath(starPath(centre, unit * 0.36f), groove.copy(alpha = 0.75f))
    }

    override fun DrawScope.drawYard(topLeft: Offset, unit: Float, colour: Color, spots: List<Offset>) {
        drawRect(colour, topLeft, Size(BoardLayout.YARD_SIZE * unit, BoardLayout.YARD_SIZE * unit))
        drawRect(groove.copy(alpha = 0.7f), topLeft, Size(BoardLayout.YARD_SIZE * unit, BoardLayout.YARD_SIZE * unit), style = Stroke(1.25.dp.toPx()))
        drawRoundRectCourt(topLeft, unit)
        for (spot in spots) {
            drawCircle(groove.copy(alpha = 0.35f), unit * 0.62f, spot)
            drawCircle(colour, unit * 0.56f, spot)
        }
    }

    /** The grain: long wavy lines running across the board, with a few knots. */
    override fun DrawScope.drawFinish(extent: Size, unit: Float) {
        val random = Random(id.hashCode())
        val width = maxOf(0.75.dp.toPx(), unit * 0.025f)
        var y = 0f
        while (y < extent.height) {
            val path = Path()
            val phase = random.nextFloat() * 6f
            val amplitude = unit * (0.04f + random.nextFloat() * 0.08f)
            val wavelength = unit * (2.5f + random.nextFloat() * 3f)
            var x = 0f
            path.moveTo(0f, y)
            while (x <= extent.width) {
                x += unit * 0.25f
                path.lineTo(x, y + amplitude * sin(phase + x / wavelength * 2 * PI.toFloat()))
            }
            drawPath(path, grain.copy(alpha = 0.10f + random.nextFloat() * 0.12f), style = Stroke(width))
            y += unit * (0.12f + random.nextFloat() * 0.16f)
        }
        repeat(5) {
            val knot = Offset(random.nextFloat() * extent.width, random.nextFloat() * extent.height)
            val size = Size(unit * (0.5f + random.nextFloat() * 0.5f), unit * (0.18f + random.nextFloat() * 0.12f))
            drawOval(grain.copy(alpha = 0.22f), knot, size)
            drawOval(grain.copy(alpha = 0.18f), knot - Offset(size.width * 0.35f, size.height * 0.6f), size * 1.7f, style = Stroke(width))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Stone
// ---------------------------------------------------------------------------------------------

/**
 * A stone board: each square a bevelled slab of [slab] set in [mortar], the seats' slabs stone in their colour, and a
 * speckle over the lot. The safe squares' stars are carved in.
 */
class StoneBoardStyle(id: String, private val slab: Color, private val mortar: Color, private val fleck: Color) :
    LayeredBoardStyle(id, slab) {
    override val plain: Color = slab
    override val court: Color = lerp(slab, Color.White, 0.12f)
    override val line: Color = mortar
    override fun seatTone(colour: Color): Color = lerp(colour, slab, 0.16f)

    override fun DrawScope.ground(extent: Size) = drawRect(mortar, size = extent)

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) = drawSlab(squareRect(topLeft, unit), unit, fill)

    private fun DrawScope.drawSlab(rect: Rect, unit: Float, fill: Color) {
        val gap = unit * 0.04f
        val inner = rect.deflate(gap)
        drawRect(fill, inner.topLeft, inner.size)
        val bevel = unit * 0.07f
        val light = Path().apply {
            moveTo(inner.left, inner.bottom); lineTo(inner.left, inner.top); lineTo(inner.right, inner.top)
            lineTo(inner.right - bevel, inner.top + bevel); lineTo(inner.left + bevel, inner.top + bevel); lineTo(inner.left + bevel, inner.bottom - bevel)
            close()
        }
        val dark = Path().apply {
            moveTo(inner.left, inner.bottom); lineTo(inner.right, inner.bottom); lineTo(inner.right, inner.top)
            lineTo(inner.right - bevel, inner.top + bevel); lineTo(inner.right - bevel, inner.bottom - bevel); lineTo(inner.left + bevel, inner.bottom - bevel)
            close()
        }
        drawPath(light, Color.White.copy(alpha = 0.22f))
        drawPath(dark, Color.Black.copy(alpha = 0.22f))
    }

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        drawPath(starPath(centre + Offset(unit * 0.03f, unit * 0.03f), unit * 0.34f), Color.White.copy(alpha = 0.28f))
        drawPath(starPath(centre, unit * 0.34f), Color.Black.copy(alpha = 0.42f))
    }

    override fun DrawScope.drawYard(topLeft: Offset, unit: Float, colour: Color, spots: List<Offset>) {
        drawSlab(Rect(topLeft, Size(BoardLayout.YARD_SIZE * unit, BoardLayout.YARD_SIZE * unit)), unit, colour)
        drawRoundRectCourt(topLeft, unit)
        for (spot in spots) {
            drawCircle(Color.Black.copy(alpha = 0.22f), unit * 0.62f, spot + Offset(-unit * 0.03f, -unit * 0.03f))
            drawCircle(lerp(colour, court, 0.35f), unit * 0.58f, spot)
        }
    }

    /** The speckle: fine flecks of [fleck] and of light over every stone. */
    override fun DrawScope.drawFinish(extent: Size, unit: Float) {
        val random = Random(id.hashCode())
        val dot = maxOf(0.6.dp.toPx(), unit * 0.022f)
        repeat(1200) {
            val at = Offset(random.nextFloat() * extent.width, random.nextFloat() * extent.height)
            val colour = if (random.nextBoolean()) fleck else Color.White
            drawCircle(colour.copy(alpha = 0.10f + random.nextFloat() * 0.14f), dot * (0.6f + random.nextFloat()), at)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Neon
// ---------------------------------------------------------------------------------------------

/**
 * A dark board in glowing lines: the plain squares outlined in [tube], the seats' squares lit in their own colour, and
 * the safe squares' stars in [star].
 */
class NeonBoardStyle(id: String, private val dark: Color, private val tube: Color, private val star: Color) :
    LayeredBoardStyle(id, tube) {
    override val plain: Color = lerp(dark, Color.White, 0.05f)
    override val court: Color = dark
    override val line: Color = tube
    override fun DrawScope.ground(extent: Size) = drawRect(dark, size = extent)

    private fun DrawScope.glow(unit: Float, colour: Color, draw: DrawScope.(Stroke, Color) -> Unit) {
        draw(Stroke(unit * 0.16f), colour.copy(alpha = 0.18f))
        draw(Stroke(unit * 0.08f), colour.copy(alpha = 0.35f))
        draw(Stroke(maxOf(1.dp.toPx(), unit * 0.035f)), lerp(colour, Color.White, 0.35f))
    }

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        val inner = squareRect(topLeft, unit).deflate(unit * 0.1f)
        val tubeColour = if (seat) fill else tube
        drawRect(if (seat) fill.copy(alpha = 0.32f) else plain, inner.topLeft, inner.size)
        glow(unit, tubeColour) { stroke, colour -> drawRect(colour, inner.topLeft, inner.size, style = stroke) }
    }

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        val path = starPath(centre, unit * 0.3f)
        glow(unit * 0.7f, star) { stroke, colour -> drawPath(path, colour, style = stroke) }
    }

    override fun DrawScope.drawYard(topLeft: Offset, unit: Float, colour: Color, spots: List<Offset>) {
        val outer = Rect(topLeft, Size(BoardLayout.YARD_SIZE * unit, BoardLayout.YARD_SIZE * unit)).deflate(unit * 0.15f)
        drawRect(colour.copy(alpha = 0.16f), outer.topLeft, outer.size)
        glow(unit, colour) { stroke, c -> drawRect(c, outer.topLeft, outer.size, style = stroke) }
        drawRoundRectCourt(topLeft, unit)
        for (spot in spots) glow(unit * 0.8f, colour) { stroke, c -> drawCircle(c, unit * 0.55f, spot, style = stroke) }
    }

    override fun DrawScope.drawHomeTriangle(points: List<Offset>, unit: Float, colour: Color) {
        val triangle = points.toPath()
        drawPath(triangle, colour.copy(alpha = 0.3f))
        clipPath(triangle) { glow(unit, colour) { stroke, c -> drawPath(triangle, c, style = stroke) } }
    }

    override fun DrawScope.drawHomeSpot(centre: Offset, radius: Float) {
        val spot = centre + Offset(0f, -0.05f * radius)
        drawCircle(dark, radius * 1.12f, spot)
        drawCircle(tube, radius * 1.12f, spot, style = Stroke(maxOf(1.dp.toPx(), radius * 0.08f)))
    }
}

// ---------------------------------------------------------------------------------------------
// Ocean
// ---------------------------------------------------------------------------------------------

/**
 * A seaside board: a track of [sand] squares edged in [shore], courts of rippling [water], and a [starfish] on each
 * safe square.
 */
class OceanBoardStyle(
    id: String,
    private val sand: Color,
    private val shore: Color,
    private val water: Color,
    private val starfish: Color,
) : LayeredBoardStyle(id, water) {
    override val plain: Color = sand
    override val court: Color = water
    override val line: Color = shore

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        drawRect(fill, topLeft, Size(unit, unit))
        if (!seat) {
            // A grain or two of the sand.
            val random = Random((topLeft.x * 31 + topLeft.y).toInt())
            repeat(3) {
                val at = topLeft + Offset(random.nextFloat() * unit, random.nextFloat() * unit)
                drawCircle(shore.copy(alpha = 0.18f), unit * 0.025f, at)
            }
        }
        drawRect(shore.copy(alpha = 0.55f), topLeft, Size(unit, unit), style = Stroke(1.dp.toPx()))
    }

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        val body = starPath(centre, unit * 0.38f, inner = 0.42f)
        drawPath(body, starfish)
        drawPath(body, lerp(starfish, Color.Black, 0.35f), style = Stroke(maxOf(0.75.dp.toPx(), unit * 0.03f)))
        drawCircle(lerp(starfish, Color.White, 0.5f), unit * 0.05f, centre)
    }

    override fun DrawScope.drawYard(topLeft: Offset, unit: Float, colour: Color, spots: List<Offset>) {
        drawRect(colour, topLeft, Size(BoardLayout.YARD_SIZE * unit, BoardLayout.YARD_SIZE * unit))
        drawRect(shore.copy(alpha = 0.55f), topLeft, Size(BoardLayout.YARD_SIZE * unit, BoardLayout.YARD_SIZE * unit), style = Stroke(1.dp.toPx()))
        drawRoundRectCourt(topLeft, unit)
        // Ripples across the water.
        val court = Rect(topLeft + Offset(unit, unit), Size(4 * unit, 4 * unit))
        val random = Random(topLeft.hashCode())
        clipRect(court.left, court.top, court.right, court.bottom) {
            for (row in 0..9) {
                val y = court.top + (row + 0.5f) * unit * 0.4f
                val shift = random.nextFloat() * unit * 0.6f
                var x = court.left - shift
                while (x < court.right) {
                    val path = Path().apply {
                        moveTo(x, y)
                        quadraticTo(x + unit * 0.15f, y - unit * 0.1f, x + unit * 0.3f, y)
                    }
                    drawPath(path, Color.White.copy(alpha = 0.35f), style = Stroke(maxOf(0.75.dp.toPx(), unit * 0.03f), cap = StrokeCap.Round))
                    x += unit * (0.7f + random.nextFloat() * 0.3f)
                }
            }
        }
        for (spot in spots) {
            drawCircle(sand, unit * 0.6f, spot)
            drawCircle(colour.copy(alpha = 0.5f), unit * 0.6f, spot, style = Stroke(unit * 0.06f))
        }
    }

    override fun DrawScope.drawHomeSpot(centre: Offset, radius: Float) {
        val spot = centre + Offset(0f, -0.05f * radius)
        drawCircle(sand, radius * 1.12f, spot)
        drawCircle(shore, radius * 1.12f, spot, style = Stroke(maxOf(0.75.dp.toPx(), radius * 0.07f)))
    }
}

// ---------------------------------------------------------------------------------------------
// Starry
// ---------------------------------------------------------------------------------------------

/**
 * A night-sky board: [sky] squares ruled in [silver], courts of deeper [night] full of stars, and a gold star on each
 * safe square.
 */
class StarryBoardStyle(
    id: String,
    private val sky: Color,
    private val night: Color,
    private val silver: Color,
    private val gold: Color,
) : LayeredBoardStyle(id, sky) {
    override val plain: Color = sky
    override val court: Color = night
    override val line: Color = silver

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        drawRect(fill, topLeft, Size(unit, unit))
        drawRect(silver.copy(alpha = 0.45f), topLeft, Size(unit, unit), style = Stroke(1.dp.toPx()))
    }

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        drawCircle(gold.copy(alpha = 0.18f), unit * 0.45f, centre)
        drawPath(starPath(centre, unit * 0.36f), gold)
    }

    override fun DrawScope.drawYard(topLeft: Offset, unit: Float, colour: Color, spots: List<Offset>) {
        drawRect(colour, topLeft, Size(BoardLayout.YARD_SIZE * unit, BoardLayout.YARD_SIZE * unit))
        drawRoundRectCourt(topLeft, unit)
        val court = Rect(topLeft + Offset(unit, unit), Size(4 * unit, 4 * unit))
        val random = Random(topLeft.hashCode())
        clipRect(court.left, court.top, court.right, court.bottom) {
            repeat(40) {
                val at = Offset(court.left + random.nextFloat() * court.width, court.top + random.nextFloat() * court.height)
                drawCircle(Color.White.copy(alpha = 0.3f + random.nextFloat() * 0.6f), unit * (0.015f + random.nextFloat() * 0.03f), at)
            }
            repeat(3) {
                val at = Offset(court.left + random.nextFloat() * court.width, court.top + random.nextFloat() * court.height)
                drawPath(starPath(at, unit * 0.14f, points = 4, inner = 0.25f), silver)
            }
        }
        for (spot in spots) drawCircle(silver.copy(alpha = 0.5f), unit * 0.58f, spot, style = Stroke(unit * 0.05f))
    }

    /** A sprinkle of faint stars over the plain squares. */
    override fun DrawScope.drawFinish(extent: Size, unit: Float) {
        val random = Random(id.hashCode())
        repeat(160) {
            val at = Offset(random.nextFloat() * extent.width, random.nextFloat() * extent.height)
            drawCircle(Color.White.copy(alpha = 0.12f + random.nextFloat() * 0.2f), unit * (0.01f + random.nextFloat() * 0.02f), at)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Paper
// ---------------------------------------------------------------------------------------------

/**
 * A board drawn by hand on squared [paper]: sketchy [pencil] lines, the seats coloured in with crayon hatching, and a
 * pencilled star on each safe square.
 */
class PaperBoardStyle(id: String, private val paper: Color, private val pencil: Color, private val ruling: Color) :
    LayeredBoardStyle(id, paper) {
    override val plain: Color = paper
    override val court: Color = paper
    override val line: Color = pencil

    /** The paper and its faint squared ruling, two lines to a square. */
    override fun DrawScope.ground(extent: Size) {
        drawRect(paper, size = extent)
        val step = extent.width / (BoardLayout.SIZE * 2)
        val width = maxOf(0.5.dp.toPx(), step * 0.03f)
        for (i in 1..<BoardLayout.SIZE * 2) {
            drawLine(ruling, Offset(i * step, 0f), Offset(i * step, extent.height), width)
            drawLine(ruling, Offset(0f, i * step), Offset(extent.width, i * step), width)
        }
    }

    /** Crayon: a pale wash of [colour] with hatching over it, inside [clip]. */
    private fun DrawScope.hatch(bounds: Rect, unit: Float, colour: Color, clip: Path? = null) {
        val draw: DrawScope.() -> Unit = {
            drawRect(lerp(colour, paper, 0.55f), bounds.topLeft, bounds.size)
            val step = unit * 0.16f
            var d = -bounds.height
            val random = Random((bounds.left * 7 + bounds.top * 13).toInt())
            while (d < bounds.width) {
                val wobble = (random.nextFloat() - 0.5f) * step * 0.4f
                drawLine(
                    colour.copy(alpha = 0.8f),
                    Offset(bounds.left + d + wobble, bounds.bottom),
                    Offset(bounds.left + d + bounds.height + wobble, bounds.top),
                    unit * 0.065f,
                    cap = StrokeCap.Round,
                )
                d += step
            }
        }
        if (clip != null) clipPath(clip, block = draw) else clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom, block = draw)
    }

    /** A pencil line from [from] to [to], run a little past each end and off true. */
    private fun DrawScope.pencilLine(from: Offset, to: Offset, unit: Float, random: Random) {
        fun jitter() = Offset((random.nextFloat() - 0.5f) * unit * 0.06f, (random.nextFloat() - 0.5f) * unit * 0.06f)
        val along = (to - from) / (to - from).getDistance()
        drawLine(
            pencil.copy(alpha = 0.7f + random.nextFloat() * 0.2f),
            from - along * (unit * 0.05f * random.nextFloat()) + jitter(),
            to + along * (unit * 0.05f * random.nextFloat()) + jitter(),
            maxOf(1.dp.toPx(), unit * 0.028f),
            cap = StrokeCap.Round,
        )
    }

    private fun DrawScope.pencilRect(rect: Rect, unit: Float) {
        val random = Random((rect.left * 3 + rect.top * 5 + rect.width).toInt())
        pencilLine(rect.topLeft, rect.topRight, unit, random)
        pencilLine(rect.topRight, rect.bottomRight, unit, random)
        pencilLine(rect.bottomRight, rect.bottomLeft, unit, random)
        pencilLine(rect.bottomLeft, rect.topLeft, unit, random)
    }

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) {
        val rect = squareRect(topLeft, unit)
        if (seat) hatch(rect, unit, fill)
        pencilRect(rect, unit)
    }

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        drawPath(starPath(centre, unit * 0.34f), pencil.copy(alpha = 0.85f), style = Stroke(maxOf(1.dp.toPx(), unit * 0.035f)))
    }

    override fun DrawScope.drawYard(topLeft: Offset, unit: Float, colour: Color, spots: List<Offset>) {
        val outer = Rect(topLeft, Size(BoardLayout.YARD_SIZE * unit, BoardLayout.YARD_SIZE * unit))
        hatch(outer, unit, colour)
        val court = Rect(topLeft + Offset(unit, unit), Size(4 * unit, 4 * unit))
        drawRect(paper, court.topLeft, court.size)
        pencilRect(outer, unit)
        pencilRect(court, unit)
        for (spot in spots) drawCircle(pencil.copy(alpha = 0.7f), unit * 0.58f, spot, style = Stroke(maxOf(1.dp.toPx(), unit * 0.028f)))
    }

    override fun DrawScope.drawHomeTriangle(points: List<Offset>, unit: Float, colour: Color) {
        val triangle = points.toPath()
        hatch(triangle.getBounds(), unit, colour, clip = triangle)
        drawPath(triangle, pencil.copy(alpha = 0.8f), style = Stroke(maxOf(1.dp.toPx(), unit * 0.028f)))
    }
}

// ---------------------------------------------------------------------------------------------
// Retro
// ---------------------------------------------------------------------------------------------

/** The safe square's star on Retro's grid: 9 pixels square, a row a string. */
private val PixelStar = listOf(
    "....#....",
    "....#....",
    "...###...",
    "#########",
    ".#######.",
    "..#####..",
    ".###.###.",
    ".##...##.",
    "#.......#",
)

/** How many of Retro's pixels make one square. */
private const val RETRO_PIXELS = 10

/**
 * A pixel-art board on a grid of [RETRO_PIXELS] pixels a square: [block] squares bevelled like an old console's tiles,
 * set on [screen], with pixel stars, pixel spots, and stepped home triangles.
 */
class RetroBoardStyle(id: String, private val block: Color, private val screen: Color) : LayeredBoardStyle(id, screen) {
    override val plain: Color = block
    override val court: Color = screen
    override val line: Color = Color.Black

    override fun DrawScope.ground(extent: Size) = drawRect(screen, size = extent)

    /** A bevelled block filling [cells] squares across from [topLeft]. */
    private fun DrawScope.bevel(topLeft: Offset, unit: Float, fill: Color, cells: Int = 1) {
        val p = unit / RETRO_PIXELS
        val side = cells * unit
        drawRect(Color.Black, topLeft, Size(side, side))
        drawRect(fill, topLeft + Offset(p, p), Size(side - 2 * p, side - 2 * p))
        drawRect(lerp(fill, Color.White, 0.45f), topLeft + Offset(p, p), Size(side - 2 * p, p))
        drawRect(lerp(fill, Color.White, 0.45f), topLeft + Offset(p, p), Size(p, side - 2 * p))
        drawRect(lerp(fill, Color.Black, 0.35f), topLeft + Offset(p, side - 2 * p), Size(side - 2 * p, p))
        drawRect(lerp(fill, Color.Black, 0.35f), topLeft + Offset(side - 2 * p, p), Size(p, side - 2 * p))
    }

    override fun DrawScope.drawSquare(topLeft: Offset, unit: Float, fill: Color, seat: Boolean) = bevel(topLeft, unit, fill)

    override fun DrawScope.drawSafeMark(centre: Offset, unit: Float) {
        val p = unit / RETRO_PIXELS
        val origin = centre - Offset(p * PixelStar.size / 2f, p * PixelStar.size / 2f)
        PixelStar.forEachIndexed { row, pixels ->
            pixels.forEachIndexed { column, pixel ->
                if (pixel == '#') drawRect(screen, origin + Offset(column * p, row * p), Size(p, p))
            }
        }
    }

    /** A disc [radius] pixels across at [centre], snapped to the pixel grid. */
    private fun DrawScope.pixelDisc(centre: Offset, radius: Int, p: Float, colour: Color) {
        for (row in -radius..<radius) {
            for (column in -radius..<radius) {
                val dx = column + 0.5f
                val dy = row + 0.5f
                if (dx * dx + dy * dy <= radius * radius) {
                    val x = ((centre.x / p).toInt() + column) * p
                    val y = ((centre.y / p).toInt() + row) * p
                    drawRect(colour, Offset(x, y), Size(p, p))
                }
            }
        }
    }

    override fun DrawScope.drawYard(topLeft: Offset, unit: Float, colour: Color, spots: List<Offset>) {
        val p = unit / RETRO_PIXELS
        bevel(topLeft, unit, colour, cells = BoardLayout.YARD_SIZE)
        val court = topLeft + Offset(unit, unit)
        drawRect(Color.Black, court - Offset(p, p), Size(4 * unit + 2 * p, 4 * unit + 2 * p))
        drawRect(screen, court, Size(4 * unit, 4 * unit))
        for (spot in spots) {
            pixelDisc(spot, 6, p, Color.Black)
            pixelDisc(spot, 5, p, lerp(colour, screen, 0.35f))
        }
    }

    override fun DrawScope.drawHomeTriangle(points: List<Offset>, unit: Float, colour: Color) {
        // Stepped: every pixel whose centre is in the triangle, in its colour, edged in black.
        val p = unit / RETRO_PIXELS
        val bounds = points.toPath().getBounds()
        val columns = (bounds.width / p).toInt() + 1
        val rows = (bounds.height / p).toInt() + 1
        val inside = Array(rows) { row -> BooleanArray(columns) { column -> points.encloses(Offset(bounds.left + (column + 0.5f) * p, bounds.top + (row + 0.5f) * p)) } }
        for (row in 0..<rows) for (column in 0..<columns) {
            if (!inside[row][column]) continue
            val edge = row == 0 || column == 0 || row == rows - 1 || column == columns - 1 ||
                !inside[row - 1][column] || !inside[row + 1][column] || !inside[row][column - 1] || !inside[row][column + 1]
            drawRect(if (edge) Color.Black else colour, Offset(bounds.left + column * p, bounds.top + row * p), Size(p, p))
        }
    }

    override fun DrawScope.drawHomeSpot(centre: Offset, radius: Float) {
        val p = radius / 4f
        pixelDisc(centre, 5, p, Color.Black)
        pixelDisc(centre, 4, p, block)
    }
}

/** Whether [point] is inside the triangle of these three points (on an edge counts). */
private fun List<Offset>.encloses(point: Offset): Boolean {
    fun side(a: Offset, b: Offset) = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
    val sides = listOf(side(this[0], this[1]), side(this[1], this[2]), side(this[2], this[0]))
    return sides.all { it >= 0f } || sides.all { it <= 0f }
}
