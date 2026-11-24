# LudoPlus: frame cost

The patterns that keep frames cheap, what the art costs today, and what's still open. Read this for a reported
stutter, or before adding art that draws every frame. **How to measure** is the `benchmark` skill.

The short version: **keep per-frame work out of what a roll or a move redraws.** A pawn's move is where a player
notices first. Numbers from before 2026-11-16 were DiceFive's, measured on art and pages the two apps share; those of
its cups, mats, scorecard, and toss are in this file's history.

## Patterns that keep frames cheap

Each of these fixed a reported stutter. New work must keep them.

- **Paint costly, unchanging surfaces once.**
    - `drawCachedSurface(key)` (`Patterns.kt`) paints once per key and size into an `ImageBitmap`, then stamps it. It's
        an LRU of 128 and at most 48MB (~90KB each at die size, ~8MB for a phone-sized background), released when Android
        trims memory in the background (`LudoPlusApplication.onTrimMemory`). The key captures everything except size.
        Marble and Frosted cost 10x more
        when repainted every frame.
- **Hold art still where nobody sees it move.**
    - A tumbling face is frozen (`LocalArtFrozen`).
    - Off-screen Styles tiles get `LocalReduceMotion`.
    - Ambient loops obey the Animations levels (`UI.md`).
- **Animate opacity or tint, not geometry.** Neon paints its tube once and pulses the alpha. RGB paints once in white
    and tints (`ColorFilter.tint(.., SrcIn)`). A tinted image per lamp is slower than a small gradient.
    - A layer's animated alpha takes an offscreen pass per layer by default. Where its content doesn't overlap itself,
        use `CompositingStrategy.ModulateAlpha`.
- **Draw art on its own clock in `BoundedArt`, not a `Canvas`.** A state read while drawing re-records the whole layer it's
    drawn in, and any change to a Compose layer redraws the whole window on the GPU (see "Still on the table"). `BoundedArt`
    is a `RenderNode` clipped to the art, re-recorded when what it read changes, so neither happens. Give it `overflow` for
    art past its box: a clip cuts off anything further. Art that only fades is cheaper as `PulsingArt`, recorded once.
    Used by the game's background, a move, a die's `PipShape.CUSTOM`, and Glitch. `BoundedArtTest` holds it.
- **The Ludo board draws in four parts** (`LudoBoard`), so a pawn's move redraws only the move:
    - the board itself, painted once into an image per style, seats' colours, pawn count, size, and density
        (`drawCachedSurface`), on a layer of its own;
    - the turn's yard ring, on a layer, drawn again as the turn passes;
    - the pawns standing still, on a layer, drawn again as a move starts and ends (the moving and captured pawns left out);
    - the move itself in `BoundedArt`, which alone reads the move's clock. The board doesn't recompose on a move's frames.
    - A `Canvas` is not a layer: three of them side by side all re-record together when one changes.
    - `BoardFrameCostTest` holds the board painted once through a move.
- **The game's background decoration is `BoundedArt`** (`TableBackgroundFill`).
- **The die's tumble** (`GameDie`) recomposes only as a face flicks past (`derivedStateOf`), its turn is read only by its
    `graphicsLayer`, and the face flicking past is held still (`LocalArtFrozen`). A style
    that tumbles itself (D20, Egg) still reads the clock every frame. `BoardFrameCostTest` holds it.
- **Art that fills the screen gets a layer instead** (`Modifier.redrawsAlone`, on a sibling under what it sits behind): the
    menu's drifting dice, where a node would bound nothing. It saves the recording, not the redraw. `Modifier.clip` is a
    layer too.
- **Keep a shape that only changes colour.** `drawStars` builds its paths once per seed, size, and density, and a twinkle
    only changes their alpha. It used to rebuild up to ~190 paths a frame on a phone-sized board.
- **Don't re-lay out unchanged text.**
    - `FontFitCache` keeps each label's chosen size.
    - Styles tile names share one `TextMeasurer` (`rememberTileLabelMeasurer`).
    - `D20DiceStyle` shares one measurer, with no colour in the measured style.
    - `formatList`, `isRightToLeft` and the number formats are cached per language.
- **Read changing state where it's used.**
    - The Rules screen reads the current page only where it's shown (`derivedStateOf`), and remembers its tab modifiers.
    - Settings, achievements and the saved-game flag emit only when their own value changes (`distinctUntilChanged`).
