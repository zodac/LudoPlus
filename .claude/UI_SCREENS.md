# LudoPlus: screen-specific UI detail

Achievements, banners, Styles, Leaderboard and menu decisions. The general rules are in `UI.md`, the game screen is in
`UI_BOARD.md`, and what the features do is in `DESIGN.md`.

## The achievements list

**One list, grouped by theme, easiest first** (the declaration order of `Achievement`), Completionist last of the Milestones. Winning, Misfortune, and
Miscellaneous run in alphabetical order instead (Cutlery Collector aside, straight after Wooden Spoon), and so do the
Easter Eggs, which come last.

- **Unlocking doesn't move a row.** It's highlighted in place, with its own icon and a raised `secondaryContainer`
    card, so a ladder reads as a ladder.
- **Never split the list** into locked and unlocked parts, add a "hide unlocked" filter, or sort the whole list alphabetically.
- **A row's title** (`ShrinkThenWrapText`) is one line at `titleSmall` when it fits, stepped down to the 12sp floor, then
    wraps to two lines at 12sp: unlike the banner, which keeps one line down to 11sp. `AchievementsTextTest` pins it. The
    row sits under `IntrinsicSize.Min` (equal cards in landscape), so `ShrinkThenWrapText` answers intrinsic heights.
- **The count is "earned of total", and the Easter Eggs are in earned but not in total**: a player with everything
    reads "110 of 100 earned". This is intended, not a bug (`AchievementsTextTest`). It is not about secrecy: every
    Easter Egg's row (title shown, description "???") is in the list from the start.
- The unlocked/total count sits centred above the list. In landscape, where the list has two cards to a row, it moves
    into the app bar after the back arrow, with the Unearned switch at the bar's end (see `UI.md`, "Landscape pages").
- **While signed in to the online service, the count is a button** to the service's own achievements screen, with the
    service's mark (`GameServices.serviceIcon`, untinted) centred in the gap between the text's end and the card's. The
    text sits as it would centred together with the mark 6dp after it. TalkBack:
    "42 of 100 earned, button, double-tap to open Google Play Games"; the mark is decorative. Signed out it's plain
    text, with no ripple, button role, or mark. A tap still counts toward a debug build's superuser mode.

**Category headers** are `stickyHeader`s styled as a plain `Card`. A bare `Surface` has square corners.

- Up and down arrows glide to the previous or next header (`smoothScrollToItem`: one eased animation, 350-800ms by
    distance; the stock `animateScrollToItem` spring ends in a quarter of a second and reads as a snap). Each frame moves
    its share of what's left. A sticky header is laid out pinned as soon as the list reaches its category, so its own
    offset never says how far is left (`offsetWhereItLies`). The arrow with nowhere to go is greyed, not removed.
- They're stock `IconButton`s, so the header is 48dp tall.
- Each header carries its category's earned/total (`3/12`), counted over the whole category even while the Unearned switch is on
    hides rows. Label and count are `categoryHeaderStyle` (Sora, `titleSmall`, as written), as on the Styles screen. The
    label is `onSurface`, and the whole header turns brand gold (`primary`) once every one is earned. TalkBack reads
    "Milestones, 3 of 12 earned" as the heading; the drawn count is hidden from it. Every category has a count, Easter
    Eggs included: their rows are all listed, so their number is no secret.
- Rows slide under the pinned header and are cut at its middle (`Modifier.hiddenUnderPinnedHeader`, from `layoutInfo` in
    the draw pass), so they show past its rounded bottom corners. Cut at its bottom edge, their square corners stick out
    past its curve like a lip; left uncut, the top of the list cuts them flat in its top corners.

