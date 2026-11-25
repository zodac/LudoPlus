# LudoPlus: making table art (boards, pawns, dice, backgrounds)

The working method and the drawing techniques. Elsewhere:

- what a style *is* (catalogs, ids, locks): `DESIGN.md` "Table art" and `UI_SCREENS.md` "Style locks";
- rendering for review: the `render` skill;
- frame cost: `BENCHMARKS.md` and the `benchmark` skill.

The die and background art came from DiceFive (`5423b6c`), whose cups, mats, and frames LudoPlus doesn't have.

## Working with the maintainer on art

- **Render for review before any APK.** Send every state, with animations as labelled sheets, and expect several
    rounds. Comments are specific and literal ("the coins at the back lay flat on the air"), so re-read them before
    guessing.
- **They look at close-ups.** Render a zoom of anything they circle, and check fixes at that zoom.
- **They care about frame cost.** Hundreds of live draws per frame get refused even when the numbers are fine. Say
    plainly what's painted once and what's live, with counts.
- **Unlocks.**
    - Locks live in the registry (`game/style/StyleRegistries.kt`), never with the art.
    - A family with a counterpart of the same name in another category takes its lock (`StyleRegistryTest`).
    - If none is given, pick an `AchievementCount` (`StyleRegistryTest` keeps it earnable) and say which.
    - One achievement unlocks a style through `SpecificAchievement`, shown padlocked.
    - A secret colour uses `StyleVariant.secretAchievement`, not offered until earned.
    - The star and tooltip follow from `styleRewards` automatically.
- **Style over realism, where asked.** Realistic gems were "too real"; the Gems dice and pawns are flat facet tones, ink
    outlines and a crisp highlight. Offer the stylised version when it's ambiguous.

## Boards

- **A family is a `LayeredBoardStyle`** (`ui/style/BoardFamilies.kt`): it overrides the layers it changes, in the
    order they're drawn: `ground`, `drawSquare` (every track and home-column square), `drawYard`, `drawHomeTriangle`,
    `drawSafeMark`, and `drawFinish` over the lot. Classic (`BoardStyles.kt`) is drawn on its own and is what the Rules
    diagrams use.
