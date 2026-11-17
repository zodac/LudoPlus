# Tutorials

How the guided Classic tutorial is built, the rules its script follows, and how to add one for another mode. The
player-facing summary is in `DESIGN.md`, "The tutorial". DiceFive's dice tutorial, whose framework this one adapts, is in
commit `5423b6c`.

## What it is

A game of Classic, the player (Red, seat 0) against one CPU (Green, seat 1), in four parts. Each part starts from a
position of its own and teaches one thing; every roll and every move is fixed. A coach card in place of the player cards
says what to do; only that action works.

| Part | Position | What happens |
| --- | --- | --- |
| 1. Getting out | Everyone in the yard | The player's 3 can't leave the yard, so the turn passes. Green's 6 brings a pawn out and rolls again (2). The player's 6 brings a pawn out and rolls again (4) |
| 2. Captures | Red on square 10, Green 4 ahead | A 4 lands on Green's pawn and sends it home; the capture rolls again (5) |
| 3. Home | A red pawn 2 from home, another on the track | A 2 brings it home; that rolls again (3), moving the other |
| 4. Winning | Three red pawns home, the last 4 away | A 5 is too many, so the turn passes; Green moves; a 4 brings the last pawn home and wins |

- **Entry points.** Rules → How to Play (a Play the Tutorial button), and New Game's "New to the game? Play the
    tutorial" link, shown while `GameRecordRepository.hasFinishedGame()` is false. Both open the `tutorial` route.
- **Exit.** Back always asks "Leave the tutorial?", whatever the confirm-before-leaving setting. Its Game Over offers
    Done (back where it was started) and New Game (back to New Game if it was started there).

## The pieces