**The list scrolls past its last row, on purpose.** A trailing blank item (`tail-space`, measured as the viewport less
the header, the last row, and the list's gap before it) lets the final rows reach the middle, so a banner long press can
centre them. At the furthest scroll the last row rests whole, directly under the pinned header (`AchievementsLastRowTest`).

- It's for this page only.
- It must be an item, not `contentPadding`, which would shift `centeredScrollOffset`.
- It's empty, so TalkBack has nothing to stop on.

**Scroll memory.** The position is kept in a process-lifetime `object` (`AchievementsScrollMemory`), because popping
the screen discards its ViewModel and anything `rememberSaveable`d. It's restored only after the list has loaded, since
an empty `LazyColumn` clamps to the top. Reuse this approach for any list
that must remember its place, with a reset for tests (`resetAchievementsScrollMemory`, `resetRulesPageMemory`): every
Robolectric test shares one process, so a test that opens the screen calls it first.

**Icons** (`ui/achievements/AchievementIcons.kt`) are a `@Composable` `when` over the enum.

- Glyphs are Material Symbols, Rounded and filled, from `ui/icons/Symbols.kt`, which `scripts/material-symbols.sh`
    generates from Google's path data. Add a glyph to the script's list and run it; never edit `Symbols.kt` by hand.
- Bespoke drawings are allowed where Symbols has nothing that fits: `ImageVector`s in `DrawnAchievementIcons.kt`, path
    data on the 24 grid (or Symbols' 960 one, to build on a Symbol such as the trophy), one colour with details cut out.
    A Symbol's own paths can be placed, scaled, and turned in one (`pathNodes()`: One By One's and Two Birds, Two
    Stones' chess pawns). The few in fixed colours sit at that file's end, and Midnight Run's moon is in
    `AchievementIcons.kt`. There are no drawable resources for achievement icons.
- A Symbol is Google's current drawing, which can differ from its name or an older look: `Whatshot` is now a rising
    arrow in a circle, not a flame. Render a new glyph before choosing it.
- An icon is not a field on `Achievement`, so the model stays free of Compose.
- **A mode or modifier named in a description is in `backticks`**, drawn bold gold (`tooltipMarkup`) on the list and the
    banner. TalkBack and the Play Console export drop the backticks. `AchievementTextTest` checks they pair, and that no
    mode's name (in the string's own language) sits outside them, "non-`Standard`" included.
- **Locked rows all show the same question mark**, so they never spoil the achievement.
- **Fixed colours** via `iconTintOrUnspecified`, rather than the row's tint:
    - every Easter Egg (Big Fan's red heart, Are You A Developer?'s cyan, Can't Wait To Get Going's red pawn, Midnight
        Run's moon). A new Easter Egg adds its case there;
    - and the few whose point is a colour: Completionist's crystal trophy, Colour Rush's squares (Paint The Town
        Red's nine, Colour Coordinated's corner of the track), Through The Looking Glass's portal (as Portals draws
        its first pair), and Everything Everywhere All At Once's googly eye (from DiceFive).
- **A mode's win achievement's icon is also its Play leaderboards' icon** (`GAME_MODES.md`), so it has to stand for the
    mode as well as the win.
- When adding one, grep that the glyph is unused. `AchievementIconsTest` and `PlayConsoleExportTest` fail if two
    achievements draw the same.
- **Never a brand's mascot or logo**, even where Symbols has one: the Android robot needs a CC BY credit, and Flutter's
    Dash is Flutter's. I, Robot is `SmartToy`, a generic robot.

## Achievement banners

`AchievementBannerHost` wraps the **whole app** in `MainActivity`, outside the `NavHost`.

- A burst at game end keeps playing across the results page and the menu. A per-screen overlay would cut it off.
- It listens on `AchievementEvents`, a process-wide `SharedFlow`.

The constants are at the top of `AchievementBannerHost.kt`:

|          | Value                                                                                | Why                                                                                         |
|----------|--------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| Position | bottom-anchored stack; the oldest banner in front, each newer one peeking 14dp above | keeps the board clear                                                                       |
| Fade in  | 180ms on joining the stack                                                           | every banner in a burst shows at once                                                       |
| Hold     | 4s (5s on screen with the fades), or the system's "Time to take action" if longer    | readable without lingering                                                                  |
| Fade out | 820ms                                                                                | a snap mid-burst reads as a glitch                                                          |
| Stagger  | 300ms                                                                                | deals like cards                                                                            |
| Cap      | 4 on screen                                                                          | the rest **wait** (the collector suspends on `snapshotFlow { banners.size }`), none dropped |
| Swipe    | 15% of the width, either way; leaves in 180ms                                        |                                                                                             |

**Interaction.**

- Only the front banner is interactive and counting down. A promoted one starts a full hold.
- A finger on it pauses the countdown (back to full opacity), with a fresh hold on release.
- An unlock takes the front over a progress nudge; anything else joins the back.
- **Tapping toggles pause and resume.**
    - A pause/play glyph flashes for 1s (`HOLD_INDICATOR_MILLIS`). It's a child of the banner, so it shares the banner's
        alpha.
    - Resuming keeps the remaining time (`holdRemainingMillis`, monotonic, so background time counts).
    - Nothing shows that a banner is held once the glyph fades. That's by design.
    - TalkBack gets "Pause countdown" / "Resume countdown" actions.
- **A long press** jumps to the achievement on the Achievements screen; a styles banner opens Styles instead. Mid-game,
    with "confirm before leaving" on, it asks first, and every countdown pauses while the dialog is up.

**Window.** The stack lives in its own non-modal `Dialog` window, so it draws above other dialogs.

- The window is full width but wraps the stack's height. **Never `fillMaxSize`**: not-touch-modal only passes touches
    outside the window's bounds.
- **Never dim.** Clear `FLAG_DIM_BEHIND`; don't set a dim of 0. Otherwise Android 12+ drops taps beneath as an
    untrusted occlusion (the About dialog's close button broke this way).

**Three variants, deliberately unequal.**

- **Unlock.**
    - `primaryContainer`, with the achievement's own icon (not a generic trophy).
    - A one-line title and two lines of description.
    - A star badge when the achievement unlocks a style.
- **Progress.**
    - Quieter: `surfaceContainerHigh`, one line, and a thin `LinearProgressIndicator`.
    - The count and bar climb from the old value to the new over 700ms.
- **Styles** (`AchievementEvent.StylesUnlocked`), raised when an achievement count crosses a style lock, or when an
    achievement with styles locked behind it is earned (`StylesEarnedBy`).
    - `tertiaryContainer`, with the style star in a circle. It's the size of an unlock banner.
    - Title "Style(s) Unlocked"; description "Earned 23 achievements: the 'Frosted' dice style", naming two styles and
        counting the rest.
    - **Order.** `announce(update, ownsAllStyles)` (`app/AchievementEvents.kt`) emits each unlock followed by its own styles banner if
        it has styles behind it (`styleRewards`: "Earned 'Big Fan': 'Glitch' dice and background", or "the hidden
        'Multicolour' dice colour"); then **at most one** styles banner for every count lock crossed
        (`stylesUnlockedByCount`, from `unlockedCountBefore` to `unlockedCountAfter`); then the progress nudges.
    - The achievement's own banner keeps its star.
    - **None for an owner of every style** (the purchase, `PURCHASES.md`): `AchievementUnlocker` reads `ownsAllStyles` as
        it announces, and only the unlock and progress banners show. The star stays on rows and unlock banners.
    - One achievement's styles of one name are named once ("'Starry' die and background"), so they fit the two
        lines in Spanish.
    - TalkBack hears every style unlocked (`StylesUnlockedBannerSemanticsTest`).

**Text.**

- **The title is always one line** (`BannerTitle`, `ShrinkThenWrapText` with a one-line limit). It starts at 16sp and
    steps down 0.5sp to 11sp (`BANNER_TITLE_MIN_FONT_SIZE`, below the app-wide 12sp floor), then ellipsises; it never
    wraps.
    - That's what the 38-character cap is for. The longest title ("Where We're Going, We Don't Need Rules") fits at 11sp
        on the maintainer's ~345dp phone with about a character to spare. On a 360dp render (a 244dp column) it fits at
        11sp only at font scale 1.0: at 1.15 it is ellipsised, and at 12sp it was already cut at 1.0.
- **The description** is bodySmall (12sp), always exactly two lines, then ellipsised (`BannerDescription`). At 320dp
    about eight descriptions are cut.

## Style locks

A lock is on the style (`StyleGroup.unlock`, in the registry), not the colour: once it's met, all its colours are
available. `StyleRegistryTest` keeps the counts within what can be earned.

- **Behind a specific achievement**, shown padlocked:
    - Googly die and pawn (Completionist);
    - Glitch die and background (Big Fan);
    - Maths die (Counting Up);
    - Wireframe die (Are You A Developer?);
    - Devil die and pawn (Too Much Of A Good Thing);
    - Starry board, die, and background (Midnight Run).
- **Secret colours** (`StyleVariant.secretAchievement`) are left out entirely until earned. None yet.
- **On the Achievements screen**, a row whose achievement unlocks a style reads the reward's sentence with the row
    ("Earn this to unlock the 'Maths' die style"; one style's items named once, as the unlock banner does: "'Starry'
    die and background"), and a tap shows it as a tooltip that **stays until the row is tapped again**. Its action is
    "Show which style this unlocks". A row with no style behind it has no gesture.
    - Rows and banners carry `StyleRewardStar`, **a badge over the icon's corner, never in the text row** (which would
        wrap titles). The star is decorative to TalkBack: the banner says "Unlocks a style".
    - Superuser mode's long press takes precedence.
- **On the Styles screen**, a locked tile shows its first colour under a scrim, with a faded padlock.
    - A tap shows a tooltip with the core requirement ("23 achievements needed").
    - A long press opens a dialog with progress. That dialog is TalkBack's one action, since tooltips aren't announced.
- **A locked saved pick is never overwritten.** Everything that draws a saved style goes through
    `StyleCatalog.unlockedById` (never `byId`; its rule is `StyleRegistry.unlockedId`), which draws the default instead.
    A reset re-locks a style without losing the pick.

## The Styles screen

Two levels (decided 2026-11-10, replacing one page of every category's rows, which was cluttered and slow to open): the
**Styles page**, a field per category showing its pick, and each **category's page**, every style of that category at
once. Routes `styles` and `styles/<category>` (`Screen.styleCategory`), one composable per category.

**Counts.** Every colour of a style counts as one (`StyleRegistry.unlockCount`). A secret colour isn't counted until
it's earned, so the total doesn't hint at it.

- The Styles page's unlocked/total count is drawn like the Achievements page's: a full-width card over the fields, or
    compact after the back arrow in landscape. A category's page has its own count there.
- Each field's heading carries its own `46/96` at its end. Title and count turn brand gold (`primary`) once every one is
    unlocked.

**The Styles page** (`StylesScreen`): two rows of two fields (Board, Pawns, Die, and Background), in portrait and
landscape, filling the page.

- Each field is one grey `Card`, the whole of it the button: the category's name in New Game's small grey heading
    (`titleSmall`, `onSurfaceVariant`), the count at the heading's end; the pick's preview (`StylePickPreview`); and the
    pick's name in Sora bold with its colour under it (a blank line for a one-colour style, so a row's previews match),
    from the start, with the gold chevron at the end, mirrored right to left.
- Portrait: the names along the bottom, under the preview. Landscape, where a card is wide and short: beside it, so
    the preview has the card's height.
- The preview is the pick's tile, still: no badge or dots, scaled to fill its room (`MIN_PREVIEW_SCALE`..
    `MAX_PREVIEW_SCALE`), from the room's start, so every preview lines up with its heading whatever its shape. The
    Board preview is a bare corner of the board, no pawns (`BoardCornerPreview`); the Pawns preview one pawn alone, in
    player 1's colour, no board (`PawnPreview`), so neither reads as the other (the maintainer, 2026-11-23).
- Rejected on review: a blue field inside the card as New Game's pickers (wasted space; then the previews too small),
    no cards (the art bare on the backdrop), a tight card round the art alone, the chevron on the art's edge, the names
    under the preview in landscape (the previews tiny), and centred previews (the narrow cup stood apart).
- TalkBack: each field is one button - "Die, button, Ocean, Deep. 60 of 101 unlocked", action "Choose Die"
    (`common_picker_state_spoken`, `common_picker_choose_action`); the heading is cleared.
- Large fonts (only above 100%): a row's headings put their counts on a second line if any one doesn't fit; titles and
    names shrink to their 100% size before ellipsising; and where the fields would be shorter than their words and a
    96dp preview (`MIN_PREVIEW_HEIGHT`), they keep that height and the page scrolls.

**A category's page** (`StyleCategoryScreen`): titled with the category, its count and the Unlocked switch over a card
of every tile (`StyleCategoryTiles`), the page scrolling. A pick is saved at once and the page stays open. The page
opens with the pick in view: when it isn't wholly on screen, already scrolled to centre it (`LocalStyleTileJump`, once
per opening); otherwise at its top. A banner's styles take its place.

- **The Unlocked switch** (labelled "All" while off, "Unlocked" while on) sits beside the count, drawn and placed as
    the Achievements page's switch (labelled "All" / "Unearned"): after the count in portrait, at the app bar's end in
    landscape. On, it hides every locked style; it's one setting for every category's page, kept across restarts on
    this device (`SettingsRepository.unlockedOnlyStyles`), as the Unearned switch is (`lockedOnlyAchievements`); neither
    goes in the cloud save. It's left off a page once every style of it is unlocked.
- Hidden locked tiles stay composed out of sight (`HiddenTiles`), so turning it off moves them back rather than
    building them. Locked tiles come after the unlocked ones, so hiding them only shortens the run.
- **The page scrollbar** (`VerticalScrollbar`) sits in the 20dp right margin, and the whole margin is its drag handle.
    It's silent to TalkBack.

**A styles banner** (long-pressed) opens the category's page when its styles are all of one category
(`Screen.stylesBannerTarget`), over the Styles page so Back returns there; the styles are flashed gold, the page
opening already scrolled to them (`LocalStyleTileJump`), or sliding there if it was open already
(`StyleScrollRequest.animate`). Styles of several categories open the
Styles page, those fields flashed; opening one hands the request on (`CategoryFocus`), so its styles flash there.

**Unlock All** (the purchase, `PURCHASES.md`): a tonal button at the Styles page's app bar end (`UnlockAllStylesButton`,
`UnlockAllStylesIcon`).

