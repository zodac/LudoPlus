package net.zodac.ludoplus.ui.style

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
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

// The pawn families that aren't a pawn's shape: chess pieces, solid shapes, creatures, machines, and food.

/** [path] in [fill], outlined in ink. */
private fun DrawScope.inked(path: Path, fill: Brush, stroke: Stroke) {
    drawPath(path, fill)
    drawPath(path, BoardInk, style = stroke)
}

private fun DrawScope.inked(path: Path, fill: Color, stroke: Stroke) {
    drawPath(path, fill)
    drawPath(path, BoardInk, style = stroke)
}

/** A circle in [fill], [radius] on the grid from ([x], [y]), outlined in ink. */
private fun DrawScope.inkedCircle(g: PawnGrid, x: Float, y: Float, radius: Float, fill: Brush, stroke: Stroke) {
    drawCircle(fill, radius * g.r, g.at(x, y))
    drawCircle(BoardInk, radius * g.r, g.at(x, y), style = stroke)
}

/** A rounded bar in [fill], its top left at ([x], [y]) on the grid, outlined in ink. */
private fun DrawScope.inkedBar(g: PawnGrid, x: Float, y: Float, width: Float, height: Float, corner: Float, fill: Brush, stroke: Stroke) {
    drawRoundRect(fill, g.at(x, y), g.size(width, height), CornerRadius(corner * g.r))
    drawRoundRect(BoardInk, g.at(x, y), g.size(width, height), CornerRadius(corner * g.r), style = stroke)
}

/** An eye at ([x], [y]): white, outlined in ink, its pupil off centre by ([lookX], [lookY]) with a glint in it. */
private fun DrawScope.drawEye(g: PawnGrid, x: Float, y: Float, radius: Float, pupil: Float, lookX: Float, lookY: Float, stroke: Stroke) {
    drawCircle(Color.White, radius * g.r, g.at(x, y))
    drawCircle(BoardInk, radius * g.r, g.at(x, y), style = Stroke(stroke.width * 0.8f))
    drawCircle(BoardInk, pupil * g.r, g.at(x + lookX, y + lookY))
    drawCircle(Color.White, pupil * 0.32f * g.r, g.at(x + lookX - pupil * 0.35f, y + lookY - pupil * 0.35f))
}

/** A line of [colour] through the grid points [xy], [width] of the radius wide and round at its ends. */
private fun DrawScope.smoothLine(g: PawnGrid, colour: Color, width: Float, vararg xy: Float) =
    drawPath(g.smooth(*xy, closed = false), colour, style = Stroke(g.r * width, cap = StrokeCap.Round, join = StrokeJoin.Round))

// ---------------------------------------------------------------------------------------------
// Chess
// ---------------------------------------------------------------------------------------------

/** Which chess piece a [ChessPawnStyle] is. */
enum class PawnChessPiece(val swatch: Color) {
    ROOK(Color(0xFF8D6E63)),
    KNIGHT(Color(0xFFD7B98E)),
    QUEEN(Color(0xFFF2C94C)),
}

