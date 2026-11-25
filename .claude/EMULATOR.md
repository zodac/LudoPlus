# Android emulator in the sandbox: internals

Day-to-day use (start, screenshot, running the Baseline Profile journey) is the `emulator` skill. This file covers why
`emulator.sh` does what it does, CI's twin, troubleshooting, and versions.

## What `start` does, and why

| Step                                                                                                                                                                                          | Why                                                                                                                                                                                                                                                                                       |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Checks `/dev/kvm` is read-write                                                                                                                                                               | sandbox.conf passes it in (`SANDBOX_DEVICES`), and ai-sandbox's entrypoint opens this container's node to `dev` (the host's is untouched). Without KVM, emulation is far too slow.                                                                                                        |
| Installs the emulator, system image or AVD **only if missing**                                                                                                                                | They're baked into `ai-sandbox-configuration/Dockerfile`. This is the fallback for older images or another `EMULATOR_API`: `sudo android sdk install`, then a permission fix, so it needs `SANDBOX_SUDO=true` (`dev` has no sudo by default). About 1.5 GB download (5 GB unpacked).      |
| Writes `target=android-<api>` into the AVD's `.ini` and `config.ini`                                                                                                                          | The emulator turns on Vulkan and GLDirectMem only from API 29, reading the level from `target=`. Old `avdmanager`s write `target=android-0` (read as API 3). Apps then abort (`Assertion failed: !gpuCount`, or a crash in `AllocateVulkanMemory`). `-feature Vulkan` alone isn't enough. |
| Boots `-no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -verbose` (+ `-timezone "$TZ"`)                                                                              | Headless and a clean boot each time. It prints the "Deciding if GLDirectMem/Vulkan should be enabled" line. The guest uses the host's timezone.                                                                                                                                           |
| `adb root`, then `setprop debug.hwui.renderer skiavk`                                                                                                                                         | **Never remove this.** See below.                                                                                                                                                                                                                                                         |
| Checks `/data`'s size and that a Vulkan GPU exists, then waits for `package`/`activity` and an install session (`pm install-create`) to hold for 30s on one `system_server` (up to 5 minutes) | Prints each reason it isn't ready, plus any `system_server` restart and its crash log. A failed boot saves `logcat-boot-*.txt`.                                                                                                                                                           |

- **Environment overrides**: `EMULATOR_API`, `EMULATOR_AVD_NAME` (default `sandbox-api<API>`), `EMULATOR_MEMORY_MB`,
    `EMULATOR_CORES`.
- **Image.** It's a **Google APIs** image, not Play Store, because that's what allows `adb root`. So Play Games
    Services can't be tested here.

## The renderer setting (do not remove it)

**The crash.** The emulator's software GLES path (gfxstream + SwiftShader) **segfaults in its render thread while
drawing LudoPlus**.

- The emulator exits with 139 a few seconds after the app opens, leaving no report.
- Everything above it shows a vague symptom: `adb: device offline`, a vanished device, or a
    `connectedNonMinifiedReleaseAndroidTest` that fails ~15s in with no steps run.
- Other apps draw fine.

**The fix.** The Vulkan path works, so `start` sets `debug.hwui.renderer=skiavk` after every boot.

- It resets on each boot; set it again if you boot another way.
- A boot-time `-prop` flag does **not** take effect.
- It needs guest Vulkan (the AVD `target`, above).
- Tried and still crashing: `-gpu guest`, `swiftshader_indirect` with `-feature -Vulkan`, `skiagl`, and sensors or
    audio disabled.

## If the emulator dies

1. **Get the exit status.** Use `emulator ...; echo "EXIT $?" >> log`: 139 is a segfault, 137 a kill (memory).
   `start` logs to `~/.android/emulator-sandbox-api37.0.log`.
2. **Get a backtrace.** There's no crash dump.
   - Install gdb: add it to the Dockerfile's Debian packages, or set `SANDBOX_SUDO=true` in sandbox.conf and
     `sudo apt-get update && sudo apt-get install -y gdb`.
   - Run the emulator under it, following the *parent* process:
     `gdb -batch -ex "set follow-fork-mode parent" -ex "handle all nostop noprint pass" -ex "handle SIGSEGV stop print"
     -ex run -ex "bt 25" -ex "thread apply all bt 6" --args /opt/android-sdk/emulator/emulator -avd sandbox-api37.0
     -no-window -no-audio -no-snapshot -gpu swiftshader_indirect`
   - Boot, then launch the app over adb.
   - A crash in `libgfxstream_backend.so` or the GLES translator is the known one.
3. **Check liveness with `adb shell true` or `emulator.sh status`**, never a process-listing grep (it can match
   your own command line).
4. **Fixes.** `adb kill-server` and a re-`start` fixes a wedged adb. `-wipe-data` fixes an AVD left broken by an abrupt
   exit ("Skipping PackageSetting ... missing metadata").

## The Baseline Profile journey in CI

`android-release.yml`'s `baseline-profile` job runs the journey on a GitHub-hosted runner for every release APK: 3
laps, or 10 if the journey or a profile file changed beyond its comments and blank lines. The `apk` job builds with the
profile it makes.

**`.github/scripts/ci_emulator.sh` is `emulator.sh`'s twin.** It uses the same API (read from the Dockerfile's
`ARG EMULATOR_API`), the same image, the same boot and the same renderer fix. The differences:

