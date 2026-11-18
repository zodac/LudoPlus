package net.zodac.ludo.baselineprofile

import java.io.File
import java.util.regex.Pattern
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
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

/*
 * The app's screens as steps the profile generator and the benchmarks share. Selectors are the visible labels and the
 * screen-reader descriptions the app already has (no test tags), so a relabelled button breaks the journey - which is
 * the point of [await]: it fails the run loudly instead of quietly recording a thinner profile. The generic helpers
 * (waiting, tapping until a tap takes, the failure capture) came from DiceFive's journey (commit 5423b6c).
 */

/** Waits for [selector] and returns it, or fails the run naming it. */
internal fun MacrobenchmarkScope.await(selector: BySelector, what: String): UiObject2 =
    device.wait(Until.findObject(selector), TIMEOUT_MS)
        ?: error("Baseline Profile journey: '$what' never appeared (on screen: ${screenText()})")

/**
 * Saves the screen and its UI hierarchy as a lap fails, while the app still shows where it stuck: the library
 * force-stops it straight after. Written to the run's additional test output, which Gradle pulls to
 * `build/outputs/connected_android_test_additional_output`. The screen is taken with the shell's `screencap`:
 * UiAutomator's own screenshot comes back black on the emulator. Best effort: a capture that fails must not hide the
 * journey's own failure.
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

/**
 * Taps what [find] returns until [done] says the tap took. A starved emulator drops taps, and the step after one then
 * waits for what never comes. Tapped again only after [TIMEOUT_MS] without [done], and never while [done] already
 * holds once the screen is idle, so a slow tap isn't doubled (a switch tapped twice is back where it was).
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
        if (waitUntil(done = done)) {
            device.waitForIdle()
            return
        }
    }
    error("Baseline Profile journey: tapping '$what' never took (on screen: ${screenText()})")
}

/** Whether [done] holds once the screen has gone idle; gone stale, it doesn't yet. */
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
 * True once [done] is, within [timeoutMs]; a node gone stale while it's read counts as not yet. Read without waiting
 * for the screen to go idle (see [withShortIdleWait]): the board animates, and it is read again until it holds.
 */