- **A star square is shaded as well as marked** (the maintainer, 2026-11-24), so it shows with a pawn over its mark:
    `drawSquare` is given `safeFill` there, darker than `plain`, or lighter on a darker board, and a family whose plain
    squares ignore their `fill` (Disco, Groovy's rings, Paper) must still show it. `StarSquareTest` holds every board to it.
- **A claimed star square** (Colour Rush) is its player's colour with the mark at `CLAIMED_MARK_SCALE` (`drawClaimedMark`):
    both must show, whichever of the player colours it is. A mark that would be lost on one is edged there (Starry's).
    `ClaimedSquareTest` holds every board to it.
- **One yard for every layout.** A new family extends `ShapedYardBoardStyle` (`PlayfulBoardFamilies.kt`), which draws the
    classic yard and a star board's through the same `drawYardGround`, `drawCourt`, and `drawSpot`, each cut to its shape.
- **Many dots are one draw**: `drawDots` (`drawPoints`), as Comic's halftone.
- **The seats' colours stay readable.** A family may tint them towards its material through `seatTone` (Wood, Stone),
    or draw them as light (Neon) or crayon (Paper), but every seat must stay unlike the others and unlike a plain square.
    Check all four in one render.
- **Pawns must stand out on it.** Render the board with pawns of every colour on plain squares, on their own colour's
    squares, and in the yards. A dark board (Neon, Starry) needs the pawns' ink outline to show.
- **The board is a `BoardLayout`** (`ui/game/BoardLayout.kt`): the classic 15 squares by 15 (`ClassicBoardLayout`)
    for 2 and 4 players, all of it covered by the yards, the track, and the centre, so `ground` shows only through gaps
    (Stone's mortar) or translucent squares; or for 3 and 5-8 players a round `StarBoardLayout` of an arm a seat, where
    `ground` (and `drawFinish`) are cut to the disc and show round the yards.
- **On a star board, every square is still drawn by `drawSquare`.** `drawBoardSquare` turns an arm's squares to the
    arm's angle (by its angle less whole quarter turns, so the classic board's and a straight arm's aren't turned), and
    stretches the three-seat board's six yard-side squares over their shape, cut to it. A yard there is
    `drawYardShape` (the outline, its court, and the spots, in pixels): every family draws its classic yard's art on the
    shape, its spots scaled by `spotScale` to the board's (Stone's slab through `drawBevelledShape`, Retro's through
    `steppedShape`). A home is `drawHomeTriangle` with three points, or five on the three-seat board. Render every
    family at 3, 5, and 8 seats after changing one.
- **Texture is seeded** (`Random(id.hashCode())`, or a square's position), so a board draws the same every time and
    renders compare byte for byte.
- **A grid style snaps to it.** Retro draws on a grid of `RETRO_PIXELS` to a square, including its stars, spots, and
    stepped homes (`encloses` tests each pixel's centre against the home's convex outline).
- **`drawHomeSpot`** is the disc a pawn that's home stands on: the court's colour ringed in the line colour, unless the
    family draws its own.

## Pawns

- **A family is a `GridPawnStyle`** (`ui/style/PawnFamilies.kt`). It draws the shadow and the movable ring, so every
    family stands on the same footprint, and the family draws its body on a `PawnGrid`: x and y from -1 to 1 of the
    radius, y down, the foot at about 0.85 and the top no higher than -1.
- **Always the player's colour.** A family changes shape and material, never whose a pawn is: shade the colour
    (`lit`), stain it (Peg), or make it translucent (Glass), but never swap it for another.
- **Outlines** are ink, round-joined (`outline`): a mitred join spikes at sharp corners, as the gem's girdle did.
- **Check the small sizes.** Pawns sharing a square are drawn at `SHARED_PAWN_RADIUS`, about half size. A detail that
    vanishes there is fine; one that turns to noise isn't.
- **A figure stands on something**: `drawPawnBase`, its own feet, or a `drawShadow` of its own footprint (the cube).
- **Not a mode's art.** A ghost is Haunted House's, so no pawn is one.
- **`BoardAndPawnArtTest`** draws every family: a pawn mostly its player's colour and within its footprint; a board
    showing each seat's colour on every layout, the same every time.
- **A family with several shapes** (Animal, Chess, Shapes, Monster, and Space) takes the shape as a constructor value, one colour of the family each.

## Drawing techniques that worked

- **Things must visibly rest on something.** Floating was the most repeated complaint. Use contact shadows, shadows
    shaped like the footprint (not a generic oval), and edges that meet at corners.
- **Give solids thickness** (an inner profile plus the edge between, like the Counter's rim), or they read as paper.
- **Natural variety.** Seed per face and per die with `naturalPatternSeed(value)` (it uses `LocalDieIndex`). Small
    variations in rotation, jittered outlines, shade and which facets catch the light read as organic.
- **Tilted discs are ovals.** Get the ellipse axes once from two projected in-plane directions (the eigenvalues of
    `M Mᵀ`), and draw rotated ovals. That's several times cheaper than polygons.
- **Text dice.** Copy the font's glyph metrics from fontTools (`BoundsPen`), lay the text out once at a fixed size, then
    scale it (`MathsDice.kt`). For bundling a font, see `app/licensing/README.md` and `PUBLISHING.md` (OFL; Reserved Font
    Names mean ship unmodified, or rename).
- **Recoloured.** Every `DiceStyle.recoloured(palette)` draws in the roll's colours. Multi-coloured pips all take
    `palette.pip`.

## Shared painters

A theme often shows up in more than one category (Stone dice and background; Circuit backgrounds). Paint it once, in a
function every category calls, rather than copying it. Already shared:

| Painter                                                                        | What                                                         | Used by                                        |
|--------------------------------------------------------------------------------|--------------------------------------------------------------|------------------------------------------------|
| `paintFlagstones` (`ThemedPainters.kt`)                                        | Flags in running bond, three textured and the rest stamped   | Stone background                               |
| `paintCircuitBoard`                                                            | Traces, pads and chips; `strength` fades it for a background | Circuit background                             |
| `paintPolkaDots`                                                               | Dots on a half-stepped grid                                  | Backgrounds                                    |
| `paintBricks`, `paintMarble`, `paintStone`, `paintPebbledHide`, `paintGlitter` | Textures                                                     | Dice and backgrounds of that name              |
| `drawGingham`, `drawStars`, `drawSparkle`                                      | Patterns                                                     | Gingham, Starry and Glitter art                |
| `drawSoftSpot` (`Patterns.kt`)                                                 | A glow fading to `Color.Transparent`                         | Every soft glow of the two-stop kind           |
| `Path.fourPointStar`                                                           | The hollow-sided star                                        | Starry background and dice                     |
| `starPath` (`BoardFamilies.kt`)                                                | A star of any number of points                               | The boards' safe-square marks, Starry's courts |
| `hexagonPoints`, `polygonPath`, `smoothPath`                                   | Outlines                                                     | Bestagon art, Honeycomb, Wireframe, Meadow     |
| `insetOutline`                                                                 | A rounded outline inset from the edge                        | Leather background                             |

- **Extract without changing a pixel.** Render every style before and after (the `render` skill's snapshot approach,
    one PNG per style) and compare the files byte for byte. Rendering is deterministic, so any difference is real.
- **Same maths, not just the same look.** `drawSoftSpot` fades to `Color.Transparent`, which dims the colour towards
    black as it fades. A glow that fades to its own colour at zero alpha (the Obsidian lava's) looks different, so it
    stays as it is.
- **Left alone because they only look alike:** the wavy grain of the Planks background and the Wood board, the
    Glitter background's flake dust, and the scanlines of the Glitch dice and background. Sharing them would take a
    lambda for each difference.

## Heavy art: paint once, keep it off the frame

- **The board** is a `PaintedSurface` under the pawns (`LudoBoard`): painted once, off the main thread, from only the style
    and the seats' colours, so a style may hold nothing that changes, and its texture is never redrawn on a move's frames.
    Pawns are live, a few dozen paths each.
- **Static, costly surfaces** elsewhere go through `drawCachedSurface(key) { ... }` (`Patterns.kt`). The key captures
    everything but size.
- **Live per frame**: only what moves (a background's waves, a die's sparkle). Say the count.
- **Measure** against an existing style in the same run, and measure first appearance separately.

## Shipping a style

1. **Add it, in two places in the same order.**
   - Its `StyleGroup` (or `StyleVariant`s in an existing one) in `game/style/StyleRegistries.kt`: ids, names and lock.
   - Its art in the matching `listOf(...)` of `ui/style/StyleCatalog.kt`: the class, or colours of an existing one, as
     `StyleArt(swatch, style)` or a helper. The catalogue joins the two by id and refuses a mismatch at load.
   - The id is the family's string key plus the colour's (`style_family_glitter` + `style_colour_gold` =
     `glitter_gold`), and it's permanent.
   - `StyleRegistryTest` (ids, locks) and `StyleCatalogTest` (art matches the registry) cover the invariants. Add tests
     for anything special.
2. **Render every state** for review (the `render` skill), and iterate. A board or pawn goes in a mid-game with all
    four colours, and a pawn on a close-up sheet as well: every colour, ringed, and at the shared-square size.
3. **Measure** frame cost if it draws every frame or paints anything heavy, and record it in `BENCHMARKS.md`.
4. **Accessibility.** The tile reads "Family, Colour", and the board, pawns, and die are spoken regardless of style.
    Say so.
5. **Note what later changes must know**: secret unlocks and easter eggs in `DESIGN.md` "Table art", reduced-motion
   behaviour in `UI.md`.
6. **Finish.** Delete scratch tests and debug colours, run the gate (`test` skill), and send the APK (`debug-apk`).
