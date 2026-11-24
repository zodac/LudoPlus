# LudoPlus: implementation plan

The phased plan for turning the DiceFive copy into LudoPlus. Read it before any gameplay or feature work. Keep the
**Status** line of each phase current, and tick items off as they land (in the same commit as the work).

Phases are ordered so each one ends with a playable debug APK. Phases 0-5 build the 4-player classic game up to the
first release, 1.0.0. Phases 6-12 are the features that come after it, in the order below unless the maintainer
reorders them. Phase 13 waits for the launch: the production release at the end of Phase 10.

| Phase | What | Status |
| --- | --- | --- |
| 0 | Gut the dice game, rebrand the chrome | Done |
| 1 | The Ludo rules engine (pure, tested) | Done |
| 2 | The board and game screen | Done |
| 3 | CPU opponents | Done |
| 4 | Saving, Game Over, Statistics, and the Leaderboard | Done |
| 5 | Rules, tutorial, sound, and polish (to 1.0.0) | In progress |
| 6 | Achievements | Done |
| 7 | Styles | Done |
| 8 | 2-8 players | Done |
| 9 | Game modes and modifiers | Done |
| 10 | Google Play: Play Console, Play Games, and the store listing | In progress |
| 11 | In-app purchases and ads | In progress |
| 12 | Netplay | In progress |
| 13 | Remote netplay: phones in different places | Planned (after the launch) |

## Decisions so far (2026-11-16)

- **Name.** LudoPlus. "Frustration" (the working title) is Hasbro's UK trademark and is banned (`CLAUDE.md`, "Banned
    words"). Package `net.zodac.ludoplus`, repo `github.com/zodac/LudoPlus`.
