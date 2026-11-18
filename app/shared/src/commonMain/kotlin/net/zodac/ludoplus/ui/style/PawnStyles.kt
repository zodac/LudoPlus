package net.zodac.ludoplus.ui.style

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
        val body = Path().apply {
            at(-0.3f, -0.18f).let { moveTo(it.x, it.y) }
            at(-0.36f, 0.3f).let { c -> at(-0.66f, 0.62f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            at(0.66f, 0.62f).let { lineTo(it.x, it.y) }
            at(0.36f, 0.3f).let { c -> at(0.3f, -0.18f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
            close()
        }
        drawPath(body, lit)
        drawPath(body, ink, style = outline)
        val baseTopLeft = at(-0.82f, 0.56f)
        val baseSize = Size(1.64f * r, 0.36f * r)
        drawRoundRect(lit, baseTopLeft, baseSize, CornerRadius(0.16f * r))
        drawRoundRect(ink, baseTopLeft, baseSize, CornerRadius(0.16f * r), style = outline)
        val collarTopLeft = at(-0.46f, -0.3f)
        val collarSize = Size(0.92f * r, 0.2f * r)
        drawRoundRect(lit, collarTopLeft, collarSize, CornerRadius(0.1f * r))
        drawRoundRect(ink, collarTopLeft, collarSize, CornerRadius(0.1f * r), style = outline)
        val head = at(0f, -0.62f)
        drawCircle(lit, 0.38f * r, head)
        drawCircle(ink, 0.38f * r, head, style = outline)
        drawCircle(Color.White.copy(alpha = 0.55f), 0.11f * r, at(-0.13f, -0.75f))
    }
}
