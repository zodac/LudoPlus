package net.zodac.ludo.store

import android.os.Looper
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Duration
import net.zodac.ludo.MainActivity
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.device.AndroidAppContainer
import net.zodac.ludo.game.GameState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Renders the README's (and, from Phase 10, the store listing's) screenshots, phone and 10-inch tablet, from the real
 * app driven through its own screens. A generator, not a check - see [StoreAssets.onlyWhenAsked] and
 * `.claude/STORE_ASSETS.md`.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreensGeneratorTest {

    private companion object {
        const val GAME = "game"
        const val RULES = "rules"
        const val TUTORIAL = "tutorial"
        const val GAME_OVER = "game-over"
        const val LEADERBOARD = "leaderboard"
        const val STATISTICS = "statistics"

        init {
            // The test's own clock has to drive the app - see MainActivity.useCappedFrameClock.
            MainActivity.useCappedFrameClock = false
        }
    }

    @get:Rule(order = 0)
    val onlyWhenAsked = StoreAssets.onlyWhenAsked()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val container: AppContainer
        get() = AndroidAppContainer.get(ApplicationProvider.getApplicationContext())

    @Test
    @StorePart(StoreAssets.PHONE)
    // 9:16, Play's featuring shape (1440x2560 once captioned).
    @Config(qualifiers = "w412dp-h732dp-xxhdpi")
    fun `phone screenshots`() = screenshots("phone")

    @Test
    @StorePart(StoreAssets.TABLET)
    // Landscape, where a tablet's game screen lays the board out beside the players.
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun `10-inch tablet screenshots`() = screenshots("tablet-10")

    /**
     * Six screens, the strongest first: names are listing order, and the script captions each
     * (`scripts/store-assets/captions.txt`).
     */
    private fun screenshots(device: String) {
        val dir = File(StoreAssets.outDir, device).apply { deleteRecursively(); mkdirs() }
        var number = 0
        fun shoot(name: String) {
            settle()
            StoreAssets.save(compose.captureScreen(), File(dir, "%d-%s.png".format(++number, name)))
        }
        StoreState.seed(container)

        // A game in full swing, Sam to choose between moves, one of them a capture.
        resume(Scenes.midGame())
        shoot(GAME)
        leaveGame()

        // The Rules' Captures page, with its diagram.
        openFromMenu("Rules")
        compose.onNode(isTab and hasText("Captures")).performSemanticsAction(SemanticsActions.OnClick)
        shoot(RULES)

        // The tutorial from How to Play, on to its second part (captures).
        compose.onNode(isTab and hasText("How to Play")).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNode(hasText("Play the Tutorial")).performScrollTo().performClick()
        compose.waitUntil(10_000) { exists(DIE) }
        settle()
        compose.onNode(hasContentDescription("Next part")).performClick()
        shoot(TUTORIAL)
        back()
        compose.onNode(hasText("Leave")).performClick()
        compose.waitForIdle()
        back()

        // Sam's last pawn home: the win, played out, then the results in the middle of the celebration.
        resume(Scenes.lastMove())
        compose.mainClock.autoAdvance = false
        compose.onNode(WINNING_MOVE).performSemanticsAction(SemanticsActions.OnClick)
        stepUntil(20_000) { exists(hasText("Winner")) }
        stepFor(1_200)
        StoreAssets.save(compose.captureScreen(), File(dir, "%d-%s.png".format(++number, GAME_OVER)))
        compose.mainClock.autoAdvance = true
        back()

        openFromMenu("Leaderboard")
        shoot(LEADERBOARD)
        back()

        openFromMenu("Statistics")
        shoot(STATISTICS)
        back()
    }

    // ---- Driving the app -------------------------------------------------------------------------------------------

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    /** The die, whatever it shows: on screen means the board is. */
    private val DIE = hasContentDescription("Die", substring = true)

    /** The board square whose move brings a pawn home: in [Scenes.lastMove], Sam's last, which wins. */
    private val WINNING_MOVE = SemanticsMatcher("offers the move home") { node ->
        node.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label?.endsWith("reaching home") == true
    }

    private fun exists(matcher: SemanticsMatcher): Boolean = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /**
     * Steps both clocks a frame at a time until [done]: the game's own waits run on the main looper, and a new window
     * only appears as that clock moves on under Robolectric, so a plain wait never sees them.
     */
    private fun stepUntil(timeoutMillis: Long = 5_000, done: () -> Boolean) {
        var waited = 0L
        while (!done()) {
            if (waited >= timeoutMillis) {
                StoreAssets.save(compose.captureScreen(), File(StoreAssets.outDir, "stuck.png"))
                error("Still waiting after ${timeoutMillis}ms")
            }
            compose.mainClock.advanceTimeBy(16)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            waited += 16
        }
    }

    private fun stepFor(millis: Long) {
        var stepped = 0L
        while (stepped < millis) {
            compose.mainClock.advanceTimeBy(16)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
            stepped += 16
        }
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_500)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_500))
        compose.waitForIdle()
    }

    private fun menu() {
        compose.waitUntil(10_000) { exists(hasText("Settings")) && exists(hasText("Rules")) }
    }

    private fun openFromMenu(name: String) {
        menu()
        compose.onNode(hasText(name)).performClick()
        compose.waitForIdle()
        settle()
    }

    private fun back() {
        Espresso.pressBack()
        compose.waitForIdle()
    }

    /** Saves [state] and continues it from the menu. */
    private fun resume(state: GameState) {
        StoreState.saveGame(container, state)
        menu()
        compose.waitUntil(10_000) { exists(hasText("Continue")) }
        compose.onNode(hasText("Continue")).performClick()
        compose.waitUntil(10_000) { exists(DIE) }
        settle()
    }

    private fun leaveGame() {
        back()
        if (exists(hasText("Leave"))) compose.onNode(hasText("Leave")).performClick()
        menu()
    }
}