- **Gutting.** Interim red builds were fine while gutting (the maintainer, 2026-11-16).
- **Releases.** VERSION 1.0.0 (2026-11-18): every push to `main` that changes what an app ships cuts a release.
- **Languages.** en-GB only until a later phase. Every string still goes through Compose resources.
- **Brand.** DiceFive's dark-only M3 tonal system, re-seeded: primary violet `#9C6BFF` (not a player colour, so it
    never reads as "your turn"), secondary navy `#1E2A4A`, tertiary ivory `#F1E4C3` (the board's cream squares). Sora
    stays the brand font.
- **Leaderboard.** It ranks **fastest wins**: the fewest turns to bring all four pawns home, per mode, with more
    captures winning a tie. Statistics keeps games, wins, win rate, streaks, captures made, and pawns sent home.
- **Ads**: one banner in the play app, removable by purchase (the maintainer, 2026-11-18, reversing DiceFive's "Never
    ads"; `ADS.md`).

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
    blue, clockwise. Player 1's yard is at the bottom left on every board (the maintainer, 2026-11-23).
- **The track** is 52 squares around the cross, shared by everyone. Each colour has a **start square** on the
    track, just outside its yard.
- **The home column** is 5 squares of the player's own colour, leading from the track into the centre. The centre
    triangle is **home**. Only that colour may enter its column, so pawns there are always safe.
- **Safe squares.** There are 8: the four start squares and four star squares, each 8 squares on from a start
    square. A pawn on a safe square can't be captured.
- A pawn's journey is 57 steps: 51 squares round the track from its start square, 5 up its home column, and 1 into
    home.

### Turns

- **Who starts.** Player 1 (the first on the setup page) always starts; play then goes clockwise. There is no roll
    to start (the maintainer, 2026-11-23: the opening roll-off went).
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
- **A move with no choice makes itself** (the maintainer, 2026-11-23): with one choice for the roll (pawns on one
    square, by one die, are one choice), the game makes it for a person once the die has settled
    (`LudoEngine.automaticMove`), unless they make it first. Not when it brings a pawn out of the yard with none on
    the board, nor when it brings that owner's last pawn home: the player makes those. Never in the tutorial. The
    screen asks for no pick meanwhile ("Alex rolled 3."), and rings no pawn. On a Nearby game the host makes it.
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

**Goal:** a building app with DiceFive's chrome and infrastructure, LudoPlus's name and colours, and no dice game.
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
- [x] Placeholder launcher icon: an ivory pawn on the brand navy. Replaced 2026-11-23 by the brand icon (`UI.md`,
    "Gotchas").
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
- [x] Player 1 starts (the opening roll-off was removed, 2026-11-23; saves still carry a `rollOff` with no rounds,
    which 1.0.1 and older read).
- [x] Tests: each rule above (`LudoEngineTest`, `BoardGeometryTest`), plus property tests over 2,000
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
    square-by-square move (a hop of 140ms a square, a glide out of the yard) under the Pawn moves motion group.
- [x] The die (`GameDie.kt`): DiceFive's ivory bevelled die, tapped to roll, tumbling through faces as it turns
    under the Die roll motion group, ringed in the turn's colour (and the turn's yard ringed, 2026-11-23). Shake to
    roll waits for its setting (Phase 5).
- [x] Player cards: name, CPU icon, a dot per pawn home, the current player outlined in their colour, and their place
    once finished, laid out as the yards are round the board (`playerCardRows`, 2026-11-23: player 1 bottom left, a
    yard straight above the middle on a row of its own; TalkBack still reads them in player order). A line by the die
    says what to do now; the end shows the winner. Under them, the move log
    (`MoveLogPanel.kt`, 2026-11-23): every move, roll with no move, and turn lost to a run of roll-again rolls, newest
    first, back to who went first, each after its time ("[14:05:09]", the phone's clock), in a card that scrolls back to
    its top as each comes. It's saved with the game, and left out of Nearby messages (a guest's phone builds its own
    from the host's states, timed as they arrive). In portrait the log always has room for a line, and for two where it
    can (2026-11-24, `PortraitGame`; eight players on a 16:9 phone had a sliver): on a short screen the player cards
    close up, the log drops its title, and the board gives up to a tenth of its width for the second line, and more
    only for the first (`MoveLogRoomTest`). The board's size allows for the die alone, so a prompt never resizes it.
- [x] Capture feedback: a burst on the square, and the captured pawn thrown back to its yard (2026-11-23), with a
    buzz (`Haptics.kt`); a tick for a move, the die's landing sound, a move click, and the fanfare (`SoundEffects.kt`,
    restored from `5423b6c`).
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
    die's tumble or the pawn's move and its capture's flight or home flourish, unless those motion groups are off.
    DiceFive's `CpuTurns` was about its dice cup, so only its pacing idea came back.
- [x] A tournament test (`CpuTournamentTest`, 1,000 two-player games a pairing, seeded apart from the tuning): Medium
    beats Easy 89%, Hard beats Medium 57%, Hard beats Easy 93%; each must beat the one below at least 55%. With rolls
    again for captures and pawns home (2026-11-17): 90%, 57%, and 94%.

## Phase 4: Saving, Game Over, Statistics, and the Leaderboard

- [x] Save and continue a game in progress (`data/game`: `SavedGameJson`, `InProgressGameRepository`), resilient to
    process death. Saved as it starts and after every roll and move, cleared when it finishes; the menu's Continue (and
    a game screen restored with no game) brings it back. A save that can't be read is removed. Play time
    (`StartedGame.playMillis`) counts only while the game is on screen and not under a dialog. The cloud-save
    stamps came back with Phase 10, and the crash page's removal of a save that can't be drawn (`ResumeAttempt`) on
    2026-11-24.
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
    maintainer 2026-11-17 (capture and home replaced with new picks 2026-11-23); the hold and unhold clicks (CC-BY)
    went.
- [ ] Baseline Profile journeys and benchmarks, restored from `5423b6c` (`app/baselineprofile`, the `baseline-profile`
    job in `android-release.yml`) and written for Ludo's screens (`BENCHMARKS.md`): 60fps while pawns move.
    - [x] The module, the startup and pawn-move benchmarks, Ludo's journey, and the CI job (from 1.0.0) are back
        (2026-11-17). The journey compiles in the lint job.
    - [x] Run the journey on an emulator (the sandbox's `emulator` skill), 2026-11-17: a lap passes in about two and a
        half minutes, once the Rules tab row is swiped back to How to Play. It now visits Achievements too, added with
        Phase 6. Pinned to two cores beside two busy loops, a lap still runs out of time in the tutorial (under two
        frames a second), even with its budget doubled to six minutes.
    - [ ] `PawnMoveBenchmark` on a phone: frameDurationCpuMs P90 under 16ms.
- [x] Store-assets generator scenes for Ludo, README gallery, and `assets/full-description.txt`: six screenshots (phone
    and 10-inch tablet) from staged games and a seeded history, in the README from the `readme-assets` release
    (approved 2026-11-17). The icon, feature graphic, and promo video move to Phase 10 with the Play listing (the
    maintainer, 2026-11-17); the feature graphic's GIF is back at the top of the README since 2026-11-19 (the
    maintainer).