- Debug builds: 5 taps on the Styles page's unlocked count pretend every style is owned (`DESIGN.md`, "Superuser modes").

**Tiles.**

- One tile per family. A long press opens a row of previews, one per colour. Colour names are spoken, not shown.
- Picking a die rolls it through six faces (`DicePickRoll`). It's only visual.
- The pick's tile is ringed in gold (a 3dp `primary` border, against 1dp `outline` on the others), and nothing else:
    no badge over the art, which it hid.
- Tiles are radio buttons: style and colour name, selected state, and position in the set.
- **Spacing** (`TileSpacing`, from the card's width): each tile takes its preview's width (a longer name spills into
    the gaps); the gap is `TILE_SPACING` (12dp), closing to `GALLERY_MIN_TILE_SPACING` (4dp) when that fits one more
    tile; sides are `TILES_SIDE_PADDING` (8dp). On a 360dp phone this fits four dice or backgrounds
    (`StyleCategoryPageTest`), and three boards or pawns.
- **Tile sizes.** Dice are 72x72dp. Backgrounds are 72x80dp (a phone's game screen, a little taller than wide). Boards
    and pawns are 97x76dp, so three fit across: a board's corner, `CORNER_VIEW_WIDTH` (9) squares across, or one pawn.
- **Closing up to fit** (the maintainer, 2026-11-26): a category page that only just scrolls closes up the room between
    its lines of tiles, and above the first and below the last, from 12dp to as little as 8dp
    (`MIN_TILE_LINE_SPACING`), just far enough to fit (`fittedLineSpacing`). The tiles stay full size. On the
    maintainer's 374x827dp phone (no ad) the six lines of boards and of pawns fit this way (`StyleCategoryPageTest`);
    the dice are far too many, so they scroll at the full 12dp.
- **Fitting the screen** (decided 2026-11-10): where closing up isn't enough, and only at a larger font or display size (`isDisplayZoomed`: the screen's
    density above `PlatformServices.defaultDensity`), a category page that would scroll draws its tiles smaller to fit
    (`fittedTileScale`): smaller dp, the same sp, so the names keep their size, and a smaller tile can gain a column.
    No smaller than 64/88 of its size (`MIN_TILE_FIT_SCALE`), nor any tile under the 48dp touch target; where even that
    doesn't fit, full size and scrolling. At the device's own sizes a long category
    (the dice) scrolls at full size, by the maintainer's choice. Worked out for every tile, shown or not, so the
    Unlocked switch never resizes them.

**The warm-ups**: a page's first open after launch is slow, because the drawing code runs for the first time. Two, by
the maintainer's choice (2026-11-10): the menu warms only the Styles page, and the category pages are warmed only once
the Styles page has been opened.

- `StylesWarmUp`, on the menu: 600ms after it settles, the Styles page's empty frame, then the whole page
    (`driftingDice = false`), once per process.
- `StyleCategoriesWarmUp`, under the Styles page: 600ms after it opens, every tile three at a time, a category at a
    time, then one category's whole page (every page's own code is the same; a whole page a pass made heavy frames on
    the Styles page). Once per process. It stops the moment a field is tapped, so the page cover's fade isn't fighting
    it, and carries on from the pass it had reached (`categoryWarmUpDone`) the next time the page is open
    (`StylesWarmUpTest`).
