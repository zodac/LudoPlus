# Ludo Plus: implementation plan

The phased plan for turning the DiceFive copy into Ludo Plus. Read it before any gameplay or feature work. Keep the
**Status** line of each phase current, and tick items off as they land (in the same commit as the work).

Phases are ordered so each one ends with a playable debug APK. Phases 0-5 build the 4-player classic game up to the
first release, 1.0.0. Phases 6-12 are the features that come after it, in the order below unless the maintainer
reorders them.

| Phase | What | Status |
| --- | --- | --- |
| 0 | Gut the dice game, rebrand the chrome | Done |
| 1 | The Ludo rules engine (pure, tested) | Done |
| 2 | The board and game screen | Done |
| 3 | CPU opponents | Done |
| 4 | Saving, Game Over, Statistics, and the Leaderboard | Done |
| 5 | Rules, tutorial, sound, and polish (to 1.0.0) | In progress |
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
- **Gutting.** Interim red builds were fine while gutting (the maintainer, 2026-11-16).
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
- **Rolling a 6** gives another roll after the move. A 6 with no legal move passes the turn like any other roll
    (the maintainer, 2026-11-17), and a 6 that brings the player's last pawn home gives no roll.
- **A capture, or a pawn brought home,** also gives another roll (one, however many pawns are captured), but not the
    move that brings the player's last pawn home. Classic since 2026-11-17 (the maintainer, for the tutorial): they
    were Phase 9 modifiers. A roll earned this way doesn't break or add to a run of 6s; it is part of the same turn.
- **Three 6s in a row** end the turn: the third 6 isn't moved. This is the commonest house rule, so it's on in
    Classic. Turning it off is a modifier.
- **No legal move** (say, everything in the yard and no 6): the turn passes.
- **Exact roll home.** A pawn can't overshoot home. A roll too big for it can't move that pawn. If no other pawn can
    move, the turn passes.

### Landing

- **Capture.** A pawn that ends its move on a square holding an opponent's pawn, off a safe square, sends it back
    to its owner's yard. Two pawns of one colour can share a square off a safe square, and landing there captures both
    (Classic has no blockades; the maintainer, 2026-11-17).
- **Sharing.** Pawns of the same colour may share any square. Pawns of different colours may share a safe square.
- **Passing.** Pawns may pass over any pawn. Classic has no blockades.

### Winning

- The first player to bring all four pawns home wins. By default the others play on for 2nd, 3rd, and last place.
    A "stop at first winner" setting comes in Phase 4.

### Modifiers for Phase 9 (common house rules)

- No bonus roll for a capture, or for bringing a pawn home (`GameRules.rollAgainOnCapture`, `rollAgainOnHome`).
- Leave the yard on a 1 or a 6, or on any roll while no pawn is out.
- Three tries to roll a 6 while every pawn is in the yard.
- Blockades: two pawns of one colour on a square can't be passed or captured (from Parcheesi, also called Pachisi).
- Three 6s in a row off (the run of 6s never ends).
- Must capture when you can.
- A pawn that is captured on its own start square is safe (start squares become one-colour safe squares).
- Two dice (Parcheesi-style: use each die for a different pawn, or both for one).

## Phase 0: Gut the dice game, rebrand the chrome

**Goal:** a building app with DiceFive's chrome and infrastructure, Ludo Plus's name and colours, and no dice game.
The game screen is a placeholder.

**What was kept (the skeleton).** `ui/common` (scaffold, dialogs, pickers, scrollbars, page cover, crash and failure
pages, the drifting backdrop), `ui/theme`, `ui/menu`, `ui/settings` (switches, Graphics, About, Licences),
`ui/setup` (four seats: name, Player/CPU, difficulty, colour), `navigation`, `platform`, `app` (container,
breadcrumbs), `data` (DataStore, failures, JSON), the D6 pips (`ui/style/Pips.kt`), the sound pipeline and four
clips, build-logic, the licensing guard, CI, scripts, and the Claude set-up.

