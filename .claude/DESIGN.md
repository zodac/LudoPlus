# LudoPlus: design reference

What the app does and why. The interface is in `UI.md` (plus `UI_BOARD.md` and `UI_SCREENS.md`), modes in
`GAME_MODES.md`, and art in `STYLE_ART.md`.

## Stack

- Platform code sits behind three seams. View models take an `AppContainer`, never a `Context`.
    - `platform/PlatformServices`: sound, haptics, accelerometer (the googly dice's pupils), transient messages, licences, reduce motion, frame cap.
    - `BuildInfo`: version and debug flag.
    - `AppContainer`: what each platform's entry point builds.
- There is no `kotlinx.serialization` or `kotlinx-datetime`; both were dropped to keep the APK small.
    - `data/Json.kt` is a small strict JSON reader and writer.
    - Timestamps go through `expect fun formatTimestamp`.
- One dark theme only.

## Screens

```text
NavHost(start = "menu")
  "menu"         MenuScreen - Play (New Game | Continue), Achievements, Leaderboard, Statistics, Styles, Rules, Settings
  "play" graph   one GameViewModel scoped to the graph (no player lists through nav args), shared by:
    setup        GameSetupScreen
    lobby        HostLobbyScreen (a Nearby game's host)
    game         GameScreen, then GameOverScreen and the board again to review (Continue comes straight here and loads
                 the saved game)
  "join" graph   finding and joining a Nearby game, then its board
  leaderboard, statistics, achievements, styles, rules, settings
  tutorial       GameScreen on a GameViewModel of its own, playing TutorialScript.CLASSIC (Rules' How to Play page, or New Game)
```

The routes are `Screen` (`navigation/`); the pages are in `LudoPlusNavHost`.

## Game setup

- **Players.** 2 to 8, four at first. Player 1 is always a person, and the only seat that earns achievements. The others
    are a person, the computer (Easy, Medium, or Hard; Medium at first), or in the play app a phone nearby. A computer
    player's name is generated as the game starts, unique in it (`AiNameGenerator`).
- **Names.** An empty name field shows a localised "Player N" hint, and the game uses that. Names are sanitised as
    they're typed (`model/PlayerName.kt`) and held to the most players' cap (`GameSetupForm`, `model/NameWidth.kt`: 8),
    so changing the count never cuts one. No two people may have the same name (`duplicateHumanNameSlots`): the form
    flags it, and `startGame` refuses it.
- **Colours.** Each player picks a distinct colour (`PlayerColour`).
- **Teams** (4, 6, or 8 players), the **mode**, the **house rules**, and the **custom modes**: "Game modes" and
    "Modifiers".
- **Remembered.** Everything on the form is saved for next time in `SettingsRepository`.
- **First game.** "New to the game? Play the tutorial" shows until a person has finished a game, and house rules and
    custom modes are locked until then ("Modifiers"). The modes aren't.

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
    pawn is ringed and offered to TalkBack. The CPU plays each of its steps only when the player taps Next, and a part ends on one
    too, so nothing carries on before its words are read (the maintainer, 2026-11-24).
- **The coach card** (`CoachCard`) takes the player cards' place: "Coach" and "Part 2 of 4" over the step's words, with
    back and forward strips at its edges that jump between parts. It's as tall as the longest step needs at the font in
    use up to 130%; past that the words scroll inside it. The name and words are a polite live region, read out as each
    step arrives; the part is a node of its own.
- **Nothing is kept.** No saved game (Continue stays on the player's own), no records, and the setup form isn't saved.
    `TutorialGameTest` plays it through and checks.
- **Leaving** always asks ("Leave the tutorial?"), whatever the confirm-before-leaving setting. Its Game Over shows the
    winner alone and a line that it's done, then Done (back where it was started) or New Game.

## The turn and the rules

The rules are in `IMPLEMENTATION.md`, "The classic rules", and each mode's in "Game modes". Every number comes from the
game's `GameRules`.

- **`game/LudoEngine` is pure**: `roll` (drawing from an injected `Random`), `applyRoll`, and `move` each take a
    `GameState` and return the next. `LudoEnginePropertyTest` plays random games through it.
- **A turn** is a roll, then a move if there's one; a roll with no move passes. `GameState.phase` is what the game waits
    for, with the legal moves, and `lastEvent` is what just happened, for the screen to show and say.
- **One choice makes itself**: a person's only move is made for them once the screen has caught up
    (`LudoEngine.automaticMove`), never in the tutorial.
- **A free pawn** (`IMPLEMENTATION.md`, "Turns") is a move of its own (`Move.free`), awaited as the player's turn
    starts (`TurnPhase.AwaitingMove.free`: no roll, no dice; the die keeps showing the last), so it animates, is logged
    (in the turn it starts), and goes over Nearby as any move does; it always makes itself, and the player then rolls.
    The prompt says "Sam still has no pawn out after 5 turns, so one comes out free.", and the log "Sam brought Pawn 1
    out free, with none out after 5 turns." Saved as `"free": true` with no roll, and `pityFrom`; a game saved before it
    counts from the next pawn out. Whether a player has had a pawn out is worked out (`PlayerState.haveLeftYard`), not
    stored. In Survival there's no yard, so No Free Pawn is locked there.
- **The computer** rolls and moves with a pause before each (`GamePacing`), waiting out the die and the pawns. The pauses
    stop while the game is in the background or under a dialog.
- **The end.** The last player home (or left) ends the game, or the first with "Stop at first winner" on. Every placing,
    with each person's turns, captures, and finish time, goes to the results, and people's to the records.

## Game modes

`model/GameMode.kt`. **Every rule that can differ between modes is a field on the mode's `GameRules`; there is never
an `if (mode == X)`.** `GAME_MODES.md` covers adding or changing a mode.

| Mode | Rules beyond Classic | Leaderboard and Statistics | Achievements |
| --- | --- | --- | --- |
| Classic | The classic rules | Yes | Yes |
| Colour Rush | The track's squares are claimed: the first to land on one has it, and a capture there takes it; the most at the end wins | Yes, its own, by most squares | Its own, and all but a few |
| Survival | Six pawns each, starting out, no home: a captured pawn is out of the game, and the last player with one wins; Overtime, Sudden Death, and the Cataclysm late on | Yes, its own, by the furthest travelled | Its own, and all but a few |
| Chaos | Power-ups on the track, taken by the pawn that ends its move on one | Yes, its own | Its own, and all but a few |
| Haunted House | Ghosts on the track, which send the pawns they land on back to their yards | Yes, its own | Its own, and all but a few |
| Portals | Pairs of portals on the track: a move ending on one comes out at its pair, either way round, and the pair opens again elsewhere | Yes, its own | Its own, and all but a few |
| Two Dice | Two dice, each moved on its own by any pawn; a 6 on either brings a pawn out; doubles roll again, not 6s | Yes, its own | Its own, and all but a few |

Every mode has its own Leaderboard (one a player count) and its own Statistics page, and earns achievements (the
maintainer, 2026-11-24; "Achievements"). The Rules
screen has a Game Modes group, a page a mode beyond Classic.

**Colour Rush** (`GameRules.claims`, `ColourRushEngineTest`; the maintainer, 2026-11-24, in place of Quick). The first pawn to
end a move on a square of the track claims it for its player (`GameState.claimed`), and it's no safer for it. It only changes
hands when another player captures there, whoever's pawn that was. Star squares can be claimed (the maintainer, 2026-11-25),
and never change hands, as no pawn there can be captured. Start squares can't be, whoever sits where
(`GameState.claimableSquare`), nor can squares passed over, home columns, and home: 48 of the classic board's 52 can
(`BoardGeometry.claimableSquares`). The game ends with every one of them claimed, or a player (in teams, a team) with every
pawn home; everyone is placed at once, by squares (a team's together), a tie by how far their pawns have come, then by who
plays sooner. Four Medium CPUs claim them all in 96% of games, two in 23%; games are about half of Classic's turns with four,
and the player who's home wins over half of two-player games. A claimed square is drawn as its player's home column is, in
every style (`BoardParts.claims`, `ClaimedSquareTest`), a star's mark its full size on it (`drawClaimedMark`), part of the board's
painted image, once the pawn has landed. Cards have a count on a square of the player's colour; the Leaderboard ranks the most
squares (`GameResult.squares`), then captures, as do its online boards (the squares alone). TalkBack: "Tile 24, Yellow's tile, empty", "Move Red
pawn 1 3 tiles, claiming the tile", "0 pawns home, 2 tiles".

**Two Dice** (`GameRules.dice`, `rollAgainOnDoubles`). A roll is both dice (`LudoEngine.applyDice`); the move phase
holds the dice still to move, the whole roll, and whether it has earned another roll (`TurnPhase.AwaitingMove.dice`,
`rolled`, `earned`), and each move the die it uses (`Move.roll`). Either order; a die with no move once the other is
moved is lost; a double's moves are offered once. The game screen draws the two dice at 0.8 of one die's size: a tap on
either rolls both, then a tap on a die picks the one the board's pawns move by (ringed; the other die's moves stay in
TalkBack's actions), and a moved die fades. TalkBack: "First die showing 3, selected", "Second die showing 5, moved",
action "Use this die"; the prompt "Alex rolled 3 and 5. Pick a pawn to move 3." then "Alex has 5 left. Pick a pawn to
move." (or "Alex has 5 left." when that move makes itself; `IMPLEMENTATION.md`, "Turns"). The CPU picks one move
at a time, die by die. House rules apply die by die (Out on a 1, Free Start), and to the roll (Three Tries, Must
Capture across both dice; Endless 6s makes doubles endless).

**Portals** (`GameRules.portals`, `model/Portal.kt`, `game/Portals.kt`; the maintainer, 2026-11-24, in place of snakes
and ladders). A game starts with every seat's short pair on its own stretch of the track (7 apart) and a long one into
the next seat's (16 apart, or 19 with three players), the same for each so none is favoured (`BoardGeometry.portals`),
and **every one is open to every colour** (the maintainer, 2026-11-24: long pairs were shut to the colour whose start lies
between their ends). A move that ends on a portal comes out at its pair (`Move.via` is where it went in) and captures only
there, so a pawn on a portal is only captured by one coming out. **The pair is then used up** (the maintainer,
2026-11-26): it closes and opens again at once, keeping its symbol, colour and length, anywhere on the track but a safe
square, the square before a start (which that seat's pawns never stand on), a pawn's, another pair's, or its own old
ends (`Portals.reopen`, drawn from `GameState.seed`; `GameState.portals`, saved as each pair's seat, length and first
square). The board shows the new pair once the pawn has landed. The CPU weighs a move with the pair closed, not knowing
where it will open (`LudoEngine.moveWithoutChances`). A game saved before (no `portals`, no seed) comes back with the
board's own pairs and seed 0. A pawn's progress is counted from its own start wherever it comes out
(`BoardGeometry.progressAt`), so the long pair either side of its own start takes it most of the way round (9 to 45 on the
classic board), or most of the way back. With Blockades, one at either end stops the move. A pair is told by its symbol and
colour, and a long pair by a split ring (past four seats, a long pair shares a short one's symbol). Four Medium CPUs' winner
takes 52 turns, against Classic's 84. TalkBack: "Tile 7, portal to tile 23, empty", "Move Red pawn 1 2 tiles, through a
portal to square 11".

**Survival** (`GameRules.startsOut`, `lastStanding`, `suddenDeath`; the maintainer, 2026-11-24). Every pawn starts on
its start square, and the board is endless (`BoardGeometry.endless`): no home column, every square of the track walked,
progress counting on lap after lap. A captured pawn is `PawnPosition.Out`; a player with none is knocked out
(`GameState.knockedOut`), placed at once, and passed by; a team lasts while any of its players does. A round is a turn for
everyone still in (`roundsPlayed`). Sudden death (`suddenDeathStage`) has three stages (the
maintainer, 2026-11-24): **Overtime**, the start squares unsafe after 30 + 5 rounds a player; **Sudden Death**, the stars
too after half as many again; and the **Cataclysm**,
ten turns after that, however many play (`suddenDeathTurns`; below). So safety is asked of the state (`GameState.isSafe`,
`isSafeFor`), never the board alone. A 6 needn't move a pawn off the start stack (the maintainer's choice): a stack left
there as sudden death comes can be captured whole. No Home Roll, Out on a 1, Free Start, Three Tries, and No Free Pawn change nothing
here, so are locked, saying so (`HouseRule.appliesTo`), and not played. The board is drawn with no home, and as sudden death comes without its
start colours, then its stars (`BoardParts`), under `SurvivalArt.kt`'s arena: crossed swords, then flames, then a skull,
with a red cross on the squares no longer safe. Yards tally the fallen; cards' dots are the pawns left. The Leaderboard ranks
the furthest travelled (the maintainer, 2026-11-26): the most squares the winner's pawns walked in the game
(`PlayerState.squaresMoved`, `Move.walked`: to a portal, not through it, 1 out of the yard; `GameResult.squaresMoved`),
then captures; a win recorded before 1.2.0 kept them is last, a dash in the column, spoken "distance not recorded". Its
online boards rank the squares alone, and its wins set no fewest turns. TalkBack: "Red yard, 3 of 6 pawns lost", "3
pawns left, playing now"; the line over the board ("Overtime in 3 rounds: start tiles unsafe.", "Overtime! Only stars
are safe.") is live in the last three rounds before a stage and as it comes.

**The Cataclysm** (`game/Cataclysm.kt`, `CataclysmTest`; the maintainer, 2026-11-24): two last pawns can chase each other
round a big board without ever meeting, so the track shrinks. After every fifth turn (anyone's; a run of rolls is one) one to
three random squares with no pawn on them vanish (`GameState.lostSquares`, drawn from `seed`), until seven are left: one more
than the die's faces, so a 6 never comes round to its own square, and a lone rival is always within a roll. Pawns step over
the gaps (`GameState.stepOn`); progress still counts every square, and a move says which it stepped over (`Move.skipped`,
`squares`). Eight Medium CPUs' longest of 60 games went from 772 rounds to 220. A lost square is a dark pit, opening once the
turn has played out (`VanishMotion`, `VANISH_MILLIS`), with a buzz; the log's line ends "2 tiles vanished.", the line over
the board counts the turns, and TalkBack's board leaves the square out.

**Chaos** (`GameRules.powerUps`, `model/PowerUp.kt`, `game/PowerUps.kt`; the maintainer, 2026-11-24, reversing Phase 9's
"power-up squares weren't picked"). At least one of each kind is on the track at any time: a game starts with one of each,
and the last of a kind is replaced at once as it's taken, somewhere else (`PowerUps.restock`; the maintainer, 2026-11-25,
in place of one for each player at the start). It's drawn once the pawn that took it has landed. Besides those, one of any
kind, all as likely, comes every one to five turns (anyone's, a run of rolls being one; `GameState.turnsToPowerUp`, as the
last of them starts), whether any is taken or not, up to two for each player, or seven for two or three players
(`PowerUp.most`). Each is anywhere on the track but a safe square, a pawn's, or another's. A game saved by 1.1.0, short of
some kinds, gets them as its next comes. One works a move, at once: a pawn moved onto another leaves it there.

- **Double**: the taker's next 3 rolls (not turns) move twice as far, but for one that would overshoot home, which counts
    once. A 6 still leaves the yard and rolls again. `PlayerState.doubledRolls`, on their yard
    (`UI_BOARD.md`; the maintainer, 2026-11-25).
- **Shield** (`PlayerState.shielded`): a rival landing on the pawn's square settles on the one before, as any landing
    there (a capture, or another shield to bounce off), never further back than where it came from, and the shield breaks
    (`LudoEngine.land`, `Move.bouncedFrom`). Only a capture where it settles earns a roll. A shield goes as its pawn leaves
    the track, and doesn't stop a pawn leaving the yard (Guarded Starts), which has nowhere to bounce to.
- **Roll Again**.
- **Call Up**: a pawn out of the yard, if one can come. With none in the yard it's banked (the maintainer, 2026-11-25), however
    many are: `PlayerState.callUps`, the pawn's owner's, on their yard. The next of their pawns captured is brought straight back
    out onto its start square, using one up (`PowerUps.recall`, in `LudoEngine.place`; `GameEvent.Moved.recalled`). It's a capture
    all the same, with the roll and the counts it earns; two captured at once use one each. One is kept, and the pawn waits in
    the yard, where it can't come out: past a blockade, or, with Guarded Starts, onto a start square with a pawn it would capture.
    The pawn flies back to its yard, then comes out (`RECALL_MILLIS`). The log: "Alex banked a Call Up.", "Alex captured 1
    of Gambit's pawns. Gambit's Call Up brought 1 pawn straight back out."
- **Swap** (the maintainer, 2026-11-24, in place of Teleport and of an automatic swap with the nearest rival ahead): with
    any other player's pawn on the track, safe, shielded, or a partner's, or with nobody. Not where the two would leave rivals
    sharing a square that isn't safe, nor where either would stand on the one square its way never takes it over. Each
    counts its place from its own start, as through a portal; a shield goes with its pawn; nothing is captured.

The pawn lands on the Swap first, which takes it, and then its player picks (the maintainer, 2026-11-25): two actions, each
saved, sent, logged, and played out on its own. The landing is a move like any other; it leaves the game awaiting a move
still (`TurnPhase.AwaitingMove.swapping`), whose moves each go nowhere (`from` is `to`) and say whose pawn to swap with
(`Move.swap`, `LudoEngine.swapMoves`), its own to keep its place (`GameEvent.Moved.keptPlace`, which isn't logged): so a tap
come late on the pawn that landed is no pick. With nobody to swap with, nothing is asked. What the roll and the landing
earned is kept until the pick (`earned`); with two dice, so is the other die. The pick is never made for a person. The CPU
weighs a landing by its best swap (`LudoEngine.swapsAfter`), then picks in its turn, as does one for a Nearby guest whose
time is up. A person picks once the landing has played out: the pawns to swap with ringed, with the rest of the board faded
(`UI_BOARD.md`), or Don't swap. The prompt is "Alex, pick a pawn to swap with."; TalkBack's actions, there and on each pawn's
square, are "Swap places with Green pawn 2". The log has a line for each: "Alex's Pawn 1 moved 2 tiles. Alex took Swap.",
then "Alex swapped Pawn 1 with Sam's Pawn 2."; a log of 1.1.0's has them in one.

Every chance is drawn from `GameState.seed`, which each draw moves on, so a saved game and a Nearby one play on the same;
the CPU weighs a move with its power-up left alone (`LudoEngine.moveWithoutChances`), so it never sees what a chance will
be. What a power-up did is the move's `GameEvent.Moved.effect`. TalkBack: "Tile 4, Shield power-up, empty", "Green pawn
1, 47 tiles from home, shielded", "Move Red pawn 1 2 tiles, taking Shield", "Yellow yard, 4 pawns waiting, 1 doubled roll left"; the log adds a
sentence ("Alex swapped places with Sam.").

**Haunted House** (`GameRules.ghosts`, `game/Ghosts.kt`; the maintainer, 2026-11-24). A ghost for every two players,
nobody's. After every second turn a random one moves a die's roll of squares, either way, never onto another, and what it
will do isn't shown. It sends every pawn on the square it lands on back to its yard, but for one safe there or in a
blockade; it passes over the rest, and a pawn landing on it comes to no harm. A catch is nobody's capture: it counts in
`PlayerState.timesHaunted`, not `timesCaptured`. Its chances are drawn from the seed, as Chaos's are. The move is
`GameState.ghostMove`, shown after the turn it came after, at half a pawn's speed (`GHOST_STEP_MILLIS`, `GhostArt.kt`). Games
run about 10% longer than Classic with two players, 25% with four, 50% with six, and 70% with eight (a simulation).
TalkBack: "Tile 2, ghost, empty"; the log adds "A ghost caught Sam!" to the turn's line.

**Custom modes** (`model/CustomMode.kt`, the maintainer, 2026-11-18). A name (unique among the player's and the
built-in modes', whatever its case; at most 24 characters), a base mode, and at least one house rule (without one it
would be its base). Kept as JSON in the `custom_modes` setting (`CustomModesJson`; one that can't be read is dropped),
the one picked as `custom_mode`. New Game's mode picker lists them after the built-in modes, each with its base and
house rules as its line; under it, while one is picked, Edit and Delete (Delete asks first). **No new ones can be made**
(the maintainer, 2026-11-24): those a player made before, in 1.0.1, still play, and can be changed or deleted. The
editor is a page of its own: the name, Based On (the same picker as New Game's), and a switch a house rule, with Save offered once it
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
| No Free Pawn | `no_pity_release` | `pityRelease` | No pawn comes out free for a player with none out 5 turns after the first came out |
| Pawns | setting `pawns` | `pawnsPerPlayer` | One to six pawns each, instead of the mode's own number |

- **Pawns** (the maintainer, 2026-11-24, as Quick went). A number, not a `HouseRule`: `GameRules.withPawns`, which marks the
    rules `pawnsChanged`, so the game counts as having a house rule. Its card is the picker's first, and its choices ("3 pawns each" to TalkBack)
    leave out the mode's own number; a
    pick that is it isn't played there, and is kept for other modes. The board stays as it is: only the yards' spots follow
    (`BoardLayout.yardSpotOf`; `UI_BOARD.md`, "The board"), and five
    or six pawns home are smaller (`homePawnRadiusOf`). A custom mode has none.
- **On the board.** "On the board" is the track or the home column: not the yard, not home. A Three Tries roll with no
    move keeps the turn (`GameState.yardTriesUsed`, `GameEvent.NoMove.triesLeft`, "Alex rolled 2 and had no move: 2
    more tries."), one turn however many tries.
- **Blockades.** Every other player's pawns, partners' included, are stopped by one, out of the yard too when it
    stands on their start square; its own player's pass and join it. TalkBack says "Red blockade" on its square
    (`LudoEngine.blockades`).
- **Guarded Starts.** A start square no one sits at (2 players on the classic board) is safe for nobody. A pawn
    leaving its yard captures any other on its start square. TalkBack says "safe for Red" there, not "safe".
- **Off the record.** A game with any house rule on (`GameRules.hasHouseRules`) stays off the Leaderboard
    (`playedUnmodified` false) and out of Statistics (`GameResult.houseRules`, database version 2), and earns
    only the house rules' own achievements, moving no counter (`GameFeats.earnableIn`; the maintainer, 2026-11-24). Its rows are still written, so
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

`game/CpuStrategy.kt`: pure, choosing one of the legal moves from the state alone, in any mode.

- **Easy** plays any legal move at random.
- **Medium** scores each move by what it does now: a capture, reaching home, leaving the yard, reaching safety, getting
    out of reach, a portal's gain or loss, a power-up, and a little for the furthest pawn; ending in an opponent's reach
    costs.
- **Hard** looks a roll ahead (`ExpectimaxStrategy`): how far every pawn has come, for and against, less what each
    opponent's next roll can be expected to take, plus what its own can. A power-up is worth the same whatever it is, as
    its chances aren't known. The weights were tuned against Medium, and `CpuTournamentTest` checks them.
- A partner's pawns count as its own.

## Persistence

Everything is in app-private storage. **Every stored id is permanent once released**, because achievements and modes
will map to Play Games.

- **Game records**: Room, `ludo.db`, table `results` (`GameResult`), version 3. Each row has a random `uuid` (32 hex
    digits, unique index), its id on every device; `id` is the device's own. Version 3's migration gives every older row
    one. A merge inserts rows by uuid, ignoring one already there (`GameRecordRepository.mergeCloud`).
- **Settings**: every key but the Graphics page's and the two list filters is synced (`SettingsRepository.SYNCED_KEYS`,
    house rules as a string set), stamped `synced_at:<key>` when it changes, and merged last writer wins.
- **The saved game**: stamped `saved_at` on every save and clear, never earlier than the last; `finished_id` is the last
    game finished here. Each game has an id (`StartedGame.id`) and a step count (`steps`, one a state change), saved with
    it, so of two copies of one game the one with more steps wins (`furtherOn`).
- **Achievements**: counters `SUM` (a share each device, `counter_share_id` and `counter_shares`) or `LATEST` (the two
    streaks, stamped `counter_at:<id>`). The time played is the `PLAY_SECONDS` counter, out of `AchievementsState`; an
    older build's `play_seconds` setting is moved into it at launch, then removed.
- **The save** (`online/CloudSave`, `CloudSaveCodec` version 1): achievements, records (packed rows: names and modes once
    each, then one string of rows), the synced settings, and the saved game, gzipped JSON. `CloudSaveMerge` combines two
    without picking. Progress files carry it ("Distribution"), and so will Play Games' cloud save once it's wired (`PLAY_GAMES.md`, "Cloud
    save").

- **The records' rules.**
    - **Names are matched by `playerKey`** (`playerNameKey`), never by `playerName`: SQLite folds only ASCII case.
    - A game's rows are written, and a merge's, in one transaction each.
    - Every schema change needs a version bump and a migration in `MIGRATIONS` (`data/records/AppDatabase.kt`), tested by
        `AppDatabaseMigrationTest` from the exported schema (`app/shared/schemas/`). There is **deliberately no
        destructive fallback**: a missing migration must fail loudly, never wipe records.
    - **Failing loudly is a page, not a crash**: `DatabaseFailurePage` (see "Errors").
- **Preferences.** Five DataStore files (`PreferencesFile`), kept apart so a reset can clear one. Each is a
    `ResilientDataStore`: a write that fails (a full disk) is skipped and a read that fails is empty, never thrown into
    the coroutine that asked. A failed read is retried three times, after 1s, 3s and 5s, and a retry that reads the file
    gives it to the collector; after the third, it stops.
    - `settings`. A key no version reads any more is listed in `RETIRED_KEYS` and removed at launch (`forgetRetired`).
    - `achievements`: unlock times and counters, plus the counter shares and the streaks' stamps.
    - `in_progress_game`, stamped `saved_at` on every save and clear.
    - `online`: Play Games choices and the cloud save's bookkeeping.
    - `purchases`: what the store last said (`PURCHASES.md`). It isn't in the cloud save.
- **Progress files** (the github app only): the same snapshot, sealed, in a file the player keeps. "Distribution",
    below.
- **The in-progress game.** `InProgressGameRepository` holds at most one, saved as JSON (`SavedGameJson`) after every change
    and cleared when the game ends.
    - Writes are queued and conflated in a process-lifetime scope, so leaving a game can't lose its last move.
    - A save that can't be read, or that the rules couldn't have reached (`SavedGameJson.decode` checks it), is removed
        as Continue loads it. A merge never takes one.
    - One that can be read but not drawn: "Errors", `ResumeAttempt`.
- **Style picks.** Four ids (board, pawns, die, and background) in `settings`. An id nothing recognises draws the default.

## Errors

The rule: **an error may cost a game, never the app.** What can be recovered from is caught where it happens. What
can't is a page saying so, with the stack trace to report. The app never just closes.

- **Recovered where it happens**, so nothing reaches the pages:
    - storage: `ResilientDataStore` (see "Persistence"), and `orNullOnFailure` (`data/Failures.kt`, which rethrows a
        `CancellationException`) around writes and loads that can fail;
    - a cloud or progress-file game that can't be read or played (`SavedGameJson.decode`) is never taken;
    - a failed cloud sync costs that sync (`LudoPlusApp`), and a progress file that won't open is reported on its card;
    - Leaderboard or Statistics data that can't be read leaves what's on view, or opens empty with nothing on view yet.
- **The records database won't open** (a migration missing or failing): `AppContainer` opens it as it's built and keeps
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
    - **Last actions** are `Breadcrumbs` (`app/`): the last 12 pages opened (`LudoPlusApp`) and steps taken (a game
        started or resumed, and every roll and move in it, whoever's: `GameBreadcrumbs`), oldest first. They hold ids and
        numbers, never a name or anything typed.
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
            doesn't read the settings, so it's handed the motion the app last drew with (`LastMotionSettings`).
        - **Restart** (filled) and **Report on GitHub** (tonal) sit side by side, one above the other at a large font.
            Restart starts the app in a fresh process and ends the crash page's.
        - **Report on GitHub** opens the browser at a new issue on the repo, filled in with the report
            (`CrashReport.issueUrl`), titled `[Crash]` and the error's first line: a line for the player to say what they
            were doing, a table of the details, the last actions, and the trace. The player reads it and posts it
            themselves; the app sends nothing. The link is kept short enough to survive GitHub's sign-in page
            (`MAX_SIGN_IN_URL_LENGTH`, measured 2026-11-24: over it, signing in fails or loses the report), by cutting the
            trace from its end and saying so. With no browser, nothing opens.
        - **Show Details** opens the collapsed section (Hide Details closes it): what the details are for, **Copy
            details** (tonal), then the report. Copy details puts the report on the clipboard, then reads "Copied", a
            polite live region; both labels are laid out, so the button keeps its width. The section opens downward
            under the page, never moving what's above it (`FailurePage`'s `below`): that part is centred as the page first
            opens and stays where it is. The section fills the room left down to the screen's foot and scrolls in it, the
            whole of it (`ScrollingColumn`, its bar in the page's margin); on a screen too short to leave 160dp it gets that
            much, and the page scrolls too. The database page's details are
            always shown.
- **A saved game that can't be resumed** goes to the crash page, then goes for good (`ResumeAttempt`; the crash page's
    half was lost in Phase 4 and restored 2026-11-24).
    - `ResumeAttempt` marks what counts: Continue's load and the board's first frames (`LudoPlusNavHost`). Any error
        nothing caught during one, on any thread, counts. One after it, such as on the next roll, is an ordinary crash.
    - `CrashHandler` then writes `SavedGameDiscardFile`, whether or not a page opens, and `CrashPage` says the game will be
        removed (`gameRemoved`).
    - `InProgressGameRepository` removes it at the app's next start, before anything reads the save, however the page was
        closed: Restart, Back, reported or not, or swiped away. No copy of it comes back from a merge (`FINISHED_KEY`).
- **Not caught**: native crashes and ANRs.
- **Trying it**: a debug build's crash mode on Settings > About (see "Superuser modes"). The taps on the name are
    raw gestures, attached only in a debug build, so no other build has them and TalkBack is never handed a dead link.
    For a crash anywhere else, throw from a temporary line, and take it out again.
- **Tests**: `ResumeAttemptTest`, `InProgressGameRepositoryTest`, `CrashHandlerTest`, `CrashReportTest`, `CrashActivityTest`, `CrashPageTest`, `BreadcrumbsTest`,
    `GameBreadcrumbsTest`, `AboutDebugCrashesTest`, and `DatabaseFailureTest`.

## Leaderboard and Statistics

**By mode**: a drop-down at the top of each page (`ui/records/ModePicker.kt`), DiceFive's (the maintainer, 2026-11-24):
every mode, Classic first and the one they open on, and on Statistics then **All Modes** (`ModePage`), every mode's games
together. Each page's pick is a preference (`leaderboard_choice`, `statistics_choice`: the mode's id, or `all_modes`),
loaded with the app container, restored on every open, and carried in the cloud save and progress files; a mode no longer
known opens Classic's.

- **The Leaderboard** reads a board the first time it's picked, and keeps it until a game is recorded. The board on view
    stays up until the one picked is read, then gives way in one frame, name and rows together (`LeaderboardViewModel`).
- **Statistics** reads the records once (again when a game is recorded) and adds up every mode and count then, off the
    main thread (`Statistics`), so a pick reads nothing.

**By player count** (Phase 8, 2026-11-18). Each record keeps its game's `playerCount`, and both pages have a row of
counts under their heading (`ui/records/PlayerCountRow.kt`, TalkBack "4 players"). The Leaderboard has a board a count,
2 to 8 (a bigger board's lap is longer, so turns only compare within a count), opening at the count New Game last
started; Statistics opens on "All" (every game, spoken "All games") and can show one count's games. Filtered in the
query and in `statistics`. A team game records each player at their team's place, and
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

**The Leaderboard** (`LeaderboardScreen`) ranks a mode's wins at a player count: the fewest turns, in Colour Rush the most
squares, or in Survival the furthest travelled (`GameMode.ranking`), then the most captures. 1st to 3rd are in gold,
silver, and bronze. Columns become two lines a row where a name hasn't room beside them (a large font). TalkBack reads a
row as one: "1st, Alex, 38 turns, 4 captures"; in Survival "1st, Alex, 412 tiles travelled, 4 captures". While signed
in to Play Games, a button opens the mode's online board.

**Statistics** (`StatisticsScreen`) is a card for each person, by name (`playerKey`), in name order: games, wins, win rate,
streaks, fewest turns to win, captures, times captured, and pawns home, less the race's figures in Survival. Added up
in `GameRecordRepository.statistics`, off the main thread. Each figure is read with its label as one.

## Achievements

`model/Achievement.kt` (110: 100 counted, 10 Easter Eggs), `game/AchievementTracker.kt` and `game/AchievementEngine.kt`
(pure), `data/achievements/`, `app/AchievementUnlocker.kt`, `ui/achievements/`. Rebuilt for Ludo in Phase 6; DiceFive's
110 are in `5423b6c`.

- **Ids.**
    - The enum constant is the English title in snake case; the stored `id` is that in lower case
        (`GETTING_COMFORTABLE`/`"getting_comfortable"`), and a title renamed since release leaves both alone (Revenge Is Sweet is
        `PAYBACK`, and Public Nuisance `PUBLIC_ENEMY`). `AchievementEngineTest` enforces it. Permanent once released.
    - Removed achievements' ids are kept in `RETIRED_ACHIEVEMENT_IDS`, never to be used again (eleven,
        2026-11-23, ten, 2026-11-24, four, 2026-11-25, and one, 2026-11-26: Centurion's, which became Seasoned with a new id, at
        the maintainer's word, as no one had earned it).
    - String keys follow the constant: `achievement_<constant>_title`, `_description`.
- **Player 1 only.** The person in seat 1 earns them; every other seat, person or CPU, is an opponent. The tutorial
    earns nothing.
- **The catalog** is `model/Achievement.kt` itself, in display order: Milestones (20, Pest and Completionist among
    them, Completionist last), The Die (5), Captures (10), Racing (10), Winning (15), Game Modes (15), Misfortune (15), Miscellaneous (10,
    hidden), and Easter Eggs (10, hidden, outside Completionist and the page's total): 100 counted. Each entry's
    description is its rule; the thresholds are the tracker's constants (`AchievementTracker`'s companion). The maintainer asked for 100, plus 10
    Easter Eggs, on 2026-11-17 ("a variety of them in groups, like DiceFive"), removed eleven on 2026-11-23,
    twelve on 2026-11-24 (two of them never released), and four on 2026-11-25, and is
    adding new ones: My Favourite Spot (every pawn on one square, not the start square, home column included) and
    We're All Friends Here (every player with a pawn on one track square, by anyone's move) on 2026-11-23, when Pest and
    Completionist moved to Milestones and the Collection category went. Loaded Dice (one number 5 times in a
    row) replaced Full Spread, and Visiting The Neighbours (a pawn of player 1's on every player's start square at once, their
    own included) Buddy System, on 2026-11-25. Visiting The Neighbours is kept out of nothing, though four pawns can't do it
    against more than three others (the maintainer).
    - Speed Demon is a win within 5 minutes of the game on screen (`StartedGame.playMillis`, as Time Flies counts it; the
        maintainer, 2026-11-23). Locked Out is 5 turns.
    - The climbs were shortened on 2026-11-26 (the maintainer): Getting Comfortable, Regular, Seasoned, Old Hand, and Lifetime
        Achievement are 5, 10, 25, 50, and 100 games; Champion, Hall Of Famer, and Living Legend 5, 10, and 25 wins; Globetrotter
        500 pawns home; and Ultramarathon 50,000 squares.
    - Stop Hitting Yourself (15 captured) comes from simulated games: a player is captured about 6 times a game.
    - Easter Eggs: Alliteration (every name starting with one letter), Are You A Developer? (the version tapped seven
        times), Big Fan (a person named "zodac", exactly), Can't Wait To Get Going (the main menu's logo pawn tapped,
        which hops three times; added 2026-11-23), Full Retreat (every pawn on the board at once, then every one back in the yard,
        captured or caught by a ghost; in place of Who's Ludo?, 2026-11-25), Hungry For 6s (every pawn of player 1's out of the yard before any other
        player, a partner included, has brought one out; in place of Sideways, 2026-11-25), Midnight Run (a new game from midnight to 6am), One By One
        (every pawn home, none ever brought out with another on the track, by a move or Call Up, banked or not, seen from the first roll;
        one in its home column is off the track, 2026-11-26),
        The Great Escape (all four pawns out together, each brought out within the last four turns; both 2026-11-24),
        and Time Flies (an hour of one game on screen).
    - Wire To Wire needs the whole game seen (from player 1's first roll): one continued from its save can't earn it.
    - Opening About (Who Made This?) earns a hidden one. So does tapping the menu's logo pawn (Can't Wait To Get Going,
        `MenuScreen`'s `onLogoPawnTapped`, unlocked in `LudoPlusNavHost`). The tutorial earns nothing.
- **Counters** (`AchievementCounter`): games played, games won, the win streak, the modes finished, captures, pawns home,
    rolls, 6s, squares moved, and last places. Each
    moves as its event happens, never at a game's end only, so a game left unfinished still counts what was played.
    - A game counts as played the moment player 1 is placed; a win the moment they are first.
    - The win streak goes up with a win, and back to 0 the moment another player wins.
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
    - A roll's or move's feats are stored at once, but announced (banner and online) only once the screen has played it
        out: the die settled, the pawn landed (`GamePacing.playOutMillis`, `GameViewModel.record`). Leaving the game
        announces them at once.
- **Modes and house rules** (the maintainer, 2026-11-24, as DiceFive). Every mode earns the ordinary achievements, but for
    those its rules make free, rule out, or turn into something else (`Achievement.notEarnableIn`, `impossibleIn`; the audit
    is in `GAME_MODES_AUDIT.md`). The Game Modes category has one for starting any mode but Classic, each mode's win and a
    feat of its own, Everything Everywhere All At Once (a win of any mode but Classic with every house rule that changes it
    on), and Grand Tour. A game with house rules earns only those marked `earnableWithHouseRules`, and moves no counter. A limited
    one's row has a tip naming the modes left ("Can only be earned in the 'Classic' and 'Chaos' modes"), read with the row by
    TalkBack.
- **Display.**
    - Declaration order is display order: grouped by `AchievementCategory`, easiest first; Milestones is the firsts, then a
        ladder at a time (rolls, games played, wins, win streaks, and captures; the maintainer, 2026-11-24), Completionist last. `AchievementEngineTest`
        fails if a category is split, or a counter's ladder is out of order; `AchievementTextTest` holds Winning,
        Misfortune, Miscellaneous, and Easter Eggs alphabetical, but for Cutlery Collector, straight after Wooden Spoon (the
        maintainer, 2026-11-25).
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
    (Phase 10).

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
board or pawn tile on the Styles page is a corner of the board (`BoardCornerPreview`): the top-left yard (seat 1's, coloured as
seat 0) with three pawns, one on its start square, and the next seat's pawn on the track, in the default seat colours, the tile's style drawn
with the player's pick of the other.

The boards are Classic, Wood, Ocean, Comic, Patchwork, Candy, Obsidian, Disco, Retro, Paper, Starry, Groovy, Stone,
Meadow, Circuit, Bestagons, and Neon. Every family but Classic is a `LayeredBoardStyle` (`ui/style/BoardFamilies.kt`,
and `PlayfulBoardFamilies.kt`, 2026-11-24): a ground, the squares, the yards, the home triangles, the safe squares'
marks, the start squares' borders, and a finish over it all (grain, speckle), each the family's own, with the seats' colours drawn through its
`seatTone` (tinted towards the material, never so far that two seats look alike). `LudoBoard` draws the board on a
Canvas of its own under the pawns, which reads only the style and the seat colours, so a texture isn't redrawn on every
frame of a move.

The pawns are Classic, Counter, Chess (Rook, Knight, and Queen), Shapes (Star, Heart, and Cube), Cone, Peg, Glass,
Monster (Cyclops and Horned), Animal (Cat, Duck, Frog, and Penguin), Retro, Toadstool, Cake, Gems, Space (Rocket and
Saucer), Robot, Neon, Googly, and Devil. Every family but Classic is a `GridPawnStyle` (`ui/style/PawnFamilies.kt`, and
`PlayfulPawnFamilies.kt`, 2026-11-24), which draws the shadow and the movable ring on one footprint for all of them and
leaves the family its body. Retro, Cake, Gems, Neon, Googly, and Devil take the lock of the die of their name. The rest
open at a count of their own: Counter 5, Chess 10, Shapes 20, Cone 20, Peg 25, Glass 30, Monster 35, Animal 40,
Toadstool 50, Space 70, and Robot 80.

**The die** has 35 families, D20 included (drawn showing 1-6). `GameDie` draws the style's face, turning
through random faces as it tumbles; a style that tumbles itself (the D20, the Egg, the Bestagon) is handed the tumble's
clock (`LocalDieTumbleMillis`) instead. Before the first roll it's the style's body colour with no face. Its ring is
drawn round the style, not by it: in the colour of the player whose turn it is, in full while it can be rolled (or, of
two dice, once picked) and faint the rest of the turn, a CPU's included (2026-11-23). On the board, that player's yard
has a white ring, edged in ink, round its inside edge (`LudoBoard`), until the game is over. Both are visual only: the
status lines name whose turn it is (`TurnHighlightTest`). The turn passes on once its last action has been shown
(`UI_BOARD.md`, "The die and its line").

**Backgrounds** have 20 families. One that moves (the Ocean's Waves, Floating Dice)
runs on the Background decoration group; a die's own sparkle or glow on the Die group (`MotionGroup.DICE`).

Each category is two lists in the same order:

- **The registry** (`game/style/StyleRegistries.kt`, pure): `StyleGroup`s (a shape or pattern, one tile on the Styles
    screen) of `StyleVariant`s, with every id, name and lock. Anything that decides what a player may use (the unlocker,
    achievements) reads only this, through `StyleCategory.registry`.
- **The art** (`ui/style/StyleCatalog.kt`): each variant's `StyleArt` (swatch and drawing). `StyleCatalog` joins it to
    the registry by id into the Styles screen's `StyleFamily`s of `StyleColour`s, and refuses a mismatch at load.

- **Ids** are the family's string key, then the colour's (`classic_ivory`, `ocean_lagoon`), and permanent once
    released. `StyleRegistryTest` enforces this. The die's and background's saved keys are
    `dice_style_id` and `table_background_id`; the board's and pawns' are `board_style_id` and `pawn_style_id`.
- **The Classic family** comes first in every category and is free. Its first colour is the default: the cream board,
    glossy pawns, the ivory die, and the navy felt.
- **A style with a counterpart of the same name is unlocked by the same thing** in every category that has one:
    Stone opens at 65 achievements, Neon and RGB at 100, the water set (the Ocean board, die, and background) at
    15 (`WATER_SET_UNLOCK`). A board family locks with the die or background of its name; those with neither
    open at a count of their own: Comic 20, Patchwork 25, Candy 30, Disco 40, Paper 50, and Groovy 60.
    `StyleRegistryTest` checks every family name across the categories.
- **Locks.** Every other family is locked behind a count of achievements (`StyleUnlock.AchievementCount`) or one
    achievement (`SpecificAchievement`).
    - A count is of **every** earned achievement, Easter Eggs included (`AchievementsState.unlockedCount`; the
        maintainer, 2026-11-23), unlike the Achievements page's total, which leaves the Easter Eggs out. No count lock
        is above 100, and every one is within the achievements there are (`StyleRegistryTest`).
    - The styles DiceFive locked behind its own achievements (2026-11-17): the Starry die and background open with
        Midnight Run; Devil (and its pawn) with Too Much Of A Good Thing; Maths with Counting Up (Number Cruncher's until it was
        removed, 2026-11-23); D20 and the Floating Dice background at 55, the one count no other style uses
        (`D20_UNLOCK`). The Irish, golden Egg, and D20 colours
        that DiceFive kept secret are ordinary colours of their families. Googly (and its pawn; Completionist), Glitch (Big Fan), and
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

- **Clips** (LudoPlus): `mat_landing` (the die landing), `pawn_step` (at 0.5 volume, once for each square a pawn moves,
    in time with it), `capture` and `pawn_home` (in place of the landing's step), a step again as a captured pawn lands
    back in its yard, and `celebration` (a person's win, with gold fireworks). All CC0 from Freesound, chosen by the
    maintainer 2026-11-17 (`capture`, a cartoon punch, and `pawn_home`, a sparkle, 2026-11-23). The cues are
    `moveSoundCues` (`ui/game/GamePacing.kt`): with Pawn moves motion off, only the landing's sound.
    - The sources are `app/audio/*.ogg`.
    - They're peak-normalised to -0.8 dBFS by ffmpeg at build time (`scripts/normalize-audio.sh`): never `res/raw/`,
        never hand-adjusted. Android packages Ogg copies (`NormalizeOggAudioTask`). `scripts/tests/normalize-audio-test.sh`
        checks they keep each clip's length and peak.
    - The app has one player (`rememberAppSoundEffects`, provided at `LudoPlusApp` as `LocalSoundEffects`). With sound
        on it loads its clips off the main thread a second after launch; otherwise on its first sound. It's held in the
        background and released with the app, so a sound started on one screen carries on to the next.
    - The app follows the Settings switch itself; no screen passes the setting in.
- **A move on the board** (`LudoBoard`, 2026-11-23): the pawn hops square by square (`PAWN_STEP_MILLIS`), rising and
    falling over a shadow left on the board; out of the yard it glides to its start square. A capture bursts on the
    square (a ring of the captured pawn's colour and ivory streaks) as the captured pawn is thrown in an arc back to its
    yard (`CAPTURE_FLIGHT_MILLIS`). A pawn reaching home pops, in a gold ring and one of its colour, with sparks
    (`HOME_FLOURISH_MILLIS`). All of it is the Pawn moves motion group's: with it off, the pawns snap. A CPU, the
    tutorial, and the results page wait it out (`GamePacing.waitAfter`).
- **Haptics** (`ui/game/Haptics.kt`). A tick for a pawn moved, and a longer buzz for a capture or a Nearby guest's turn
    running out. Settings switches sound and vibration off separately; vibration is wired like sound
    (`rememberAppHaptics`).
- **The roll.** The die tumbles for `DIE_TUMBLE_MILLIS`, flicking through random faces as it turns, and lands on its result;
    a style that tumbles itself is handed the clock (`LocalDieTumbleMillis`). A roll that passes the turn on then shows for
    `TURN_PASSED_MILLIS` before the next player is prompted (`rollMillis`; `UI_BOARD.md`, "The die and its line").
- **Animations setting** (`AnimationLevel`): High (uncapped), Medium (60fps, ambient decoration still), Low (30fps), or Off,
    each a preset of the Graphics page's **Advanced graphics settings** (`MotionSettings`): Off, 30fps, 60fps, or Max for
    the Die's and Background's decoration, Menus & pages, Achievement banners, Celebrations, the Die roll, and Pawn
    moves, and Pause when idle (High and Medium 60s, Low and Off 30s). The system's remove-animations setting counts as
    Off. Details are in `UI.md`, "Reduced motion". Tests: `MotionSettingsTest`, `AnimationLevelSettingTest`.

## Settings

- **Contents.** The online account card (the play app) or the progress files card (the github app), at the top; switches
    for Sound effects, Vibration, Move log, Confirm leaving game, and Stop at first winner; the **Graphics** row; and in
    the play app, Remove ads and the ad privacy choices (`ADS.md`).
- **Graphics** (`GraphicsScreen`, `GraphicsViewModel`, route `settings/graphics`): a page of its own, opened by a button
    row with an onward arrow. The Orientation, then the Animations level, each under its title, in one card; then the
    Advanced graphics settings, under their own heading in three cards, each collapsed until opened (`UI.md`, "Reduced
    motion"). The Gameplay card also holds **Pause when idle** (`IdleTimeout`, `motion_idle_timeout`: 30s, 60s, 90s, or
    Off), how long an untouched screen waits before its infinite animations freeze, preset by the level like the rest.
    None is synced: they're about this device. Tests: `AnimationLevelSettingTest`,
    `MotionSettingsTest`, `IdleTimeoutSettingTest`, `ScreenIdleTest`.
- **Orientation** (`ScreenOrientation`): Device (the default: turns as the system allows), Portrait or Landscape. Not
    synced, as a phone and a tablet can want different ones. `LudoPlusApp` hands it to `PlatformServices.lockOrientation`;
    on Android `MainActivity` sets `requestedOrientation` (sensor portrait or landscape, so either way up). The manifest's
    `android:appCategory="game"` is what lets the lock hold on a large screen: Android 16+ ignores it there otherwise.
    Tests: `ScreenOrientationSettingTest`, `OrientationLockTest`.
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

- **Layout.** Two tab rows: groups (**Gameplay**, **Game Modes**, and **House Rules**), then the pages of the chosen group.
    One pager runs across every page.
- **Diagrams** (`RulesDiagram.kt`): a crop of the real board (`drawBoard`, `drawPawn`, and `ClassicBoardLayout`'s positions),
    in the default seat colours, with pawns, ink arrows (dashed for a pawn sent back), and a die. One image to TalkBack,
    described in words (`rules_*_diagram_cd`).
- **Mode boards** (`RulesBoard.kt`): each Game Modes page ends with the game's own board (`LudoBoard`), in the default
    seat colours, for four players, as a game of the mode might stand (`RulesBoardKind`, staged by hand): Colour Rush's
    claimed squares, Two Dice's roll under it, the portals, Survival's Cataclysm, Chaos's power-ups, and the ghosts. One
    image to TalkBack, described in words (`rules_*_board_cd`).
- **Accuracy.** The text is kept in step with the engine and `IMPLEMENTATION.md`'s "The classic rules".
- **New mode or modifier.** It gets its own page; see `GAME_MODES.md`.
- Tests: `RulesTextTest` (every page's words, diagram, and board), `RulesBoardTest`, `RulesScreenAccessibilityTest`,
    `RulesTabIndicatorTest`, and `RulesScrollbarThumbTest`.

## Distribution

Two apps from one source, one per way the app reaches players: product flavours of `app/android` (dimension
`distribution`), each with its own source set (`src/play`, `src/github`) and one seam, `Distribution`, which
`MainActivity` and `LudoPlusApplication` call.

| | `play` | `github` |
| --- | --- | --- |
| Ships as | The App Bundle, uploaded to Google Play on demand (`play-release.yml`) | The APK on each GitHub release |
| `applicationId` | `net.zodac.ludoplus` | `net.zodac.ludoplus.github` |
| Google Play Games | Yes (`PLAY_GAMES.md`): the SDK, its manifest entries, Sidekick | None: no SDK, no manifest entries, `NoGameServices` |
| Progress files | None, signed in or not | Export and Import, in Settings |

- **Kept apart so nothing from a file reaches Play Games.** A file's progress can only be imported into the github
    app, which has no Play Games code, and the github app's data can't reach the play app's: they're separate packages
    (data, backups and installs are per package), signed by different keys (Play re-signs the bundle). Nor can an
    edited `adb` backup: up to Android 11 the play app's backup holds only the settings (`PLAY_GAMES.md`, "Account and
    sign-in"). Pinned by `GithubFlavourTest` and `PlayFlavourTest`.
- **The github app is a different app.** It installs beside the play app, not over it; a GitHub install from before
    the split (`net.zodac.ludoplus`, 1.0.0) doesn't update to it.
- **Progress files** (`online/ProgressFileCodec`, `ProgressTransfer`; on Android `AndroidProgressFiles`, through the
    system's pickers, with no storage permission).
    - **What's in one**: everything a cloud save holds (`CloudSaveStore.snapshot`), as its own bytes (`CloudSaveCodec`).
    - **An import is a merge**: `CloudSaveStore.merge` (settings, the saved game, achievements, then records), then what
        the merged counters reach (`unlockReachedTargets`), as the cloud save will (`PLAY_GAMES.md`, "Cloud save"). Nothing is picked or
        thrown away, and importing a file twice changes nothing. A newer app's file is refused whole, with "update the app".
    - **Sealed, not plain**: `LudoPlus`, a format byte and a random nonce, then the save XORed with an HMAC-SHA256
        keystream, then an HMAC-SHA256 of all of it. A file changed by one bit, or sealed by another build, isn't a progress
        file. The key is CI's `PROGRESS_FILE_KEY` secret (64 hex digits), never in the source; a build without it uses an
        all-zero key, and the release workflow refuses to build the APK without it. **The key is permanent**: files made
        with one can't be opened with another.
    - **What it stops**: editing a file by hand, or with a tool written from this public source. Not someone who pulls
        the key out of the APK, or edits a rooted phone's own data, which no offline app can stop. Neither reaches Play
        Games, as above.
    - Named `LudoPlus-<yyyy-MM-dd>.ludo` (the player's day). Tests: `ProgressFileCodecTest`, `ProgressTransferTest`,
        `ProgressFilesViewModelTest`, `ProgressFilesSettingsTest`, `CloudSaveCodecTest`, `CloudSaveMergeTest`, the stores'
        `*CloudTest`s, and `AndroidProgressFilesTest` (github).
- **Settings card** (`ProgressFilesCard`): Export progress and Import progress, each a whole-row button (a compact
    `SettingRow`, its label alone), where the online card would be. Import asks first ("Import progress?"). Under the
    rows, while an import runs, its step and count over a bar, drawn by `StepProgress`, which the online card's sync will
    share ("Opening the file", then the four merge steps, `MergeStep`, "Merging game records (5/5)" last: `MergeStepTest`
    keeps the flows in step), for at least 1s (`IMPORT_SHOWN_MILLIS`, shared out between the steps, so a quick import's bar still
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
    purchase signatures are checked with (`PURCHASES.md`, "Entitlement"). With it and the AdMob ids all unset (no Play
    Console or AdMob account yet), a release is the github APK alone, with a notice and no Play bundle. Once any of the
    three is set, the apk job fails without all of them, or on a licence key that isn't one-line Base64.
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
