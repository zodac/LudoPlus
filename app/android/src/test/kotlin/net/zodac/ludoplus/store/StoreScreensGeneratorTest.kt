package net.zodac.ludoplus.store

import android.os.Looper
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Duration
import kotlin.random.Random
import net.zodac.ludoplus.MainActivity
import net.zodac.ludoplus.app.AchievementEvent
import net.zodac.ludoplus.app.AchievementEvents
import net.zodac.ludoplus.app.AppContainer
import net.zodac.ludoplus.device.AndroidAppContainer
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.ui.game.StartedGame
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
        const val PORTALS = "portals"
        const val HAUNTED_HOUSE = "haunted-house"
        const val STYLES = "styles"
        const val GAME_OVER = "game-over"
        const val LEADERBOARD = "leaderboard"
        const val STATISTICS = "statistics"
        const val ACHIEVEMENTS = "achievements"

        /** The families the video picks on the Die page. */
        val DICE_PICKS = listOf("Metal", "Obsidian", "Retro", "Cake")

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
     * The promo video's scenes, one folder of 60fps frames each, in playing order, each named for its caption
     * (`scripts/store-assets/captions.txt`). The script lays each over its caption and joins them, with the end card
     * ([StoreArtGeneratorTest]) last.
     */
    @Test
    @StorePart(StoreAssets.VIDEO)
    // The screenshots' 9:16 phone: in a 16:9 frame the screen's height is what limits it.
    @Config(qualifiers = "w412dp-h732dp-xhdpi", shadows = [RecordingSoundPool::class])
    fun `promo video frames`() {
        val root = File(StoreAssets.outDir, "video").apply { deleteRecursively(); mkdirs() }
        StoreState.seed(container)
        // Earned already, so no banner covers a scene but the last scene's own, which unlocks one of those left.
        StoreState.unlockAllBut(container, Scenes.LOCKED)
        val dealt = DealtRandom()
        container.diceRandom = dealt

        // A first turn: a 6 brings a pawn out, and earns another roll, which moves it on.
        resume(Scenes.newGame())
        FrameRecorder(compose, File(root, "1-turn")).apply {
            hold(700)
            dealt.deal(6)
            tap(compose.onNode(DIE))
            holdUntil(5_000) { exists(moveLabelled { it.startsWith("Bring") }) }
            hold(500)
            tap(compose.onAllNodes(moveLabelled { it.startsWith("Bring") })[0])
            holdUntil(5_000) { exists(DIE and hasClickAction()) }
            hold(500)
            dealt.deal(5)
            tap(compose.onNode(DIE))
            hold(900)
            if (exists(moveLabelled { it.startsWith("Move") })) tap(compose.onAllNodes(moveLabelled { it.startsWith("Move") })[0])
            hold(1_800)
            finish()
        }
        leaveGame()

        // A capture, from the screenshot's game in full swing.
        resume(Scenes.midGame())
        FrameRecorder(compose, File(root, "2-capture")).apply {
            hold(800)
            tap(compose.onAllNodes(moveLabelled { "capturing" in it })[0])
            hold(2_600)
            finish()
        }
        leaveGame()

        // Through a portal, at a table of its own.
        StoreState.applyStyles(container, Scenes.POND)
        resume(Scenes.portals())
        FrameRecorder(compose, File(root, "3-portals")).apply {
            hold(800)
            tap(compose.onAllNodes(moveLabelled { "through a portal" in it })[0])
            hold(2_600)
            finish()
        }
        leaveGame()

        // A ghost catching a pawn, on the six-player board.
        StoreState.applyStyles(container, Scenes.CRYPT)
        resume(Scenes.hauntedHouse())
        FrameRecorder(compose, File(root, "4-haunted-house")).apply {
            hold(800)
            tap(compose.onAllNodes(moveLabelled { it.startsWith("Move") })[0])
            holdUntil(15_000) { exists(hasText("A ghost caught", substring = true)) }
            hold(1_800)
            finish()
        }
        leaveGame()
        StoreState.applyStyles(container, StylePicks())

        // Styles: the Styles page, then its dice, picking a few.
        openFromMenu("Styles")
        FrameRecorder(compose, File(root, "5-styles")).apply {
            hold(1_200)
            tap(compose.onNode(DICE_FIELD))
            hold(1_200)
            for (family in DICE_PICKS) {
                tap(compose.onAllNodes(hasContentDescription("$family,", substring = true))[0])
                hold(800)
            }
            finish()
        }
        back()
        back()
        StoreState.applyStyles(container, StylePicks())

        // Sam's last pawn home: the win, then the results' celebration.
        resume(Scenes.lastMove())
        FrameRecorder(compose, File(root, "6-win")).apply {
            hold(700)
            tap(compose.onNode(WINNING_MOVE))
            holdUntil(20_000) { exists(hasText("Winner")) }
            hold(2_600)
            finish()
        }
        back()

        openFromMenu("Leaderboard")
        FrameRecorder(compose, File(root, "7-leaderboard")).apply {
            hold(2_400)
            finish()
        }
        back()

        // An achievement unlocking, banner and all.
        openFromMenu("Achievements")
        FrameRecorder(compose, File(root, "8-achievements")).apply {
            hold(500)
            AchievementEvents.emit(AchievementEvent.Unlocked(Scenes.UNLOCK))
            hold(3_000)
            finish()
        }
        container.diceRandom = Random.Default
    }

    /**
     * Eight screens (Play's most), the strongest first: names are listing order, and the script captions each
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
        // Earned already, so no banner covers a screen but the last's own: a landscape game is an achievement itself (Sideways).
        StoreState.unlockAllBut(container, Scenes.LOCKED - Achievement.SIDEWAYS)

        // A game in full swing, Sam to choose between moves, one of them a capture.
        resume(Scenes.midGame())
        shoot(GAME)
        leaveGame()

        // Two of the other modes, each at a table of its own.
        StoreState.applyStyles(container, Scenes.POND)
        resume(Scenes.portals())
        shoot(PORTALS)
        leaveGame()
        StoreState.applyStyles(container, Scenes.CRYPT)
        resume(Scenes.hauntedHouse())
        shoot(HAUNTED_HOUSE)
        leaveGame()
        StoreState.applyStyles(container, StylePicks())

        // Every die at once, opened from its field on the Styles page.
        openFromMenu("Styles")
        compose.onNode(DICE_FIELD).performClick()
        shoot(STYLES)
        back()
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

        // An achievement unlocking, its banner fully in.
        openFromMenu("Achievements")
        AchievementEvents.emit(AchievementEvent.Unlocked(Scenes.UNLOCK))
        stepFor(1_500)
        StoreAssets.save(compose.captureScreen(), File(dir, "%d-%s.png".format(++number, ACHIEVEMENTS)))
    }

    // ---- Driving the app -------------------------------------------------------------------------------------------

    /** The Styles page's Die field, which opens the dice. */
    private val DICE_FIELD = hasContentDescription("Die") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    /** The die, whatever it shows: on screen means the board is. */
    private val DIE = hasContentDescription("Die", substring = true)

    /** The board square whose move brings a pawn home: in [Scenes.lastMove], Sam's last, which wins. */
    private val WINNING_MOVE = SemanticsMatcher("offers the move home") { node ->
        node.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label?.endsWith("reaching home") == true
    }

    /** A board square offering a move whose label [wanted] says yes to ("Bring Red pawn 1 out of the yard", say). */
    private fun moveLabelled(wanted: (String) -> Boolean) = SemanticsMatcher("offers a move") { node ->
        node.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label?.let(wanted) == true
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

    /** Saves [game] and continues it from the menu. */
    private fun resume(game: StartedGame) {
        StoreState.saveGame(container, game)
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
