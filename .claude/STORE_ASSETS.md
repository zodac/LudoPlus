# LudoPlus: store listing assets

How the listing's graphics are made, what to change, and the traps. **Running the script** is the `store-assets` skill.
The rest of the Play submission is in `PUBLISHING.md`.

The generator makes the **screenshots** and the **feature graphic**'s GIF, which the README shows, the **hi-res icon**,
the feature graphic's PNG, and the **promo video**, back for the Play listing in Phase 10 (`StoreArtGeneratorTest`, approved
2026-11-18; the video's scenes in `StoreScreensGeneratorTest`). `scripts/generate-store-assets.sh` makes the screenshots
alone by default; `--parts icon,feature,video` the rest.

- **Icon** (`icon` part): the launcher icon's two layers, the middle 76dp of their 108dp canvas, so it follows the
    launcher icon: the tilted board with the red pawn in focus (`UI.md`, "Gotchas").
- **Feature graphic** (`feature` part): the menu's backdrop with its pawns drifting, the menu's logo centred in the left
    half, and the board mid-game (`Scenes.midGame`) in the right, nothing that matters in the middle (Play's play button)
    or at an edge (its crops). The GIF is 6 seconds of the drift. The video's end card is the same scene at 16:9.
- **Promo video** (`video` part, `LudoPlus-Promo-1080p60-<version>.mp4`, about 32 seconds): eight scenes recorded from
    the real app a frame at a time (`FrameRecorder`, both clocks stepped), each beside its caption on the screenshots'
    lit navy, cross-faded, then the end card. A first turn (a dealt 6 brings a pawn out, a 5 moves it), a capture
    (`Scenes.midGame`), a move through a portal (`Scenes.portals`), a ghost catching a pawn (`Scenes.hauntedHouse`),
    picking dice on the Styles page, the win and the results (`Scenes.lastMove`), the Leaderboard, and Lap Of Honour
    unlocking on the Achievements page. Rolls are dealt through `AppContainer.diceRandom` (`DealtRandom`). Every
    achievement but `Scenes.LOCKED` is earned first, quietly, so no banner covers a scene but the last's, and the page
    reads "94 of 100" (an Easter egg would count past the total). The sound is the app's own clips, logged at their
    frames (`RecordingSoundPool`, `SoundLog`) and mixed in, under CC0 music ("Wednesday Night" by Zane Little Music,
    fetched by the script; the maintainer's pick, 2026-11-24, as "Happy Vibes" is DiceFive's), with
    `video-description.txt` for YouTube. No tutorial: it's not important enough for the video (the maintainer,
    2026-11-18).

## Output (`assets/<version>/`, gitignored)

| File                                                                | Play's requirement       |
|---------------------------------------------------------------------|--------------------------|
| `phone/phone-1-game.png` ... `phone-8-achievements.png`             | 1440x2560                |
| `tablet-10/tablet-10-1-game.png` ... `tablet-10-8-achievements.png` | 2560x1440 (landscape)    |
| `icon-512.png`                                                      | 512x512, 32-bit PNG      |
| `feature-graphic-1024x500.png`                                      | 1024x500, no alpha       |
| `feature-graphic.gif`                                               | not uploaded: the README |
| `LudoPlus-Promo-1080p60-<version>.mp4`                              | YouTube, then its URL    |
| `video-description.txt`                                             | the video's description  |
| `contact-sheet.png`                                                 | not uploaded: for review |

- **File names** start with the device, so the two folders can't be mixed up in an upload.
- **`assets/full-description.txt`** is the hand-written listing text. It's the only tracked file under `assets/`
    (`.gitignore` makes an exception for it), and the script refuses to run if anything else there is tracked.

**Why these eight** (Play's most; the maintainer, 2026-11-24, after DiceFive's). Play considers a listing for featuring
only with **4+ at 1080px or more**, and most people see only the first two or three: a Classic game in play, Portals and
Haunted House each at a dressed table, the Styles page's dice, Game Over in the middle of the celebration, the
Leaderboard, Statistics, and an achievement unlocking. No Rules or tutorial. The same eight on the 10-inch tablet, in
landscape (`w1280dp-h800dp-land-xhdpi`); the phone is 9:16 (`w412dp-h732dp-xxhdpi`).

**Captions** are part of the screenshot: a one-line gold headline on the brand navy, lit towards violet behind it
(`backdrop` in the script), above the framed screen.

## Changing what's shown

- **Captions**: `scripts/store-assets/captions.txt`, keyed by screen name. Rerun with `--no-render`. The banned-words rule
    applies, and each caption must stay true of the app.
- **Screens and order**: `screenshots()` in `app/android/src/test/kotlin/net/zodac/ludoplus/store/StoreScreensGeneratorTest.kt`.
    A new screen needs a caption, and the README's gallery and `readme-assets.yml` name each file.
- **Staged games**: `Scenes` in `StoreAssets.kt`. Each is a real game, played by the Medium CPU for every seat up to the
    moment shown, found by a seed search: `midGame` (Sam to choose between moves, one a capture, everyone out on the
    track), `portals` (one of Sam's moves through a portal), `hauntedHouse` (eight players and four ghosts, one of them
    catching a pawn as Ada's turn ends: a ghost moves after every second turn, so never after Sam's), and `lastMove`
    (Sam's last pawn a move from home, stopping at the first winner so the move ends the game). Each brings its own move
    log.
- **Dressed tables**: `Scenes.POND` (Portals) and `Scenes.CRYPT` (Haunted House). A style still locked by
    `Scenes.LOCKED` (the Starry and Neon ones) is drawn as the default.
- **The history** is `StoreState.seed`: 24 finished games over seven weeks for Sam, Jo, and Alex and the CPU Ada, each
    played to its end by the CPU, which the Leaderboard and Statistics show.
    Every achievement but `Scenes.LOCKED` is recorded as earned, so no banner covers a screen but the last's own.

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

`.github/workflows/readme-assets.yml` renders `feature,phone,tablet` from the pushed commit, shrinks the screenshots to
PNGs (phone 540px wide, tablet 1280px), and uploads them with the feature graphic's GIF, replacing the old files, to the
`readme-assets` GitHub release: an asset host, not a software release (no version, never Latest). `README.md` embeds
them as `releases/download/readme-assets/<name>`: the GIF under the title, the phone shots under the introduction, the
tablet ones in a collapsed section. A failed run leaves the previous images up.

- **It runs on a push** that touches `android-release.yml`'s paths or the generator's own, and on demand
    (`gh workflow run readme-assets.yml`, or the Actions tab).
- Until it has run once, the README's images don't exist.