/** A turned chess piece on a stepped foot. */
class ChessPawnStyle(id: String, private val piece: PawnChessPiece) : GridPawnStyle(id, piece.swatch) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val stroke = outline(g)
        val fill = lit(g, colour)
        when (piece) {
            PawnChessPiece.ROOK -> {
                inked(g.polygon(-0.42f, 0.5f, -0.3f, -0.34f, 0.3f, -0.34f, 0.42f, 0.5f), fill, stroke)
                inked(
                    g.polygon(
                        -0.52f, -0.34f, -0.52f, -0.92f, -0.28f, -0.92f, -0.28f, -0.72f, -0.13f, -0.72f, -0.13f, -0.92f,
                        0.13f, -0.92f, 0.13f, -0.72f, 0.28f, -0.72f, 0.28f, -0.92f, 0.52f, -0.92f, 0.52f, -0.34f,
                    ),
                    fill, stroke,
                )
                drawLine(BoardInk.copy(alpha = 0.45f), g.at(-0.5f, -0.52f), g.at(0.5f, -0.52f), g.r * 0.05f)
            }
            PawnChessPiece.KNIGHT -> {
                // The neck and head facing left: chest, muzzle, brow, ear, and the mane's curve down the back.
                val head = Path().apply {
                    fun to(x: Float, y: Float) = g.at(x, y).let { lineTo(it.x, it.y) }
                    fun curve(cx: Float, cy: Float, x: Float, y: Float) = g.at(cx, cy).let { c -> g.at(x, y).let { quadraticTo(c.x, c.y, it.x, it.y) } }
                    g.at(-0.42f, 0.5f).let { moveTo(it.x, it.y) }
                    curve(-0.36f, 0.14f, -0.04f, -0.1f)
                    curve(-0.24f, -0.1f, -0.4f, 0.04f)
                    curve(-0.62f, 0.1f, -0.68f, -0.14f)
                    curve(-0.56f, -0.4f, -0.36f, -0.62f)
                    curve(-0.26f, -0.74f, -0.14f, -0.76f)
                    to(-0.06f, -0.98f)
                    to(0.08f, -0.76f)
                    curve(0.62f, -0.56f, 0.48f, 0.5f)
                    close()
                }
                inked(head, fill, stroke)
                smoothLine(g, BoardInk.copy(alpha = 0.5f), 0.06f, 0.1f, -0.6f, 0.28f, -0.3f, 0.3f, 0.1f)
                drawCircle(BoardInk, 0.06f * g.r, g.at(-0.26f, -0.46f))
                drawCircle(BoardInk, 0.035f * g.r, g.at(-0.56f, -0.1f))
            }
            PawnChessPiece.QUEEN -> {
                inked(g.smoothFlare(), fill, stroke)
                inkedBar(g, -0.44f, -0.3f, 0.88f, 0.17f, 0.08f, fill, stroke)
                inked(g.polygon(-0.34f, -0.3f, -0.56f, -0.78f, -0.24f, -0.56f, 0f, -0.86f, 0.24f, -0.56f, 0.56f, -0.78f, 0.34f, -0.3f), fill, stroke)
                for ((x, y) in listOf(-0.56f to -0.8f, 0f to -0.88f, 0.56f to -0.8f)) inkedCircle(g, x, y, 0.1f, lit(g, lerp(colour, Color.White, 0.45f)), Stroke(stroke.width * 0.8f))
            }
        }
        inkedBar(g, -0.58f, 0.4f, 1.16f, 0.2f, 0.09f, fill, stroke)
        inkedBar(g, -0.78f, 0.58f, 1.56f, 0.3f, 0.13f, fill, stroke)
        // The light down its left, clear of the outline.
        val light = if (piece == PawnChessPiece.ROOK) -0.16f else -0.1f
        if (piece != PawnChessPiece.KNIGHT) drawLine(Color.White.copy(alpha = 0.45f), g.at(light, -0.02f), g.at(light - 0.06f, 0.28f), g.r * 0.08f, cap = StrokeCap.Round)
    }

    /** A turned stem, waisted under the collar and flaring to the foot. */
    private fun PawnGrid.smoothFlare(): Path = Path().apply {
        at(-0.26f, -0.16f).let { moveTo(it.x, it.y) }
        at(-0.26f, 0.2f).let { c -> at(-0.48f, 0.5f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
        at(0.48f, 0.5f).let { lineTo(it.x, it.y) }
        at(0.26f, 0.2f).let { c -> at(0.26f, -0.16f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
        close()
    }
}

// ---------------------------------------------------------------------------------------------
// Shapes
// ---------------------------------------------------------------------------------------------

/** Which solid a [ShapePawnStyle] is. */
enum class PawnShape(val swatch: Color) {
    STAR(Color(0xFFFFD54F)),
    HEART(Color(0xFFEC407A)),
    CUBE(Color(0xFF42A5F5)),
}

/** A solid shape: a faceted star or a puffed heart on a base, or a cube seen corner on. */
class ShapePawnStyle(id: String, private val shape: PawnShape) : GridPawnStyle(id, shape.swatch) {
    /** The cube's is its own foot's, thrown to the right. */
    override fun DrawScope.drawShadow(g: PawnGrid) {
        if (shape == PawnShape.CUBE) {
            drawPath(g.polygon(-0.74f, 0.46f, 0f, 0.9f, 0.3f, 0.92f, 1f, 0.58f, 0.74f, 0.3f), Color.Black.copy(alpha = 0.25f))
        } else {
            drawOval(Color.Black.copy(alpha = 0.25f), g.at(-0.8f, 0.72f), g.size(1.6f, 0.36f))
        }
    }

    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val stroke = outline(g)
        when (shape) {
            PawnShape.STAR -> {
                drawPawnBase(g, colour)
                drawStar(g, colour, stroke)
            }
            PawnShape.HEART -> {
                drawPawnBase(g, colour)
                val heart = Path().apply {
                    fun cubic(ax: Float, ay: Float, bx: Float, by: Float, x: Float, y: Float) {
                        val a = g.at(ax, ay)
                        val b = g.at(bx, by)
                        val to = g.at(x, y)
                        cubicTo(a.x, a.y, b.x, b.y, to.x, to.y)
                    }
                    g.at(0f, 0.64f).let { moveTo(it.x, it.y) }
                    cubic(-0.4f, 0.34f, -0.86f, 0.02f, -0.82f, -0.46f)
                    cubic(-0.78f, -0.98f, -0.16f, -1.04f, 0f, -0.58f)
                    cubic(0.16f, -1.04f, 0.78f, -0.98f, 0.82f, -0.46f)
                    cubic(0.86f, 0.02f, 0.4f, 0.34f, 0f, 0.64f)
                    close()
                }
                inked(heart, lit(g, colour), stroke)
                smoothLine(g, Color.White.copy(alpha = 0.7f), 0.12f, -0.6f, -0.34f, -0.58f, -0.58f, -0.42f, -0.72f)
            }
            PawnShape.CUBE -> {
                val top = g.polygon(0f, -0.94f, 0.74f, -0.56f, 0f, -0.18f, -0.74f, -0.56f)
                val left = g.polygon(-0.74f, -0.56f, 0f, -0.18f, 0f, 0.84f, -0.74f, 0.44f)
                val right = g.polygon(0.74f, -0.56f, 0f, -0.18f, 0f, 0.84f, 0.74f, 0.44f)
                inked(left, colour, stroke)
                inked(right, lerp(colour, Color.Black, 0.28f), stroke)
                inked(top, lerp(colour, Color.White, 0.4f), stroke)
                drawLine(Color.White.copy(alpha = 0.5f), g.at(-0.62f, -0.38f), g.at(-0.62f, 0.34f), g.r * 0.07f, cap = StrokeCap.Round)
            }
        }
    }

    /** Five points, each split down its ridge into a lit facet and a shaded one. */
    private fun DrawScope.drawStar(g: PawnGrid, colour: Color, stroke: Stroke) {
        val middle = Offset(0f, -0.12f)
        val corners = (0..9).map { i ->
            val reach = if (i % 2 == 0) 0.88f else 0.44f
            val angle = -PI / 2 + i * PI / 5
            middle + Offset((reach * cos(angle)).toFloat(), (reach * sin(angle)).toFloat())
        }
        corners.forEachIndexed { i, a ->
            val b = corners[(i + 1) % corners.size]
            // Lit from the top left: a facet's tone is the way its outer edge faces.
            val facing = (a + b) / 2f - middle
            val light = -(facing.x + facing.y) * 0.4f + if (i % 2 == 0) -0.12f else 0.12f
            val tone = if (light >= 0) lerp(colour, Color.White, light.coerceAtMost(0.5f)) else lerp(colour, Color.Black, (-light).coerceAtMost(0.4f))
            drawPath(g.polygon(middle.x, middle.y, a.x, a.y, b.x, b.y), tone)
        }
        val star = g.polygon(*corners.flatMap { listOf(it.x, it.y) }.toFloatArray())
        drawPath(star, BoardInk, style = stroke)
    }
}

// ---------------------------------------------------------------------------------------------
// Monster
// ---------------------------------------------------------------------------------------------

/** Which monster a [MonsterPawnStyle] is. */
enum class PawnMonster(val swatch: Color) {
    CYCLOPS(Color(0xFF7E57C2)),
    HORNED(Color(0xFF26A69A)),
}

/** A small shaggy monster in its player's colour: one great eye, or two and a pair of horns. */
class MonsterPawnStyle(id: String, private val monster: PawnMonster) : GridPawnStyle(id, monster.swatch) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val stroke = outline(g)
        val dark = lerp(colour, Color.Black, 0.3f)
        for (x in listOf(-0.32f, 0.32f)) {
            drawOval(dark, g.ovalTopLeft(x, 0.72f, 0.46f, 0.26f), g.size(0.46f, 0.26f))
            drawOval(BoardInk, g.ovalTopLeft(x, 0.72f, 0.46f, 0.26f), g.size(0.46f, 0.26f), style = stroke)
        }
        when (monster) {
            PawnMonster.CYCLOPS -> {
                inked(fur(g, centreY = -0.06f, width = 0.74f, height = 0.74f), lit(g, colour), stroke)
                drawEye(g, 0f, -0.26f, 0.34f, 0.16f, 0.04f, 0.04f, stroke)
                drawMouth(g, 0.24f, 0.36f)
            }
            PawnMonster.HORNED -> {
                for (side in listOf(-1f, 1f)) {
                    val horn = Path().apply {
                        g.at(side * 0.26f, -0.5f).let { moveTo(it.x, it.y) }
                        g.at(side * 0.4f, -0.86f).let { c -> g.at(side * 0.7f, -0.96f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
                        g.at(side * 0.78f, -0.6f).let { c -> g.at(side * 0.56f, -0.32f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
                        close()
                    }
                    inked(horn, Color(0xFFFFF3D6), stroke)
                }
                inked(fur(g, centreY = 0.02f, width = 0.76f, height = 0.68f), lit(g, colour), stroke)
                for (x in listOf(-0.26f, 0.26f)) drawEye(g, x, -0.2f, 0.2f, 0.1f, -x * 0.2f, 0.04f, stroke)
                drawMouth(g, 0.26f, 0.3f)
            }
        }
    }

    /** The shaggy body's outline: an oval whose edge runs in and out, longer towards the foot. */
    private fun fur(g: PawnGrid, centreY: Float, width: Float, height: Float): Path = Path().apply {
        val tufts = 18
        for (i in 0..<tufts * 2) {
            val angle = -PI / 2 + i * PI / tufts
            val reach = if (i % 2 == 0) 1f else 0.87f
            val at = g.at((width * reach * cos(angle)).toFloat(), centreY + (height * reach * sin(angle)).toFloat())
            if (i == 0) moveTo(at.x, at.y) else lineTo(at.x, at.y)
        }
        close()
    }

    /** A grin across the middle at [y], [halfWidth] to each side, with two teeth up from its lower lip. */
    private fun DrawScope.drawMouth(g: PawnGrid, y: Float, halfWidth: Float) {
        val mouth = Path().apply {
            g.at(-halfWidth, y).let { moveTo(it.x, it.y) }
            g.at(halfWidth, y).let { lineTo(it.x, it.y) }
            g.at(0f, y + 0.46f).let { c -> g.at(-halfWidth, y).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            close()
        }
        drawPath(mouth, BoardInk)
        for (x in listOf(-0.14f, 0.14f)) drawPath(g.polygon(x - 0.08f, y, x + 0.08f, y, x, y + 0.13f), Color.White)
    }
}

// ---------------------------------------------------------------------------------------------
// Retro
// ---------------------------------------------------------------------------------------------

/** The pixel pawn, a row a string: `#` ink, `w` the glint, `l` lit, `c` its colour, `s` shaded. */
private val PixelPawn = listOf(
    "....#####....",
    "...#llccs#...",
    "..#lwwcccs#..",
    "..#lwwcccs#..",
    "..#llccccs#..",
    "...#lcccs#...",
    "..#########..",
    "..#llccsss#..",
    "..#########..",
    "....#lcs#....",
    "....#lcs#....",
    "...#llccs#...",
    "..#llcccss#..",
    "#############",
    "#lllccccssss#",
    "#############",
)

/** A pawn in pixels, like an old console's sprite. */
class RetroPawnStyle(id: String) : GridPawnStyle(id, Color(0xFF7E57C2)) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val tones = mapOf('#' to BoardInk, 'w' to Color.White, 'l' to lerp(colour, Color.White, 0.35f), 'c' to colour, 's' to lerp(colour, Color.Black, 0.3f))
        val p = g.r * PIXEL
        val origin = g.at(-PixelPawn[0].length * PIXEL / 2, 0.86f - PixelPawn.size * PIXEL)
        PixelPawn.forEachIndexed { row, pixels ->
            // A run of one colour is one rectangle.
            var start = 0
            while (start < pixels.length) {
                var end = start
                while (end < pixels.length && pixels[end] == pixels[start]) end++
                val tone = tones[pixels[start]]
                // A hair over, so no seam shows between rows.
                if (tone != null) drawRect(tone, origin + Offset(start * p, row * p), Size((end - start) * p + p * 0.04f, p * 1.04f))
                start = end
            }
        }
    }

    private companion object {
        /** A pixel's side, of the radius. */
        const val PIXEL = 0.117f
    }
}

// ---------------------------------------------------------------------------------------------
// Toadstool
// ---------------------------------------------------------------------------------------------

/** A toadstool: a cap in its player's colour, spotted in cream, on a fat cream stalk. */
class ToadstoolPawnStyle(id: String) : GridPawnStyle(id, Color(0xFFE53935)) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val stroke = outline(g)
        val cream = Color(0xFFFFF3DC)
        inked(g.smooth(-0.28f, -0.2f, 0.28f, -0.2f, 0.34f, 0.3f, 0.5f, 0.72f, 0f, 0.86f, -0.5f, 0.72f, -0.34f, 0.3f), lit(g, cream, shade = 0.18f), stroke)
        // The gills' shadow under the cap, then the cap.
        val cap = Path().apply {
            fun cubic(ax: Float, ay: Float, bx: Float, by: Float, x: Float, y: Float) {
                val a = g.at(ax, ay)
                val b = g.at(bx, by)
                val to = g.at(x, y)
                cubicTo(a.x, a.y, b.x, b.y, to.x, to.y)
            }
            g.at(-0.88f, 0f).let { moveTo(it.x, it.y) }
            cubic(-0.86f, -0.66f, -0.44f, -0.98f, 0f, -0.98f)
            cubic(0.44f, -0.98f, 0.86f, -0.66f, 0.88f, 0f)
            cubic(0.6f, 0.2f, -0.6f, 0.2f, -0.88f, 0f)
            close()
        }
        drawPath(cap, lit(g, colour))
        clipPath(cap) {
            for ((x, y, w) in listOf(Triple(-0.1f, -0.58f, 0.36f), Triple(0.46f, -0.36f, 0.28f), Triple(-0.58f, -0.22f, 0.3f), Triple(0.06f, -0.08f, 0.26f), Triple(0.78f, -0.04f, 0.22f))) {
                drawOval(cream, g.ovalTopLeft(x, y, w, w * 0.8f), g.size(w, w * 0.8f))
            }
        }
        drawPath(cap, BoardInk, style = stroke)
    }
}

// ---------------------------------------------------------------------------------------------
// Cake
// ---------------------------------------------------------------------------------------------

/** A cupcake: a pleated case, a swirl of icing in its player's colour with sprinkles, and a cherry on top. */
class CakePawnStyle(id: String) : GridPawnStyle(id, Color(0xFFF48FB1)) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val stroke = outline(g)
        val case = g.polygon(-0.66f, 0.1f, 0.66f, 0.1f, 0.46f, 0.84f, -0.46f, 0.84f)
        drawPath(case, lit(g, lerp(colour, Color.Black, 0.3f)))
        clipPath(case) {
            for (i in -3..3) drawLine(BoardInk.copy(alpha = 0.45f), g.at(i * 0.19f, 0.1f), g.at(i * 0.13f, 0.86f), g.r * 0.05f)
        }
        drawPath(case, BoardInk, style = stroke)
        val icing = lit(g, lerp(colour, Color.White, 0.18f))
        for ((y, width, height) in listOf(Triple(0.04f, 1.56f, 0.5f), Triple(-0.28f, 1.16f, 0.46f), Triple(-0.56f, 0.74f, 0.4f))) {
            drawOval(icing, g.ovalTopLeft(0f, y, width, height), g.size(width, height))
            drawOval(BoardInk, g.ovalTopLeft(0f, y, width, height), g.size(width, height), style = stroke)
        }
        for ((x, y, tilt) in listOf(Triple(-0.44f, 0.02f, 0.06f), Triple(-0.06f, 0.14f, -0.05f), Triple(0.4f, 0.02f, 0.07f), Triple(-0.24f, -0.28f, -0.06f), Triple(0.22f, -0.26f, 0.05f))) {
            drawLine(Color.White, g.at(x - 0.06f, y - tilt), g.at(x + 0.06f, y + tilt), g.r * 0.08f, cap = StrokeCap.Round)
        }
        inkedCircle(g, 0f, -0.79f, 0.17f, lit(g, lerp(colour, Color.Black, 0.25f)), stroke)
        drawCircle(Color.White.copy(alpha = 0.8f), 0.05f * g.r, g.at(-0.06f, -0.85f))
    }
}