- [x] Privacy policy re-read against the app, and rewritten for 1.0.0 (approved 2026-11-17): no sign-in, cloud save,
    purchase, or progress files yet, and nothing leaves the device but a crash report the player posts. It must be
    updated again before Phase 10 or 11 ships.
- [x] Set VERSION to 1.0.0, which turns publishing on: the first GitHub release. **The maintainer makes this change by
    hand, never Claude** (the maintainer, 2026-11-17). Done 2026-11-18, at the maintainer's word, with
    `readme-assets.yml`'s push trigger restored.

## Phase 6: Achievements

- [x] Achievement catalog for Ludo (`DESIGN.md`, "Achievements"): 110, 100 counted and 10 Easter Eggs (the maintainer, 2026-11-17), in Milestones, The
    Die, Captures, Racing, Winning, Misfortune, Miscellaneous (hidden), and Easter Eggs (Collection folded into
    Milestones, 2026-11-23), with permanent ids and titles of 38 characters or less. First win, captures (Gotcha!,
    Bouncer, Public Enemy: 1, 100, 1,000), Comeback Kid, Untouchable, Hat Trick, Lap Of Honour (the full lap), streaks,
    and hidden ones. A debug build's game has a superuser mode setting the next roll, to check them. DiceFive's page,
    banners, unlocker, and store came back from `5423b6c` (2026-11-17), without style rewards (Phase 7), Play Games
    (Phase 10), or per-mode limits (Phase 9). Eleven removed (2026-11-23), leaving 99 (92 counted, 7 Easter Eggs), then
    My Favourite Spot and We're All Friends Here added (101: 94 counted), and the Easter Egg Can't Wait To Get Going (102: 8
    Easter Eggs), with more to come; Speed Demon became a win
    within 5 minutes on screen, Locked Out 5 turns, and Lifetime Achievement 500 games. The catalog awaits the
    maintainer's review.
- [x] Audited against the one mode and Settings' stop at first winner (`GAME_MODES_AUDIT.md`).
- [x] Unlocks are tested from the engine's events, not the UI (`AchievementTrackerTest`, `AchievementEngineTest`), and
    end to end through `GameViewModel` (`GameAchievementsTest`).

## Phase 7: Styles

Decided with the maintainer (2026-11-17):

- **Order.** Slice 1 brings back DiceFive's style system (registry, saved picks, the Styles screen, unlocks and their
    banners) with the die and background art, and a Classic-only board and pawns. Later slices add the board and pawn
    families, each approved from renders.
- **Die.** All 35 of DiceFive's families, D20 included (drawn showing 1-6).
- **Boards.** Classic, Wood, Stone, Neon, Ocean, Starry, Paper, and Retro. Each keeps the four player colours readable;
    a family changes the track, safe squares, surround, and texture. A board shares its unlock with the die and
    background of the same name.
- **Pawns.** Classic, Counter, Cone, Peg, Gem, Glass, and Animal (one animal per family, tinted). Pawns always take their
    player's colour; a family changes shape and material.

