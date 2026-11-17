# Ludo Plus: design reference

> **Partly inherited from DiceFive.** Phase 0 of `IMPLEMENTATION.md` removed DiceFive's dice game. Sections on what
> is still in the app (the stack, errors and the crash page, settings, achievements, styles, distribution, and the
> release pipeline) hold, and so do Game modes and Modifiers (rewritten in Phase 9);
> sections on the dice, the scorecard, the CPU, scores, and saved games describe the code
> in commit `5423b6c` (`git show 5423b6c:<path>`), for bringing a feature back in its phase. Rewrite a section for
> Ludo Plus when the code it describes is.

What the app does and why. The interface is in `UI.md` (plus `UI_BOARD.md` and `UI_SCREENS.md`), modes in
`GAME_MODES.md`, and art in `STYLE_ART.md`.

## Stack

- Platform code sits behind three seams. View models take an `AppContainer`, never a `Context`.
    - `platform/PlatformServices`: sound, haptics, accelerometer, transient messages, licences, reduce motion, frame cap.
    - `BuildInfo`: version and debug flag.
    - `AppContainer`: what each platform's entry point builds.
- There is no `kotlinx.serialization` or `kotlinx-datetime`; both were dropped to keep the APK small.
    - `data/Json.kt` is a small strict JSON reader and writer.
    - Timestamps go through `expect fun formatTimestamp`.
- One dark theme only.

## Screens

```text
NavHost(start = "menu")
  "menu"         MenuScreen - Play (New Game | Continue), Achievements, Styles, Leaderboard, Statistics, Rules, Settings
  "play" graph   one GameViewModel scoped to the graph (no player lists through nav args), shared by:
    "play/setup"   GameSetupScreen
    "play/game"    GameScreen, then GameOverScreen and the scorecard review (Continue comes straight here and loads
                   the saved game)
  "scores"  ScoresScreen (the Leaderboard)   "statistics"  "achievements"  "styles"  "rules"
  "tutorial"     GameScreen on a GameViewModel of its own, playing TutorialScript.CLASSIC (Rules' How to Play page, or New Game)
  "settings"     SettingsScreen (About and Licences open from links at its foot)
```

## Game setup

- **Players.** There are 1-4.
    - Player 1 is always human and is the only seat that earns achievements.
    - Players 2-4 are Human or CPU.
    - A CPU has a difficulty (Easy/Medium/Hard, default Medium). Its name is generated at Start Game, unique in the game
        (`AiNameGenerator`).
- **Names.**
    - An empty name field shows a localised "Player N" hint, and the game uses that name.
    - Names are sanitised as they're typed (`model/PlayerName.kt`).
    - Names are capped by width per player count (`model/NameWidth.kt`'s `maxPlayerNameLength` 14/12/10/8; see
        `UI_BOARD.md`).
    - `GameEngine.newGame` holds every table to its rules: 1-4 players, a Human in seat 1 whenever any play, distinct
        colours, and no two Human names alike (`duplicateHumanNameSlots`; the Leaderboard and Statistics tell players apart
        by name). The New Game form asks the same function, and `startGame` refuses a clash.
