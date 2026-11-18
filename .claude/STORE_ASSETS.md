# Ludo Plus: store listing assets

How the listing's graphics are made, what to change, and the traps. **Running the script** is the `store-assets` skill.
The rest of the Play submission is in `PUBLISHING.md`.

The generator makes the **screenshots**, which the README shows, the **hi-res icon**, and the **feature graphic** (and its
README GIF), back for the Play listing in Phase 10 (`StoreArtGeneratorTest`, approved 2026-11-18). The **promo video** is
next: DiceFive's scenes are in commit `5423b6c` (`app/android/src/test/kotlin/net/zodac/ludo/store/`), and the script
still holds its encoding (the backdrop and sound mix among them), switched off until then.

- **Icon** (`icon` part): the launcher icon's two layers, the middle 76dp of their 108dp canvas, so it follows the
    launcher icon. That is still the placeholder pawn (kept for now, the maintainer, 2026-11-18).
- **Feature graphic** (`feature` part): the menu's backdrop with its pawns drifting, the menu's logo centred in the left
    half, and the board mid-game (`Scenes.midGame`) in the right, nothing that matters in the middle (Play's play button)
    or at an edge (its crops). The GIF is 6 seconds of the drift. The video's end card is the same scene at 16:9.

## Output (`assets/<version>/`, gitignored)

| File                                                              | Play's requirement       |
|-------------------------------------------------------------------|--------------------------|
| `phone/phone-1-game.png` ... `phone-6-statistics.png`             | 1440x2560                |
| `tablet-10/tablet-10-1-game.png` ... `tablet-10-6-statistics.png` | 2560x1440 (landscape)    |
| `icon-512.png`                                                    | 512x512, 32-bit PNG      |
| `feature-graphic-1024x500.png`                                    | 1024x500, no alpha       |
| `feature-graphic.gif`                                             | not uploaded: the README |
| `contact-sheet.png`                                               | not uploaded: for review |

- **File names** start with the device, so the two folders can't be mixed up in an upload.
- **`assets/full-description.txt`** is the hand-written listing text. It's the only tracked file under `assets/`
    (`.gitignore` makes an exception for it), and the script refuses to run if anything else there is tracked.

**Why these six.** Play accepts 2-8 screenshots per device, considers a listing for featuring only with **4+ at 1080px or
more**, and most people see only the first two or three: a game in play, the Rules' Captures page with its diagram, the
tutorial's coach card, Game Over in the middle of the celebration, the Leaderboard, and Statistics. The same six on the
10-inch tablet, in landscape (`w1280dp-h800dp-land-xhdpi`); the phone is 9:16 (`w412dp-h732dp-xxhdpi`).

**Captions** are part of the screenshot: a one-line gold headline on the brand navy, lit towards violet behind it
(`backdrop` in the script), above the framed screen.

## Changing what's shown

- **Captions**: `scripts/store-assets/captions.txt`, keyed by screen name. Rerun with `--no-render`. The banned-words rule
    applies, and each caption must stay true of the app.
- **Screens and order**: `screenshots()` in `app/android/src/test/kotlin/net/zodac/ludo/store/StoreScreensGeneratorTest.kt`.
    A new screen needs a caption, and the README's gallery and `readme-assets.yml` name each file.
- **Staged games**: `Scenes` in `StoreAssets.kt`. Each is a real game, played by the Medium CPU for every seat up to the
    moment shown, found by a seed search: `midGame` (Sam to choose between moves, one a capture, everyone out on the
    track) and `lastMove` (Sam's last pawn a move from home, stopping at the first winner so the move ends the game).
- **The history** is `StoreState.seed`: 24 finished games over seven weeks for Sam, Jo, and Alex and the CPU Ada, each
    played to its end by the CPU, which the Leaderboard and Statistics show.

## How it works, and the traps

The generators are unit tests in `app/android/src/test/.../store/`, skipped without `-PgenerateStoreAssets`.

- Each is tagged `@StorePart(...)`. `-PstoreAssetParts` picks parts, and `-PstoreAssetsDir` sets where raw renders go.
- They check nothing, so CI's checks never run them. Only the README-assets workflow does (below).
- They drive the whole app (`MainActivity`) on the test's own clock (`useCappedFrameClock = false`). The game's own waits
    run on the main looper, so a wait for something the game does next steps both clocks (`stepUntil`).
- A Rules tab half under its row's chevron is selected through its click action, not a tap: a tap there lands on the
    chevron.
- `magick` is ImageMagick's own AppImage, the same build in the sandbox (`ai-sandbox-configuration/Dockerfile`) and in CI
    (`.github/scripts/install_imagemagick.sh`). The script passes the app's font to every text and `montage` call: the
    AppImage's default font needs system fonts a runner may not have.

## The README's images

`.github/workflows/readme-assets.yml` renders `phone,tablet` from the pushed commit, shrinks them to PNGs (phone 540px
wide, tablet 1280px), and uploads them, replacing the old files, to the `readme-assets` GitHub release: an asset host,
not a software release (no version, never Latest). `README.md` embeds them as `releases/download/readme-assets/<name>`:
the phone shots under the introduction, the tablet ones in a collapsed section. A failed run leaves the previous images
up.

- **Pre-1.0.0 it runs on demand only** (`gh workflow run readme-assets.yml`, or the Actions tab). With VERSION's move to
    1.0.0 its push trigger comes back: `android-release.yml`'s paths plus the generator's own.
- Until it has run once, the README's images don't exist.
