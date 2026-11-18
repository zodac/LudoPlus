package net.zodac.ludoplus.ui.style

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import net.zodac.ludoplus.data.settings.MotionGroup

/** White light, to be tinted the lighting's colour as it's drawn. */
private fun tint(colour: Color) = ColorFilter.tint(colour, BlendMode.SrcIn)

/** A lighting effect from a gaming keyboard's software, for the RGB background. */
enum class RgbEffect {
    /** The whole panel one colour at a time, going round the hues - and breathing, brighter and dimmer. */
    RAINBOW,

    /** Diagonal bands of every colour sweeping across. */
    WAVE,

    /** Rings of colour spreading out from a point, one after another. */
    RIPPLE,

    /** The colours turning round the middle like a wheel. */
    SWIRL,

    /** A dim glow going round the hues, lights twinkling on and off across it in colours of their own. */
    STARLIGHT,
}

// The LED grid behind the panel: a light every RGB_MATRIX_PITCH dp, every other row set half a pitch over.
private const val RGB_MATRIX_PITCH = 13f

// How many board widths across one round of the hues is, for the Wave's bands and the Ripple's rings.
private const val RGB_WAVE_BOARD_WIDTHS = 0.85f
private const val RGB_RIPPLE_BOARD_WIDTHS = 0.4f

// Rainbow breathes in and out every RGB_BREATH_SECONDS.
private const val RGB_BREATH_SECONDS = 4f

// Starlight: at most this many lights twinkling at once, each for RGB_TWINKLE_SECONDS of every RGB_TWINKLE_PERIOD.
private const val RGB_TWINKLES = 22
private const val RGB_TWINKLE_SECONDS = 1.4f
private const val RGB_TWINKLE_PERIOD = 3.2f

// Faint enough to read the scorecard's numbers over.
private const val RGB_MATRIX_STRENGTH = 0.5f

/**
 * An RGB light panel - to go with the RGB dice: a dark smoked panel over a grid of LEDs, each a soft point of light
 * with a faint glow spread between them, lit with one of a gaming keyboard's lighting [effect]s (see [RgbEffect]).
 * Dark, as every background is, for the scorecard's numbers to read over. Still under reduced motion.
 *
 * Live per frame: the grid's light, painted once in white, stamped and coloured - by a tint (Rainbow, Starlight) or
 * a gradient laid over it in an offscreen layer (Wave, Ripple, Swirl) - and Starlight's few twinkles.
 */
class RgbLightBackground(override val id: String, private val palette: BackgroundPalette, private val effect: RgbEffect) : TableBackground {
    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(palette.top, palette.bottom))

    private var seconds by mutableFloatStateOf(0f)

    @Composable
    override fun Animate() {
        TwinkleClock(MotionGroup.BACKGROUND) { seconds = it }
    }

    override fun DrawScope.drawScoreAreaDecoration() {
        val pitch = RGB_MATRIX_PITCH.dp.toPx()
        val light = cachedSurface("background:rgb_matrix") { paintMatrix(pitch) } ?: return
        val phase = seconds / RGB_CYCLE_SECONDS
        when (effect) {
            RgbEffect.RAINBOW -> {
                val breath = 0.5f + 0.5f * sin(2f * PI.toFloat() * seconds / RGB_BREATH_SECONDS)
                drawImage(light, alpha = RGB_MATRIX_STRENGTH * (0.55f + 0.45f * breath), colorFilter = tint(rgbColour(phase)))
            }
            RgbEffect.STARLIGHT -> {
                drawImage(light, alpha = RGB_MATRIX_STRENGTH * 0.5f, colorFilter = tint(rgbColour(phase)))
                drawTwinkles(pitch)
            }
            else -> {
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                drawImage(light, alpha = RGB_MATRIX_STRENGTH)
                drawRect(effectBrush(phase), blendMode = BlendMode.SrcIn)
                drawContext.canvas.restore()
            }
        }
        // The smoked panel's sheen, across its top.
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.04f), Color.Transparent), endY = size.height * 0.35f))
    }

    /** The colours laid over the light, the hues moving on as they cycle: away from the middle (Ripple), round it (Swirl) or across (Wave). */
    private fun DrawScope.effectBrush(phase: Float): Brush = when (effect) {
        RgbEffect.RIPPLE -> Brush.radialGradient(
            rgbSpectrum(-phase * 2f),
            center = Offset(size.width * 0.5f, size.height * 0.42f),
            radius = size.width * RGB_RIPPLE_BOARD_WIDTHS,
            tileMode = TileMode.Repeated,
        )
        RgbEffect.SWIRL -> Brush.sweepGradient(rgbSpectrum(phase), center)
        else -> {
            val band = size.width * RGB_WAVE_BOARD_WIDTHS
            // Diagonally, down and to the right.
            val shift = band * (phase % 1f)
            Brush.linearGradient(rgbSpectrum(0f).reversed(), start = Offset(shift, 0f), end = Offset(shift + band, band * 0.5f), tileMode = TileMode.Repeated)
        }
    }

    /** Starlight's twinkles: single lights of the grid flaring up in a colour of their own and fading. */
    private fun DrawScope.drawTwinkles(pitch: Float) {
        val columns = (size.width / pitch).toInt() + 1
        val rows = (size.height / pitch).toInt() + 1
        for (slot in 0 until RGB_TWINKLES) {
            val (where, glow) = sparkle(id.hashCode(), slot, seconds, period = RGB_TWINKLE_PERIOD, flash = RGB_TWINKLE_SECONDS) ?: continue
            val row = (where.y * rows).toInt()
            val spot = matrixSpot((where.x * columns).toInt(), row, pitch)
            val colour = rgbColour(where.x * 7f + where.y * 3f)
            val reach = pitch * 1.8f
            drawCircle(Brush.radialGradient(listOf(colour.copy(alpha = glow), colour.copy(alpha = 0.35f * glow), Color.Transparent), center = spot, radius = reach), reach, spot)
            drawCircle(lerp(colour, Color.White, 0.55f).copy(alpha = glow), pitch * 0.2f, spot)
        }
    }

    /** The grid's light, in white: a soft point at every LED, and a faint even glow behind them all. */
    private fun DrawScope.paintMatrix(pitch: Float) {
        drawRect(Color.White.copy(alpha = 0.1f))
        val reach = pitch * 0.42f
        var row = 0
        while (row * pitch * 0.87f < size.height + pitch) {
            var column = 0
            while (column * pitch < size.width + pitch) {
                val spot = matrixSpot(column, row, pitch)
                drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.3f), Color.Transparent), center = spot, radius = reach), reach, spot)
                column++
            }
            row++
        }
    }

    /** Where the LED in [column] and [row] is: rows a little closer than columns, every other one set half a [pitch] over. */
    private fun matrixSpot(column: Int, row: Int, pitch: Float) =
        Offset((column + if (row % 2 == 1) 0.5f else 0f) * pitch, (row + 0.5f) * pitch * 0.87f)
}