- **Colours.** Each player picks a distinct colour (`PlayerColour`).
- **Remembered.** The mode, the modifiers and everything on the form are saved for next time in `SettingsRepository`.
- **Locked until a first game is finished.** Until any game reaches its end (any mode, solo or not, won or lost; a
    game left unfinished doesn't count), the Game Mode and Modifiers cards show only their titles over "Finish a game to
    unlock game modes" / "... modifiers" (no picker), and the form plays Standard with no modifiers. A "New to the game?
    Play the tutorial" button, which starts it, sits above Start Game, only while locked.
    - The flag is `first_game_completed` (`SettingsRepository.firstGameCompleted`). It's synced, only ever set, and no
        reset clears it. The tutorial game won't set it.
    - It costs nothing once unlocked. It comes in the setup form's single settings read (`GameSetupState.modesUnlocked`),
        and it's written once, at the first game over.
    - While locked, the saved mode and modifier picks are neither restored nor overwritten.

## The tutorial

How it's built, its scripting rules, and how to add one for another mode: `TUTORIAL.md`.

- **What it is.** A guided game of Classic, the player (Red) against one CPU (Green), in four parts, each from a
    position of its own (`game/Tutorial.kt`, `TutorialScript.CLASSIC`): getting out of the yard on a 6, with the 6's roll
    again; a capture and its roll again; a pawn home with the exact roll and its roll again; and winning, after a roll too
    big to get home. Every roll and move is fixed, so it plays the same each time (`TutorialScriptTest`).
- **Where it opens.** Rules' How to Play page (Play the Tutorial), and New Game's "New to the game? Play the tutorial"
    link, shown until any person has finished a game (`GameRecordRepository.hasFinishedGame`).
- **How it plays.** The real game screen and engine. `GameViewModel(tutorial = ...)` rolls the script's numbers and
    moves on step by step (`tutorialStep`, shown once the action before it has played out): only the step's own action is
    taken. At a roll step the die pulses in a violet outline (steady under reduced motion); at a move step only the step's
    pawn is ringed and offered to TalkBack. The CPU plays its scripted steps at its usual pace. A part ends with its last
    step, and the next is set out a beat later.
- **The coach card** (`CoachCard`) takes the player cards' place: "Coach" and "Part 2 of 4" over the step's words, with
    back and forward strips at its edges that jump between parts. It's as tall as the longest step needs at the font in
    use up to 130%; past that the words scroll inside it. The name and words are a polite live region, read out as each
    step arrives; the part is a node of its own.
- **Nothing is kept.** No saved game (Continue stays on the player's own), no records, and the setup form isn't saved.
    `TutorialGameTest` plays it through and checks.
- **Leaving** always asks ("Leave the tutorial?"), whatever the confirm-before-leaving setting. Its Game Over shows the
    winner alone and a line that it's done, then Done (back where it was started) or New Game.

## The turn and the rules

All numbers here are Standard's. Each comes from the game's `GameMode`.

- **A turn** starts with 5 dice, 3 rolls and nothing held. A roll rolls every unheld die.
- **One roll path.** Every roll, human or CPU, lands through `GameViewModel.performRoll` after the cup has shaken for
    `CUP_SHAKE_MILLIS`.
    - A human's roll comes from the cup tap (`onCupTap`): a finger or a phone shake, which calls
        `GameViewModel.shakeAndRoll`.
    - A CPU's roll comes from its own turn loop. Both shakes are timed in the view model and pause with the game.
    - A tap while a roll is shaking or landing is ignored.
- **Holding** toggles a die once the turn has rolled. It's still allowed after the last roll: it does nothing then,
    but disabled dice looked broken.
- **Scoring** is any open box after the first roll. `ScoreCalculator` applies two rules:
    - The upper bonus: 63 or more in the upper section earns 35.
    - The joker rule. Every 5x after a scored 50 adds a +100 chip, recorded on the box it scores in (`jokerBonusSlots`)
      and shown there. If the 5x box is filled, the score goes in the
        matching upper box if it's open, else any open lower box (Full House and the straights at their fixed values),
        else a zero in any open box.
- **The CPU** scores, then plays its rolls with short pauses so it's visibly animated.
- **Turn timer** (a modifier):
    - Running out on a rolled turn scores the first open box (in Stud, the hand is completed first).
    - Running out before the first roll forfeits the turn as a zero in the first open box, with no roll
        (`GameEngine.forfeitTurn`). Both are `GameEngine.timeOut`.
    - CPUs time out the same way.
- **Undo** exists in solo games only. It takes back the last score; a roll or hold clears it (they aren't undoable),
    as does any CPU move, and a finished game has none. It's saved with the game, so a resumed game can still undo.
- **The end.** The game ends when every card is full. **Human** totals are recorded; CPU scores never are. The results
    screen shows the ranking with tie-break captions.
- `DiceScoring`, `ScoreCalculator` and `GameEngine` are pure, with no platform dependencies.

**Maximum and minimum scores.** These are `GameMode.maxPossibleScore` and `minPossibleScore`, each with its derivation
as a doc comment. `GameModeTest` locks each one by playing that game through the engine: the perfect game, and the
lowest. Chance is the only box that can't score 0, so a mode's minimum is its Chance slots at five 1s each. Quickfire's
is 0, because some of its cards have Chance switched off. The game screen marks a running total as a new Leaderboard
score only from the minimum up (`newLeaderboardScoreSeats`).

| Mode           | Maximum | Minimum |
|----------------|---------|---------|
| Standard, Stud | 1575    | 5       |
| Tricolour      | 2120    | 5       |
| Third Wind     | 4725    | 15      |
| D20            | 1350    | 0       |
| Hit List       | 785     | 0       |
| Quickfire      | 210     | 0       |

The Leaderboard pads scores to the digit width of `HIGHEST_POSSIBLE_SCORE` (4 digits).

### Tie-break house rule

The official rules call a tie a shared win. Here, equal scores are separated by whoever had more handicaps, checked in
this order:

1. Fewest 5x.
2. Most zeroed boxes.
3. Fewest Tricolour colour boxes scored (Tricolour only).
4. Lowest upper section (without its bonus).
5. Lowest Chance.
6. Lowest 3x.
7. Lowest 4x.

The first criterion that differs decides it. If all match, it's a true tie with a shared rank ("=2").

- **Where it's used.** `game/TieBreak.kt` holds the criteria and `TieBreak.rank`. They're shared by the results
    screen (its caption: "Won on fewer 5x") and the Tie Break achievement.
- **The Leaderboard** uses the same criteria minus the Tricolour one, as every board shares one SQL `ORDER BY`, which
    mirrors the comparator.
- **Who won.** `GameState.wonBy` (`game/Standings.kt`): rank 1 after the tie-break, so only a true tie shares a win.
    The one rule for Statistics' `won`, the win streak, every win-gated achievement and One More Time. Score rows
    recorded up to Android 1.2.1 keep the raw-score rule they were saved with.
- **Player tabs** rank by total during play ("=1st"), then take `TieBreak.rank` once every card is full.

## Game modes

`model/GameMode.kt`. **Every rule that can differ between modes is a field on the mode's `GameRules`; there is never
an `if (mode == X)`.** `GAME_MODES.md` covers adding or changing a mode.

| Mode | Rules beyond Classic | Leaderboard and Statistics | Achievements |
| --- | --- | --- | --- |
| Classic | The classic rules | Yes | Yes |
| Quick | Two pawns each; each yard draws two spots, on opposite corners (the maintainer, 2026-11-18) | Yes, its own | No |
| Two Dice | Two dice, each moved on its own by any pawn; a 6 on either brings a pawn out; doubles roll again, not 6s | Yes, its own | No |

Every mode has its own Leaderboard (one a player count) and its own Statistics page. Only Classic earns achievements
(`GameMode.earnsAchievements`) until the maintainer's review of the list adds any for a mode (2026-11-18). The Rules
screen has a Game Modes group, a page a mode beyond Classic.

**Two Dice** (`GameRules.dice`, `rollAgainOnDoubles`). A roll is both dice (`LudoEngine.applyDice`); the move phase
holds the dice still to move, the whole roll, and whether it has earned another roll (`TurnPhase.AwaitingMove.dice`,
`rolled`, `earned`), and each move the die it uses (`Move.roll`). Either order; a die with no move once the other is
moved is lost; a double's moves are offered once. The game screen draws the two dice at 0.8 of one die's size: a tap on
either rolls both, then a tap on a die picks the one the board's pawns move by (ringed; the other die's moves stay in
TalkBack's actions), and a moved die fades. TalkBack: "First die showing 3, selected", "Second die showing 5, moved",
action "Use this die"; the prompt "Alex rolled 3 and 5. Pick a pawn to move 3." then "Alex has 5 left. Pick a pawn to
move." The CPU picks one move at a time, die by die. House rules apply die by die (Out on a 1, Free Start), and to the
roll (Three Tries, Must Capture across both dice; Endless 6s makes doubles endless).

**Custom modes** (`model/CustomMode.kt`, the maintainer, 2026-11-18). A name (unique among the player's and the
built-in modes', whatever its case; at most 24 characters), a base mode, and at least one house rule (without one it
would be its base). Kept as JSON in the `custom_modes` setting (`CustomModesJson`; one that can't be read is dropped),
the one picked as `custom_mode`. New Game's mode picker lists them after the built-in modes, each with its base and
house rules as its line; under it, New Custom Mode, and while one is picked, Edit and Delete (Delete asks first). The
editor is a page of its own: the name, Based On (a segment a mode), and a switch a house rule, with Save offered once it
can be. While one is picked, the House Rules card says "Set by Cut-Throat: Blockades and Must Capture." in place of the
switches, which keep their own picks for the built-in modes. A game plays its base with its house rules, titled by its
name (`StartedGame.customName`, saved with the game), and counts towards nothing, as any game with house rules. They're
locked with the house rules until a first game is finished: hidden, and the base played plain.

## Modifiers

**House rules** (Phase 9, 2026-11-18): `model/HouseRule.kt`, each one a switch on New Game's House Rules card (the
`ModifierPicker` from `ui/common/ChoicePicker.kt`), any mix on, all off by default. Each changes one
`GameRules` field through `HouseRule.applyTo` (`GameRules.withHouseRules`), and the engine, the CPU, and the board
read only the rules, never which house rules are on. The rules keep the set (`GameRules.houseRules`) for the save and
the records.

| House rule | `id` | Field | What it changes |
| --- | --- | --- | --- |
| No Capture Roll | `no_capture_roll` | `rollAgainOnCapture` | A capture earns no roll |
| No Home Roll | `no_home_roll` | `rollAgainOnHome` | A pawn home earns no roll |
| Endless 6s | `endless_sixes` | `rollAgainLimit` | A run of 6s never ends the turn |
| Out on a 1 | `exit_on_one` | `yardExitRolls` | A 1 leaves the yard too |
| Free Start | `free_start` | `freeStart` | Any roll leaves the yard while no pawn is on the board |
| Three Tries | `three_tries` | `yardTries` | Three rolls a turn to leave the yard while no pawn is on the board |
| Blockades | `blockades` | `blockades` | Two of one player's pawns on a track square can't be passed or landed on by another's |
| Must Capture | `must_capture` | `mustCapture` | A roll with a capture allows only captures |
| Guarded Starts | `guarded_starts` | `guardedStarts` | A start square is safe only for its own seat's pawns |

- **On the board.** "On the board" is the track or the home column: not the yard, not home. A Three Tries roll with no
    move keeps the turn (`GameState.yardTriesUsed`, `GameEvent.NoMove.triesLeft`, "Alex rolled 2 and had no move: 2
    more tries."), one turn however many tries.
- **Blockades.** Every other player's pawns, partners' included, are stopped by one, out of the yard too when it
    stands on their start square; its own player's pass and join it. TalkBack says "Red blockade" on its square
    (`LudoEngine.blockades`).
- **Guarded Starts.** A start square no one sits at (2 players on the classic board) is safe for nobody. A pawn
    leaving its yard captures any other on its start square. TalkBack says "safe for Red" there, not "safe".
- **Off the record.** A game with any house rule on (`GameRules.hasHouseRules`) stays off the Leaderboard
    (`playedUnmodified` false) and out of Statistics (`GameResult.houseRules`, database version 2), and earns no
    achievements: `GameViewModel` keeps no `AchievementTracker` for it, and records nothing at its start, its
    continuing, or its end (`StartedGame.earnsAchievements`; the maintainer, 2026-11-18). Its rows are still written, so
    New Game's tutorial link knows a game was finished. The card's note says so while any is on.
- **Remembered** as the `house_rules` preference (a set of ids; one no longer known is dropped), and saved with the
    game by id (`SavedGameJson` version 2; an unknown id fails the read).
- **The CPU** plays every house rule through the legal moves it's offered. Medium and Hard count a pawn in a blockade
    as safe, and a guarded start as safe only for its own seat's; they don't plan for Three Tries or a blockade's path.
- **The Rules screen** has a House Rules group: an About page, then the rules by what they change (Rolling, The Yard,
    Captures), each as its name in italics before its description (`RulesHouseRule`).
- **Locked until a first game is finished** (the maintainer, 2026-11-18): until a game is recorded
    (`GameRecordRepository.hasFinishedGame`, read as the form is restored, so nothing changes once it shows), the card
    is a dimmed note, "Finish a game to unlock house rules", one TalkBack stop. The saved picks are kept and saved, but
    a game started then plays none (`GameSetupState.playedHouseRules`). Teams and the mode aren't locked.

## The CPU

`game/AiTurnPlayer.kt` plays from the player's own mode and card.

- **Easy** never holds. It rerolls everything until some open box scores above zero, then takes the highest.
- **Medium** plays by rules of thumb.
    - It holds a forming straight, else the largest matching group (colour sets in Tricolour, the best open number in
        D20).
    - It banks a good-enough hand early: Full House, Large Straight, a Small Straight once Large is gone, or three or
        more 4s/5s/6s with that box open.
    - Ties lean toward an upper box that's on pace for the bonus.
- **Hard** plans the whole turn exactly: every hold, every outcome, every reroll left. `DiceSpace`/`HandScoring`
    number and score each hand once, so a decision is a few array passes. It values a finished hand as follows.
    - **Standard without Extended Scores.** The bundled **perfect-play table** (`StandardPerfectPlayTable`,
        `composeResources/files/standard_perfect_play.bin`) holds the optimal expected value of every start-of-turn state.
        - Hard plays Standard perfectly, about 254 a game.
        - `StandardPerfectPlayTableTest` fails if the rules drift from the table. Regenerating it is in the `test` skill.
    - **Elsewhere.** An estimate: the score less the box's baseline, plus a share of any reachable section bonus.
        Tricolour's ~10 MB space is built in the background when a game with a Hard CPU starts.
    - **Special cases.**
        - Stud maps each seven-dice roll to its best five (`RollSpace`).
        - Hit List has its own exact play at every difficulty (`HitListPlay.kt`).
        - With a die locked by Unlucky Dice, Hard plays as Medium.

## Persistence

Everything is in app-private storage. **Every stored id is permanent once released**, because achievements and modes
will map to Play Games.

- **Scores.**
    - Room, `ludo.db`, `AppDatabase`, with each version's schema exported to `app/shared/schemas/`.
    - Tables:
        - `scores` (`ScoreEntry`): name, score, time, `won` (null for a solo game), `isPrimaryPlayer`, the tie-break stats,
            `gameModeId`, `playedUnmodified`, `onLeaderboard`, `playerKey`, `diceRolled`, and a random `uuid`. Every column
            but `won` is NOT NULL. `diceRolled` (`PlayerState.diceRolled`: each roll's unheld dice, saved with the game)
            came in database version 6; older games have 0, and the cloud save's rows add it at the end, read as 0
            when missing.
        - `dismissed_player_stats`: name, when it was dismissed, and `playerKey` (the primary key: one dismissal a player).
    - **`uuid` is the primary key**, a row's id on every device and in the cloud save. It is a `kotlin.uuid.Uuid`, stored
        as its 16 bytes (`UuidConverters`) and written to the cloud save as 32 hex digits. There is no device-local id.
    - **Names are matched by `playerKey`**, never by `playerName` or `COLLATE NOCASE` (SQLite folds only ASCII case, so
        "Ángel" and "ángel" would be two players). It is `playerNameKey(playerName)`, derived in the entity, so a copy with
        another name gets that name's key.
    - **`onLeaderboard` is derived too**: the mode's `countsOnLeaderboard` and `playedUnmodified`, so it can't disagree with
        them. The cloud save doesn't hold it from format 2 on; a format 1 save's is ignored, and it is worked out on reading.
    - **Every read made while the app is in use comes from an index**, never a scan or a sort of the table. Statistics
        (`playerRecords`) adds up every game, so reads a whole index, but never the table; the cloud save (`allScores`) reads
        every row by design. `ScoreQueryPlanTest` checks each read's query plan against the real SQLite. The indices:
        - `index_scores_unmodified_order` and `index_scores_mode_order` hold the Combined board and each mode's board in
            `LEADERBOARD_ORDER`, with the same ASC/DESC per column, so a page is read in order. Room's annotation can't be built
            from `TieBreak.leaderboardOrder`, so a change to the tie-break order must change both (`ScoreQueryPlanTest` fails
            until it does).
        - `playerKey, onLeaderboard, score`: a player's best.
        - `index_scores_primary_player`: player 1's career points and modes played.
        - `index_scores_player_mode_totals` and `index_scores_player_history`: Statistics (below).
    - **Statistics is added up by SQLite.** `playerRows` reads, in one transaction, each player's games summed per mode (a
        `GROUP BY` over a covering index), their latest spelling, and only the outcomes of their decided games, unordered,
        for the win streaks; window functions, which could do the streaks too, need Android 11. That's three reads for
        every player together, never a read per player (`ScoreQueryPlanTest`). `playerRecords` then puts each player's
        outcomes newest first and walks the streaks, and `ScoreRepository` adds the modes up, all after the transaction and
        off the main thread, so a long history holds up no write. `PlayerStatisticsQueryTest` checks the result equals
        walking every game.
    - **Leaderboard order is total.** Rows equal on every criterion follow in `rowid` (recording) order, so `LIMIT/OFFSET`
        pages never repeat or skip a row.
    - **Multi-statement reads and writes are one transaction**: a game's rows (`insertGame`), a reset (`clearAll`), the
        achievements' figures (`leaderboardTotals`), Statistics' reads (`playerRows`) and the cloud merge (`mergeCloud`).
    - Every schema change needs a version bump and a migration in `MIGRATIONS` (`data/scores/Migrations.kt`), tested by
        `ScoreDatabaseMigrationTest` from the exported schema it migrates from. There is **deliberately no destructive
        fallback**: a missing migration must fail loudly, never wipe scores. A change to `playerNameKey` changes stored
        keys, so it needs a migration too.
    - **Failing loudly is a page, not a crash**: `DatabaseFailurePage` (see "Errors").
- **Preferences.** Five DataStore files (`PreferencesFile`), kept apart so a reset can clear one. Each is a
    `ResilientDataStore`: a write that fails (a full disk) is skipped and a read that fails is empty, never thrown into
    the coroutine that asked. A failed read is retried three times, after 1s, 3s and 5s, and a retry that reads the file
    gives it to the collector; after the third, it stops.
    - `settings`. Every key but the Animations level, the Orientation, and the two list filters is synced
        (`SettingsRepository.SYNCED_KEYS`) and stamped `synced_at:<key>` when it changes. A key no version reads any more is listed in
        `RETIRED_KEYS` and removed at launch (`forgetRetired`).
    - `achievements`: unlock times and counters, plus the cloud save's counter shares (this file's own share id and the
        others' counts) and the streak's stamp.
    - `in_progress_game`, stamped `saved_at` on every save and clear.
    - `online`: Play Games choices and the cloud save's bookkeeping (leaderboard reset marker, "Saved").
        No reset clears it.
    - `purchases`: `owns_all_styles`, what the store last said (`PURCHASES.md`). No reset clears it, and it isn't in
        the cloud save.
- **Cloud save** (signed in only): one gzipped JSON snapshot of all of the above but the Animations level, the
    Orientation, and the two list filters, merged, never picked. `PLAY_GAMES.md`, "Cloud save", is the source.
- **Progress files** (the github app only): the same snapshot, sealed, in a file the player keeps. "Distribution",
    below.
- **The in-progress game.** `InProgressGameRepository` holds at most one, saved as JSON (`GameStateJson`).
    - It's saved after every change and cleared when the game ends.
    - Each game has an id (`GameState.id`, from `GameEngine.newGame`), and `rollsMade` only goes up. The cloud save
        keeps the copy of a game with more rolls, whatever the stamps say, and never brings back a game finished on this
        device (`PLAY_GAMES.md`, "Cloud save"). A save stamp is never earlier than the last, even with the clock set back.
    - Writes are queued and conflated in a process-lifetime scope, so leaving a game can't lose its last move.
    - An undecodable save is treated as nothing to resume: a missing required field, an unknown mode, a mode's draw
        missing, or a game the board can't play (`checkPlayable`: the seats, the dice count, each die within the mode's
        `dieValues`, the hold slots, the rolls). A cloud save or progress file can bring in what this app never wrote.
    - Fields a modifier adds are left out when it's off.
    - What Undo goes back to is saved as a nested game (`undoSnapshot`), and the box an undo took back as
        `undoneCategory`. One that can't be read only loses the undo; the game still loads.
- **Style picks.** Five ids (dice, cup, mat, background, frame) in `settings`. An id nothing recognises draws the
    default.

## Errors

The rule: **an error may cost a game, never the app.** What can be recovered from is caught where it happens. What
can't is a page saying so, with the stack trace to report. The app never just closes.

- **Recovered where it happens**, so nothing reaches the pages:
    - storage: `ResilientDataStore` (see "Persistence"), and `orNullOnFailure` (`data/Failures.kt`, which rethrows a
        `CancellationException`) around writes and loads that can fail;
    - a cloud or progress-file game that can't be read or played (`GameStateJson.checkPlayable`) is never taken;
    - a failed cloud sync costs that sync (`LudoPlusApp`), and a progress file that won't open is reported on its card;
    - Hard's perfect-play table that can't be read leaves Hard estimating (`AppContainer`);
    - Leaderboard or Statistics data that can't be read leaves what's on view, or opens empty with nothing on view yet.
- **The scores database won't open** (a migration missing or failing): `AppContainer` opens it as it's built and keeps
    the stack trace in `databaseFailure`. `LudoPlusApp` then shows `DatabaseFailurePage` in place of every page: what
    happened, that nothing was deleted, to update, and the trace. The process stays up.
- **An error nothing caught**, on any thread (a coroutine's included): `CrashHandler` (`device/`), installed by
    `LudoPlusApplication`, opens `CrashActivity` with a `CrashReport` (`device/`). Then it logs the error and ends the
    app's process itself.
    - It doesn't hand the error to the platform's handler. That handler puts Android's "has stopped" dialog over the
        page (on some phones at the first crash) and reports to Play's crash reports, so those reports don't see these
        crashes: the GitHub issues and the log are their record.
    - An error with no page goes to the platform's handler, as before there was a page: one whose page couldn't be
        opened, and one while the app isn't on screen (`CrashHandler.isOnScreen`), where Android quietly refuses to open
        a page and shows no dialog either.
    - The report holds the build and flavour, the Android version, the device, the screen and font scale, the language,
        the thread, the last actions, and the stack trace, cut to 100,000 characters to fit an Intent. It's developer
        text, never translated.
    - **Last actions** are `Breadcrumbs` (`app/`): the last 12 pages opened (`LudoPlusApp`) and moves made (a game
        started or resumed, a roll, a hold or release, a score, an undo, and a CPU's roll, in `GameViewModel`), oldest
        first. They hold ids and numbers, never a name or anything typed.
    - Only a process's first error opens the page; another thread's waits for it, then ends with it. Installing again
      (each Robolectric test's Application) replaces the
        handler rather than stacking it. With no platform handler (a plain JVM), the error is printed.
    - `CrashActivity` runs in a process of its own (`:crash`, the manifest's), since the app's own is on its way down.
        `LudoPlusApplication` sets up nothing there except Compose resources' context. Their ContentProvider gives it
        only to the app's own process, so `CrashActivity.provideComposeResourcesContext` sets the library's field by
        name. `r8-rules.pro` keeps it, and `CrashActivityTest` fails if a library update moves it.
    - It shows `CrashPage`: a heading, what happened, what's kept, the buttons, and the report, collapsed.
        `CrashPage`, `DatabaseFailurePage`, and their shared layout (`FailurePage`, `FailureDetails`) are in `ui/common`.
        - It opens as a page from the menu does: under the page cover, which fades away once it has settled
            (`RevealedPage`). Android's own window animation is off (`FLAG_ACTIVITY_NO_ANIMATION`). The crash process
            doesn't read the settings, so it's handed the Animations level the app last drew with (`LastAnimationLevel`).
        - **Restart** (filled) and **Report on GitHub** (tonal) sit side by side, one above the other at a large font.
            Restart starts the app in a fresh process and ends the crash page's.
        - **Report on GitHub** opens the browser at a new issue on the repo, filled in with the report
            (`CrashReport.issueUrl`), titled `[Crash]` and the error's first line: a line for the player to say what they
            were doing, a table of the details, the last actions, and the trace. The player reads it and posts it
            themselves; the app sends nothing. The link stays within 7,500 characters, under GitHub's limit, by cutting the
            trace from its end and saying so. With no browser, nothing opens.
        - **Show Details** opens the collapsed section (Hide Details closes it): what the details are for, **Copy
            details** (tonal), then the report. Copy details puts the report on the clipboard, then reads "Copied", a
            polite live region; both labels are laid out, so the button keeps its width. The section opens downward
            under the page, never moving what's above it (`FailurePage`'s `below`): that part is centred as the page first
            opens and stays where it is. The section fills the room left down to the screen's foot and scrolls in it, the
            whole of it (`ScrollingColumn`, its bar in the page's margin); on a screen too short to leave 160dp it gets that
            much, and the page scrolls too. The database page's details are
            always shown.
- **A saved game that can't be resumed** (an upgrade across versions, say) goes to the crash page, then goes for good.
    - Continue offers any saved game. `loadToResume` throws `UnresumableGameException` for one it can't read.
    - `ResumeAttempt` marks what counts: Continue's load and the board's first frames (`LudoPlusNavHost`), and the menu's
        off-screen drawing of the saved game (`SavedGameWarmUp`), which would otherwise fail at every start. Any error nothing
        caught during one, on any thread, counts. One after it, such as on the next roll, is an ordinary crash.
    - `CrashHandler` then writes `SavedGameDiscardFile`, whether or not a page opens, and `CrashPage` says the game will be
        removed (`gameRemoved`).
    - `InProgressGameRepository` removes it at the app's next start, before anything reads the save, however the page was
        closed: Restart, Back, reported or not, or swiped away. No copy of it comes back from the cloud (`FINISHED_KEY`).
- **Not caught**: native crashes and ANRs.
- **Trying it**: a debug build's crash mode on Settings > About (see "Superuser modes"). The taps on the name are
    raw gestures, attached only in a debug build, so no other build has them and TalkBack is never handed a dead link.
    For a crash anywhere else, throw from a temporary line, and take it out again.
- **Tests**: `ResumeAttemptTest`, `InProgressGameRepositoryTest`, `CrashHandlerTest`, `CrashReportTest`, `CrashActivityTest`, `CrashPageTest`, `BreadcrumbsTest`,
    `GameBreadcrumbsTest`, `AboutDebugCrashesTest`, and `DatabaseFailureTest`.

## Leaderboard and Statistics

**By mode** (Phase 9, 2026-11-18): a row of modes under each page's heading (`ModeRow`, each said by its name). The
Leaderboard opens at the mode New Game last started; Statistics opens on "All" (spoken "All modes", every mode's
games), and can show one mode's.

**By player count** (Phase 8, 2026-11-18). Each record keeps its game's `playerCount`, and both pages have a row of
counts under their heading (`ui/records/PlayerCountRow.kt`, TalkBack "4 players"). The Leaderboard has a board a count,
2 to 8 (a bigger board's lap is longer, so turns only compare within a count), opening at the count New Game last
started; Statistics opens on "All" (every game, spoken "All games") and can show one count's games. Filtered in the
query and in `playerStatistics`, with no schema change. A team game records each player at their team's place, and
stays off the Leaderboard (`playedUnmodified` false): a team's win is its players' together, not one player's race.
A game with house rules is in neither (`GameResult.houseRules`; "Modifiers").

**Time played** is a line at the top of Statistics, on every page and with no players yet: `PlayTimeLine`, seconds
counting up for the first minute ("42s"), then minutes, hours and minutes ("12h 34m"), and from 24 hours days, hours,
and minutes, however many days ("Time played: 3d 4h 12m", spoken "3 days, 4 hours, and 12 minutes"). Its numbers are
gold in the brand font (Arabic's digits fall back to the system font, in gold). `LivePlayTimeLine` reads the total
again only when the line would next change, and only while resumed, and holds its state itself, so an update
recomposes only that line. `PlayTimeTracker` counts the app in front of the player, from `ON_RESUME` to `ON_PAUSE` on
the monotonic clock, and saves at each pause and every five minutes while resumed. It's the counter
`AchievementCounter.PLAY_SECONDS`, for the per-device `SUM` in the cloud save and progress files, but stays out of
`AchievementsState`, so it never moves achievement flows or a game's counter write, and out of `changeStamp`, so it
goes with the next save that has something else to send. It started at zero in the version that added it.

The rest of this section is DiceFive's (its drop-down of mode pages and Combined, from `5423b6c`); Ludo's pages pick
by the mode and count rows above. Both pages picked what they showed from a drop-down at the top: a page for each game
mode, Standard's first and the one they opened on, then **Combined**, every mode's games together, last.

- **Leaderboard** (`ScoresScreen`). The drop-down (`ModePage`) picks a board. The pick is the
    `leaderboard_choice` preference (the mode's id, or `combined`), restored on every open and carried in the cloud save
    and the GitHub app's export; a mode no longer known opens Standard's. Details are in `UI_SCREENS.md`.
    - **Every board is the same `LeaderboardTable`**: score-descending, 1,000 a page, 1st-3rd in gold/silver/bronze, "="
        only for a true tie, paged on its own.
    - Every board has only games with no modifier on (`playedUnmodified`). A **mode's board** has its mode's, the same
        ones its online leaderboard takes. **Combined** has every mode's (Stud's and Third Wind's run far above
        Standard's), each name in its mode's colour (`UI.md`, "Mode colours").
    - Rows show rank, name, 5x count and score. A long press shows the date, and on Combined the mode; that is also the
        row's spoken state.
- **Statistics** (`StatisticsScreen`). One card per distinct human *name*; a rename starts a new player.
    - **Pages** (`ModePage`). The drop-down picks one; the pick is the `statistics_choice` preference (as the
        Leaderboard's). A mode's page has that mode's games alone: its own numbers, win streaks, best score and first game
        (`PlayerStatistics.forMode`), and a player with no game in it has no card there. Combined has every mode's games
        together, and every player.
    - Closed, a card is the name and the page's best score ("1,287pts", the unit at 0.75 of the number's size). Its
        header opens it.
    - **Open**: games, win rate and average (in points), then a won/lost/solo bar with its key, then the current and
        best win streak, 5x scored, dice rolled, total points, and first played (date and time), all of the page's games.
        On Combined, a table follows: a row per mode played, in mode order (games, win rate, solo games and best score, the name in the
        mode's colour). Its total points are the player's (by name); Professional Roller's are seat 1's.
    - **Open by default** when every card fits on screen opened (the list is laid out with every card open under the page cover, which
        builds only the cards on screen, and they fit if it has nothing to scroll; more than 12
        players skip the measure and don't fit). Otherwise all start closed but the top card of each column (the first
        one in portrait, the first two in landscape), which always open. Opening a card never closes another. A new page
        deals them out afresh.
    - **A closed card's open content is built ahead** while the page is still, one card on screen a frame, and kept
        built, unseen, silent and taking no room (`OpenAhead`, `BENCHMARKS.md`); opening only gives it its room.
    - **Sort** (`StatisticsSort`), **each page its own**: a button at the app bar's end opens a menu of Name, First
        played, Highest score, Highest average and Most games, each by the page's numbers. The one in use is marked with
        an arrow, up for ascending and down for descending; picking it reverses it, picking another starts it in its own
        direction (A to Z and oldest first for Name and First played, highest first for the rest). Ties go by name. A new
        order deals the cards out afresh: the top card of each column opens, the rest as at the start. Each page's sort is
        a preference (`<id>:asc` or `<id>:desc`, `StatisticsSortOrder`): `statistics_sort_<mode id>` for a mode's page,
        and `statistics_sort` for Combined's (the one sort there was before 1.5.0, when every card held every mode).
        They're loaded with the app container and carried in the cloud save and the GitHub app's export.
    - Above 1.3x font, the header's score goes under the name, the details put the label over the value, and each mode
        table's name takes its own line over its numbers. A mode's name, or a heading, shrinks to stay on one line; each
        stacked column's share of the row is its widest text.
    - **Win rate** is won / (won + lost), a whole percentage rounded half up. Solo games aren't in it; with only solo
        games it's shown as "-" and not spoken.
    - TalkBack: the drop-down is one ("Statistics, Standard"). A card's header is one stop (name, best score, its state
        and action), its numbers two, and each mode table row one, in full sentences. The headings and bars are silent.
    - Every recorded game with no modifier on counts (`playedUnmodified`, in both Statistics indices). A solo game's
        outcome is null: it isn't a win or a loss, and it doesn't break a streak.
    - Each card has a delete button (top end, open or closed) that dismisses it after a confirmation; the scores stay.
        Playing again under that name brings it back. It goes from every page, as the confirmation says.
- Settings' "Reset leaderboard" deletes every score, and with them the Statistics.

## Achievements

`model/Achievement.kt` (110: 100 counted, 10 Easter Eggs), `game/AchievementTracker.kt` and `game/AchievementEngine.kt`
(pure), `data/achievements/`, `app/AchievementUnlocker.kt`, `ui/achievements/`. Rebuilt for Ludo in Phase 6; DiceFive's
110 are in `5423b6c`.

- **Ids.**
    - The enum constant is the English title in snake case; the stored `id` is that in lower case
        (`GETTING_COMFORTABLE`/`"getting_comfortable"`). `AchievementEngineTest` enforces it. Permanent once released.
    - String keys follow the constant: `achievement_<constant>_title`, `_description`.
- **Player 1 only.** The person in seat 1 earns them; every other seat, person or CPU, is an opponent. The tutorial
    earns nothing.
- **The catalog** is `model/Achievement.kt` itself, in display order: Milestones (19), The Die (8), Captures (16), Racing
    (12), Winning (17), Misfortune (17), Miscellaneous (10, hidden), Collection (Completionist), and Easter Eggs (10,
    hidden, outside Completionist and the page's total): 100 counted, as DiceFive had. Each entry's description is its rule; the thresholds are the
    tracker's constants (`AchievementTracker`'s companion). The maintainer is reviewing the list (asked for 100, plus 10
    Easter Eggs, on 2026-11-17, "a variety of them in groups, like DiceFive").
    - Speed Demon (60 turns) and Lightning Fast (50), and Punching Bag (15 captured), come from simulated games: a win
        takes player 1 about 80 turns, the fastest tenth about 60, the fastest hundredth about 50.
    - Easter Eggs: Alliteration (every name starting with one letter), Are You A Developer? (the version tapped seven
        times), Big Fan (a person named "zodac", exactly), I Am Not A Robot (player 1 named "CPU", any case), Midnight Run
        (a new game from midnight to 6am), Musical Chairs (nobody in their seat's own colour), Sideways (a game shown
        with the phone on its side), Time Flies (an hour of one game on screen), What's In A Name? (a person's name as
        wide as the setup form allows), Who's Ludo? (anyone named "Ludo", any case).
    - Wire To Wire needs the whole game seen (from player 1's first roll): one continued from its save can't earn it.
    - Opening the Rules (Read The Manual), Statistics (Number Cruncher), or About (Who Made This?), and finishing the
        tutorial (Back To School) earn hidden ones. The tutorial earns nothing else.
- **Counters** (`AchievementCounter`): games played, games won, the win and losing streaks, captures, pawns home,
    rolls, 6s, squares moved, and last places. Each
    moves as its event happens, never at a game's end only, so a game left unfinished still counts what was played.
    - A game counts as played the moment player 1 is placed; a win the moment they are first.
    - The win streak goes up with a win, and back to 0 the moment another player wins; the losing streak the other way.
    - Rolls, 6s, and squares moved wait to be written with the next step that earns anything else, or when the game leaves the screen
        (`AchievementTracker.flush`), so a roll alone doesn't write the store.
- **When they're judged.** The engine decides, never a call site.
    - `AchievementEngine.atGameStart`: The Journey Begins, One More Time, Big Fan, Midnight Run. `atGameResume`: Let's
        Finish This.
    - `AchievementTracker.observe`: every roll and move of a game, from the state before and after it (`GameEvent`),
        as it's played. What one `GameState` can't show (a lap, a turn's captures, who last captured player 1, a
        trailing or leading moment) it keeps, for that game only: made afresh for a game continued from its save, which
        only ever makes one harder.
    - `AchievementEngine.evaluate`: the step's feats and counts against the stored state; a counter reaching a target
        unlocks it, and the last of the rest brings Completionist.
    - Every unlock goes through the app's one `AchievementUnlocker` (`AppContainer.achievementUnlocker`), whose locks
        (its own and the store's `locked`) serialise them all across screens, so nothing is announced twice.
- **Modes.** One mode for now; the audit is in `GAME_MODES_AUDIT.md`.
- **Display.**
    - Declaration order is display order: grouped by `AchievementCategory`, easiest first. `AchievementEngineTest`
        fails if a category is split, or a counter's ladder is out of order; `AchievementTextTest` holds Winning,
        Misfortune, Miscellaneous, and Easter Eggs alphabetical.
    - Every Miscellaneous and Easter Egg achievement is `HIDDEN` (the title is shown, the description is "???" until
        earned), and only those are.
- **Progress banners.** A streak announces every step. A running total announces only at quarter marks (or every
    `progressStepSize`), and never alongside its own unlock.
- **Superuser modes (debug builds only).**
    - In a game, a long press on the die turns it on, with a toast; each long press after that offers the next roll's
        number (1 to 6, or a fair roll), which whoever rolls next rolls, person or CPU (`GameViewModel.onDieLongPressed`,
        `pickNextRoll`; never in the tutorial). The dialog's words aren't translated.
    - On the Achievements screen, tapping the count banner `SUPERUSER_TAP_TARGET` times turns on long-press editing:
    - a long press on a row locks, unlocks or steps it (in-memory, never written);
    - a long press on the banner toggles everything.
    - On Settings > About, tapping the author's name (`zodac`) `DebugCrashes.TAP_TARGET` (5) times turns on the crash
        mode (see "Errors").
- **Not brought back yet.** Style rewards and the styles banners (Phase 7), the Play Games mirror and cloud merge
    (Phase 10), and DiceFive's per-mode limits and tips (Phase 9).

## Table art (Styles)

Four independently saved categories (`ui/style/`), bundled as `GameVisualTheme` and provided to the game screen as
`LocalGameVisualTheme` from the app's saved picks (`AppContainer.savedStyles`):

- boards (`BoardStyle`): the board under the pawns, and the spot a home pawn stands on
- pawns (`PawnStyle`): one pawn, always in its player's colour
- die (`DiceStyle`): the one die, its face and any tumble of its own
- backgrounds (`TableBackground`): the game screen behind the board, from the foot of the app bar down
    (`ScreenScaffold`'s `underlay`)

The Styles page's fields and the category pages are in `UI_SCREENS.md`. The Rules diagrams always draw the Classic
board and pawns.

**Boards and pawns** keep the players' colours readable: a board style changes the track, the safe squares' marks, the
surround, and the texture, never which colour a seat is; a pawn style changes shape and material, never whose it is. A
board or pawn tile on the Styles page is a corner of the board (`BoardCornerPreview`): seat 0's yard with three pawns,
one on its start square, and the next seat's pawn on the track, in the default seat colours, the tile's style drawn
with the player's pick of the other.

The boards are Classic, Wood, Ocean, Retro, Paper, Starry, Stone, and Neon. Every family but Classic is a
`LayeredBoardStyle` (`ui/style/BoardFamilies.kt`): a ground, the squares, the yards, the home triangles, the safe
squares' marks, and a finish over it all (grain, speckle), each the family's own, with the seats' colours drawn through
its `seatTone` (tinted towards the material, never so far that two seats look alike). `LudoBoard` draws the board on a
Canvas of its own under the pawns, which reads only the style and the seat colours, so a texture isn't redrawn on every
frame of a move.

The pawns are Classic, Counter, Cone, Peg, Glass, Animal (Cat and Duck), and Gems. Every family but Classic is a
`GridPawnStyle` (`ui/style/PawnFamilies.kt`), which draws the shadow and the movable ring on one footprint for all of
them and leaves the family its body. Gems takes the Gems die's lock; the rest open at 5, 20, 25, 30, and 40
achievements.

**The die** is DiceFive's 35 families, D20 included (drawn showing 1-6). `GameDie` draws the style's face, turning
through random faces as it tumbles; a style that tumbles itself (the D20, the Egg, the Bestagon) is handed the tumble's
clock (`LocalDieTumbleMillis`) instead. Before the first roll it's the style's body colour with no face. The violet ring
while it can be rolled is drawn round the style, not by it.

**Backgrounds** are DiceFive's 20 families. One that moves (the Ocean's Waves, Floating Dice)
runs on the Background decoration group; a die's own sparkle or glow on the Die group (`MotionGroup.DICE`).

Each category is two lists in the same order:

- **The registry** (`game/style/StyleRegistries.kt`, pure): `StyleGroup`s (a shape or pattern, one tile on the Styles
    screen) of `StyleVariant`s, with every id, name and lock. Anything that decides what a player may use (the unlocker,
    achievements) reads only this, through `StyleCategory.registry`.
- **The art** (`ui/style/StyleCatalog.kt`): each variant's `StyleArt` (swatch and drawing). `StyleCatalog` joins it to
    the registry by id into the Styles screen's `StyleFamily`s of `StyleColour`s, and refuses a mismatch at load.

- **Ids** are the family's string key, then the colour's (`classic_ivory`, `ocean_lagoon`), and permanent once
    released. `StyleRegistryTest` enforces this. The die's and background's are DiceFive's, as are their saved keys
    (`dice_style_id`, `table_background_id`); the board's and pawns' are `board_style_id` and `pawn_style_id`.
- **The Classic family** comes first in every category and is free. Its first colour is the default: the cream board,
    glossy pawns, the ivory die, and the navy felt.
- **A style with a counterpart of the same name is unlocked by the same thing** in every category that has one:
    Stone opens at 65 achievements, Neon and RGB at 100, the water set (the Ocean board, die, and background) at
    15 (`WATER_SET_UNLOCK`). A board family locks with the die of its name; Paper, which has none, opens at 50.
    `StyleRegistryTest` checks every family name across the categories.
- **Locks.** Every other family is locked behind a count of achievements (`StyleUnlock.AchievementCount`) or one
    achievement (`SpecificAchievement`).
    - A count is of **counted** achievements: every one but an Easter Egg (`AchievementsState.countedUnlocks`), as the
        Achievements page totals them. No count lock is above 100.
    - The styles DiceFive locked behind its own achievements (2026-11-17): the Starry die and background open with
        Midnight Run; Devil with Too Much Of A Good Thing; Maths with Number Cruncher; D20 and the Floating Dice
        background at 55, the one count no other style uses (`D20_UNLOCK`). The Irish, golden Egg, and D20 colours
        that DiceFive kept secret are ordinary colours of their families. Googly (Completionist), Glitch (Big Fan), and
        Wireframe (Are You A Developer?) keep their achievements.
    - A colour can still be secret (`StyleVariant.secretAchievement`): not offered at all until earned. None is yet.
    - Every lock takes a `StyleAccess`: the achievements, and `ownsAllStyles`, which meets every lock. Until the
        purchase (Phase 11), only a debug build's pretend switch sets it: five taps on the Styles page's unlocked count.
    - A locked saved pick draws the default but stays saved.
- **Unlock banners.** Earning a style's achievement, or crossing its count, puts up a styles banner after the unlock
    (`announce`); a long press opens the one category's page its styles are in, or the Styles page with their fields
    flashed (`Screen.stylesBannerTarget`). An achievement that unlocks a style wears a star on its row and banner, and
    its row's tap shows which (read with the row by TalkBack).

## Sound, haptics and motion

- **Clips** (Ludo Plus): `mat_landing` (the die landing), `pawn_step` (at 0.5 volume, once for each square a pawn moves,
    in time with it), `capture` and `pawn_home` (in place of the landing's step), and `celebration` (a person's win, with
    gold fireworks). All CC0 from Freesound, chosen by the maintainer 2026-11-17. The cues are `moveSoundCues`
    (`ui/game/GamePacing.kt`): with Pawn moves motion off, only the landing's sound.
    - The sources are `app/audio/*.ogg`.
    - They're peak-normalised to -0.8 dBFS by ffmpeg at build time (`scripts/normalize-audio.sh`): never `res/raw/`,
        never hand-adjusted. Android packages Ogg copies (`NormalizeOggAudioTask`). `scripts/tests/normalize-audio-test.sh`
        checks they keep each clip's length (the shake window is timed to them) and peak.
    - The app has one player (`rememberAppSoundEffects`, provided at `LudoPlusApp` as `LocalSoundEffects`). With sound
        on it loads its clips off the main thread a second after launch; otherwise on its first sound. It's held in the
        background and released with the app, so a sound started on one screen carries on to the next.
    - The app follows the Settings switch itself; no screen passes the setting in.
- **Haptics.** A tick on hold or unhold, and a buzz for the shake. Settings switches sound and vibration off
    separately. Vibration is wired like sound: one `DiceHaptics` (`rememberAppHaptics`, `LocalDiceHaptics`) that follows
    the switch. Only the player's own rolls and holds vibrate: a CPU's roll still plays its sounds but never buzzes.
- **The roll.**
    - As the cup shakes, unheld dice are swept into it.
    - On landing they're thrown up their own columns, bounce off the far wall, and tumble to rest on their results. A
        flat-faced die tumbles as a cube of its own faces, starting from the face it showed, so no face ever jumps.
    - The toss starts in the frame its dice arrive (`RollTracker`), so the result never flashes first.
    - Scoring and the next roll both wait for the dice to land.
    - Each die casts one shadow from a fixed light.
- **Animations setting** (`AnimationLevel`): High (uncapped), Medium (60fps, ambient decoration still), Low (30fps,
    simple roll) or Off, each a preset of the Graphics page's **Advanced graphics settings** (`MotionSettings`): Off, 30fps, 60fps
    or Max for each style's decoration (Dice, Dice Cup, Mat, Background, and Boxes), Menus & pages, Banners, Scores, Celebrations, Score glow, the cup's
    shake, and the dice's roll (no Off), the dice's roll itself (Off, In place, or Tossed), and Pause when idle (High
    and Medium 60s, Low and Off 30s). The system's
    remove-animations setting counts as Off. Details are in `UI.md`, "Reduced motion".

## Settings

- **Contents.** The online account card (the play app) or the progress files card (the github app), at the top; switches
    for Sound effects, Vibration, Confirm leaving game, and Tricolour shapes; the **Graphics** row; and the leaderboard
    reset behind a confirmation.
- **Graphics** (`GraphicsScreen`, `GraphicsViewModel`, route `settings/graphics`): a page of its own, opened by a button
    row with an onward arrow. The Orientation, then the Animations level, each under its title, in one card; then the
    Advanced graphics settings, under their own heading in three cards, each collapsed until opened (`UI.md`, "Reduced
    motion"). The Gameplay card also holds **Pause when idle** (`IdleTimeout`, `motion_idle_timeout`: 30s, 60s, 90s, or
    Off), how long an untouched screen waits before its infinite animations freeze, preset by the level like the rest.
    None is synced: they're about this device. Tests: `GraphicsScreenTest`, `AnimationLevelSettingTest`,
    `MotionSettingsTest`, `IdleTimeoutSettingTest`, `ScreenIdleTest`.
- **Tricolour shapes** (`tricolour_marks`, synced, off by default): a triangle, circle, or square for red, yellow, or
    blue, on a Tricolour die's corner (`DieColourMark`), in the game and the Rules page's examples, and in its
    colour's box (`BoxColourMark`), for telling the dice apart without their colours. Not while a die is tossed, and
    not spoken, as a die and a box already say their colour. Tests: `ColourMarksTest`, `RulesScreenAccessibilityTest`.
- **Orientation** (`ScreenOrientation`): Device (the default: turns as the system allows), Portrait or Landscape. Not
    synced, as a phone and a tablet can want different ones. `LudoPlusApp` hands it to `PlatformServices.lockOrientation`;
    on Android `MainActivity` sets `requestedOrientation` (sensor portrait or landscape, so either way up). The manifest's
    `android:appCategory="game"` is what lets the lock hold on a large screen: Android 16+ ignores it there otherwise.
    Tests: `ScreenOrientationSettingTest`, `OrientationLockTest`, `GraphicsScreenTest`.
- **Foot of the page.** The version pill is the last thing on the page (not pinned), after About and **Licences**: every
  library, font and sound, with its licence
    text, generated at build time (`app/licensing/README.md`).
- **Licence guards.** The build fails on a copyleft or unknown licence, a missing text or copyright line, or an
    unlisted bundled asset.
- **The Licences list** is one card per licence. On Android each card is **one platform `TextView`**
    (`TextViewLicenceDocument`), in one `ScrollView`, with the app's scrollbar.
    - Only that gives selection across a card's rows without running into the next card, system long-press and smart
        URL selection, and a Copy link / Copy text menu.
    - Compose failed on a device:
        - a `LinkAnnotation` opens on every press inside a `SelectionContainer`;
        - a custom link menu raced selection and crashed;
        - a selection in a dialog doesn't clear;
        - a selection can't span separate texts.
    - The `AndroidView` needs `clipToBounds()`, or scrolled text draws over the title.
    - Tests: `LicensesDialogTest`.

## Rules screen

`ui/rules/RulesContent.kt` (the pages: `RULES_GROUPS` and its block types) and `ui/rules/RulesScreen.kt` (drawing them).

- **Layout.** Two tab rows: groups, then the pages of the chosen group. One pager runs across every page. There is only
    **Gameplay** so far (How to Play, The Board, Your Turn, Captures, and Winning), so its group row isn't shown;
    **Modes** and **Modifiers** join it in Phase 9.
- **Diagrams** (`RulesDiagram.kt`): a crop of the real board (`drawBoard`, `drawPawn`, and `ClassicBoardLayout`'s positions),
    in the default seat colours, with pawns, ink arrows (dashed for a pawn sent back), and a die. One image to TalkBack,
    described in words (`rules_*_diagram_cd`).
- **Accuracy.** The text is kept in step with the engine and `IMPLEMENTATION.md`'s "The classic rules".
- **New mode or modifier.** It gets its own page; see `GAME_MODES.md`.
- Tests: `RulesTextTest` (every page's words and diagram), `RulesScreenAccessibilityTest`, `RulesTabIndicatorTest`, and
    `RulesScrollbarThumbTest`.

## Distribution

Two apps from one source, one per way the app reaches players: product flavours of `app/android` (dimension
`distribution`), each with its own source set (`src/play`, `src/github`) and one seam, `Distribution`, which
`MainActivity` and `LudoPlusApplication` call.

| | `play` | `github` |
| --- | --- | --- |
| Ships as | The App Bundle, uploaded to Google Play on demand (`play-release.yml`) | The APK on each GitHub release |
| `applicationId` | `net.zodac.ludo` | `net.zodac.ludo.github` |
| Google Play Games | Yes (`PLAY_GAMES.md`): the SDK, its manifest entries, Sidekick | None: no SDK, no manifest entries, `NoGameServices` |
| Progress files | None, signed in or not | Export and Import, in Settings |

- **Kept apart so nothing from a file reaches Play Games.** A file's progress can only be imported into the github
    app, which has no Play Games code, and the github app's data can't reach the play app's: they're separate packages
    (data, backups and installs are per package), signed by different keys (Play re-signs the bundle). Nor can an
    edited `adb` backup: up to Android 11 the play app's backup holds only the settings (`PLAY_GAMES.md`, "Account and
    sign-in"). Pinned by `GithubFlavourTest` and `PlayFlavourTest`.
- **The github app is a different app.** It installs beside the play app, not over it; a GitHub install from before
    the split (`net.zodac.ludo`, 1.0.0) doesn't update to it.
- **Progress files** (`online/ProgressFileCodec`, `ProgressTransfer`; on Android `AndroidProgressFiles`, through the
    system's pickers, with no storage permission).
    - **What's in one**: everything a cloud save holds (`CloudSaveStore.snapshot`), as its own bytes (`CloudSaveCodec`).
    - **An import is the cloud save's merge from another source**: `CloudSaveStore.merge`, then what the merged totals
        reach (`unlockReachedTargets`), so the same rules apply (`PLAY_GAMES.md`, "Cloud save"). Nothing is picked or
        thrown away, and importing a file twice changes nothing. A newer app's file is refused whole, with "update the app".
    - **Sealed, not plain**: `Ludo Plus`, a format byte and a random nonce, then the save XORed with an HMAC-SHA256
        keystream, then an HMAC-SHA256 of all of it. A file changed by one bit, or sealed by another build, isn't a progress
        file. The key is CI's `PROGRESS_FILE_KEY` secret (64 hex digits), never in the source; a build without it uses an
        all-zero key, and the release workflow refuses to build the APK without it. **The key is permanent**: files made
        with one can't be opened with another.
    - **What it stops**: editing a file by hand, or with a tool written from this public source. Not someone who pulls
        the key out of the APK, or edits a rooted phone's own data, which no offline app can stop. Neither reaches Play
        Games, as above.
    - Named `LudoPlus-<yyyy-MM-dd>.ludo` (the player's day). Tests: `ProgressFileCodecTest`, `ProgressTransferTest`,
        `ProgressFilesViewModelTest`, `ProgressFilesSettingsTest`.
- **Settings card** (`ProgressFilesCard`): Export progress and Import progress, each a whole-row button (a compact
    `SettingRow`, its label alone), where the online card would be. Import asks first ("Import progress?"). Under the
    rows, while an import runs, its step and count over a bar, drawn by the same `StepProgress` as the online card's
    sync ("Opening the file", then the same four merge steps, `MergeStep`, named the same: `MergeStepTest` keeps the two
    flows in step), for at least 1s (`IMPORT_SHOWN_MILLIS`, shared out between the steps, so a quick import's bar still
    visibly runs: the card catches up with the import a step at a time, `nextShownStatus`, counting only while the app
    is in front); then how the last export or import ended, in red if it failed, until the next starts. No toasts. The
    line is a polite live region: TalkBack hears "Importing progress" (not each step), then the outcome.

## Release pipeline

- **Version.** `VERSION` at the repo root, the one app's single source: `versionName` is its contents, and
    `versionCode` is `major*1000000 + minor*1000 + patch` (`AppVersion`). Up to 1.2.1 it was `major*10000 + minor*100 +
      patch`; every new code is above every old one, so updates install over them.
    - `scripts/set-version.sh X.Y.Z` writes it, and `scripts/bump-patch-version.sh` bumps its patch. The `android-v` tag
        prefix leaves room for another app.
    - Debug builds are signed with the repo's `app/android/debug.keystore`, so every debug APK shares one SHA-1 (which
        has no Play Games credential, as the key is public: `PLAY_GAMES.md`, "Play Console"). Built with `-PdevStamp`,
        one also takes the minute it was built as its `versionCode` (`<version>-dev<minute>`), so each installs over the
        last. Without it, a debug build keeps `VERSION`'s, so test
        and lint runs stay up to date. The minute is read through a `ValueSource` when the build runs, never while the
        script is evaluated, or the configuration cache would hand every build the same one.
- **On every push to `main` that changes what Android ships**, `.github/workflows/android-release.yml` runs:
    - a `version` job that picks what's built: `main` as it is when the run starts, never the pushed commit. Runs queue
        one at a time, so a push made during a run builds after that run's bump, not the version just released. It fails
        when `android-v<VERSION>` already exists (a release's files are never replaced), and releases nothing when the
        only commits since the last release are the bot's own (every other job is then skipped);
    - parallel jobs, each on that commit: Android tests (a runner per flavour), shared tests, lint, the upgrade check
        (a game on the last release's APK must resume on this one; `EMULATOR.md`, "The upgrade check"), the Baseline Profile
        (generated fresh on an emulator from the play app, and given to both; see `EMULATOR.md`), and the github APK plus
        the play App Bundle;
    - then a `release` job that publishes GitHub release `android-v<version>` once every check has passed, with
        `LudoPlus-<version>.apk` (github), `.aab` (play), `-mapping.txt` (the APK's) and `-play-mapping.txt` (the bundle's),
        tagging the commit built;
    - then a commit that bumps the patch and empties `RELEASE_NOTES.md` (`[Release] Bump Android version to X
        [skip ci]`). A `VERSION` set by hand on `main` during the run is left as it is (`[Release] Clear the release notes
        after X [skip ci]`).
- **To Google Play, on demand**, `.github/workflows/play-release.yml` (run by hand: a release tag, blank for the newest,
    and a track) uploads a GitHub release's `.aab` and its play mapping to that track as a completed release. Nothing is
    built.
    - **`publish`** (boolean, default on) decides whether the update is sent for review. Off, it is only prepared: the
        upload lands in Play Console with `changesNotSentForReview`, to be sent from Publishing overview.
    - **What's new** (en-GB): the GitHub release's URL, a blank line, then as many of its bullets as fit whole in Play's 500
        characters, without their commit links, then "..." for the rest (`scripts/play-release-notes.sh`, tested by
        `scripts/tests/play-release-notes-test.sh`).
    - The commit sends the edit for review with Play's default, which "send[s] all the changes for publishing". Whether
        that includes listing changes saved in Play Console isn't confirmed yet. Play Games achievements and boards are
        published in Play Console: the API can't.
    - Signs in with the `PLAY_SERVICE_ACCOUNT_JSON` secret, a service account invited in Play Console with release
        rights. Play never takes a `versionCode` twice, so each release goes to Play once.
- **Beside it, not part of it**, `readme-assets.yml` renders the README's feature graphic and screenshots from the
    same push and replaces the files on the `readme-assets` release (`STORE_ASSETS.md`, "The README's images").
- **Release description.** `RELEASE_NOTES.md` (the maintainer's), then "## Changes since X" from
    `scripts/release-changelog.sh`, grouped by each commit's `[Category]`.
    - The notes are the whole file, as written; it's kept blank between releases. A release takes it all, then empties
        it (`scripts/release-notes.sh`, tested by `scripts/tests/release-notes-test.sh`).
    - The changelog lists the commits since the previous `android-v` tag.
- **Signing.** Four `ANDROID_RELEASE_*` env vars (repo secrets in CI); without them the build is unsigned. The keystore
    (alias `upload`) is the Play upload key, and signs both the bundle and the github APK.
- **`PROGRESS_FILE_KEY`** (a repo secret): the github APK's progress file key ("Distribution"). The apk job fails
    without it.
- **`PLAY_LICENCE_KEY`** (a repository variable, not a secret: it's a public key): the play app's licence key, which
    purchase signatures are checked with (`PURCHASES.md`, "Entitlement"). The apk job fails without it, or on one that
    isn't one-line Base64.
- **R8** shrinks and obfuscates release builds (`app/android/r8-rules.pro`). Release dex is stored uncompressed.
    - It lays the dex out from the startup profile (`dexLayoutOptimization`): the code a cold start runs to the menu
        (`StartupProfileGenerator`) goes first. The release workflow fails if it generates no `startup-prof.txt`.
- **Dependencies.** `.github/workflows/update-dependencies.yml` bumps every pinned version monthly, re-runs the gates,
    commits `[Dependencies] Update dependency versions` and starts a release. It never moves targetSdk. The update and
    its gates run read-only, with no credentials in the checkout; a separate job that runs no build applies their patch,
    pushes and starts the release.
- **The Gradle wrapper** pins `distributionSha256Sum`, which the update script moves with the version.
- **Warnings are errors**: deprecated APIs, build-script warnings, and lint's `Deprecated`/`ObsoleteSdkInt`.
- **Two known plugin warnings** can't be silenced. Revisit each on a plugin upgrade.
    - AboutLibraries resolves configurations during configuration time ("`playDebugCompileClasspath` was resolved during
        configuration time").
    - AGP calls the deprecated `Configuration.setVisible`. It must be gone before Gradle 11.

## Verification

- **Gates and tests**: the `test` skill.
- **Renders**: the `render` skill.
- **The real app on the sandbox emulator**: the `emulator` skill.
- **A real device is still needed** for TalkBack's speech, real frame times, haptics, the accelerometer and gestures
    Robolectric doesn't simulate. Say what was and wasn't seen.
