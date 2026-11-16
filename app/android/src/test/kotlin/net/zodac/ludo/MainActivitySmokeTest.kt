package net.zodac.ludo

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real app, launched through [MainActivity] under Robolectric - the sandbox has no device or
 * emulator. Everything the shared module leaves to the platform meets here: the composition locals
 * MainActivity provides, Compose Multiplatform resources (the Sora font, the vector icons) packaged
 * from :app:shared, navigation, the storage behind AndroidAppContainer, and system back reaching the
 * shared BackHandler through the Activity.
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        init {
            // The rule's own recomposer and test clock, not the Activity's capped one - see MainActivity.useCappedFrameClock.
            MainActivity.useCappedFrameClock = false
        }
    }

    // Real time, not the test clock: generous, since the waits below return as soon as their text is there, and a cold JVM
    // on a CI runner is slow.
    private val waitMillis = 30_000L

    @Test
    fun `a game can be started from the menu and system back leaves it for the menu`() {
        compose.onNodeWithText("Ludo Plus").assertExists()

        compose.onNodeWithText("Play").performClick()
        // The form (and its button) only appears once the last game's choices are read back from disk.
        compose.waitUntil(timeoutMillis = waitMillis) { compose.onAllNodesWithText("Start Game").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Start Game").performClick()
        compose.waitForIdle()

        // The placeholder game screen (Phase 0 of .claude/IMPLEMENTATION.md): the players, and a note that the board is coming.
        compose.waitUntil(timeoutMillis = waitMillis) {
            compose.onAllNodesWithText("The board is on its way. For now, this is who's playing.").fetchSemanticsNodes().isNotEmpty()
        }

        // No game is saved or confirmed before leaving yet (Phases 2 and 4), so back goes straight to the menu.
        Espresso.pressBack()
        compose.waitUntil(timeoutMillis = waitMillis) { compose.onAllNodesWithText("Play").fetchSemanticsNodes().isNotEmpty() }
    }
}
