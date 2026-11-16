package net.zodac.ludo.baselineprofile

import java.io.File
import java.util.regex.Pattern
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until

internal const val PACKAGE = "net.zodac.ludo"

// Generous: on a hosted runner's emulator a single lookup of the screen can take two to four seconds, so
// a short timeout allows only one or two looks. It only costs time when something is really wrong.
private const val TIMEOUT_MS = 15_000L

/**
 * An integer passed to the run as an instrumentation argument - from Gradle,
 * `-Pandroid.testInstrumentationRunnerArguments.<name>=<n>` - or null when it wasn't. Used to cut a
 * run down to one or two laps while the journey itself is being checked.
 */
internal fun intArgument(name: String): Int? =
    InstrumentationRegistry.getArguments().getString(name)?.toIntOrNull()?.takeIf { it > 0 }

/**
 * The app's screens as steps the profile generator and the benchmarks share. Selectors are the
 * visible labels and the screen-reader descriptions the app already has (no test tags), so a
 * relabelled button breaks the journey - which is the point of [await]: it fails the run loudly
 * instead of quietly recording a thinner profile.
 */

/** Waits for [selector] and returns it, or fails the run naming it. */
internal fun MacrobenchmarkScope.await(selector: BySelector, what: String): UiObject2 =
    device.wait(Until.findObject(selector), TIMEOUT_MS)
        ?: error("Baseline Profile journey: '$what' never appeared (on screen: ${screenText()})")

/**
 * Saves the screen and its UI hierarchy as a lap fails, while the app still shows where it stuck: the library
 * force-stops it straight after, so a screenshot taken once Gradle has finished shows only the launcher. Written to
 * the run's additional test output, which Gradle pulls to `build/outputs/connected_android_test_additional_output`.
 * The screen is taken with the shell's `screencap`: UiAutomator's own screenshot comes back black on the emulator.
 * Best effort: a capture that fails must not hide the journey's own failure.
 */
internal fun MacrobenchmarkScope.captureFailure(lap: Int) {
    val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: return
    try {
        dir.mkdirs()
        device.executeShellCommand("screencap -p ${File(dir, "journey-failure-lap$lap.png").absolutePath}")
        device.dumpWindowHierarchy(File(dir, "journey-failure-lap$lap.xml"))
    } catch (_: Exception) {
        // Nothing saved - the failure's message still names what was on screen.
    }
}

/** The text on screen, for a failure's message: what the journey was looking at instead. */
private fun MacrobenchmarkScope.screenText(): String =
    try {
        device.findObjects(By.text(Pattern.compile(".+", Pattern.DOTALL))).joinToString(" | ") { it.text }.take(SCREEN_TEXT_CHARS)
    } catch (_: StaleObjectException) {
        "the screen was changing"
    }

internal fun MacrobenchmarkScope.tapText(text: String) = tap(text) { await(By.text(text), text) }

/**
 * Taps what [find] returns, looking it up again if it goes stale first: a node found while its screen
 * or dialog is still opening (the mode picker's list, say) can be re-composed before the tap reaches it.
 */
private fun MacrobenchmarkScope.tap(what: String, find: () -> UiObject2) {
    repeat(TAP_ATTEMPTS) {
        try {
            find().click()
            device.waitForIdle()
            return
        } catch (_: StaleObjectException) {
            // Re-composed between the lookup and the tap - look again.
        }
    }
    error("Baseline Profile journey: '$what' kept going stale before it could be tapped")
}

/**
 * Taps what [find] returns until [done] says the tap took. A starved emulator drops taps - two in a row went with the
 * unlock banners animating at the foot of the screen - and the step after one then waits for what never comes. Tapped
 * again only after [TIMEOUT_MS] without [done]. The first tap is on [found] when the caller has just looked it up,
 * saving a lookup; later ones look again.
 *
 * Never tapped while [done] already holds, read once the screen is idle - before the first tap as before each retry.
 * [done] read straight after a tap can still see the screen from before it, so one that held already would pass before
 * the tap landed, and the tap could then undo it (a card or switch tapped again closes): a Statistics card the page had
 * opened was closed that way on a 7th lap. Before a retry, the wait for idle lets a slow tap land, so it isn't doubled.
 */
private fun MacrobenchmarkScope.tapUntil(what: String, find: () -> UiObject2, found: UiObject2? = null, done: () -> Boolean) {
    var next = found
    repeat(TAP_ATTEMPTS) {
        if (settledHolds(done)) return
        val target = next ?: find()
        next = null
        try {
            target.click()
        } catch (_: StaleObjectException) {
            // Re-composed between the lookup and the tap - look again.
            return@repeat
        }
        if (waitUntil(done)) {
            device.waitForIdle()
            return
        }
    }
    error("Baseline Profile journey: tapping '$what' never took (on screen: ${screenText()})")
}