- [x] The style categories, re-cast for Ludo: **board**, **pawns**, **die** (DiceFive's styles, kept),
    **background** (kept). Unlocks are tied to achievements, as in DiceFive.
    - [x] Slice 1 (2026-11-18, structure approved by the maintainer the same day): the registry and saved picks for the four
        categories, DiceFive's 35 die families (fonts and licences back) and 20 backgrounds, Classic board and pawns
        (today's art, moved to `ui/style/BoardStyles.kt` and `PawnStyles.kt`), the Styles page and category pages, the
        menu's Styles button, unlock banners and the achievement rows' reward star and tip, the Die decoration motion
        group, and the game drawing the picks. DiceFive's locks on its own achievements remapped (`DESIGN.md`, "Table
        art"). The "Unlock all styles" button waits for Phase 11; a debug build's pretend switch stands in.
    - [x] Board families (2026-11-18, approved from renders): Wood (Oak, Walnut), Ocean (Lagoon, Deep), Retro (Blue),
        Paper (Pencil), Starry (Midnight), Stone (Slate, Sandstone), and Neon (Night), drawn as `LayeredBoardStyle`s
        (`ui/style/BoardFamilies.kt`). Each locks with the die of its name; Paper, which has none, at 50 achievements.
        The board draws on a layer of its own under the pawns, so a move doesn't redraw its texture.
    - [x] Pawn families (2026-11-18, approved from renders): Counter (Ridged), Cone (Painted), Peg (Stained), Glass
        (Clear), Animal (Cat, Duck), and Gems (Cut), drawn as `GridPawnStyle`s (`ui/style/PawnFamilies.kt`). Locks at 5,
        20, 25, 30, and 40 achievements; Gems, named for the Gems die, shares its 65.
    - [x] More boards and pawns (2026-11-24, approved from renders): nine board families
        (Comic, Patchwork, Candy, Obsidian, Disco, Groovy, Meadow, Circuit, and Bestagons) and Paper's Blueprint, in
        `PlayfulBoardFamilies.kt`; eleven pawn families (Chess, Shapes, Monster, Retro, Toadstool, Cake, Space, Robot,
        Neon, Googly, and Devil) and Animal's Frog and Penguin, in `PlayfulPawnFamilies.kt`. Locks in `DESIGN.md`,
        "Table art". `BoardAndPawnArtTest` draws every board and pawn.
- [x] The Styles screen's previews show a corner of the board with pawns (`BoardCornerPreview`).
- [x] `STYLE_ART.md` rewritten for board and pawn art (2026-11-18).

## Phase 8: 2-8 players

There is no standard board for more than four. This phase designs them. Decided with the maintainer (2026-11-18):
the big boards were rendered and approved before any code; 2 players sit opposite on the classic board; every other
count but 4 has a board of its own; one player on two colours was dropped.

- [x] 2 players on the classic board, opposite each other (`BoardGeometry.seatsFor`), the other yards empty (grey).
- [x] A board of its own for 3 and for 5, 6, 7, and 8 players (`BoardGeometry.forPlayers`, `StarBoardLayout`), approved
    from renders (2026-11-18): an arm a seat as the classic board's (3 wide, 6 long, 13 track squares a seat), round a
    centre with a side an arm, the yards between the arms out to a round edge. A lap is longer the more seats: 70 steps
    for 5 to 109 for 8 (57 on the classic board).
    - Three seats: the arms on every other side of a hexagon, so each home has room, and 3 more track squares along
        each yard's side of it (16 a seat, the star 11 on; 53 steps).
    - Every board family draws them: arm squares turned, the three-seat board's yard-side squares stretched, and each
        family's own yards (`drawYardShape`: Wood's grooves, Stone's slab, Neon's tubes, Ocean's ripples, Starry's
        stars, Paper's hatching, Retro's pixels), approved from renders (2026-11-18; `STYLE_ART.md`, "Boards").
- [x] New Game's Players row (2-8), eight slots kept, the count saved (`player_count`).
- [x] Teams as a New Game setting (decided with the maintainer, 2026-11-18): Off, Two Teams (seats alternating: 2v2,
    3v3, 4v4), or Pairs (partners opposite: two, three, or four teams), with 4, 6, or 8 players (`TeamMode`). Two
    options: partners capture each other (off by default), and a player with every pawn home rolls for a partner (on).
    A team is placed when all its pawns are home, its players sharing the place, and the rest play on for the places
    after. The CPU counts a partner's pawns as its own. Team games stay off the Leaderboard (`GAME_MODES_AUDIT.md`,
    "Teams").
- [x] Player colours past four: orange, cyan, pink, and purple, kept by the maintainer though purple is near the
    movable ring. Checked for colour blindness (`UI.md`, "Player colours"): blue and purple, and green and cyan, are
    close for some, so the defaults stay and the picker offers four more (lime, magenta, berry, and scarlet; the
    maintainer, 2026-11-18).
- [x] The CPU: Hard's weights hold at every count (one Hard among Mediums wins 40% of 3-player and 23% of 6-player
    games; 54% of 2-player games, now opposite; `CpuTournamentTest`).
- [ ] Audit each achievement for team games one by one (`GAME_MODES_AUDIT.md`, "Teams"): the win, the opponents, and
    Wooden Spoon are team-aware; the rest await review.
- [x] Statistics and the Leaderboard by player count (`DESIGN.md`, "Leaderboard and Statistics"): a Leaderboard a count,
    and Statistics for every game or one count's.

## Phase 9: Game modes and modifiers

- [x] Modifiers: the house rules above, mixed in on New Game's House Rules card (`model/HouseRule.kt`, `DESIGN.md`,
    "Modifiers"). Decided with the maintainer (2026-11-18): every one of them but two dice (a mode); a game with any on
    stays off the Leaderboard and out of Statistics, and earns no achievements. Must Capture, Guarded Starts (a start
    square safe only for its own colour), Free Start, Three Tries, Out on a 1, Blockades, No Capture Roll, No Home Roll,
    and Endless 6s.
