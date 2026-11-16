# Ludo Plus: implementation plan

The phased plan for turning the DiceFive copy into Ludo Plus. Read it before any gameplay or feature work. Keep the
**Status** line of each phase current, and tick items off as they land (in the same commit as the work).

Phases are ordered so each one ends with a playable debug APK. Phases 0-5 build the 4-player classic game up to the
first release, 1.0.0. Phases 6-12 are the features that come after it, in the order below unless the maintainer
reorders them.

| Phase | What | Status |
| --- | --- | --- |
| 0 | Gut the dice game, rebrand the chrome | In progress |
| 1 | The Ludo rules engine (pure, tested) | Not started |
| 2 | The board and game screen | Not started |
| 3 | CPU opponents | Not started |
| 4 | Saving, Game Over, Statistics, and the Leaderboard | Not started |
| 5 | Rules, tutorial, sound, and polish (to 1.0.0) | Not started |
| 6 | Achievements | Not started |
| 7 | Styles | Not started |
| 8 | 2-8 players | Not started |
| 9 | Game modes and modifiers | Not started |
| 10 | Google Play: Play Console, Play Games, and the store listing | Not started |
| 11 | In-app purchases | Not started |
| 12 | Netplay | Not started |

## Decisions so far (2026-11-16)

- **Name.** Ludo Plus. "Frustration" (the working title) is Hasbro's UK trademark and is banned (`CLAUDE.md`, "Banned
    words"). Package `net.zodac.ludo`, repo `github.com/zodac/LudoPlus`.