// ---------------------------------------------------------------------------------------------
// Space
// ---------------------------------------------------------------------------------------------

/** Which spacecraft a [SpacePawnStyle] is. */
enum class PawnSpacecraft(val swatch: Color) {
    ROCKET(Color(0xFFE57373)),
    SAUCER(Color(0xFF80DEEA)),
}

/** A rocket stood on its fins, or a flying saucer on its beam. */
class SpacePawnStyle(id: String, private val craft: PawnSpacecraft) : GridPawnStyle(id, craft.swatch) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val stroke = outline(g)
        when (craft) {
            PawnSpacecraft.ROCKET -> drawRocket(g, colour, stroke)
            PawnSpacecraft.SAUCER -> drawSaucer(g, colour, stroke)
        }
    }

    private fun DrawScope.drawRocket(g: PawnGrid, colour: Color, stroke: Stroke) {
        val dark = lerp(colour, Color.Black, 0.32f)
        inked(g.polygon(-0.22f, 0.5f, 0.22f, 0.5f, 0.3f, 0.74f, -0.3f, 0.74f), Color(0xFF5C6675), stroke)
        for (side in listOf(-1f, 1f)) {
            val fin = Path().apply {
                g.at(side * 0.34f, 0.04f).let { moveTo(it.x, it.y) }
                g.at(side * 0.78f, 0.26f).let { c -> g.at(side * 0.76f, 0.86f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
                g.at(side * 0.54f, 0.58f).let { c -> g.at(side * 0.28f, 0.54f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
                close()
            }
            inked(fin, dark, stroke)
        }
        val hull = Path().apply {
            g.at(0f, -1f).let { moveTo(it.x, it.y) }
            g.at(0.72f, -0.4f).let { c -> g.at(0.34f, 0.56f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            g.at(-0.34f, 0.56f).let { lineTo(it.x, it.y) }
            g.at(-0.72f, -0.4f).let { c -> g.at(0f, -1f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            close()
        }
        drawPath(hull, lit(g, colour))
        clipPath(hull) { drawRect(dark, g.at(-0.6f, -1f), g.size(1.2f, 0.36f)) }
        drawPath(hull, BoardInk, style = stroke)
        drawCircle(Color(0xFFDFF6FF), 0.2f * g.r, g.at(0f, -0.2f))
        drawCircle(BoardInk, 0.2f * g.r, g.at(0f, -0.2f), style = stroke)
        drawArc(Color.White, 180f, 80f, false, g.at(-0.11f, -0.31f), g.size(0.22f, 0.22f), style = Stroke(g.r * 0.05f, cap = StrokeCap.Round))
        drawLine(Color.White.copy(alpha = 0.5f), g.at(-0.36f, -0.06f), g.at(-0.28f, 0.36f), g.r * 0.07f, cap = StrokeCap.Round)
    }

    private fun DrawScope.drawSaucer(g: PawnGrid, colour: Color, stroke: Stroke) {
        // The beam down to its spot on the ground, then the dome and the hull over it.
        val beam = g.polygon(-0.3f, 0.1f, 0.3f, 0.1f, 0.62f, 0.84f, -0.62f, 0.84f)
        drawPath(beam, Brush.verticalGradient(listOf(lerp(colour, Color.White, 0.6f).copy(alpha = 0.95f), colour.copy(alpha = 0.55f)), startY = g.at(0f, 0.1f).y, endY = g.at(0f, 0.84f).y))
        drawOval(lerp(colour, Color.White, 0.5f), g.ovalTopLeft(0f, 0.84f, 1.24f, 0.22f), g.size(1.24f, 0.22f))
        val dome = Path().apply {
            g.at(-0.46f, -0.28f).let { moveTo(it.x, it.y) }
            g.at(-0.46f, -0.98f).let { a -> g.at(0.46f, -0.98f).let { b -> g.at(0.46f, -0.28f).let { cubicTo(a.x, a.y, b.x, b.y, it.x, it.y) } } }
            close()
        }
        inked(dome, Color(0xFFDFF6FF), stroke)
        // The pilot, up to its eyes.
        clipPath(dome) {
            drawOval(lerp(colour, Color.Black, 0.2f), g.ovalTopLeft(0f, -0.44f, 0.5f, 0.6f), g.size(0.5f, 0.6f))
            for (x in listOf(-0.1f, 0.1f)) drawOval(BoardInk, g.ovalTopLeft(x, -0.53f, 0.13f, 0.18f), g.size(0.13f, 0.18f))
        }
        drawOval(lit(g, colour), g.ovalTopLeft(0f, -0.08f, 1.84f, 0.6f), g.size(1.84f, 0.6f))
        drawOval(BoardInk, g.ovalTopLeft(0f, -0.08f, 1.84f, 0.6f), g.size(1.84f, 0.6f), style = stroke)
        for ((x, y) in listOf(-0.62f to -0.06f, -0.22f to 0.06f, 0.22f to 0.06f, 0.62f to -0.06f)) {
            drawCircle(Color(0xFFFFF59D), 0.1f * g.r, g.at(x, y))
            drawCircle(BoardInk, 0.1f * g.r, g.at(x, y), style = Stroke(stroke.width * 0.7f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Robot
// ---------------------------------------------------------------------------------------------

/** A tin robot in its player's colour: a lit visor, a dial on its chest, and an aerial. */
class RobotPawnStyle(id: String) : GridPawnStyle(id, Color(0xFF90A4AE)) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val stroke = outline(g)
        val fill = lit(g, colour)
        val darkFill = lit(g, lerp(colour, Color.Black, 0.3f))
        for (x in listOf(-0.5f, 0.06f)) inkedBar(g, x, 0.56f, 0.44f, 0.3f, 0.1f, darkFill, stroke)
        for (side in listOf(-1f, 1f)) {
            inkedBar(g, if (side < 0) -0.82f else 0.56f, 0f, 0.26f, 0.52f, 0.13f, darkFill, stroke)
        }
        inkedBar(g, -0.56f, -0.06f, 1.12f, 0.7f, 0.12f, fill, stroke)
        drawCircle(Color(0xFFF5F5F5), 0.18f * g.r, g.at(-0.2f, 0.28f))
        drawCircle(BoardInk, 0.18f * g.r, g.at(-0.2f, 0.28f), style = Stroke(stroke.width * 0.8f))
        drawLine(BoardInk, g.at(-0.2f, 0.28f), g.at(-0.1f, 0.18f), g.r * 0.06f, cap = StrokeCap.Round)
        for (y in listOf(0.16f, 0.36f)) drawLine(BoardInk.copy(alpha = 0.7f), g.at(0.14f, y), g.at(0.38f, y), g.r * 0.07f, cap = StrokeCap.Round)
        drawLine(BoardInk, g.at(0f, -0.74f), g.at(0f, -0.88f), g.r * 0.09f)
        inkedCircle(g, 0f, -0.89f, 0.11f, lit(g, lerp(colour, Color.White, 0.4f)), Stroke(stroke.width * 0.8f))
        for (x in listOf(-0.64f, 0.48f)) inkedBar(g, x, -0.5f, 0.16f, 0.26f, 0.05f, darkFill, stroke)
        inkedBar(g, -0.5f, -0.74f, 1f, 0.66f, 0.14f, fill, stroke)
        drawRoundRect(BoardInk, g.at(-0.38f, -0.58f), g.size(0.76f, 0.32f), CornerRadius(0.12f * g.r))
        for (x in listOf(-0.18f, 0.18f)) drawCircle(Color(0xFFB2FFF4), 0.1f * g.r, g.at(x, -0.42f))
        drawLine(BoardInk, g.at(-0.18f, -0.17f), g.at(0.18f, -0.17f), g.r * 0.06f, cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------------------------------------
// Neon
// ---------------------------------------------------------------------------------------------

/** A pawn bent from a glowing tube in its player's colour, round a dark inside. */
class NeonPawnStyle(id: String) : GridPawnStyle(id, Color(0xFF3FD8FF)) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val body = Path().apply {
            g.at(-0.2f, -0.22f).let { moveTo(it.x, it.y) }
            g.at(-0.28f, 0.3f).let { c -> g.at(-0.72f, 0.54f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            g.at(-0.8f, 0.82f).let { c -> g.at(-0.6f, 0.82f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            g.at(0.6f, 0.82f).let { lineTo(it.x, it.y) }
            g.at(0.8f, 0.82f).let { c -> g.at(0.72f, 0.54f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            g.at(0.28f, 0.3f).let { c -> g.at(0.2f, -0.22f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            close()
        }
        val head = g.at(0f, -0.54f)
        val dark = Color(0xFF0E0E24)
        // Dark to the edge of its glow, so the tube reads on a pale board and on its own colour.
        val backing = Stroke(g.r * 0.34f, join = StrokeJoin.Round)
        drawPath(body, dark)
        drawPath(body, dark, style = backing)
        drawCircle(dark, 0.53f * g.r, head)
        for ((width, tone) in listOf(0.3f to colour.copy(alpha = 0.3f), 0.18f to colour, 0.07f to lerp(colour, Color.White, 0.75f))) {
            val tube = Stroke(g.r * width, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(body, tone, style = tube)
            drawCircle(tone, 0.36f * g.r, head, style = tube)
        }
        drawCircle(colour.copy(alpha = 0.3f), 0.28f * g.r, head)
    }
}

// ---------------------------------------------------------------------------------------------
// Googly
// ---------------------------------------------------------------------------------------------

/** A wobbly blob in its player's colour, with googly eyes of two sizes looking two ways. */
class GooglyPawnStyle(id: String) : GridPawnStyle(id, Color(0xFF66BB6A)) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val stroke = outline(g)
        val blob = g.smooth(-0.04f, -0.84f, 0.42f, -0.66f, 0.56f, -0.2f, 0.62f, 0.24f, 0.84f, 0.66f, 0.5f, 0.86f, 0f, 0.8f, -0.5f, 0.86f, -0.82f, 0.62f, -0.58f, 0.2f, -0.6f, -0.26f, -0.44f, -0.68f)
        inked(blob, lit(g, colour), stroke)
        drawOval(Color.White.copy(alpha = 0.45f), g.ovalTopLeft(-0.42f, 0.28f, 0.16f, 0.32f), g.size(0.16f, 0.32f))
        drawEye(g, -0.26f, -0.4f, 0.32f, 0.16f, 0.1f, 0.1f, stroke)
        drawEye(g, 0.32f, -0.34f, 0.24f, 0.12f, 0.03f, -0.09f, stroke)
        smoothLine(g, BoardInk, 0.09f, -0.24f, 0.2f, -0.06f, 0.36f, 0.16f, 0.34f, 0.34f, 0.16f)
        val tongue = g.smooth(0.04f, 0.36f, 0.24f, 0.31f, 0.26f, 0.5f, 0.12f, 0.56f)
        inked(tongue, lerp(colour, Color.White, 0.5f), Stroke(stroke.width * 0.8f))
    }
}

// ---------------------------------------------------------------------------------------------
// Devil
// ---------------------------------------------------------------------------------------------

/** A grinning imp in its player's colour, horned, with a barbed tail. */
class DevilPawnStyle(id: String) : GridPawnStyle(id, Color(0xFFC62828)) {
    override fun DrawScope.drawBody(g: PawnGrid, colour: Color) {
        val stroke = outline(g)
        val dark = lerp(colour, Color.Black, 0.35f)
        // The tail up the right, barbed at its tip.
        val tail = g.smooth(0.3f, 0.6f, 0.74f, 0.56f, 0.8f, 0.2f, 0.72f, -0.06f, closed = false)
        drawPath(tail, BoardInk, style = Stroke(g.r * 0.22f, cap = StrokeCap.Round))
        drawPath(tail, dark, style = Stroke(g.r * 0.1f, cap = StrokeCap.Round))
        inked(g.polygon(0.5f, -0.04f, 0.7f, -0.36f, 0.94f, -0.06f), dark, stroke)
        inked(g.smooth(-0.3f, 0f, 0.3f, 0f, 0.56f, 0.6f, 0.4f, 0.84f, -0.4f, 0.84f, -0.56f, 0.6f), lit(g, colour), stroke)
        for (side in listOf(-1f, 1f)) {
            val horn = Path().apply {
                g.at(side * 0.16f, -0.62f).let { moveTo(it.x, it.y) }
                g.at(side * 0.3f, -0.82f).let { c -> g.at(side * 0.34f, -1f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
                g.at(side * 0.62f, -0.82f).let { c -> g.at(side * 0.48f, -0.44f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
                close()
            }
            inked(horn, dark, stroke)
        }
        inkedCircle(g, 0f, -0.28f, 0.52f, lit(g, colour), stroke)
        for (side in listOf(-1f, 1f)) {
            drawPath(g.polygon(side * 0.34f, -0.5f, side * 0.07f, -0.34f, side * 0.22f, -0.22f, side * 0.36f, -0.3f), Color.White)
            drawLine(BoardInk, g.at(side * 0.4f, -0.56f), g.at(side * 0.06f, -0.36f), g.r * 0.1f, cap = StrokeCap.Round)
            drawCircle(BoardInk, 0.065f * g.r, g.at(side * 0.22f, -0.33f))
        }
        val grin = Path().apply {
            g.at(-0.28f, -0.1f).let { moveTo(it.x, it.y) }
            g.at(0f, 0.02f).let { c -> g.at(0.28f, -0.1f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            g.at(0f, 0.3f).let { c -> g.at(-0.28f, -0.1f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            close()
        }
        inked(grin, Color.White, Stroke(maxOf(0.75.dp.toPx(), g.r * 0.06f), join = StrokeJoin.Round))
    }
}
