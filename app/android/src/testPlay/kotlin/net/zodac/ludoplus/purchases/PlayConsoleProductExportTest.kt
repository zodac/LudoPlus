package net.zodac.ludoplus.purchases

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.ludoplus.online.exportingPlayConsole
import net.zodac.ludoplus.online.playConsoleDir
import net.zodac.ludoplus.ui.icons.Symbols
import net.zodac.ludoplus.ui.styles.UnlockAllStylesIcon
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Each product's icon for Play Console (Monetise with Play > Products > One-time products), entered by hand: the rule
 * they keep to, checked on every run, and (with `-PexportPlayConsole`, which `scripts/export-play-console.sh` passes)
 * the icons themselves, written to the gitignored `assets/play-console/` folder.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w600dp-h900dp-mdpi")
class PlayConsoleProductExportTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `each icon's artwork keeps clear of its edges - Play asks for it in the centre`() {
        var tile = 0
        var shown by mutableStateOf(Product.entries.first())
        compose.setContent { IconTile(shown, onTile = { tile = it }) }
        for (product in Product.entries) {
            shown = product
            val bitmap = icon()
            val edge = (0 until ICON_SIZE).flatMap { i ->
                (0 until MARGIN).flatMap { m ->
                    listOf(bitmap.getPixel(i, m), bitmap.getPixel(i, ICON_SIZE - 1 - m), bitmap.getPixel(m, i), bitmap.getPixel(ICON_SIZE - 1 - m, i))
                }
            }
            assertTrue("$product: artwork within $MARGIN px of the edge", edge.all { it == tile })
        }
    }

    @Test
    fun `export the icon for Play Console`() {
        assumeTrue("Only with -PexportPlayConsole", exportingPlayConsole)
        val icons = playConsoleDir().resolve("product-icons").apply { deleteRecursively(); mkdirs() }
        var shown by mutableStateOf(Product.entries.first())
        compose.setContent { IconTile(shown) }
        for (product in Product.entries) {
            shown = product
            icons.resolve("${product.id}.png").outputStream().use { icon().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun icon(): Bitmap {
        compose.waitForIdle()
        return compose.onNodeWithTag(ICON_TAG).captureToImage().asAndroidBitmap().also { assertEquals(ICON_SIZE, it.width) }
    }

    /**
     * A product's Play icon on the achievements' tile, 512px square as theirs are: the styles' mark, the no-ads mark, or
     * the two side by side for the combined product.
     */
    @Composable
    private fun IconTile(product: Product, onTile: (argb: Int) -> Unit = {}) {
        LudoPlusTheme {
            val colours = MaterialTheme.colorScheme
            onTile(colours.primaryContainer.toArgb())
            Box(Modifier.size(ICON_SIZE.dp).background(colours.primaryContainer).testTag(ICON_TAG), contentAlignment = Alignment.Center) {
                val styles = @Composable { size: Int -> UnlockAllStylesIcon(size = size.dp, tint = colours.onPrimaryContainer) }
                val noAds = @Composable { size: Int ->
                    Icon(imageVector = Symbols.Block, contentDescription = null, tint = colours.onPrimaryContainer, modifier = Modifier.size(size.dp))
                }
                when (product) {
                    Product.UNLOCK_ALL_STYLES -> styles(ICON_SIZE)
                    Product.REMOVE_ADS -> noAds(NO_ADS_SIZE)
                    Product.UNLOCK_ALL_STYLES_AND_REMOVE_ADS -> Row(verticalAlignment = Alignment.CenterVertically) {
                        styles(ICON_SIZE / 2)
                        noAds(NO_ADS_SIZE / 2)
                    }
                }
            }
        }
    }

    private companion object {
        const val ICON_SIZE = 512

        /** Clear on every side: the padlock's disc, the nearest, ends 47px from the bottom and 59px from the left. */
        const val MARGIN = 40

        /** The no-ads mark: a glyph's share of the tile, as the achievements' are. */
        const val NO_ADS_SIZE = 320
        const val ICON_TAG = "product-icon"
    }
}
