---
name: emulator
description: Boot the sandbox's Android emulator to look at the real app's screen. Not for timings or unit tests. Troubleshooting and CI details are in .claude/EMULATOR.md.
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

## The Baseline Profile journey

Not in the repo yet: `app/baselineprofile` was removed until there is an app to profile, and comes back from commit
`5423b6c` in Phase 5 of `IMPLEMENTATION.md`, with DiceFive's instructions for running it.
