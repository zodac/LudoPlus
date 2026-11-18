package net.zodac.ludo

import androidx.compose.ui.test.hasContentDescription
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
    fun `a game can be started from the menu, left with system back once confirmed, and continued`() {
        compose.onNodeWithText("LudoPlus").assertExists()

        compose.onNodeWithText("Play").performClick()
        // The form (and its button) only appears once the last game's choices are read back from disk.
        compose.waitUntil(timeoutMillis = waitMillis) { compose.onAllNodesWithText("Start Game").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Start Game").performClick()
        compose.waitForIdle()

        // The game screen: the roll-off's winner is named under the board.
        compose.waitUntil(timeoutMillis = waitMillis) {
            compose.onAllNodesWithText("rolled highest, so goes first.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Back asks before leaving a game that's on (Settings' "Confirm leaving game", on by default); Leave goes to the menu.
        Espresso.pressBack()
        compose.waitUntil(timeoutMillis = waitMillis) { compose.onAllNodesWithText("Leave game?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Leave").performClick()

        // The game was saved, so Play is split: Continue brings it back as it was left.
        compose.waitUntil(timeoutMillis = waitMillis) { compose.onAllNodesWithText("Continue").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New Game").assertExists()
        compose.onNodeWithText("Continue").performClick()
        // The die, whatever a CPU going first has rolled since.
        compose.waitUntil(timeoutMillis = waitMillis) {
            compose.onAllNodes(hasContentDescription("Die", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