- **Load ahead of the first use.** The sound clips load a second after launch, off the main thread, not on a game's
    first roll.
- **Warm a page up off screen with `OffscreenWarmUp`** (`ui/common`): a 1dp clipped box under an opaque screen,
    hidden from screen readers, that steps through passes a frame or two each once the screen above has settled.
    `StylesWarmUp`, `StyleCategoriesWarmUp`, and `LeaderboardWarmUp` are each one; a new warm-up is too. Tests see it by
    `OFFSCREEN_WARM_UP_TAG`.
- **Build a page behind the page cover** (`UI.md`, "Component conventions"). A page's first frames (composition, first
    paint, data arriving) are its costliest. They now land while the cover is opaque, and only one page is ever drawn at
    a time. Before, a 350ms cross-fade drew both pages every frame and started only after the next page's first frame.
    Measured 2026-10-27, desktop JVM, real `MainActivity` (compose and layout of the opening frame, ms):
    - The first opening after launch: Setup 117-138, Rules 85, Achievements 55-60, Settings 56-59, Start Game 51, Styles
        31-41. Later openings: 5-20. All of these now land with the cover at full strength; the fade-in frames are under
        5, apart from Styles' tile building.
    - Continue used to make two navigations, the board arriving part-way through two overlapping cross-fades. It now
        opens the game under one cover.
    - Going back isn't covered. Back to a game is 18-25 then 9-16 over two frames, back to the menu 8-11 then 4-10.