**What was removed, and comes back from commit `5423b6c`.** That commit is the renamed copy before the gut: each
removed feature is restored from it in its phase (`git show 5423b6c:<path>`), not written again.

| Removed | Comes back in |
| --- | --- |
| The dice engine, scoring, CPU strategies, tie-break, and tutorial script (`game/`, most of `model/`) | Never (Ludo's own in Phases 1 and 3); the CPU pacing UI in Phase 3 |
| The scorecard, tray, cup, totals, undo, leave-game confirmation, haptics, and sound hooks (`ui/game/`) | Phase 2 (leave confirmation, haptics, sound hooks) |
| The dice, cup, mat, frame, and box art (`ui/style/`, `game/style/`), the Styles screen (`ui/styles/`) | Phase 2 (the D6 die), Phase 7 (the style framework and screen) |
| Room, the Leaderboard, and Statistics (`data/scores/`, `ui/scores/`, `ui/statistics/`) | Phase 4 |
| Saved games and Continue (`data/game/`, `ResumeAttempt`) | Phase 4 |
| Progress files (github flavour) | Phase 4 |
| Achievements: engine, catalog, banners, and list (`data/achievements/`, `ui/achievements/`, `game/Achievement*`) | Phase 6 |
| The Baseline Profile journey and benchmarks (`app/baselineprofile`), and its CI job | Phase 5 |
| The guided tutorial | Phase 5 |
| Play Games, cloud save, and online sync (`online/`, `src/play`) | Phase 10 |
| Play Billing and "Unlock all styles" (`purchases/`) | Phase 11 |
| Spanish and Arabic | A later languages phase |

- [x] Dice code, its tests, and its resources removed (fonts, the perfect-play table, the cup and chicken clips, and
    their licence records).
- [x] Settings cut to sound, vibration, confirm leaving, and Graphics. The motion groups are Ludo's: Background,
    Menus & pages, Achievement banners, Celebrations, Die roll, and Pawn moves.
- [x] Setup form: four seats (seat 1 always a player), names, Player/CPU, difficulty, colour, and the mode (Classic).
- [x] Placeholder game screen: the players, and a note that the board is coming. Back goes to the menu.
- [x] Rules screen: the classic rules above, a card a part.
- [x] Strings: every unused one deleted; the es/ar folders removed. `StringChecks` guards the Ludo trademarks.
- [x] Theme re-seeded (violet `#9C6BFF`, navy `#1E2A4A`, ivory `#F1E4C3`, generated with material-color-utilities).
    Player colours red, green, yellow, and blue.
- [x] Placeholder launcher icon: an ivory pawn on the brand navy.
- [x] Baseline Profile journey (`app/baselineprofile`) and its CI job removed: premature with no app to profile. The
    upgrade check runs only from the second release on.
- [x] Docs that still describe DiceFive say so at their top; `UI.md`'s colour, player-colour, mode-colour, and icon
    notes rewritten.
- [x] Renders of the menu, setup, and settings in the new colours, for the maintainer's approval (`render` skill).
    Approved 2026-11-17, with the menu's drifting dice to become a Ludo motif (Phase 2).
- [x] The full gate green (the `test` skill), and the debug APK sent.

## Phase 1: The Ludo rules engine

**Goal:** the complete classic 4-player game as pure Kotlin in `model/` and `game/`, unit-tested, with no UI. Keep
it independent of board size from the start (Phase 8), and keep every rule a `GameMode` field (`GAME_MODES.md`'s
"mode is data" rule).

- [x] `BoardGeometry` (`model/`): track length, start offsets per seat, star squares, home-column length. Classic
    has 52 / 13 per seat / 5. A pawn is stored as its progress from its start square (-1 in the yard, 56 home), and
    read as a `PawnPosition`: `Yard`, `Track(n)` (relative to the pawn's own start), `HomeColumn(n)`, or `Home`.
- [x] `GameRules` (`model/GameMode.kt`): every rule a mode or modifier can change (board, pawns, die faces, the rolls
    that leave the yard and roll again, the three-6s limit). A `GameMode` holds its rules; a `GameState` carries the
    rules it plays by, so Phase 9's modifiers change a copy.
- [x] `GameState` (`game/`): players (seat, pawns, turns, captures, times captured), current player, the run of 6s,
    phase (`AwaitingRoll`, `AwaitingMove` with its legal moves, `Finished`), finishing order, and the last event
    (`Rolled`, `NoMove`, `RollAgainLimit`, `Moved`) for the screen to show and say. Immutable, with `copy`.
- [x] `LudoEngine` (`game/`): pure `applyRoll`, `legalMoves`, and `move`, and `roll` drawing from an injected
    `Random`, so tests are deterministic. A roll with no legal move passes the turn itself.
- [x] Opening roll-off for first player (`RollOff`), keeping each round's rolls for the screen.
- [x] Tests: each rule above (`LudoEngineTest`, `BoardGeometryTest`, `RollOffTest`), plus property tests over 2,000
    seeded random games of 2-4 players (`LudoEnginePropertyTest`: pawn count, positions in range, no two colours off a
    safe square, capture tallies agree, every game ends with every place decided).
- [x] Spoken positions: `pawnSpoken` (`ui/game/PawnSpeech.kt`, "Red pawn 2, 14 squares from home"), with the pure
    `stepsToHome` in `model/`, for TalkBack in Phase 2.

## Phase 2: The board and game screen

**Goal:** a playable pass-and-play game on a device. Visual work goes through `render` first.

- [x] Board drawn in Compose `Canvas` (`ui/game/LudoBoard.kt`) from `BoardLayout`, the classic 15x15 cross mapped
    from `BoardGeometry` (`BoardLayoutTest`): the yards, start squares, home columns, and home triangles in the
    seated players' colours, and the stars. Portrait and landscape, phones and tablets. Look approved 2026-11-17.
- [x] Pawns (`BoardPieces.kt`): true pawns, standing, seen from the side (head, collar, flared body, base), shaded in
    the player's colour. One colour's pawns on a square drawn as one with a count, colours sharing a safe square a
    quarter each, each pawn home seen on its own spot in a line along its triangle's base (the maintainer,
    2026-11-17), the movable ones ringed in violet, a tap within 1.6 squares picking the nearest, and a
    square-by-square move (110ms a square) under the Pawn moves motion group.
- [x] The die (`GameDie.kt`): DiceFive's ivory bevelled die, tapped to roll, tumbling through faces as it turns
    under the Die roll motion group. Shake to roll waits for its setting (Phase 5).
- [x] Player cards: name, CPU icon, a dot per pawn home, the current player outlined in their colour, and their place
    once finished. Lines under the die say what just happened and what to do now; the end shows the winner.
- [x] Capture feedback: the captured pawn slides back to its yard, with a buzz (`Haptics.kt`); a tick for a move,
    the die's landing sound, a move click, and the fanfare (`SoundEffects.kt`, restored from `5423b6c`).
- [x] Accessibility (`BoardSemantics.kt`, `BoardSemanticsTest`): every yard, track square (numbered 1-52 from red's
    start), home-column square, and home is a node saying what is on it; a square with a movable pawn is a button
    whose action names the move ("Move Red pawn 3 6 squares, capturing Green pawn 1"); the prompt line offers every
    move as a custom action; the die is a "Roll" button.
- [x] Leave-game confirmation (restored from `5423b6c`, `LeaveGameTest`): back asks first while Settings' "Confirm
    leaving game" is on. A CPU's turn waits while the game is off screen or under the dialog.
- [x] The menu backdrop: the drift's odd faces are pawns, so it is a die among pawns (maintainer, 2026-11-17).

## Phase 3: CPU opponents

- [x] `CpuStrategy` per difficulty (`game/CpuStrategy.kt`, `CpuStrategyTest`), played by the view model.
    - Easy: a random legal move.
    - Medium: a scored heuristic (capture > reach home > leave the yard > reach safety > escape a threat > advance
        the furthest pawn; ending in reach of an opponent costs).
    - Hard: an expectimax one ply deep: every pawn's worth for and against, less the expected loss over each
        opponent's next roll, plus the expected capture on its own. Weights tuned 2026-11-17 over 1,000 two-player
        games a setting against Medium.
- [x] Pacing (`GamePacing`, `GamePacingTest`): a CPU thinks 500ms before each roll and move, and first waits out the
    die's tumble or the pawn's move and capture slide, unless those motion groups are off. DiceFive's `CpuTurns` was
    about its dice cup, so only its pacing idea came back.
- [x] A tournament test (`CpuTournamentTest`, 1,000 two-player games a pairing, seeded apart from the tuning): Medium
    beats Easy 89%, Hard beats Medium 57%, Hard beats Easy 93%; each must beat the one below at least 55%. With rolls
    again for captures and pawns home (2026-11-17): 90%, 57%, and 94%.

## Phase 4: Saving, Game Over, Statistics, and the Leaderboard

- [x] Save and continue a game in progress (`data/game`: `SavedGameJson`, `InProgressGameRepository`), resilient to
    process death. Saved as it starts and after every roll and move, cleared when it finishes; the menu's Continue (and
    a game screen restored with no game) brings it back. A save that can't be read is removed. Play time
    (`StartedGame.playMillis`) counts only while the game is on screen and not under a dialog. DiceFive's cloud-save
    stamps and its crash-page removal of a save that can't be drawn (`ResumeAttempt`) were left in `5423b6c`: the
    stamps come back with cloud save (Phase 10).