/** Whether [done] holds once the screen has gone idle (the usual wait, not [withShortIdleWait]'s); gone stale, it doesn't yet. */
private fun MacrobenchmarkScope.settledHolds(done: () -> Boolean): Boolean {
    device.waitForIdle()
    return try {
        done()
    } catch (_: StaleObjectException) {
        false
    }
}

private fun MacrobenchmarkScope.tapTextUntil(text: String, done: () -> Boolean) = tapUntil(text, { await(By.text(text), text) }, done = done)

/**
 * True once [done] is, within [TIMEOUT_MS]; a node gone stale while it's read counts as not yet. Read without waiting
 * for the screen to go idle (see [withShortIdleWait]): it is read again until it holds, and the tap that follows waits
 * for idle as usual.
 */
private fun waitUntil(done: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + TIMEOUT_MS
    while (System.currentTimeMillis() < deadline) {
        try {
            if (withShortIdleWait(done)) return true
        } catch (_: StaleObjectException) {
            // Re-composed while it was read - read it again.
        }
        Thread.sleep(POLL_MS)
    }
    return false
}

/**
 * Sets the switch, segment or chip around [label] to [checked], tapping until it says so. Already [checked], it isn't
 * tapped: a second tap would undo the first.
 */
private fun MacrobenchmarkScope.setChecked(label: BySelector, what: String, checked: Boolean) {
    val control = await(By.checkable(true).hasDescendant(label), what)
    if (control.isChecked == checked) return
    // Within the node just found, so without another wait for idle; gone stale, the tap looks it up afresh.
    val found = try {
        control.findObject(label)
    } catch (_: StaleObjectException) {
        null
    }
    tapUntil(what, { await(label, what) }, found) {
        device.hasObject(By.checkable(true).checked(checked).hasDescendant(label))
    }
}

/** Whether the switch around [text] is on. */
private fun MacrobenchmarkScope.isChecked(text: String): Boolean = await(By.checkable(true).hasDescendant(By.text(text)), text).isChecked

/** True if [text] is on screen right now, without waiting. */
internal fun MacrobenchmarkScope.hasText(text: String): Boolean = device.hasObject(By.text(text))

/** The menu's "Ludo Plus" wordmark (AppLogo): only the menu shows it, so its going says the menu has. */
private val MENU = By.text("Ludo Plus")

/**
 * Taps the menu's [label] button until the menu has gone. A tap can be dropped - one that lands while
 * a dialog's dim layer is still fading out over the menu ("Untrusted touch due to occlusion" in logcat),
 * or that a slow emulator doesn't take - and the steps after it would then run on the menu: their back
 * press leaves the app. Nor can the screen be told by its title, as the menu has a button of that name.
 */
internal fun MacrobenchmarkScope.openFromMenu(label: String) {
    await(By.text(label), label)
    repeat(CLOSE_ATTEMPTS) { attempt ->
        // Tapped again only if the menu is still up once the screen is idle: a slow tap may have opened the page late.
        if (attempt > 0 && settledHolds { !device.hasObject(MENU) }) return
        try {
            device.findObject(By.text(label))?.click()
        } catch (_: StaleObjectException) {
            // Re-composed between the lookup and the tap - look again.
        }
        if (device.wait(Until.gone(MENU), TIMEOUT_MS)) {
            device.waitForIdle()
            return
        }
    }
    error("Baseline Profile journey: '$label' never opened from the menu")
}

/**
 * Taps a dialog's button, found by [selector], until the dialog is gone. A back press sent while it
 * is still closing reaches the dialog's window after it has dropped its back handler, and the system
 * takes it as leaving the app: the run lands on the launcher. A tap can also go unanswered: one that
 * lands while the dialog's text is still settling from a scroll only stops the scroll.
 */
private fun MacrobenchmarkScope.closeDialog(selector: BySelector, what: String) {
    repeat(CLOSE_ATTEMPTS) {
        try {
            device.findObject(selector)?.click()
        } catch (_: StaleObjectException) {
            // Re-composed between the lookup and the tap - look again.
        }
        if (device.wait(Until.gone(selector), CLOSE_WAIT_MS)) {
            device.waitForIdle()
            return
        }
    }
    error("Baseline Profile journey: '$what' never closed its dialog")
}

internal fun MacrobenchmarkScope.back() {
    device.pressBack()
    device.waitForIdle()
}

/**
 * Back from a page opened from the menu, pressed again if the menu hasn't come back: a starved emulator can drop a back
 * press, and the next step would then look for the menu on that page. Pressed again only after [TIMEOUT_MS] without
 * the menu, and only if the menu is still not up once the screen is idle, so a slow press isn't doubled into one that
 * leaves the app.
 */