- `/dev/kvm` is opened with a udev rule;
- packages install without `sudo`;
- the emulator and image are cached per API level (saved even on failure);
- there's no timezone (CI is UTC);
- **`/data` is pinned at 6 GB with `-partition-size`.**

**Why `/data` is pinned.** The runner's `avdmanager` gives 800 MB, and a first boot fills it in ~30s.

- Every write then fails (`ENOSPC`, `SQLiteFullException`) and `system_server` crash-loops.
- It looks like flakiness: `cmd: Can't find service: package`, `not allowed to perform GET_USAGE_STATS`,
    `DeadSystemException`, or Gradle passing with no profile.
- The image is sparse, so only ~2 GB is stored.

**Robustness.**

- The APKs build before the boot.
- `.github/scripts/watch_checks.sh` stops the job as soon as another release check fails.
- A failed boot or install is retried, and so is a failing journey once in a 3-lap run (three attempts in all). A 10-lap
    soak fails on the first failing journey. The failed run's screen and results go in the report under
    `journey-attempt<N>/`.
- Journey taps confirm they took (`tapUntil` and its kin in `Journeys.kt`), tapping again only after `TIMEOUT_MS` without
    their effect: a starved runner drops taps.
- Gradle passing with no `baseline-prof.txt` counts as a failed install.
- Limits: 190 min for the step, 210 for the job. 1.0.1's ten laps (a game's first turns, the whole tutorial, and every
    page) took 52 minutes. The tutorial has grown since: its Next steps took it past six minutes on a runner (2026-11-24).