- [x] Game Over (`GameOverScreen`, `GameOverTest`): once the last move has played out, the winner's card, then each
    placing with its finish time ("Finished in 18m 42s", the last player's when the game ended), turns, captures, and
    times captured (the last, their pawns home in place of turns). Review Board, Main Menu, and Play Again (the same
    players). A person's win gets DiceFive's fanfare and gold fireworks. Each player's finish time
    (`StartedGame.finishMillis`) is saved with the game. Approved from renders 2026-11-17.
- [x] Room schema v1 (`data/records`: `GameResult`, `GameResultDao`, `AppDatabase`, `GameRecordRepository`): a row
    per person per finished game (CPUs aren't recorded, as in DiceFive): mode, player count, place, finish time, turns,
    captures, times captured, pawns home, the game's length, and whether it was played unmodified. Written once, as the
    game finishes. A database that can't be opened shows DiceFive's failure page. `GameResultDaoTest` runs the real
    queries on SQLite; the shared tests use `InMemoryGameResultDao`.
- [x] Leaderboard (`ui/records/LeaderboardScreen`): the mode's fastest wins (fewest turns, then more captures), the
    top three on gold, silver, and bronze pills; two lines a row where a large font leaves a name no room. The top 100
    for now: paging and a mode picker come with more modes (Phase 9).
- [x] Statistics per player (`ui/records/StatisticsScreen`): games, wins, win rate, current and best streaks, fewest
    turns to win, captures, times captured, and pawns home. Added up overall; `PlayerStatistics.modes` has them per
    mode, for a mode picker once there's more than one. Both screens approved from renders 2026-11-17
    (`RecordsScreensTest`). DiceFive's "Time played" line came back at the top of Statistics (the maintainer,
    2026-11-17): `PlayTimeTracker` counts the time the app is in front, saved under the settings key `play_seconds`
    (DiceFive kept it with the achievements' counters, which Phase 6 can move it to).
- [x] A "stop at first winner" setting (Settings, off by default): `GameRules.stopAtFirstWinner`, set from it when a
    game starts and saved with the game. The game ends when the first player has every pawn home; the rest are placed
    by how far their pawns have come, a tie to who plays sooner after the winner, each finishing as the game ends.
- Progress files (github flavour) moved to Phase 10 (the maintainer, 2026-11-17): they're built on DiceFive's cloud
    save format and merge, which Phase 10's cloud save restores.

## Phase 5: Rules, tutorial, sound, and polish (to 1.0.0)

- [ ] Review the game screen in landscape with the maintainer, once every phase to 1.0.0 is in (asked 2026-11-17).
- [x] Rules screen: the classic rules with board diagrams. DiceFive's tabbed pages came back (`RulesScreen`, `RulesContent`,
    the maintainer, 2026-11-17): How to Play, The Board, Your Turn, Captures, and Winning, each but the first with a
    diagram cropped from the real board (`RulesDiagram`), one image to TalkBack. The group row (Gameplay, Modes,
    Modifiers) shows once Phase 9 brings a second group. Approved from renders 2026-11-17.
- [x] Guided tutorial (`TUTORIAL.md`): the player against Green in four scripted parts, as the maintainer asked
    (2026-11-17): getting out on a 6 (with its roll again), a capture, a pawn home, each with its bonus roll, then
    winning after a roll too big. A coach card in place of the player cards, back and forward between parts, nothing
    kept. Opens from Rules' How to Play and New Game's link (until a first game is finished). Approved from renders
    2026-11-17.
- [x] Sounds (licensed, recorded in `app/licensing`, normalised): roll (DiceFive's die landing), a step for each square
    a pawn moves, capture, home, and win (DiceFive's fanfare). The three new clips are CC0 from Freesound, picked by the
    maintainer 2026-11-17; the hold and unhold clicks (CC-BY) went.
- [ ] Baseline Profile journeys and benchmarks, restored from `5423b6c` (`app/baselineprofile`, the `baseline-profile`
    job in `android-release.yml`) and written for Ludo's screens (`BENCHMARKS.md`): 60fps while pawns move.
    - [x] The module, the startup and pawn-move benchmarks, Ludo's journey, and the CI job (from 1.0.0) are back
        (2026-11-17). The journey compiles in the lint job.
    - [ ] Run the journey on an emulator (the sandbox's `emulator` skill): written on a host without one, so not yet run.
    - [ ] `PawnMoveBenchmark` on a phone: frameDurationCpuMs P90 under 16ms.
- [x] Store-assets generator scenes for Ludo, README gallery, and `assets/full-description.txt`: six screenshots (phone
    and 10-inch tablet) from staged games and a seeded history, in the README from the `readme-assets` release
    (approved 2026-11-17). The icon, feature graphic, and promo video move to Phase 10 with the Play listing (the
    maintainer, 2026-11-17).
- [x] Privacy policy re-read against the app, and rewritten for 1.0.0 (approved 2026-11-17): no sign-in, cloud save,
    purchase, or progress files yet, and nothing leaves the device but a crash report the player posts. It must be
    updated again before Phase 10 or 11 ships.
- [ ] Set VERSION to 1.0.0, which turns publishing on: the first GitHub release. **The maintainer makes this change by
    hand, never Claude** (the maintainer, 2026-11-17): VERSION stays 0.1.0 until then. When they do, restore
    `readme-assets.yml`'s push trigger with it.

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
- [ ] The store listing's hi-res icon, feature graphic (and the README's GIF), and promo video: DiceFive's generators
    are in `5423b6c`; the script keeps their steps, refusing those parts until then (`STORE_ASSETS.md`).
- [ ] Play Games Services: achievements, the fastest-win leaderboards, cloud save, and sign-in (`PLAY_GAMES.md`).
- [ ] Progress files (github flavour), restored from `5423b6c` with the cloud save format they share
    (`online/CloudSave*`, `ProgressFileCodec`, `ProgressTransfer`, the Settings card, `AndroidProgressFiles`, and the
    `PROGRESS_FILE_KEY` CI secret): settings, the saved game, and the game records (moved from Phase 4, 2026-11-17).
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
