package net.zodac.ludo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records only what a cold start runs, up to the menu being on screen, for the startup profile
 * (`startup-prof.txt`): R8 lays the dex out so this code is loaded first. Kept apart from
 * [BaselineProfileGenerator], whose journey covers every screen - marked as startup too, it made the
 * startup profile the whole app, which leaves R8 nothing to put first. Stops at the menu.
 * Generated with the rest by `./gradlew :app:android:generateBaselineProfile`. Skipped once the journey has failed.
 */
@RunWith(AndroidJUnit4::class)
class StartupProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        assumeFalse("The Baseline Profile journey failed, so the run fails without a startup profile", journeyFailed)
        rule.collect(
            packageName = PACKAGE,
            maxIterations = intArgument("journeyLaps") ?: 15,
            stableIterations = intArgument("journeyLaps") ?: 3,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            await(By.text("LudoPlus"), "the menu's LudoPlus wordmark")
        }
    }
}
