package net.zodac.ludo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import kotlin.test.Test
import kotlin.test.assertTrue
import net.zodac.ludo.app.AchievementEvent
import net.zodac.ludo.app.AchievementEvents
import net.zodac.ludo.app.LocalAppContainer
import net.zodac.ludo.model.Achievement
import net.zodac.ludo.ui.achievements.AchievementBannerHost
import net.zodac.ludo.ui.common.LocalReduceMotion
import net.zodac.ludo.ui.theme.LudoPlusTheme

/**
 * Under reduced motion the achievement banners don't move: a burst is all there at once rather than dealt out one by
 * one, a banner doesn't fade in, and it's gone the moment its time is up rather than fading out.
 */
class BannerReducedMotionTest : UiTest() {

    private fun announcement(name: String) = hasContentDescription("Achievement earned: $name.", substring = true)

    private fun banner(name: String) = compose.onNode(announcement(name), useUnmergedTree = true)

    private fun exists(name: String) = compose.onAllNodes(announcement(name), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun shot(name: String): Bitmap = banner(name).captureToImage().asAndroidBitmap()

    private fun samePixels(a: Bitmap, b: Bitmap): Boolean =
        a.width == b.width && a.height == b.height && (0 until a.width step 2).all { x -> (0 until a.height step 2).all { y -> a.getPixel(x, y) == b.getPixel(x, y) } }

    @Test
    fun `a burst of banners is there at once, at full strength, and each is gone the moment its time is up`() = uiTest {
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalReduceMotion provides true,
            ) {
                LudoPlusTheme {
                    AchievementBannerHost(
                        isOnGameScreen = { false },
                        onAchievementSelected = {},
                    ) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        val achievements = listOf(Achievement.GOTCHA, Achievement.HAT_TRICK, Achievement.PACIFIST)
        val names = listOf("Gotcha!", "Hat Trick", "Pacifist")
        achievements.forEach { AchievementEvents.emit(AchievementEvent.Unlocked(it)) }
        compose.mainClock.advanceTimeBy(FRAMES_TO_SHOW)
        // Idle with the clock still paused: lets the banners' dialog window come up, without any time passing.
        compose.waitForIdle()
        // All of them, not one every STAGGER_MILLIS.
        names.forEach { assertTrue(exists(it), "$it isn't showing yet") }

        // The first to arrive is the front banner. Already as it'll stay: no fade-in still under way.
        val front = names.first()
        val atOnce = shot(front)
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue(samePixels(atOnce, shot(front)), "$front was still fading in")

        // Gone as soon as its 4s hold is up, not still there fading out.
        compose.mainClock.advanceTimeBy(HOLD_MILLIS - 1_000 + FRAMES_TO_SHOW)
        assertTrue(!exists(front), "$front was still on screen after its time was up")
        assertTrue(exists(names[1]), "${names[1]} was gone too soon")
    }

    private companion object {
        /** A few frames: for a change to be composed and a snap to land. */
        const val FRAMES_TO_SHOW = 64L

        /** AchievementBannerHost's HOLD_MILLIS: how long the front banner stays before it goes. */
        const val HOLD_MILLIS = 4_000L
    }
}