- Each draws in a 1dp clipped box under its page's opaque backdrop. Compose doesn't cull clipped content.
- The pages and the warm-ups share `StyleCategoryTiles`, `StyleCategoryScaffold` and `StylesScaffold`, so new
    categories are warmed automatically. Keep it that way.

## The Leaderboard and Statistics

What they show is in `DESIGN.md`, "Leaderboard and Statistics". Both open with the modes' drop-down
(`DropdownChoiceField`, in `ui/records/ModePicker.kt`) and a row of player counts (`PlayerCountRow`), each number said as
players.

- **The drop-down** stays at the top while the rest scrolls under it, and has a divider after Classic and, on Statistics,
    before All Modes. Another mode's page starts at its top. It isn't there on Statistics with no games at all.
    TalkBack: one drop-down ("Leaderboard, Classic"), with state Collapsed or Expanded; each menu row is a radio button,
    the picked one "Selected", with its place in the set.
- **Everything under it is one `LazyColumn`** (`RecordsList`): only what's on screen is built, so a pick costs the same on
    a full board as on a short one. The Leaderboard's card is drawn a piece to each row (`TablePiece`).
- **`LeaderboardWarmUp`**: 2s after the menu settles, it draws two boards once from made-up wins (no database), the same
    way as `StylesWarmUp` (`LeaderboardWarmUpTest`).