| Where | What |
| --- | --- |
| `game/Tutorial.kt` | Pure: `TutorialScript` (mode, parts, `start(part)`), `TutorialPart` (each side's pawns and the steps), `TutorialStep`, `TutorialMessage`. `TutorialScript.CLASSIC` is the script |
| `ui/game/TutorialProgress.kt` | Where the game is: the part, the step waited for (`current`), and the step shown (`shown`) |
| `ui/game/GameViewModel.kt` | `tutorial: TutorialScript?` (null in real games), `tutorialFactory`, `startTutorial`, `tutorialStep`, `tutorialPart`, `jumpToTutorialPart`, and the gates (below) |
| `ui/game/TutorialGuide.kt` | `rememberTutorialPulse` and `Modifier.tutorialOutline`: the die's pulsing violet outline at a roll step |
| `ui/game/CoachCard.kt` | `CoachCard` and `coachCardHeight`; the `TutorialMessage` → string map |
| `ui/game/GameScreen.kt` | `TutorialView`; the card replaces `PlayerCards`; the moves and the die are gated on the shown step; the tutorial's leave and Game Over |
| `ui/game/LeaveGameConfirmation.kt` | `request(tutorial = true)` words the dialog as the tutorial's |
| `ui/game/GameOverScreen.kt` | `tutorial`: the winner alone, the done line, Done / New Game |
| `ui/rules/RulesContent.kt`, `RulesScreen.kt` | The `RulesTutorialStart` block on How to Play, and `onStartTutorial` |
| `ui/setup/GameSetupScreen.kt` | `showTutorialLink`, `onTutorial` |
| `navigation/LudoPlusNavHost.kt` | The `tutorial` route: its own `GameViewModel` (never the play graph's), started on first composition |

## Steps

| Step | Who | Moves on |
| --- | --- | --- |
| `Roll(message, value)` | The player taps the die, which rolls `value` | at once |
| `Move(message, pawn)` | The player moves that pawn, the only one offered | at once |
| `CpuRoll(message, value)` | Green rolls `value`, at the CPU's pace | at once |
| `CpuMove(message, pawn)` | Green moves that pawn | at once |

A CPU step's message stays on the card while Green plays. There are no words-only steps: a card that explains ends
"Tap the die", so one tap both carries on and acts.

**Timing.** What the player may do moves on as the action is taken (`TutorialProgress.current`). What they see (the card,
the die's outline, the ringed pawn) moves on once the screen has played the action out (`shown`, after
`GamePacing.waitAfter` less the think time). An action is taken only once its step is shown. After a part's last step, a
beat later, the next part's position is set out (`startTutorialPart`).

## Scripting rules

- **One lesson a part**, from a position that shows it at once; the part's first card says what it's about.
- **Every bonus roll is shown**: a 6's (part 1, Green's and the player's), a capture's (2), and a pawn home's (3).
- **No accidental lessons.** No step may capture unless it's the capture part's (`TutorialScriptTest` counts them), and
    a move step names a pawn the rules allow.
- **The player's steps on the player's turn**, the CPU's on Green's: the script test replays every part through
    `LudoEngine` and checks each.
- **Each part but the last ends with the turn passed to Green**; the last ends with the player's win.
- **Keep the card short.** Its height is the longest message's, so trim the longest few.

## The coach card

- In place of the player cards: "Coach" and "Part N of 4" on one line, the words under them, and an arrow strip at
    each edge (48dp wide, the card's height): back to the start of this part, or the one before if already there;
    forward to the next part. Back is off at the very start, forward on the last part.
- **Height**: `coachCardHeight` measures every message at the font in use, up to 130% (`MAX_MEASURED_FONT_SCALE`), so
    nothing moves between steps and every word shows to that size. Past it the words scroll inside the card, with the
    app's `VerticalScrollbar`.
- **TalkBack**: the name and words are one node and a polite live region (read out as each step arrives); the part is
    a node of its own so it isn't repeated. The arrows are "Previous part" and "Next part".
- Markup: **bold** for a number.

## Nothing is kept

The tutorial's `GameViewModel` uses the real repositories (for the player's saved name) but writes nothing: `commit`
returns before the save and the records, and `startTutorial` doesn't save the setup form. `TutorialGameTest` plays it
through and checks there's no save and no record.

## Strings

- One string per `TutorialMessage`: `game_tutorial_<message lowercased>`, mapped in `CoachCard.kt`'s `resource`. Plus
    `game_tutorial_coach_name`, `game_tutorial_part`, `game_tutorial_cpu_name`, the arrows' `game_tutorial_*_part_cd`,
    the leave dialog's `game_leave_tutorial_*`, Game Over's `gameover_tutorial_*`, Rules' `rules_how_to_play_tutorial`
    and `rules_tutorial_start`, and New Game's `setup_tutorial_link`.
- The numbers a message quotes must match the script.

## Tests

- `TutorialScriptTest` (pure): replays each part through `LudoEngine`; each part's lesson (the bonus rolls, the
    capture, the pawn home, the win), and no unplanned capture.
- `TutorialGameTest` (view model): plays it through to the win; only the step's action, and not before it's shown;
    back and forward; nothing kept.
- `TutorialScreenTest` (Robolectric): the card's words and live region; only the step's action offered to TalkBack; the
    leave dialog.
- `RulesTextTest` (How to Play's button) and `SetupTextTest` (the link).
- **Harness traps.** Give each screen its own `LeaveGameConfirmation`. The view model's waits run on the main looper:
    a scratch render steps both clocks (`compose.mainClock` and `ShadowLooper.idleMainLooper(ms, MILLISECONDS)`); the
    screen test sets `GamePacing` to zero so each step shows at once.

## Adding a tutorial for another mode

1. **Write the script** as a new `TutorialScript` beside `CLASSIC`, with its own `TutorialMessage` entries, part by
   part on paper first (positions, rolls, moves), then let a script test prove it.
2. **Check what the mode draws at random.** The script feeds die values only; a mode that draws anything else (two
   dice, say) needs its steps to feed that too.
3. **Mode-specific UI**: anything the mode adds to the board needs the same gating: offered only at its step.
4. **Entry**: a Play the Tutorial button on the mode's own Rules page, and a route that takes the script (today the
   `tutorial` route plays `CLASSIC`; pass the mode's id as an argument).
5. **Tests**: the three classes above for the new script.
6. **Docs**: this file, `DESIGN.md`'s tutorial section, and `GAME_MODES.md`'s checklist if a mode gains a tutorial.