- **The Styles pages.**
    - The Styles page draws six previews (one per category's pick); each category's page builds only its own tiles,
        all at once (40 or so at most, the dice), in place of every category's rows on one page (about 150 tiles).
    - Each tile's preview is drawn in its own offscreen layer.
    - `StylesWarmUp` pre-draws the Styles page on the menu's idle frames, and `StyleCategoriesWarmUp` the category pages
        on the Styles page's (`UI_SCREENS.md`).
    - Opening frames (2026-11-10, Robolectric at 411x891dp, every style unlocked, warm openings; relative only):
        compose and layout, then draw. The old single page: 40-60 then 60-64ms, then up to ~39ms a frame while its rows
        built. The Styles page: 53-88 then 61-77ms. The Dice page: 47-70 then 92-106ms. The Mat page: 14-21 then 54-71ms.
    - Building a category page's tiles a screenful first, then a line a frame, was tried and dropped: on a phone a
        screenful and a line is nearly every die, and in one run with the two alternating, the Dice page's opening frame
        was the same either way (56/141ms against 59/142ms). Its cost is drawing the tiles on screen.
    - A category page's opening (2026-11-10, profiled): about half composing its tiles, a fifth measuring them. Each
        tile's lookups of the words every tile shares - "Select", "Locked", the colour word, and the templates the tile
        fills with its own names - are made once a page (`TileWords`, `fillTemplate`), leaving only its own name and
        colour to look up: the Dice page's compose and layout went from about 62 to 55ms. Left: the Maths and Poker dice
        lay their faces' text out afresh each opening (about 6%); caching it would touch the game's art too.
    - The Styles page's heading fits (`HeadingFits`) are kept for the process, and its names are plain `Text` at 100%
        (`ShrinkThenWrapText` measured them for nothing); profiled, those were its own largest costs, the rest being
        Compose's.
- **The Rules screen.**
    - A tab snaps to its page (`scrollToPage`) without building the pages between.
    - A page builds its first screenful, then the rest over the following frames (`rememberShownBlocks`).
    - Tab rows never re-centre (`KeepTabInView`).

## Current costs

Desktop JVM, warm, in ms. These are relative only: compare within a run.

- **Dice at rest** (row harness of seven, median of three runs, p95): no style is over 10: Tally ~9, Glitter ~8.6, RGB
    ~8, Classic ~6.5, blank 3-4.
- **Backgrounds.** First paint, whole-screen harness (411x891dp xxhdpi, ~25 floor): Leather and Granite add ~50, Black
    Marble, Meadow, and the Honeycomb one 30-38. Painted once after: Stone, Meadow, and Circuit sit within ~3.5 of the
    plain Classic one. Neon is ~29, level with Honeycomb and under Glitter and Glitch (32-34).
    - Alone, 411x560dp (median of three runs, p95): Ocean Waves is ~19, under Glitch ~22 and Glitter ~20, level with Starry
        ~17.5 and Classic ~16-20. It draws only its seven moving crest lines each frame; its glow is painted once.
- **Live per frame**, for moving art: glitter's sparkles (2 a die, 5 on its background); neon strokes, and the Neon
    background's horizon glow (one gradient rect; its grid is painted once); the Ocean Waves background's 7 crest lines;
    and glitch bands only during a burst.
- **Recording an always-on frame** (2026-11-05, sandbox emulator, debug build, a scratch Activity's `FrameMetrics`
    `DRAW_DURATION` over 300 idle frames, three runs each; p50 then p95). The only numbers here from real RenderNodes, as
    Robolectric repaints everything each capture. Compare within this list only. CPU recording alone, not the GPU.
    - A board and tray with no ambient art, the tile glow alone: 0.01 and 0.01, before and after. The glow only sets a
        layer's alpha.
    - Starry background and mat: 2.2 and ~4.2, now ~0.87 and ~1.4.
    - Floating Dice background: 1.43 and ~2.7, now ~0.52 and ~0.8. No stars in it, so this is the layer's saving alone.
    - The menu: 0.95 and ~2.5, now ~0.80 and ~1.4. Its content was already in layers; a backdrop with nothing on it is
        ~0.75, so what's left is the drifting dice drawing themselves.
- **A pawn move and a die's tumble** (2026-11-23, sandbox emulator, debug build, a scratch Activity's `FrameMetrics`
    over 40 moves or tumbles, two runs each; p50 then p95, ms). Classic styles, a mid-game 4-player board:
    - A move's frames: draw 2.2 and ~6.9, now 0.01 and ~3.3 (the frames a move starts and ends on); animation phase
        (composition and the move's recording) 1.1 and ~3.5, now 0.87 and ~3.0.
    - A tumble's frames, animation phase: Classic 2.3, now 1.5; RGB Rainbow 2.3, now 1.2. Its p95 (a new face) is
        unchanged at ~3.6-4.5.
- **Pages.** Styles opens at ~42-44 the first time after launch, then 16-24. A tile scrolling in costs ~0.6. Settings'
    first frame is ~26-30. A Rules tap frame is ~6-8.

A debug build on a phone is several times slower than any of this. Judge on a release build (R8 plus the Baseline
Profile).

## Still on the table

- **Any animating Compose layer redraws the whole window on the GPU** (2026-11-05). `redrawsAlone` saves recording, not
    this. HWUI bounds a node's damage only with `clipToBounds`; without it the damage is effectively infinite
    (`RenderNode::damageSelf`). Compose 1.12's `GraphicsLayerV29` sets `clipToBounds` only with `clip` on and no outline,
    and `clip = true` on the ring's `graphicsLayer` didn't narrow it. Show view updates on the emulator: one 48dp pulsing
    box on an empty Compose screen flashed a far corner of the window, as did a tile, the board, and the menu; a still
    screen flashed nothing and drew no frames; the same box as a plain `View` in a plain Activity left that corner alone.
    The trace's `Drawing` rectangle says the same: `atrace -t 2 gfx view`, with a screen's rectangle per frame.
    - On a phone at 120Hz (HWUI bars, read by eye), the rings alone were ~1.7x the green line, nearly all red and orange.
    - A score tile's glow is therefore `PulsingArt`, a `RenderNode` of its own with `clipToBounds`, faded through
        `onDescendantInvalidated` (Android 10 up). Emulator rectangles, real board: 100% of the window to 14% with four
        rings on the left, and 30% with the 5x tile too, as damage is one rectangle round them all. Its phone numbers are
        still to be taken. A hosted `View` didn't help: an `AndroidView` still drew the whole window.
    - `BoundedArt` does the same for art that changes shape. Emulator rectangles: any animated background (Starry, Floating
        Dice, Ocean Waves) 42% of the window, the Starry mat 21%, RGB, neon, and glitter dice ~11%, a neon, fishbowl, or
        cauldron cup ~5%. Moving together, they cover one rectangle round them all: Starry background and mat with rings,
        64%.
    - Still the whole window: the menu's dice (they roam it), a cup's shake and a die's toss (Compose layers turning),
        a score's count-up, and Material's drop-downs. Each but the dice is under a second.
    - **The HWUI bars can't show any of this.** Profile HWUI rendering makes every frame redraw the whole window
        (`FrameInfoVisualizer::unionDirty`), so it reads the same either way. Time it with `FrameMetrics` and no overlay.
- **Always-on animations' battery cost.** All of them stop after 60s without a touch at High and Medium, 30s at Low
    (`ScreenIdle`; the Pause when idle setting). The menu drift skips a 120Hz screen's every other refresh and sleeps between its frames.
    Starry, steam, cauldron, and the tile glow run at the display rate at High. Measure on a real 120Hz phone (Macrobenchmark
    `PowerMetric`, or Android Studio's Power Profiler) before capping. Not by frame-rate votes (see "Tried and
    dropped").
- **Baseline Profile benchmarks on a phone.**
    - Measure the generated profile's before and after
        (`./gradlew :app:baselineprofile:connectedBenchmarkReleaseAndroidTest`).
    - Decide whether the hand-written wildcard `app/android/src/main/baseline-prof.txt` (all app code hot, ~1.2MB
        compiled) can go.
    - Emulator numbers mean nothing here.

## Tried and dropped

- **Bounding the menu's drifting dice** (2026-11-06, considered, not built). Damage is one rectangle per frame, and four dice
    spread over the screen need nearly all of it, whether or not each has a node of its own. Moving one die a frame doesn't
    help: `computeDirtyRect` joins the last few frames' damage for buffer reuse, and each die would move less often than the
    60fps floor. Keeping the dice together, or resting between moves, would change how the backdrop looks. Caching the menu's
    content in a layer would cost ~10MB for an unmeasured saving. Left at the whole window by the maintainer's choice.
- **A 60Hz vote for ambient art at High** (`Modifier.preferredFrameRate(FrameRateCategory.Normal)`, 2026-11-03,
    investigated, not built). From Android 15, Compose takes the highest vote among the layers that redrew in a frame;
    a layer with no vote, and Compose's own scrolling and flings, add nothing. So a vote on always-running art (stars,
    steam, the cauldron, Neon) would pull whatever moves with it, unvoted, down to 60: the roll on those tables, and
    flings on the Styles page once the finger lifts. Keeping them at the screen's rate means a High vote on every
    other animation, kept up for each new one. Dropped by the maintainer's choice: High stays fully uncapped. Only
    the score tile glow, lit while the dice are still, would have been low-risk, and it's the smallest saving.
- **Moving `stringsLanguageConfiguration()`'s `runBlocking` off `attachBaseContext`** (2026-10-20, measured only).
    Sandbox emulator, debug build: 8-71ms on a cold start (most runs 10-35ms). About 2-4ms is coroutines' first load and
    nearly all the rest Compose resources' first read; a second call takes 0.2ms. The first screen would pay that first
    read anyway, so moving it wouldn't shorten start-up.
- **Replacing `material-icons-extended` (frozen at 1.7.3, 118 icons used) with Material Symbols** (2026-10-20). As
    vector drawables in `composeResources/drawable/`, R8'd dex fell 45 KB but the 118 XML files added 36 KB compressed
    plus ~27 KB of zip headers: the release APK grew 13.9 KB (download +10.9 KB). Path strings in Kotlin were estimated
    ~30 KB smaller (0.5% of the APK), not worth restyling every icon. The library is plain path code with almost no API
    to break. Symbols also redraws some glyphs (`whatshot` became a trend arrow; its flame, `mode_heat`, matches
    `local_fire_department`, which `PlayConsoleExportTest` rejects).
    - **Done after all** (2026-10-27), for the maintained, Material 3 look rather than for size: Rounded, filled, as path
        strings in Kotlin (`ui/icons/Symbols.kt`, `scripts/material-symbols.sh`), 117 glyphs. Play release APK 6,538,307 →
        6,537,839 bytes (-468), download 3,432,231 → 3,421,524 (-10.7 KB): R8'd code fell ~55 KB and the path strings
        added about as much. `minifyPlayReleaseWithR8` alone, warm, 8 runs each after an untimed one: median 24.5s before,
        24.3s after - no change worth the name, though R8 no longer reads the library's 11,123 classes.
