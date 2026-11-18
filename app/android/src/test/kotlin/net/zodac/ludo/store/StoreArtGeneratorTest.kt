package net.zodac.ludo.store

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import net.zodac.ludo.R
import net.zodac.ludo.game.GameState
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.ui.common.AppLogo
import net.zodac.ludo.ui.common.BrandBackdrop
import net.zodac.ludo.ui.game.LudoBoard
import net.zodac.ludo.ui.theme.LudoPlusTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Renders the store listing's art (the hi-res icon, the feature graphic, and the promo video's closing card) from the
 * app's own icon and artwork, each at three or four times its size for the script to scale down. A generator, not a
 * check: see [StoreAssets.onlyWhenAsked] and `.claude/STORE_ASSETS.md`.
 */
@RunWith(AndroidJUnit4::class)
class StoreArtGeneratorTest {

    @get:Rule(order = 0)
    val onlyWhenAsked = StoreAssets.onlyWhenAsked()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    /**
     * The adaptive icon (its background and foreground layers, nothing else) close to how a launcher shows it: the
     * middle of its 108dp canvas fills the square, 76dp, a little more than the 72dp a launcher shows, for a margin
     * inside Play's rounded corners. Play masks the square to its own rounded corners and adds the shadow, so neither is
     * drawn here. It follows the launcher icon, whatever that is.
     */
    @Test
    @StorePart(StoreAssets.ICON)
    fun `hi-res icon`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val size = ICON_PIXELS * SUPERSAMPLE
        val icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(icon)
        val bleed = size * (ADAPTIVE_CANVAS_DP - VISIBLE_DP) / VISIBLE_DP / 2
        listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach { layer ->
            val drawable = checkNotNull(ContextCompat.getDrawable(context, layer))
            drawable.setBounds(-bleed, -bleed, size + bleed, size + bleed)
            drawable.draw(canvas)
        }
        StoreAssets.save(icon, File(StoreAssets.outDir, "icon.png"))
    }

    /**
     * 1024x500, drawn at 3x. Play lays its play button over the middle when the listing has a video, and crops the edges
     * in some places, so the logo sits in the left half and the board in the right, nothing that matters at an edge.
     *
     * Also the backdrop's pawns drifting, every [FEATURE_GIF_STRIDE]th frame into a folder, for the README's animated copy.
     */
    @Test
    @StorePart(StoreAssets.FEATURE)
    @Config(qualifiers = "w1024dp-h500dp-xxhdpi")
    fun `feature graphic`() {
        val dir = File(StoreAssets.outDir, "feature-graphic-frames").apply { deleteRecursively(); mkdirs() }
        val game = Scenes.midGame()
        compose.mainClock.autoAdvance = false
        compose.setContent { LudoPlusTheme { BannerScene(game) } }
        for (frame in 1..FEATURE_LOOP_FRAMES) {
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            if (frame == 1) StoreAssets.save(compose.onRoot().captureToImage().asAndroidBitmap(), File(StoreAssets.outDir, "feature-graphic.png"))
            if (frame % FEATURE_GIF_STRIDE == 0) {
                StoreAssets.save(compose.onRoot().captureToImage().asAndroidBitmap(), File(dir, "%05d.png".format(frame / FEATURE_GIF_STRIDE)))
            }
        }
        compose.mainClock.autoAdvance = true
    }

    /** The video's last card: 1920x1080, drawn at 3x, the same scene, the backdrop's pawns drifting. 60fps frames, like a video scene. */
    @Test
    @StorePart(StoreAssets.VIDEO)
    @Config(qualifiers = "w640dp-h360dp-xxhdpi")
    fun `video end card`() {
        val dir = File(StoreAssets.outDir, "video-end-card").apply { deleteRecursively(); mkdirs() }
        val game = Scenes.midGame()
        compose.mainClock.autoAdvance = false
        compose.setContent { LudoPlusTheme { BannerScene(game) } }
        repeat(END_CARD_FRAMES) { frame ->
            compose.mainClock.advanceTimeBy(FRAME_MILLIS)
            StoreAssets.save(compose.onRoot().captureToImage().asAndroidBitmap(), File(dir, "%05d.png".format(frame)))
        }
        compose.mainClock.autoAdvance = true
    }

    private companion object {
        const val ICON_PIXELS = 512
        const val SUPERSAMPLE = 4
        const val ADAPTIVE_CANVAS_DP = 108
        const val VISIBLE_DP = 76
        const val FRAME_MILLIS = 16L
        const val END_CARD_FRAMES = 188

        /** The animated feature graphic: 6s of the drift. */
        const val FEATURE_LOOP_FRAMES = 375

        /** The animated feature graphic keeps one frame in this many: 48ms apart, close to a GIF's 50ms. */
        const val FEATURE_GIF_STRIDE = 3
    }
}

/**
 * The menu's backdrop with its pawns drifting, the menu's logo centred in the left half (no tagline: the logo is the
 * brand), and the board mid-game ([game], the screenshots' staged one) on the right, in the default colours.
 */
@Composable
private fun BannerScene(game: GameState) {
    BrandBackdrop(modifier = Modifier.fillMaxSize(), driftingDice = true) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val boardSize = maxHeight * BOARD_HEIGHT_FRACTION
            val logoScale = maxHeight / LOGO_HEIGHT_DIVISOR
            val half = maxWidth / 2
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    AppLogo(Modifier.scale(logoScale))
                }
                Box(Modifier.width(half).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    LudoBoard(
                        state = game,
                        seatColours = List(game.players.size) { PlayerColour.defaultFor(it) },
                        moves = emptyList(),
                        onMove = {},
                        modifier = Modifier.size(boardSize).shadow(12.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
    }
}

/** The board's height, as a share of the scene's: room above and below for Play's crops. */
private const val BOARD_HEIGHT_FRACTION = 0.82f

/** The logo's scale is the scene's height over this: about 2x on the feature graphic. */
private val LOGO_HEIGHT_DIVISOR = 250.dp