- **The Leaderboard** is one card: a heading row, then the ranked rows, 1st to 3rd in gold, silver, and bronze. Where a
    name hasn't room beside the number columns, the heading goes and each row is two lines. A row is one TalkBack item. A
    board with no rows shows a card saying how to get on it.
- **Global**, while signed in to a service with a board for the mode: a button to it, with rows or without.
- **Statistics** is a card a person: the name as a heading, then each figure with its label as one item. The time played
    is over them, with players or without.

## The menu's backdrop and logo

**Drifting dice.** `BrandBackdrop`'s watermark dice drift on the menu and on every screen off it. The game itself has
none.

- An even face is a die; an odd one is a pawn, the Classic style's (`ClassicPawnShape`, which `ClassicPawnStyle` draws
    from too), whatever pawns the player picked.
- One `DriftState`, held in `LudoPlusApp` and shared through `LocalDriftState`, so the dice keep their places across
    screens.
- It resets only after a game left unfinished, or on a fresh launch.
- `DiceDrift` holds the logic, free of Compose (`DiceDriftTest`):
    - five dice, each crossing in a straight line, up and to one side, turning slower than it travels;
    - a die that leaves re-enters just below the bottom on a new path, size and face;
    - the lean is capped (~25 degrees upright) so dice don't bunch low;
    - a returning die picks whichever of a dozen candidate paths stays furthest from the others over 12s;
    - "gone" uses each die's current extent, which keeps three to five showing.
