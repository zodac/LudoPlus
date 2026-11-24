package net.zodac.ludoplus.ui.style

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt
import kotlin.random.Random

/** A honeycomb's colours: its [wax] walls and caps, and the honey in its cells, [honey] at the rim deepening to [deepHoney]. */
internal class CombColours(val wax: Color, val honey: Color, val deepHoney: Color)

/**
 * Honeycomb across the whole surface: flat-topped cells [radius] across, in [colours]' wax - most of
 * them full of glistening honey, some sealed with a pale wax cap, the odd one empty and showing its depth.
 * Seeded by [seed], so it's the same comb every time. Hundreds of cells: paint it through
 * [drawCachedSurface], never live.
 */
internal fun DrawScope.paintHoneycomb(seed: Int, colours: CombColours, radius: Float) {
    val random = Random(seed)
    val wall = radius * 0.16f
    val rowHeight = radius * sqrt(3f)
    drawRect(lerp(colours.wax, Color.Black, 0.35f))
    var column = 0
    var cx = 0f
    while (cx < size.width + radius) {
        var cy = if (column % 2 == 0) 0f else rowHeight / 2f
        while (cy < size.height + rowHeight) {
            val centre = Offset(cx, cy)
            val cell = polygonPath(hexagonPoints(centre, radius - wall * 0.5f))
            val inside = polygonPath(hexagonPoints(centre, radius - wall * 1.5f))
            // The wax wall, lit along its top.
            drawPath(cell, Brush.linearGradient(listOf(lerp(colours.wax, Color.White, 0.25f), colours.wax), start = centre - Offset(0f, radius), end = centre + Offset(0f, radius)))
            val roll = random.nextFloat()
            when {
                roll < 0.7f -> {
                    // Honey: deep in the middle, brighter towards the walls where it's shallow, a glint of light on its surface.
                    drawPath(inside, Brush.radialGradient(listOf(colours.deepHoney, colours.honey, lerp(colours.honey, Color.White, 0.2f)), center = centre, radius = radius))
                    clipPath(inside) {
                        drawRect(Color.Black.copy(alpha = 0.18f), topLeft = centre - Offset(radius, radius), size = Size(radius * 2f, wall * 1.6f))
                    }
                    drawOval(Color.White.copy(alpha = 0.45f), topLeft = centre + Offset(-radius * 0.5f, -radius * 0.55f), size = Size(radius * 0.42f, radius * 0.22f))
                    drawCircle(Color.White.copy(alpha = 0.6f), radius * 0.06f, centre + Offset(radius * 0.32f, radius * 0.3f))
                }
                roll < 0.93f -> {
                    // A wax cap, sealed over the honey: pale, softly domed.
                    drawPath(inside, Brush.radialGradient(listOf(lerp(colours.wax, Color.White, 0.55f), lerp(colours.wax, Color.White, 0.2f)), center = centre - Offset(radius * 0.2f, radius * 0.25f), radius = radius))
                    drawPath(inside, lerp(colours.wax, Color.Black, 0.15f).copy(alpha = 0.5f), style = Stroke(width = wall * 0.4f))
                }
                else -> {
                    // Empty: dark at the bottom of the cell, its far walls catching the light.
                    drawPath(inside, Brush.radialGradient(listOf(lerp(colours.deepHoney, Color.Black, 0.65f), lerp(colours.wax, Color.Black, 0.4f)), center = centre + Offset(radius * 0.1f, radius * 0.15f), radius = radius))
                    clipPath(inside) {
                        drawRect(lerp(colours.wax, Color.White, 0.1f).copy(alpha = 0.35f), topLeft = centre + Offset(-radius, radius * 0.45f), size = Size(radius * 2f, radius))
                    }
                }
            }
            cy += rowHeight
        }
        cx += radius * 1.5f
        column++
    }
}

/**
 * A honeycomb background - to go with the Bestagon dice: a honeycomb, dimmed well back so the board and cards read over
 * it. Painted once. [BackgroundPalette.detail] is its wax.
 */
class HoneycombCombBackground(override val id: String, private val palette: BackgroundPalette) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    override fun DrawScope.drawScoreAreaDecoration() {
        drawCachedSurface("background:$id") {
            paintHoneycomb(37, CombColours(palette.detail, lerp(palette.detail, palette.top, 0.3f), palette.bottom), 16.dp.toPx())
            drawRect(Brush.verticalGradient(listOf(palette.top.copy(alpha = 0.72f), palette.bottom.copy(alpha = 0.8f))))
        }
    }
}
