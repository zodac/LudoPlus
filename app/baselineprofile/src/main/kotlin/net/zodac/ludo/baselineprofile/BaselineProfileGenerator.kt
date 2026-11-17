package net.zodac.ludo.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records what a typical session touches, so the generated profile compiles those paths ahead of time: cold start, a
 * game's first turns (the player's roll and move, then the CPUs'), the game resumed with Continue and rolled in
 * landscape, the whole guided tutorial to its Game Over (rolls again, a capture, a pawn home, a win), the Rules' tabs,
 * the Leaderboard and Statistics, and Settings with its Graphics page and dialogs. Run on a device:
 * `./gradlew :app:android:generateBaselineProfile`.
 *
 * A real game isn't played to its end: four players take hundreds of turns. The tutorial's Game Over covers that page.
 * Deliberately left out: opening a link (it leaves the app, so no app code runs) and selecting text (Compose's and the
 * platform's code, which their libraries' own profiles already cover).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val laps = intArgument("journeyLaps") ?: DEFAULT_LAPS
    private var lap = 0

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE,
        // Laps: the library's own defaults (up to 15, stopping after 3 that add nothing new) unless
        // `-Pandroid.testInstrumentationRunnerArguments.journeyLaps=N` asks for exactly N - one or two
        // while the journey is being checked, so a failing step stops the run quickly.
        maxIterations = laps,
        stableIterations = intArgument("journeyLaps") ?: 3,
        // The journey goes far past startup; the startup profile is StartupProfileGenerator's alone.
        includeInStartupProfile = false,
    ) {
        val thisLap = ++lap
        try {
            journey(first = thisLap == 1)
        } catch (failure: Throwable) {
            journeyFailed = true
            captureFailure(thisLap)
            throw failure
        }
    }

    private fun MacrobenchmarkScope.journey(first: Boolean) {
        // Only the first lap starts from nothing, whatever a retried run left on the emulator.
        if (first) wipeAppData()
        pressHome()
        startActivityAndWait()

        // A game is left saved behind, so the menu then offers Continue - and the next lap starts from that state.
        playATurn()
        resumeGame()
        visitRules()
        visitRecords()
        visitSettings()
    }
}

private const val DEFAULT_LAPS = 15

/**
 * Set as a lap of [BaselineProfileGenerator] fails. The test classes share one instrumentation process and run in
 * name order, so [StartupProfileGenerator] sees it and skips itself: a failed journey fails the run anyway.
 */
@Volatile
internal var journeyFailed = false
