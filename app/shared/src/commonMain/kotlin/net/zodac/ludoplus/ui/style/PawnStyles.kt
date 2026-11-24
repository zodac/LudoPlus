package net.zodac.ludoplus.ui.style

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import net.zodac.ludoplus.data.settings.DefaultStyleIds
import net.zodac.ludoplus.ui.theme.BoardInk
import net.zodac.ludoplus.ui.theme.BoardIvory
import net.zodac.ludoplus.ui.theme.Primary

/** The ring round a pawn that can be moved. */
internal val MovableRing = Primary

/** The default [PawnStyle]: a glossy turned pawn, lit from the left and outlined in ink. */
object ClassicPawnStyle : PawnStyle, Swatched {
    override val id: String = DefaultStyleIds.PAWNS
    override val swatch: Color = BoardIvory

    /**
     * A pawn standing on its spot, seen from the side: a round head on a collar, over a body flaring to a base, lit from
     * the left in [colour] and outlined in ink, with a shadow at its foot. It stands within [radius] across of [centre]
     * (a little taller than wide), and a violet ring round it when [ringed].
     */
    override fun DrawScope.drawPawn(centre: Offset, radius: Float, colour: Color, ringed: Boolean) {
        val r = radius
        fun at(x: Float, y: Float) = Offset(centre.x + x * r, centre.y + y * r)
        if (ringed) drawCircle(MovableRing, r * 1.3f, at(0f, -0.04f), style = Stroke(r * 0.2f))
        val ink = BoardInk
        val outline = Stroke(width = maxOf(1.dp.toPx(), r * 0.09f))
        val light = lerp(colour, Color.White, 0.35f)
        val shade = lerp(colour, Color.Black, 0.28f)
        val lit = Brush.horizontalGradient(listOf(light, colour, shade), startX = centre.x - 0.7f * r, endX = centre.x + 0.7f * r)
        // The shadow it casts, then the base, body, collar, and head, back to front.
        drawOval(Color.Black.copy(alpha = 0.25f), at(-0.8f, 0.72f), Size(1.6f * r, 0.36f * r))
        val body = ClassicPawnShape.body(centre, r)
        drawPath(body, lit)
        drawPath(body, ink, style = outline)
        for (part in listOf(ClassicPawnShape.BASE, ClassicPawnShape.COLLAR)) {
            val topLeft = at(part.left, part.top)
            val partSize = Size(part.width * r, part.height * r)
            val corner = CornerRadius(part.topLeftCornerRadius.x * r)
            drawRoundRect(lit, topLeft, partSize, corner)
            drawRoundRect(ink, topLeft, partSize, corner, style = outline)
        }
        val head = at(ClassicPawnShape.HEAD.x, ClassicPawnShape.HEAD.y)
        drawCircle(lit, ClassicPawnShape.HEAD_RADIUS * r, head)
        drawCircle(ink, ClassicPawnShape.HEAD_RADIUS * r, head, style = outline)
        drawCircle(
            Color.White.copy(alpha = 0.55f),
            ClassicPawnShape.GLINT_RADIUS * r,
            at(ClassicPawnShape.GLINT.x, ClassicPawnShape.GLINT.y),
        )
    }
}

/**
 * The classic pawn's parts, in pawn radii from its centre: what [ClassicPawnStyle] draws, and the menu's drifting pawns
 * ([net.zodac.ludoplus.ui.common.BrandBackdrop]) with it.
 */
internal object ClassicPawnShape {

    val BASE = RoundRect(Rect(Offset(-0.82f, 0.56f), Size(1.64f, 0.36f)), CornerRadius(0.16f))
    val COLLAR = RoundRect(Rect(Offset(-0.46f, -0.3f), Size(0.92f, 0.2f)), CornerRadius(0.1f))
    val HEAD = Offset(0f, -0.62f)
    const val HEAD_RADIUS = 0.38f
    val GLINT = Offset(-0.13f, -0.75f)
    const val GLINT_RADIUS = 0.11f

    /** The middle of the pawn's height, from the top of its head to the bottom of its base. */
    val MIDDLE = Offset(0f, (HEAD.y - HEAD_RADIUS + BASE.bottom) / 2f)

    /** The whole pawn as one shape, for a fill that's the same strength where its parts overlap. */
    val silhouette: Path by lazy { parts().reduce { whole, part -> Path.combine(PathOperation.Union, whole, part) } }

    /**
     * Every line the pawn shows, as one path: each part's outline, but for what a part in front of it hides. One path, so
     * a translucent stroke is the same strength where two parts meet.
     */
    val lines: Path by lazy {
        val parts = parts()
        Path().apply {
            parts.forEachIndexed { index, part ->
                addPath(parts.drop(index + 1).fold(part) { left, front -> Path.combine(PathOperation.Difference, left, front) })
            }
        }
    }

    /** The body of a pawn of [radius] at [centre], flaring from under its collar to its base. */
    fun body(centre: Offset, radius: Float): Path {
        fun at(x: Float, y: Float) = Offset(centre.x + x * radius, centre.y + y * radius)
        return Path().apply {
            at(-0.3f, -0.18f).let { moveTo(it.x, it.y) }
            at(-0.36f, 0.3f).let { c -> at(-0.66f, 0.62f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            at(0.66f, 0.62f).let { lineTo(it.x, it.y) }
            at(0.36f, 0.3f).let { c -> at(0.3f, -0.18f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            close()
        }
    }

    /** The body, base, collar, and head, back to front. */
    private fun parts(): List<Path> = listOf(
        body(Offset.Zero, 1f),
        Path().apply { addRoundRect(BASE) },
        Path().apply { addRoundRect(COLLAR) },
        Path().apply { addOval(Rect(HEAD, HEAD_RADIUS)) },
    )
}
