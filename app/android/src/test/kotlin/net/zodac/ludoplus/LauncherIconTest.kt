package net.zodac.ludoplus

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The launcher icon's layers, drawn at [PIXELS_PER_UNIT] pixels for each unit of their 108-unit canvas. */
@RunWith(AndroidJUnit4::class)
class LauncherIconTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun render(layer: Int): Bitmap {
        val size = CANVAS_UNITS * PIXELS_PER_UNIT
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val drawable = checkNotNull(ContextCompat.getDrawable(context, layer))
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun `the wrapper has all three layers`() {
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.background)
        assertNotNull(icon.foreground)
        assertNotNull(icon.monochrome)
    }

    @Test
    fun `the background is opaque to every edge of the canvas, where launchers move it about`() {
        val background = render(R.drawable.ic_launcher_background)
        val pixels = IntArray(background.width * background.height)
        background.getPixels(pixels, 0, background.width, 0, 0, background.width, background.height)
        assertEquals(0, pixels.count { Color.alpha(it) != OPAQUE })
    }

    @Test
    fun `the background is the brand navy, the secondary seed`() {
        val background = render(R.drawable.ic_launcher_background)
        val pixel = background.getPixel(0, 0)
        assertEquals(BRAND_NAVY, pixel)
    }

    @Test
    fun `the pawn keeps its margin inside the 72 units a launcher shows`() {
        val foreground = render(R.drawable.ic_launcher_foreground)
        val low = ((VISIBLE_START + MIN_MARGIN) * PIXELS_PER_UNIT).toInt()
        val high = ((CANVAS_UNITS - VISIBLE_START - MIN_MARGIN) * PIXELS_PER_UNIT).toInt()
        val outside = (0 until foreground.height).sumOf { y ->
            (0 until foreground.width).count { x ->
                (x < low || x >= high || y < low || y >= high) && Color.alpha(foreground.getPixel(x, y)) == OPAQUE
            }
        }
        assertEquals(0, outside)
    }

    @Test
    fun `the themed icon's art sits inside the foreground's`() {
        val foreground = render(R.drawable.ic_launcher_foreground)
        val monochrome = render(R.drawable.ic_launcher_monochrome)
        var solid = 0
        var uncovered = 0
        for (y in 0 until monochrome.height) {
            for (x in 0 until monochrome.width) {
                if (Color.alpha(monochrome.getPixel(x, y)) == OPAQUE) {
                    solid++
                    if (Color.alpha(foreground.getPixel(x, y)) != OPAQUE) uncovered++
                }
            }
        }
        // Antialiased edges may differ by a hair; art out of place is far more.
        assertTrue("$uncovered of $solid solid pixels are off the foreground", uncovered * 100 < solid)
    }

    private companion object {
        const val VISIBLE_START = 18
        const val MIN_MARGIN = 2.5
        const val CANVAS_UNITS = 108
        const val PIXELS_PER_UNIT = 10
        const val OPAQUE = 255

        /** The secondary seed in the shared module's ui/theme/Color.kt. */
        const val BRAND_NAVY = 0xFF1E2A4A.toInt()
    }
}