internal fun MacrobenchmarkScope.backToMenu() {
    repeat(CLOSE_ATTEMPTS) { attempt ->
        if (attempt > 0 && settledHolds { device.hasObject(MENU) }) return
        back()
        // The menu's wordmark found is the menu there, so it's looked for without waiting for idle.
        if (withShortIdleWait { device.wait(Until.hasObject(MENU), TIMEOUT_MS) }) return
    }
    error("Baseline Profile journey: back never returned to the menu (on screen: ${screenText()})")
}

/** Swipes up the middle of the screen [times] times - a long page or a dialog's text. */
internal fun MacrobenchmarkScope.scrollDown(times: Int) {
    val x = device.displayWidth / 2
    repeat(times) {
        device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 20)
        device.waitForIdle()
    }
}

/**
 * Settings, flipping one switch (and flipping it back), then its Graphics page, opening the Gameplay card, then an
 * animation level (and back to High), and back to Settings to open the Licences and About dialogs.
 */
internal fun MacrobenchmarkScope.visitSettings() {
    openFromMenu("Settings")
    val sound = By.text("Sound effects")
    val soundOn = isChecked("Sound effects")
    setChecked(sound, "Sound effects", !soundOn)
    setChecked(sound, "Sound effects", soundOn)
    tapTextUntil("Graphics") { device.hasObject(By.text("Advanced graphics settings")) }
    tapTextUntil("Gameplay") { device.hasObject(By.text("Die roll")) }
    // Back to High: the journeys after this one are profiling the full animations.
    setChecked(By.text("Low"), "Low", true)
    setChecked(By.text("High"), "High", true)
    back()
    await(By.text("Sound effects"), "Settings after the Graphics page")

    // Each dialog is waited for by its OK button and left by it: the Settings page behind is
    // itself scrollable and has the same "Licences" text, so neither a scrollable nor a label says
    // the dialog is up, and a back press sent too early would leave Settings altogether.
    tapTextUntil("Licences") { device.hasObject(By.text("OK")) }
    scrollDown(3)
    closeDialog(By.text("OK"), "the Licences dialog's OK button")

    tapTextUntil("About") { device.hasObject(By.text("OK")) }
    scrollDown(1)
    closeDialog(By.text("OK"), "the About dialog's OK button")

    backToMenu()
}

/**
 * Wipes the app's data, as on a fresh install: the only way a release build can lock an achievement again, and a run
 * retried on the same emulator (android-release.yml) finds the last run's data otherwise. Stops the app, if it's running.
 */
internal fun MacrobenchmarkScope.wipeAppData() {
    val result = device.executeShellCommand("pm clear $PACKAGE").trim()
    check(result == "Success") { "Baseline Profile journey: clearing the app's data said '$result'" }
}

/**
 * Runs [block] with UiAutomator's wait for the screen to go idle cut to [SHORT_IDLE_WAIT_MS]. Every node
 * lookup waits for that first, and the menu and the banner animate, so on a hosted runner's emulator each
 * lookup sat out two to four seconds of it - most of the four seconds the banner stays up, so the banner
 * was found only as it went. The rest of the journey keeps the usual wait.
 */
private inline fun <T> withShortIdleWait(block: () -> T): T {
    val configurator = Configurator.getInstance()
    val saved = configurator.waitForIdleTimeout
    configurator.waitForIdleTimeout = SHORT_IDLE_WAIT_MS
    try {
        return block()
    } finally {
        configurator.waitForIdleTimeout = saved
    }
}

private const val POLL_MS = 250L

private const val SCREEN_TEXT_CHARS = 400

private const val CLOSE_ATTEMPTS = 4

private const val TAP_ATTEMPTS = 4

private const val CLOSE_WAIT_MS = 3_000L

private const val SHORT_IDLE_WAIT_MS = 100L

/** The placeholder game screen's note (Phase 0 of .claude/IMPLEMENTATION.md): the game screen has opened. */
private val GAME_SCREEN = By.text("The board is on its way. For now, this is who's playing.")

/** From the menu: Play -> Start Game, onto the game screen, then back to the menu. */
internal fun MacrobenchmarkScope.playAGame() {
    openFromMenu("Play")
    // The form (and its button) only appears once the last game's choices are read back from disk.
    await(By.text("Start Game"), "the setup form's Start Game")
    tapTextUntil("Start Game") { device.hasObject(GAME_SCREEN) }
    backToMenu()
}

/** The Rules page, scrolled to its foot, then back to the menu. */
internal fun MacrobenchmarkScope.visitRules() {
    openFromMenu("Rules")
    await(By.text("How to Play Ludo Plus"), "the Rules page's heading")
    scrollDown(4)
    backToMenu()
}
