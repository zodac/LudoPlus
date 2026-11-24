# LudoPlus: the game screen (UI detail)

The board, the player cards, the die, and the move log. The general rules are in `UI.md`, and what the game does is in
`DESIGN.md`. DiceFive's scorecard screen is in commit `5423b6c`.

## The page

- **Portrait** (`PortraitGame`, `GameScreen.kt`), top to bottom: the player cards, the board, the die and its line
    (`TurnPanel`), and the move log in what's left.
    - The board is the screen's width, less what it gives up on a short screen: up to a tenth for the log's second line,
        and more only for its first, never under 60% (`portraitBoardSide`). Its size allows for the die alone, so a long
        prompt, or the winner's line, takes its room from the log and never resizes the board.
    - On a screen too short for it all, the player cards close up (`compact`).
- **Landscape**: the board at the screen's height, and beside it, at least 280dp wide, the cards, the die, and the log.
- Over the cards: a Nearby game's notice (`NetplayNotice`), and in Survival how long until sudden death
    (`SuddenDeathNotice`), both polite live regions. The tutorial's coach card takes the cards' place (`TUTORIAL.md`).
- The background style fills the page from the foot of the app bar down (`ScreenScaffold`'s `underlay`).
- Tests: `MoveLogRoomTest`, `NetplayNoticeTest`, `TutorialScreenTest`.

## The board

- **`BoardLayout`** (`BoardLayout.kt`) places every square, yard, and home column: the classic 15x15 cross for 2 and 4
    players, a hexagon-like board for 3, and a star of arms for 5 to 8 (`BoardGeometry.forPlayers`). `BoardLayoutTest` and
    `StarBoardLayoutTest` hold it.
- **`LudoBoard`** draws it in four parts, so a move redraws only the move (`BENCHMARKS.md`). What a mode adds is drawn
    under the pawns: portals (`PortalArt.kt`), power-ups (`PowerUpArt.kt`), Survival's arena (`SurvivalArt.kt`), and
    ghosts (`GhostArt.kt`).
- **Pawns on one square** share it, smaller; two or more of a colour are one pawn with a count. A safe square, a
    blockade, and a shield each have their mark.
- **A movable pawn is ringed** in violet. A tap on its square makes the move; where a square holds more than one, a tap
    asks which. The player whose turn it is has a white ring, edged in ink, inside their yard.
- **Moves** are played square by square (`GamePacing`, "Sound, haptics and motion" in `DESIGN.md`).

## The player cards

`PlayerCard` (`GameScreen.kt`): a disc of the player's colour with their rank on it, their name, a CPU's icon, a badge for their team or a doubled roll,
and a dot for each pawn home (in Survival, each pawn left), or their place once they have one. The card of the player
whose turn it is has an outline in their colour.

- **Laid out as the yards are** round the board (`playerCardRows`, `PlayerCardRowsTest`): player 1 bottom left, and a yard
    straight above or below the middle on a row of its own. TalkBack reads them in player order, whatever the rows.
- **Rank** (`GameState.ranks`, `RanksTest`; the maintainer, 2026-11-24): where the player stands now, in every mode. Those
    placed keep their place; the rest go by pawns left in Survival (`lastStanding`), and otherwise by how far their pawns
    have come in all (`stepsOf`), as a game stopped at its first winner places them. Partners share their team's, a tie
    shares one, and nobody has one while everyone is level. TalkBack: "currently 2nd", "currently joint 2nd", or "4th place"
    once placed (`PlayerCardTest`).
- **A name too long for its card** is cut with an ellipsis, and a tap anywhere on the card shows all of it in a tooltip. It
    isn't a button to TalkBack, which reads the whole name with the card.
- **Names** are held to the most players' cap (`model/NameWidth.kt`: 8), in widths, not UTF-16 units: full-width
    characters count two, combining marks none. CPU names are shorter, so the cap never
    cuts one (`AiNameGenerator`, `AiNameGeneratorTest`), though a narrow card may.
- **Sanitising** (`sanitizePlayerName`, `PlayerNameTest`) happens as names are typed and again in `startGame`. Nothing is
    censored.
    - Removed: control characters (whitespace collapses to one space), zero-width and bidi override/isolate characters,
        soft hyphens, BOMs, replacement/private-use/non-characters, unpaired surrogates, and Hangul fillers.
    - Kept: ZWJ/ZWNJ and LRM/RLM. Capped: 4 combining marks a character (8 tag characters for flags), none on nothing, and
        64 code points. The result is NFC (`normalizeNfc`).
    - Not trimmed on the way in, because a trailing space must survive typing. Compared with `playerNameKey`.

## The die and its line

- `GameDie` draws the player's die style, with a ring in the turn's colour: in full while it can be rolled, and faint the
    rest of the turn. It can't be rolled until the last action has played out (`GameViewModel.settled`: the die's tumble, a
    pawn's move, or a ghost's drift). Two Dice draws two, at 0.8 of the size; a tap on one picks it for the next move (`TwoDice`).
- `StatusLines` says what to do now, names in their players' colours. It's a polite live region, and holds every legal
    move as a custom action, so TalkBack can make a move from there as well as from a pawn's square.

## The move log

`MoveLogPanel`: every move, newest first, back to who went first, in a card that scrolls back to its top as each comes.
Where there isn't room for the title and two lines, the card has no title; where there isn't room for a line, nothing is
drawn, and TalkBack still hears the newest. The lines are a list to TalkBack ("1 of 12"), read without their times.

## What TalkBack hears

`BoardSemantics.kt` and `PawnSpeech.kt` hold the board's spoken names: every square says what's on it, and a movable pawn's
square offers its move ("Move Red pawn 1 2 squares, capturing Green pawn 3"). `BoardSemanticsTest` and `PawnSpeechTest` pin
the words. A game's steps are spoken by the log's newest line and the status line, both polite.