private fun waitUntil(timeoutMs: Long = TIMEOUT_MS, done: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
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
 * Sets the switch or segment around [label] to [checked], tapping until it says so. Already [checked], it isn't
 * tapped: a second tap would undo the first.
 */
private fun MacrobenchmarkScope.setChecked(label: BySelector, what: String, checked: Boolean) {
    val control = await(By.checkable(true).hasDescendant(label), what)
    if (control.isChecked == checked) return
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

/**
 * Selects the Rules tab [text], the topmost match (the page's title can read the same), tapping until it is selected.
 * A tab scrolled out of the tab row is scrolled back into it first.
 */
internal fun MacrobenchmarkScope.selectTab(text: String) {
    val selected = By.selected(true).hasDescendant(By.text(text))
    scrollTabIntoView(text)
    await(By.text(text), text)
    if (device.hasObject(selected)) return
    tapUntil(text, { topmost(text) }) { device.hasObject(selected) }
}

/**
 * Scrolls the Rules' tab row until the tab [text] shows: towards the start for a tab before the ones on screen, towards
 * the end otherwise. The row scrolls sideways, and a tab out of it isn't on screen to be found.
 */
private fun MacrobenchmarkScope.scrollTabIntoView(text: String) {
    val target = RULES_TABS.indexOf(text)
    repeat(TAB_SCROLL_ATTEMPTS) {
        if (device.wait(Until.hasObject(By.text(text)), CLOSE_WAIT_MS)) return
        val shown = RULES_TABS.indices.filter { device.hasObject(By.text(RULES_TABS[it])) }
        // The topmost: the page below the row can show a tab's name as its heading.
        val row = device.findObjects(By.scrollable(true).hasDescendant(By.text(Pattern.compile(RULES_TABS.joinToString("|")))))
            .minByOrNull { it.visibleBounds.top } ?: return
        val bounds = try {
            row.visibleBounds
        } catch (_: StaleObjectException) {
            return@repeat // The row was redrawn: looked for again on the next attempt.
        }
        // A plain swipe across the row, from a quarter in to a quarter from the other side: the row didn't take
        // UiObject2.scroll's gesture on the emulator.
        val towardsStart = shown.isNotEmpty() && target < shown.min()
        val (from, to) = bounds.left + bounds.width() / 4 to bounds.right - bounds.width() / 4
        val y = bounds.centerY()
        if (towardsStart) device.swipe(from, y, to, y, TAB_SWIPE_STEPS) else device.swipe(to, y, from, y, TAB_SWIPE_STEPS)
    }
}

/** True if [text] is on screen right now, without waiting. */
private fun MacrobenchmarkScope.hasText(text: String): Boolean = device.hasObject(By.text(text))

/** The menu's "LudoPlus" wordmark (AppLogo): only the menu shows it, so its going says the menu has. */
private val MENU = By.text("LudoPlus")

/** The die, whatever it shows: on screen means the board is. */
private val DIE = By.desc(Pattern.compile("Die( showing \\d)?"))

/** The die while it can be rolled: a button only then (GameDie). */
private val ROLLABLE_DIE = By.clickable(true).desc(Pattern.compile("Die( showing \\d)?"))

/**
 * A square with a pawn that can be moved: the board's squares are buttons only then (BoardSemantics) - a yard, a track
 * square, or a home column square, in any colour.
 */
private val MOVABLE_SQUARE = By.clickable(true).desc(Pattern.compile("(.* yard|Square \\d+|.* home column).*", Pattern.DOTALL))

private val GAME_OVER = By.text("Game Over")

/**
 * Taps the menu's [label] button until the menu has gone. A tap can be dropped - one that lands while a dialog's dim
 * layer is still fading out over the menu, or that a slow emulator doesn't take - and the steps after it would then run
 * on the menu: their back press leaves the app.
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
 * Taps a dialog's button, found by [selector], until the dialog is gone. A back press sent while it is still closing
 * reaches the dialog's window after it has dropped its back handler, and the system takes it as leaving the app.
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

private fun MacrobenchmarkScope.back() {
    device.pressBack()
    device.waitForIdle()
}

/**
 * Back from a page opened from the menu, pressed again if the menu hasn't come back: a starved emulator can drop a back
 * press. Pressed again only if the menu is still not up once the screen is idle, so a slow press isn't doubled into one
 * that leaves the app.
 */
private fun MacrobenchmarkScope.backToMenu() {
    repeat(CLOSE_ATTEMPTS) { attempt ->
        if (attempt > 0 && settledHolds { device.hasObject(MENU) }) return
        back()
        if (withShortIdleWait { device.wait(Until.hasObject(MENU), TIMEOUT_MS) }) return
    }
    error("Baseline Profile journey: back never returned to the menu (on screen: ${screenText()})")
}

/** Swipes up the middle of the screen [times] times - a long page or a dialog's text. */
private fun MacrobenchmarkScope.scrollDown(times: Int) {
    val x = device.displayWidth / 2
    repeat(times) {
        device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 20)
        device.waitForIdle()
    }
}

/** The topmost match for [text]: a Rules tab is above the page title of the same name. */
private fun MacrobenchmarkScope.topmost(text: String): UiObject2 {
    await(By.text(text), text)
    return device.findObjects(By.text(text)).minByOrNull { it.visibleBounds.top }
        ?: error("Baseline Profile journey: '$text' never appeared")
}

/**
 * The player's next action on the board, if there is one now: the die while it can be rolled, else the one square with
 * a pawn to move (the first, in a real game). False while it's a CPU's turn, or the screen is still playing a move out.
 */
private fun MacrobenchmarkScope.takePlayersAction(): Boolean {
    val target = withShortIdleWait { device.findObject(ROLLABLE_DIE) ?: device.findObject(MOVABLE_SQUARE) } ?: return false
    return try {
        target.click()
        true
    } catch (_: StaleObjectException) {
        false
    }
}

/**
 * From the menu: Play (or New Game, when a game is saved) -> Start Game, then [PLAYER_ACTIONS] of the player's own
 * rolls and moves, each waiting out the CPUs' turns between (their thinking, the die tumbling, pawns moving), then back
 * to the menu by way of the leave dialog, cancelled once first.
 */
internal fun MacrobenchmarkScope.playATurn() {
    await(By.text(Pattern.compile("Play|New Game")), "the menu's Play or New Game")
    openFromMenu(if (hasText("New Game")) "New Game" else "Play")
    tapTextUntil("Start Game") { device.hasObject(DIE) }
    repeat(PLAYER_ACTIONS) {
        if (!waitUntil(CPU_TURNS_MS) { takePlayersAction() }) error("Baseline Profile journey: the player's turn never came (on screen: ${screenText()})")
        Thread.sleep(ACTION_SETTLE_MS)
    }
    cancelLeaving()
    leaveGame()
}

/**
 * From the menu, with a game saved: Continue, the board turned to landscape and the player's next roll or move made
 * there, then back to portrait and out. The tap is tried again if the board doesn't come: the system drops a touch that
 * lands while the last dialog's dim layer is still fading out over the menu.
 */
internal fun MacrobenchmarkScope.resumeGame() {
    await(By.text("Continue"), "Continue")
    repeat(CLOSE_ATTEMPTS) {
        device.findObject(By.text("Continue"))?.click()
        if (device.wait(Until.hasObject(DIE), TIMEOUT_MS)) {
            device.waitForIdle()
            device.setOrientationLandscape()
            await(DIE, "the board in landscape")
            waitUntil(CPU_TURNS_MS) { takePlayersAction() }
            Thread.sleep(ACTION_SETTLE_MS)
            device.setOrientationNatural()
            await(DIE, "the board back in portrait")
            leaveGame()
            return
        }
    }
    error("Baseline Profile journey: 'the board after Continue' never appeared")
}

/**
 * Back out of a game: the back press asks first (unless the setting is off), and "Leave" confirms. It waits for
 * whichever comes - the dialog or the menu - rather than giving the dialog a fixed time.
 */
private fun MacrobenchmarkScope.leaveGame() {
    back()
    await(By.text(Pattern.compile("Leave game\\?|LudoPlus")), "the leave dialog or the menu after leaving a game")
    if (hasText("Leave game?")) closeDialog(By.text("Leave"), "the leave dialog's Leave button")
    await(MENU, "the menu after leaving a game")
}

/** Back press in a game, then "Cancel" on the leave dialog (if the setting asks): the game carries on. */
private fun MacrobenchmarkScope.cancelLeaving() {
    back()
    await(By.text(Pattern.compile("Leave game\\?|LudoPlus")), "the leave dialog or the menu after a back press")
    if (hasText("Leave game?")) {
        closeDialog(By.text("Cancel"), "the leave dialog's Cancel button")
        await(DIE, "the board after cancelling the leave dialog")
    }
}

/** The Rules: every tab, each page scrolled to its diagram, then the tutorial from How to Play, and back to the menu. */
internal fun MacrobenchmarkScope.visitRules() {
    openFromMenu("Rules")
    for (tab in RULES_TABS.drop(1)) {
        selectTab(tab)
        scrollDown(1)
    }
    // The Rules open on the page last shown while the app has been running, so How to Play is asked for.
    selectTab(RULES_TABS.first())
    playTutorial()
    backToMenu()
}

/**
 * From the Rules' How to Play page: the whole tutorial, each of its steps taken as the coach card asks (the die, or the
 * one pawn offered), to its Game Over, then Done back to the page.
 */
internal fun MacrobenchmarkScope.playTutorial() {
    val start = By.text("Play the Tutorial")
    // Under the page's text, which a short screen can push below the fold.
    if (!device.wait(Until.hasObject(start), CLOSE_WAIT_MS)) scrollDown(1)
    tapTextUntil("Play the Tutorial") { device.hasObject(DIE) }
    val deadline = System.currentTimeMillis() + TUTORIAL_MS
    while (!withShortIdleWait { device.hasObject(GAME_OVER) }) {
        if (System.currentTimeMillis() > deadline) error("Baseline Profile journey: the tutorial never ended (on screen: ${screenText()})")
        if (takePlayersAction()) Thread.sleep(ACTION_SETTLE_MS) else Thread.sleep(POLL_MS)
    }
    // The celebration plays a moment before Done.
    Thread.sleep(CELEBRATION_MS)
    tapTextUntil("Done") { device.hasObject(start) }
}

/**
 * Achievements from the menu: the list scrolled through its groups, a group header's Next category button tapped once
 * if it shows, and back. Waits on the earned count, which only this screen shows (the menu's button shares its title).
 */
internal fun MacrobenchmarkScope.visitAchievements() {
    openFromMenu("Achievements")
    await(By.textContains(" earned"), "the Achievements' earned count")
    scrollDown(ACHIEVEMENT_SCROLLS)
    // Nothing later relies on it, so one tap and no retry.
    device.findObject(By.desc("Next category"))?.click()
    device.waitForIdle()
    backToMenu()
}

/** The Leaderboard, then Statistics, each from the menu and back. */
internal fun MacrobenchmarkScope.visitRecords() {
    openFromMenu("Leaderboard")
    await(By.text("Leaderboard"), "the Leaderboard's title")
    backToMenu()
    openFromMenu("Statistics")
    await(By.text("Statistics"), "the Statistics title")
    backToMenu()
}

/**
 * Settings: one switch flipped and flipped back, its Graphics page with an animation level picked (and High again, so
 * the next lap profiles the full animations), then the Licences and About dialogs.
 */
internal fun MacrobenchmarkScope.visitSettings() {
    openFromMenu("Settings")
    val sound = By.text("Sound effects")
    val soundOn = isChecked("Sound effects")
    setChecked(sound, "Sound effects", !soundOn)
    setChecked(sound, "Sound effects", soundOn)
    tapTextUntil("Graphics") { device.hasObject(By.text("Advanced graphics settings")) }
    setChecked(By.text("Low"), "Low", true)
    setChecked(By.text("High"), "High", true)
    back()
    await(By.text("Sound effects"), "Settings after the Graphics page")

    // Each dialog is waited for by its OK button and left by it: the Settings page behind has the same "Licences" text,
    // so a back press sent too early would leave Settings altogether.
    tapTextUntil("Licences") { device.hasObject(By.text("OK")) }
    scrollDown(3)
    closeDialog(By.text("OK"), "the Licences dialog's OK button")
    tapTextUntil("About") { device.hasObject(By.text("OK")) }
    scrollDown(1)
    closeDialog(By.text("OK"), "the About dialog's OK button")
    backToMenu()
}

/**
 * Wipes the app's data, as on a fresh install: a run retried on the same emulator (android-release.yml) finds the last
 * run's saved game and records otherwise. Stops the app, if it's running.
 */
internal fun MacrobenchmarkScope.wipeAppData() {
    val result = device.executeShellCommand("pm clear $PACKAGE").trim()
    check(result == "Success") { "Baseline Profile journey: clearing the app's data said '$result'" }
}

/**
 * Runs [block] with UiAutomator's wait for the screen to go idle cut to [SHORT_IDLE_WAIT_MS]. Every node lookup waits
 * for that first, and the board animates, so on a hosted runner's emulator each lookup sat out seconds of it.
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

/** The Rules' tabs, in order (RulesContent). */
private val RULES_TABS = listOf("How to Play", "The Board", "Your Turn", "Captures", "Winning")

private const val POLL_MS = 250L
private const val SCREEN_TEXT_CHARS = 400
/** The player's own rolls and moves in [playATurn]. */
private const val PLAYER_ACTIONS = 4
/** Three CPUs' turns between the player's: each thinks 500ms before a roll and a move, on top of the board's animations. */
private const val CPU_TURNS_MS = 60_000L
private const val ACTION_SETTLE_MS = 1_500L
/**
 * The whole tutorial: four parts, about twenty steps, each played out on the board. Six minutes: an emulator starved to
 * under two frames a second (two cores, two busy loops) was still in its second part after three.
 */
private const val TUTORIAL_MS = 360_000L
private const val CELEBRATION_MS = 1_000L
private const val CLOSE_ATTEMPTS = 4
private const val TAP_ATTEMPTS = 4
private const val TAB_SCROLL_ATTEMPTS = 4
private const val ACHIEVEMENT_SCROLLS = 3
// About a fifth of a second a swipe: slow enough to scroll the row rather than fling it.
private const val TAB_SWIPE_STEPS = 40
private const val CLOSE_WAIT_MS = 3_000L
private const val SHORT_IDLE_WAIT_MS = 100L