- **Releases.** VERSION 0.1.0. CI runs every check, but publishes nothing until VERSION reaches 1.0.0.
- **Languages.** en-GB only until a later phase. Every string still goes through Compose resources.
- **Brand.** DiceFive's dark-only M3 tonal system, re-seeded: primary violet `#9C6BFF` (not a player colour, so it
    never reads as "your turn"), secondary navy `#1E2A4A`, tertiary ivory `#F1E4C3` (the board's cream squares). Sora
    stays the brand font.
- **Leaderboard.** It ranks **fastest wins**: the fewest turns to bring all four pawns home, per mode, with more
    captures winning a tie. Statistics keeps games, wins, win rate, streaks, captures made, and pawns sent home.
- **Never ads** (inherited from DiceFive).

## The classic rules (what Phase 1 implements)

Ludo has no single official rulebook, and sources disagree on the details. These are the most common rules, and they
are what **Classic** plays. Anything marked *modifier* is a common house rule that Phase 9 offers as an option, off by
default.

Sources: [Wikipedia, "Ludo"](https://en.wikipedia.org/wiki/Ludo),
[rarepike.com rules for beginners](https://rarepike.com/ludo/articles/rules-for-beginners/),
[rarepike.com glossary](https://rarepike.com/ludo/articles/glossary/),
[rarepike.com variants](https://rarepike.com/ludo/articles/variants/), and
[groupgames101.com](https://groupgames101.com/ludo-game-rules).

### The board

- A 15x15 cross. Each player has a coloured corner **yard** holding four pawns. Colours run red, green, yellow, and
    blue, clockwise.
- **The track** is 52 squares around the cross, shared by everyone. Each colour has a **start square** on the
    track, just outside its yard.
- **The home column** is 5 squares of the player's own colour, leading from the track into the centre. The centre
    triangle is **home**. Only that colour may enter its column, so pawns there are always safe.
- **Safe squares.** There are 8: the four start squares and four star squares, each 8 squares on from a start
    square. A pawn on a safe square can't be captured.
- A pawn's journey is 57 steps: 51 squares round the track from its start square, 5 up its home column, and 1 into
    home.

### Turns

- **Who starts.** Each player rolls once, and the highest roll starts (ties roll again). Play then goes clockwise.
- **A turn** is one roll of a single six-sided die, then one move of one pawn by exactly that many squares. The
    player chooses which pawn.
- **Leaving the yard.** Only a **6** brings a pawn out of the yard, onto its start square. That move uses the whole
    6.
- **Rolling a 6** gives another roll after the move.
- **Three 6s in a row** end the turn: the third 6 isn't moved. This is the commonest house rule, so it's on in
    Classic. Turning it off is a modifier.
- **No legal move** (say, everything in the yard and no 6): the turn passes.
- **Exact roll home.** A pawn can't overshoot home. A roll too big for it can't move that pawn. If no other pawn can
    move, the turn passes.

### Landing

- **Capture.** A pawn that ends its move on a square holding one opponent pawn, off a safe square, sends that pawn
    back to its owner's yard.
- **Sharing.** Pawns of the same colour may share any square. Pawns of different colours may share a safe square.
- **Passing.** Pawns may pass over any pawn. Classic has no blockades.

### Winning

- The first player to bring all four pawns home wins. By default the others play on for 2nd, 3rd, and last place.
    A "stop at first winner" setting comes in Phase 4.

### Modifiers for Phase 9 (common house rules)

- Bonus roll for a capture, and for bringing a pawn home.
- Leave the yard on a 1 or a 6, or on any roll while no pawn is out.
- Three tries to roll a 6 while every pawn is in the yard.
- Blockades: two pawns of one colour on a square can't be passed or captured (from Parcheesi, also called Pachisi).
- Three 6s in a row off (the run of 6s never ends).
- Must capture when you can.
- A pawn that is captured on its own start square is safe (start squares become one-colour safe squares).
- Two dice (Parcheesi-style: use each die for a different pawn, or both for one).

## Phase 0: Gut the dice game, rebrand the chrome

**Goal:** a building app with DiceFive's chrome and infrastructure, Ludo Plus's name and colours, and no dice game.
The game screen is a placeholder. Tests may be red along the way (the maintainer agreed, 2026-11-16); the phase ends
green.

**Keep (the skeleton).** `ui/common`, `ui/theme`, `ui/menu`, `ui/settings` (graphics, about, licences, progress
files), `ui/setup` (players, names, colours, CPU), `navigation`, `platform`, `app` (container, breadcrumbs, crash
pages), `data/settings`, `data` failures/DataStore/JSON, `online` and `purchases` (their seams), the Room scores
database (rebuilt in Phase 4), the achievements engine and list UI (emptied, refilled in Phase 6), the Styles screen
and style framework (re-cast in Phase 7), the D6 die art (the Ludo die), the backgrounds, the build-logic, the
licensing guard, and the Baseline Profile set-up.

**Remove (the dice game).** The checklist below. Delete a test with the code it covers.

- [ ] `model/`: the dice-only types (`Die`, `DieColour`, `ScoreCategory`, `SectionBonus`, `HitList`,
    `RollModifiers`, `UnluckyDice`, `TurnTimer`, the Easter eggs, `FlowerpotGrowth`, `ModePage`). `GameMode`,
    `PlayerConfig`, `PlayerColour`, `PlayerType`, `Difficulty`, `PlayerName`, and `StoredId` stay, cut down.
- [ ] `game/`: the dice engine, scoring, CPU strategies, tie-break, Hit List, perfect-play table, and the tutorial
    script. Keep `Clock`, `LocalHour`, and `AiNameGenerator`.
- [ ] `ui/game/`: the scorecard, tray, cup, shake detector, totals, undo, coach card, and tutorial guide. Leave a
    placeholder `GameScreen` reached from New Game, and keep `LeaveGameConfirmation`, `Haptics`, and `SoundEffects`
    (emptied).
- [ ] `ui/style/`: the cups, mats, score frames and boxes, and the D20/Maths/Poker/Tally/Mahjong dice (not D6).
    `GameVisualTheme` becomes the board/pawn/die/background theme seam. Drop the `ludo_d20` and `ludo_maths_*` fonts,
    their subsetting scripts, and their licensing records.
- [ ] `app/audio/`: the dice clips and their Freesound licence records. Keep `NormalizeOggAudioTask` (Ludo's sounds
    come in Phase 5). Check the build copes with no clips.
- [ ] Achievements: empty the catalog and the dice event rules. Keep the engine, persistence, banners, and list
    screen.
- [ ] Leaderboard and Statistics: keep the screens, and strip the dice columns down to placeholders. The Room schema
    restarts at version 1 (nothing has shipped).
- [ ] Rules screen: replace the dice rules text with the classic Ludo rules above.
- [ ] Strings: delete every dice string, and the `values-es`/`values-ar` folders (Compose resources and
    `app/android` res). Move `StringChecks` to guard the Ludo trademarks in `CLAUDE.md`.
- [ ] Theme: re-seed `ui/theme/Color.kt` (violet/navy/ivory, generated tonal palettes, not hand-nudged). The player
    colours are red, green, yellow, and blue. Render the menu and settings for approval (`render` skill).
- [ ] Launcher icon: a placeholder pawn on the navy background (`scripts/generate-launcher-dice.py` becomes the icon
    generator).
- [ ] Docs: rewrite `DESIGN.md`'s rules, modes, CPU, and table-art sections for Ludo. Retire `UI_BOARD.md`,
    `GAME_MODES_AUDIT.md`, `TUTORIAL.md`, and `STYLE_ART.md`'s dice content, or mark it as to-come. Bring the
    `commit` skill's categories up to date (`[Board]` for `[Gameplay]`'s scorecard sense, and drop `[Game Mode]`
    until Phase 9).
- [ ] Store-assets and Baseline Profile journeys: cut them to what exists (menu, setup, placeholder game). The README
    gallery comes back at 1.0.0.
- [ ] Phase exit: `./gradlew` full gate green (the `test` skill), with the debug APK sent.

## Phase 1: The Ludo rules engine

**Goal:** the complete classic 4-player game as pure Kotlin in `model/` and `game/`, unit-tested, with no UI. Keep
it independent of board size from the start (Phase 8), and keep every rule a `GameMode` field (`GAME_MODES.md`'s
"mode is data" rule).

- [ ] `BoardGeometry`: track length, start offsets per seat, star squares, home-column length. Classic has 52 / 13
    per seat / 5. A position is either `Yard`, `Track(n)` (relative to the pawn's own start), `HomeColumn(n)`, or
    `Home`.
- [ ] `GameState`: seats, pawns, current player, roll and sixes-in-a-row, phase (`AwaitingRoll`, `AwaitingMove`,
    `Finished`), finishing order, turn count, and a captures tally. Immutable, with `copy`.
- [ ] `LudoEngine`: `roll(die)`, `legalMoves()`, `move(pawn)`, and passing on no legal move, implementing every rule
    above. The die comes from an injected `Random`, so tests are deterministic.
- [ ] Opening roll-off for first player.
- [ ] Tests: each rule above gets one test or more, plus property tests (pawn count is conserved, positions stay in
    range, a game always ends) over thousands of seeded random games.
- [ ] Spoken positions: a pure `describePosition` ("Red pawn 2, 14 squares from home") for TalkBack in Phase 2.

## Phase 2: The board and game screen

**Goal:** a playable pass-and-play game on a device. Visual work goes through `render` first.

- [ ] Board drawn in Compose `Canvas` from `BoardGeometry`: the cross, the coloured yards and home columns, the stars,
    the centre. It scales to phones and tablets, in portrait and landscape (`UI.md`, "Landscape pages").
- [ ] Pawns: a stacked count badge when they share a square, the movable ones highlighted after a roll, a tap to
    move, and a step-by-step move animation that respects reduced motion (`UI.md`, "Reduced motion").
- [ ] The die: reuse DiceFive's D6 art and roll animation (`ui/style`'s dice), tapped to roll. Shake to roll stays a
    setting.
- [ ] Player header: whose turn it is, each player's pawns home, and the CPU icon.
- [ ] Capture feedback: haptics and a short animation of the pawn returning to its yard.
- [ ] Accessibility: each square is a node that says what's on it, and each movable pawn is an action ("Move red
    pawn 2 by 4, captures green"), plus a "Roll" action. TalkBack must be able to play a whole game (`UI.md`, the
    checklist).
- [ ] Leave-game confirmation and back handling, kept from DiceFive.

## Phase 3: CPU opponents

- [ ] `CpuStrategy` per difficulty, reusing DiceFive's CPU-pacing UI (`CpuTurns`, `GamePacing`).
    - Easy: a random legal move.
    - Medium: a scored heuristic (capture > leave the yard > reach safety > advance the furthest pawn > avoid ending in
        reach of an opponent).
    - Hard: an expectimax one ply deep over the opponents' next rolls, with tuned weights.
- [ ] A tournament test: Hard beats Medium beats Easy over N seeded games, by a margin the test asserts.

## Phase 4: Saving, Game Over, Statistics, and the Leaderboard

- [ ] Save and continue a game in progress (DataStore, as DiceFive's `data/game`), resilient to process death.
- [ ] Game Over: the finishing order, turns, captures, and the celebration (reused).
- [ ] Room schema v1: a game record per finished game (mode, players, placements, turns, captures, duration).
- [ ] Leaderboard: fastest wins per mode (fewest turns, then more captures).
- [ ] Statistics per player: games, wins, win rate, current and best streaks, captures, times captured, and pawns
    home, overall and per mode.
- [ ] A "stop at first winner" setting.
- [ ] Progress files (github flavour) carry the new records.

## Phase 5: Rules, tutorial, sound, and polish (to 1.0.0)

- [ ] Rules screen: the classic rules with board diagrams.
- [ ] Guided tutorial (`TUTORIAL.md`'s framework): a scripted first game.
- [ ] Sounds (licensed, recorded in `app/licensing`, normalised): roll, move step, capture, home, win.
- [ ] Baseline Profile journeys and benchmarks for the board (`BENCHMARKS.md`): 60fps while pawns move.
- [ ] Store-assets generator scenes for Ludo, README gallery, and `assets/full-description.txt`.
- [ ] Privacy policy re-read against the app.
- [ ] Set VERSION to 1.0.0, which turns publishing on: the first GitHub release.

## Phase 6: Achievements

- [ ] Achievement catalog for Ludo, with permanent ids and titles of 38 characters or less: first win, captures (1,
    100, 1,000), comeback wins, a win with no pawn captured, three captures in a turn, the full lap, streaks, and
    hidden ones.
- [ ] Audit them against modes and modifiers (`GAME_MODES_AUDIT.md`).
- [ ] Unlocks are tested from the engine's events, not the UI.

## Phase 7: Styles

- [ ] The style categories, re-cast for Ludo: **board**, **pawns**, **die** (DiceFive's D6 styles, kept),
    **background** (kept). Unlocks are tied to achievements, as in DiceFive.
- [ ] The Styles screen's previews show a corner of the board with pawns.
- [ ] `STYLE_ART.md` rewritten for board and pawn art.

## Phase 8: 2-8 players

There is no standard board for more than four. This phase designs one.

- [ ] 2 and 3 players on the classic board (empty yards, or one player on two colours as a setting).
- [ ] 5-6 players: a six-armed board (each arm 3 wide, 13 squares a seat). 7-8 players: an eight-armed board.
    `BoardGeometry` already allows any seat count (Phase 1). Render each before building.
- [ ] Teams (2v2, 3v3, 4v4: partners across the board, both sets home to win) as a setting.
- [ ] Player colours past four: purple, orange, cyan, and pink (checked for colour-blind contrast, `UI.md`, "Player
    colours").
- [ ] The CPU, Statistics, and the Leaderboard keyed by player count.

## Phase 9: Game modes and modifiers

- [ ] Modifiers: the house rules above, mixed in on the setup screen. A game with any on stays off the Leaderboard
    (DiceFive's rule).
- [ ] Modes: rule sets that are data on `GameMode`, never `if (mode == X)`, such as two dice, a pawn-count
    variant (2 or 3 pawns, quick), a short-track Quick mode, and power-up squares.
- [ ] Custom game modes: a player-built set of modifiers, saved and named.

## Phase 10: Google Play

- [ ] Create the Play Console project, the signing key and upload key, and the CI secrets (`PUBLISHING.md`).
- [ ] Play Games Services: achievements, the fastest-win leaderboards, cloud save, and sign-in (`PLAY_GAMES.md`).
- [ ] Store listing, data-safety form, and content rating. Internal testing track, then production.

## Phase 11: In-app purchases

- [ ] The "Unlock all styles" purchase, from DiceFive (`PURCHASES.md`), wired to Ludo Plus's Play Console.
- [ ] Any further purchases: decide what they are first, since purchasable styles must stay earnable through play
    unless the maintainer decides otherwise.

## Phase 12: Netplay

The biggest change: it ends "no account, no network". Decide the approach before building it.

- [ ] Choose the transport. Google Play Games' real-time multiplayer API was shut down in 2020, so it's one of:
    - a hosted relay (a small server, or a managed realtime database), which works over the internet, but costs money,
        needs moderation, and changes the privacy policy;
    - Nearby Connections, which works only in the same room, needs no server, and keeps privacy simple;
    - both, with Nearby first.
- [ ] The engine is already deterministic (Phase 1), so the host is the authority: it rolls, and the clients send
    their moves and get the state back.
- [ ] Lobby, invites, reconnect, turn timeouts, and the CPU stepping in for a player who drops out.
- [ ] Privacy policy, data-safety form, and the `CLAUDE.md` aims updated.