- It skips a 120Hz screen's every other refresh (60 redraws a second; `DriftState.onFrame`) without changing speed,
    and never redraws under 60 where the screen can: 90Hz stays at 90. `DriftState.run` sleeps 7ms after each move, so
    the refresh it would skip doesn't wake the app (`DriftFrameRateTest`).
- It holds where it is on an idle screen, as all ambient decoration does (`ScreenIdle`; `UI.md`, "Reduced motion").

**`AppLogo` is live Compose art**, the launcher icon's art drawn in Compose (from 2026-11-23): the classic board
turned so red's yard is nearest and flattened into perspective (0.56), narrower than the wordmark, whole and unfaded
(the maintainer, 2026-11-23), with a red pawn standing in red's yard, at the front, over the "LudoPlus" wordmark.

- Drawn in the player's board and pawn picks (`MenuScreen`'s `logoTheme`, from `AppContainer.savedStyles`), or their
    defaults while a pick is locked (the maintainer, 2026-11-23). The feature graphic passes the defaults.
- It's held invisible, still taking its space, until the picks load, so it never flashes the defaults (`AppLogoTest`).
- The art is decoration (`clearAndSetSemantics`): TalkBack hears only "LudoPlus", as a heading.
- **The pawn hops when tapped** (an Easter egg, 2026-11-23): three hops where it stands, each the board's hop from
    square to square (`HOP_HEIGHT`, `HOP_SWELL`, its ground shadow, `PAWN_STEP_MILLIS` each), on a layer of its own over
    the board's, at the Menus & pages pace. Under reduced motion it stays still. Either way the tap earns Can't Wait To Get
    Going. A tap within 1.2 pawn radii of its middle finds it; a tap while it's hopping is ignored. It's a raw gesture
    (`detectTapGestures`), so TalkBack doesn't reach it, as with the version's seven taps (`AppLogoTest`).
- Keep the "LudoPlus" text: `MainActivitySmokeTest` asserts on it.
- The feature graphic draws it too (`StoreArtGeneratorTest`), so a change to it is a store-asset change.
