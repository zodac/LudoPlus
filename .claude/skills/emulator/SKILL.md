---
name: emulator
description: Boot the sandbox's Android emulator to look at the real app's screen or run/debug the Baseline Profile journey (app/baselineprofile). Not for timings or unit tests. Troubleshooting and CI details are in .claude/EMULATOR.md.
---

# The sandbox emulator

Run everything inside the sandbox. The host's phones and emulators can't be reached from it.

```sh
emulator.sh start               # idempotent; ~20s; waits until ready, applies the Vulkan renderer fix
emulator.sh status              # exit 0 + a line if running
emulator.sh screenshot out.png  # then Read the PNG
emulator.sh stop                # ALWAYS stop when done: it holds ~4GB RAM and 4 cores
```

**Use it for:**

- **The real app's screen.** Install a build, launch it, take a screenshot. Drawing is in software, so it shows layout
    and behaviour, not smoothness or art quality; for art, use the `render` skill.

**Not for:**

- Benchmark timings (they measure this machine).
- Unit tests (those run under Robolectric).
- TalkBack, haptics, the accelerometer or real frame times. Those need the maintainer's phone.

## Look at the app

```sh
emulator.sh start
./gradlew -q assembleGithubDebug -PdevStamp && adb install -r "$(ls -t app/android/build/outputs/apk/github/debug/*.apk | head -1)"
adb shell monkey -p net.zodac.ludo.github -c android.intent.category.LAUNCHER 1
emulator.sh screenshot <scratchpad>/screen.png
```

## Run the Baseline Profile journey

```sh
emulator.sh start
adb shell settings put system font_scale 1.0   # the emulator's settings outlast a session; CI's is 1.0
adb uninstall net.zodac.ludo 2>/dev/null   # a debug build's higher versionCode makes the benchmark install a no-op
./gradlew :app:android:generatePlayReleaseBaselineProfile -Pandroid.testInstrumentationRunnerArguments.journeyLaps=1
emulator.sh stop
```

- The journey (`app/baselineprofile/.../Journeys.kt`): a game's first turns (the player's rolls and moves, waiting out
    the CPUs'), Continue with a move in landscape, every Rules tab, the whole tutorial to its Game Over, Achievements
    scrolled through, the Leaderboard and Statistics, and Settings with its Graphics page and dialogs. First run here
    2026-11-17: a lap takes about two and a half minutes. Under the two-core stress below the app draws under two frames a second, and the tutorial alone
    outlasts even its six-minute budget, so a stressed lap fails there on time, not on a race.
- Just after a boot on starved cores, the uninstall can fail with `DELETE_FAILED_INTERNAL_ERROR`, which the `2>/dev/null`
    above doesn't hide: try it again, or the benchmark install may not replace what's there.
- Start with one lap. One lap doesn't prove a full run, because timing races can take several laps to show.
- The first lap clears the app's data (`wipeAppData`), so it starts like a fresh install on any run, CI's retry
    included.
- A journey step names labels the app shows, so removing or relabelling one means changing `Journeys.kt` with it.
- A failing step names itself (`Baseline Profile journey: 'X' never appeared`). The report is at
    `app/baselineprofile/build/reports/androidTests/connected/nonMinifiedRelease/index.html`. The screen and UI hierarchy
    as it failed are saved as `journey-failure-lap<N>.png`/`.xml` under
    `app/baselineprofile/build/outputs/connected_android_test_additional_output/`. Fix `Journeys.kt`, and rerun.
- The benchmark tests show as skipped during `generatePlayReleaseBaselineProfile`. That's expected. Run them with
    `./gradlew :app:baselineprofile:connectedBenchmarkReleaseAndroidTest` (startup, and `PawnMoveBenchmark`'s frame
    times through the tutorial), on a phone for numbers that mean anything.
- The generated profile (`app/android/src/playRelease/generated/baselineProfiles/`, the play app's; CI copies it to the
    github app's) is gitignored, because CI generates its own.

**Write journey steps for a slow device.** A hosted runner takes 2-4s per UiAutomator lookup.

- Wait for things rather than checking once: `hasText` only after an `await`.
- A tap whose effect later steps rely on goes through `tapUntil` (or `setChecked`, `selectTab`), which taps again until
    that effect shows: a starved emulator drops taps.
- Open screens with `openFromMenu`, which retries until the "Ludo Plus" wordmark is gone.
- A check read straight after a tap can still see the screen from before it. So a tap's check must not hold before the
    tap: `tapUntil` won't tap while it holds (read once idle).
- To shake out races, pin the emulator and two busy loops to two cores (`taskset -c 0-1`) and run two laps.

If the emulator dies (`adb: device offline`, exit 139), see `.claude/EMULATOR.md`, "If the emulator dies". **Never
remove the `debug.hwui.renderer=skiavk` step**: without it the emulator segfaults when Ludo Plus draws.