- [x] Modes: rule sets that are data on `GameMode`, never `if (mode == X)` (`GAME_MODES.md`). Decided with the
    maintainer (2026-11-18): each mode has its own Leaderboard and Statistics page, and earned no achievements until the
    review of the list (2026-11-24, below); a short track and power-up squares weren't picked.
    - [x] Quick: two pawns each.
    - [x] Two Dice: each die moved on its own, one after the other, on any pawns (the same pawn twice allowed); a 6 on
        either die brings a pawn out with that die; doubles roll again, three in a row end the turn; a capture or a pawn
        home still earns a roll (the maintainer, 2026-11-18).
- [x] Portals, a mode (the maintainer, 2026-11-24): short and long pairs of two-way portals, from a design that began as
    snakes and ladders (`DESIGN.md`, "Game modes").
- [x] Survival, a mode (the maintainer, 2026-11-24): six pawns each starting out, no home, captured pawns out of the game,
    and stages of sudden death, later the more players there are (Overtime and Sudden Death, then the Cataclysm) (`DESIGN.md`, "Game modes").
- [x] Chaos, a mode (the maintainer, 2026-11-24): six power-ups on the track, Double, Shield, Teleport, Roll Again, Call
    Up, and Swap (`DESIGN.md`, "Game modes"). It reverses the decision above against power-up squares.
- [x] Haunted House, a mode (the maintainer, 2026-11-24), the seventh, as DiceFive had: ghosts that send pawns back.
- [x] Achievements for the modes and house rules (the maintainer, 2026-11-24), 15 of them, set up as DiceFive's were, and
    the ordinary ones opened to every mode, each audited in each (`GAME_MODES_AUDIT.md`).
- [x] Custom game modes (the maintainer, 2026-11-18): a base mode and at least one house rule, named and saved, listed
    in New Game's mode picker after the built-in modes, with New, Edit, and Delete on the card. As any game with house
    rules, their games count towards nothing (`DESIGN.md`, "Custom modes"). New was removed (the maintainer, 2026-11-24).

## Phase 10: Google Play

- [x] Create the Play Console account, the upload key, and the CI secrets, and publish the privacy policy (the
    maintainer, 2026-11-23; `PUBLISHING.md`).
- [x] The store listing's hi-res icon, feature graphic (and the README's GIF), and promo video: DiceFive's generators
    are in `5423b6c`; the script keeps their steps, refusing those parts until then (`STORE_ASSETS.md`).
    - [x] The icon and feature graphic (`StoreArtGeneratorTest`), approved 2026-11-18. The icon follows the launcher
        icon: the placeholder pawn until 2026-11-23, then the tilted board with the red pawn in focus. The feature
        graphic's GIF heads the README from 2026-11-19 (`readme-assets.yml`).
    - [x] The promo video, approved 2026-11-18: no tutorial (the maintainer). New scenes and music 2026-11-24
        (`STORE_ASSETS.md`).
- [ ] Play Games Services: achievements, the fastest-win leaderboards, cloud save, and sign-in (`PLAY_GAMES.md`).
    The Console's achievements and leaderboards wait for the maintainer's review of them (2026-11-23).
    Decided with the maintainer (2026-11-18): a leaderboard for each built-in mode and player count, 2-8 (42 of Play's
    70: Survival has none), each the turns in a win, fewer better. Play ranks one number, so the local captures tie-break doesn't carry.
    - [x] The code, restored from `5423b6c` (2026-11-18): `OnlineSync`, the Settings card, the Achievements page's way
        to Play's screen, the Leaderboard's "Global leaderboard", About's sign-in line, `PlayGamesServices`, Sidekick,
        backup rules, and the Play Console export (achievements with proposed points, and the 42 boards' sheet and
        icons). Ids are empty until the Console project exists.
    - [ ] The maintainer's Console steps (`PLAY_GAMES.md`, "Play Console"), the ids filled in, the points reviewed, and
        sign-in checked on a Play install. The privacy policy and data-safety form must cover sign-in and cloud save
        first (`PUBLISHING.md`).
