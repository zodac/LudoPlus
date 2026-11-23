# Game modes

How LudoPlus's modes and house rules are built, and how to add one. DiceFive's version of this doc (its dice modes:
Stud, Third Wind, Hit List, and the rest) is in commit `5423b6c` (`git show 5423b6c:.claude/GAME_MODES.md`). The
achievement audit (what each mode or house rule blocks or guards) is in `GAME_MODES_AUDIT.md`.

| Mode | `id` | Different from Classic | Achievements |
| --- | --- | --- | --- |
| `CLASSIC` | `classic` | The classic rules (`IMPLEMENTATION.md`, "The classic rules"). The default | Yes |
| `QUICK` | `quick` | Two pawns each (`pawnsPerPlayer = 2`) | No |
| `TWO_DICE` | `two_dice` | Two dice, each moved on its own (`dice = 2`); doubles roll again, not 6s (`rollAgainOnDoubles`, `rollAgainRolls` empty) | No |
| `PORTALS` | `portals` | Portals on the track (`portals = true`): a move ending on one comes out at its pair | No |

**Custom modes** (`model/CustomMode.kt`) are the player's own: a built-in base and house rules, saved under a name
(`DESIGN.md`, "Game modes"). They are data in the settings, not `GameMode` entries: a game of one is its base mode's,
with the house rules on.

## The one rule: a mode is data

`model/GameMode.kt`. **Every rule a mode can change is a field, never an `if (mode == X)`.** A mode is its `rules`
(`GameRules`) and a few fields of its own; the engine, the CPU, the board, and the achievements read the rules from
`GameState.rules`, never the mode.

- `GameMode` fields: `id` (stored, permanent once released), `displayName`, `description` (New Game's one line: say
    what's different), `rules`, and `earnsAchievements` (only Classic, until modes get their own achievements).
- `GameRules` fields: the board (`forPlayers` swaps it by count), `pawnsPerPlayer`, `dieFaces`, `dice`, the yard-exit and
    roll-again rolls, the roll-again limit, the capture and home rolls, the house rules' fields, the teams' fields, and
    `stopAtFirstWinner`. Each is a doc comment stating its rule.
- **House rules** (`model/HouseRule.kt`) are modifiers: each is one `GameRules` change on top of any mode
    (`DESIGN.md`, "Modifiers"). A new one is an entry there, a field if the rules need one, its strings, and its place on
    a Rules page.

## Checklist for a new mode

- **The entry** in `GameMode`, its strings (`mode_<id>`, `mode_<id>_description`), and its colour in `GameMode.color`
    (`ui/theme/Color.kt`; `GameModeColourTest`).
- **The engine**: a rule no field covers yet becomes a `GameRules` field, read by `LudoEngine` (and by the CPU's
    reckoning in `CpuStrategy` where it changes what a pawn can reach), tested in its own engine test.
- **Anything sized by the rules**: a pawn count other than 4 goes through `pawnsPerPlayer` (the header's dots, Game
    Over, and the yard: `BoardStyle.drawBoard` draws a spot a pawn, placed by `BoardLayout.yardSpotOf`).
- **The save** (`SavedGameJson`): the mode is written by id, and its rules come from it. A random-games round trip
    for the mode (`SavedGameJsonTest`).
- **Records**: each mode has its own Leaderboard (per player count) and Statistics page from its `id`; nothing to add.
    The Leaderboard and Statistics pages list `GameMode.entries` in their mode rows.
- **Achievements**: off unless `earnsAchievements`; if on, audit it (`GAME_MODES_AUDIT.md`).
- **The Rules screen**: a page in the Game Modes group (`RulesContent.kt`), and `RulesTextTest`'s words.
- **Docs**: this table, `DESIGN.md`'s "Game modes", and `IMPLEMENTATION.md`'s Phase 9.
- **Verify** with the `test` skill's full gate, render the board, then send the debug APK. Commit as
    `[Game Mode] ...`.