- **Budget each step, never a whole run of them** (DiceFive's `TURN_WAIT_MS`): a slow runner is slow, not stuck. The
    tutorial's one six-minute budget failed 1.1.0's release while it was still going; `TUTORIAL_STEP_MS` starts again at
    each tap.
- **A lookup of a screen that redraws all the time** (the tutorial's pulsing outline) takes six or seven seconds on a
    runner, and almost as long here, against a tenth of a second elsewhere. It's a cost to UiAutomator alone: the pulse
    sends no accessibility events (counted with `adb shell uiautomator events`). Make few of them.
- The `baseline-profile-report` artifact holds the report, the emulator log and every logcat buffer, and the screen and
    UI hierarchy the journey saved as it failed (`journey-failure-lap<N>.png`/`.xml`, from `captureFailure`).

**Reproducing a first boot like CI's**: run `.github/scripts/ci_emulator.sh start` with `ANDROID_HOME=/opt/android-sdk`
and an empty `ANDROID_AVD_HOME` and `RUNNER_TEMP`.

## The upgrade check in CI

`android-release.yml`'s `upgrade-check` job blocks a release unless a game left on the last release resumes on this one.
`.github/scripts/ci_upgrade_check.py` (its pure parts tested by `scripts/tests/ci-upgrade-check-test.py`) takes about two
minutes in the sandbox.

- **It plays** a fresh install of the last release's github APK: Play, Start Game (a fresh install's default setup), then
    the player's rolls and moves, at least four and until a pawn is out of its yard, and leaves the game waiting on the
    player's roll. **It upgrades** from the background with `adb install -r`. **It resumes** with Continue, compares the
    names, every board place's numbers (BoardSemantics: squares, pawns, and squares from home), and the whole move log
    (scrolled through: each line's time, players, and numbers), then takes two more rolls or moves to prove it plays on.
    The names are every text on the game screen that isn't a sentence, so a new label there must be left out in the
    script's `is_name`.
- **A release with no move log** (`LOGLESS_RELEASES`: 1.0.1) is compared without it. Temporary: remove it once 1.1.0 is
    released.
- **A line's pawns aren't compared** (`LOG_PAWNS`): releases up to 1.1.2 don't say which moved. Temporary: remove it once a
    release that does is the last one.
- **A retired package.** 1.0.0 went out as `net.zodac.ludo.github`, before the package became `net.zodac.ludoplus`, so
    no later release can upgrade it (the maintainer, 2026-11-19, let it go). The script reads both APKs' packages with
    `aapt2` and passes a last release in `RETIRED_PACKAGES` with a notice; any other mismatch fails.
- **The github APK, not the play app:** only the APK is a release asset that installs directly. Both APKs are real R8
    builds, re-signed with the debug key, so the job holds no signing secrets.
- **Labels.** It drives a release already out, so its labels are the constants at the top of the script, not `Journeys.kt`.
    Relabelling one means keeping the old label beside the new one until the new one is the last release's.
- **Exit status.** 1 (the old app couldn't be driven) is retried; 2 (the upgrade lost or changed the game, or the crash
    page showed) never is.
- **Failures.** The `upgrade-check-report` artifact has the screen, the UI hierarchy, and the logcat.
- **Trying it here:** sign both APKs as the job does, `emulator.sh start`, then
    `python3 -I .github/scripts/ci_upgrade_check.py <old.apk> <new.apk> <directory>`.

## Versions: the emulator follows compileSdk

- **`ARG EMULATOR_API=37.0` in `ai-sandbox-configuration/Dockerfile` is the one place** the emulator's version is chosen.
    - It's written as the package names it: from API 37 even base releases carry a minor; older ones are plain (`34`).
    - The image (`system-images;android-<API>;google_apis;x86_64`) and the AVD (`sandbox-api<API>`) derive from it.
    - It reaches every session as an `ENV` (the sandbox keeps the image environment), and `emulator.sh` falls back to `${EMULATOR_API:-37.0}`.
- **It tracks compileSdk's API level**: the newest stable Google APIs image of that level. There's usually no image for
    minor releases (none for 37.2).
- **`update_dependency_versions.sh`'s `sync_emulator_api` keeps it there.**
    - It moves the Dockerfile value and the fallback together.
    - It fails if they disagree, if the image isn't stable, or if a compileSdk-level image exists but isn't used. It only
        warns if none exists yet.
- **Revisions can't be pinned.** The layer is rebuilt only when `EMULATOR_API` changes.
- **Another Android version** for this container only: `EMULATOR_API=<N> emulator.sh start` (~1.5 GB). The
    journey has been run on 34 and 37.0.
- **Limits.** The emulator is slower and software-drawn, and runs one Android version at a time. It adds ~5 GB to the
    sandbox image, in layers below the SDK block.