- [x] Progress files (github flavour), restored from `5423b6c` with the cloud save format they share
    (`online/CloudSave*`, `ProgressFileCodec`, `ProgressTransfer`, the Settings card, `AndroidProgressFiles`, and the
    `PROGRESS_FILE_KEY` CI secret): settings, the saved game, and the game records (moved from Phase 4, 2026-11-17).
    Done first, as the maintainer chose (2026-11-18). The format is Ludo's own, version 1: each game record has a
    random `uuid` (database version 3), each saved game an id and a step count, and the time played moved to the
    achievements' counters (`PLAY_SECONDS`), each device's share summed. `OnlineSync`, the account card, and the
    reset marker come with Play Games.
- [ ] Store listing, data-safety form, and content rating. Internal testing track, then production.

## Phase 11: In-app purchases and ads

- [x] The "Unlock all styles" purchase, from DiceFive (`PURCHASES.md`), with "Remove ads" and a combined one beside it
    (the maintainer, 2026-11-18). Built 2026-11-18; the Play Console products and license testers wait on the Console.
- [x] Any further purchases: none beyond those three (the maintainer, 2026-11-18). Styles stay earnable through play.
- [ ] An ad banner in the play app, at the bottom of every page but the game, under the achievement banners and not
    clickable while one shows; gone with "Remove ads" (the maintainer, 2026-11-18, reversing "Never ads"; `ADS.md`).
    - [x] Built 2026-11-18: AdMob behind Google's consent form, Settings' Ad privacy choices, the privacy policy and
        data-safety drafts rewritten, and the release workflow refusing test ids.
    - [ ] The maintainer's AdMob account, ad unit, consent message, and ids (`ADS.md`, "The maintainer's steps"), and a
        check on a phone. In progress (2026-11-23).

## Phase 12: Netplay

The biggest change: it ends "no account, no network". Decide the approach before building it.

- [x] Choose the transport (the maintainer, 2026-11-18): **Nearby first**, in the play app; host-set seats with a
    "Nearby player" seat type, anything the host picks for rules, and each phone counting its own seat (`NETPLAY.md`).
    Google Play Games' real-time multiplayer API was shut down in 2020, so it was one of:
    - a hosted relay (a small server, or a managed realtime database), which works over the internet, but costs money,
        needs moderation, and changes the privacy policy;
    - Nearby Connections, which works only in the same room, needs no server, and keeps privacy simple;
    - both, with Nearby first.
- [x] The engine is already deterministic (Phase 1), so the host is the authority: it rolls, and the clients send
    their moves and get the state back (slices 1 and 3; untested on phones).
- [x] Lobby, invites, reconnect, turn timeouts, and the CPU stepping in for a player who drops out (slices 2 and 4;
    untested on phones).
- [x] Privacy policy, data-safety form, and the `CLAUDE.md` aims updated (the form's answers drafted in
    `PUBLISHING.md`; the maintainer fills it in, and checks a game on two phones, once there's a Play Console project).

## Phase 13: Remote netplay

Two or more phones in different places play one game over the internet (the maintainer, 2026-11-18). It starts once
the app has launched: after Phase 10's production release. The design is in `NETPLAY.md`, "Remote play (Phase 13)".

- [ ] Confirm the relay and the new network use (the maintainer). Proposed: Firebase Realtime Database on the free
    Spark plan, in the play app only. `CLAUDE.md`'s aims list the play app's network use, and remote games join it.
- [ ] The Firebase project (the maintainer's steps): the Spark plan, the play app registered, anonymous sign-in, App
    Check with Play Integrity, and the database's security rules.
- [ ] The relay's `NetLink`, in the play flavour: a room under a short join code, carrying the protocol Phase 12 built.
    `HostSession`, `GuestSession`, and `NetMessage` stay as they are.
- [ ] Hosting and joining: a "Remote player" seat type beside "Nearby player"; the lobby shows the join code (spoken
    by TalkBack character by character); "Join with a code" beside "Join a game nearby".
- [ ] Away and back: the relay's presence marks a guest away, and the rejoining by token, the turn timeout, and the
    CPU standing in work as they do over Nearby.
- [ ] Rooms tidied away: a room is removed when its game ends or its host leaves (the database's on-disconnect
    removal; Spark has no Cloud Functions), and the rules refuse writes to a room older than a day.
- [ ] The limits: at Spark's cap (100 connections at once) a host or join fails at once with a message, never a hang.
- [ ] The APK's growth measured (the Firebase libraries, play app only), and the licence guard passed.
- [ ] Privacy policy, data-safety form, `CLAUDE.md`'s aims, `README.md`, and the store description updated: game data
    passes through Google's servers under an anonymous id, deleted when the room is.
- [ ] Checked on two phones on different networks (one on mobile data).
