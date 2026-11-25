# LudoPlus: the game screen (UI detail)

The board, the player cards, the die, and the move log. The general rules are in `UI.md`, and what the game does is in
`DESIGN.md`. DiceFive's scorecard screen is in commit `5423b6c`.

## The page

- **Portrait** (`PortraitGame`, `GameScreen.kt`), top to bottom: the player cards, the board, the die and its line
    (`TurnPanel`), and the move log in what's left.
    - The board is the screen's width, less what it gives up on a short screen: what the log needs for its newest turn's
        heading and a line, never under 60% (`portraitBoardSide`). Its size allows for the die alone, so a long
        prompt, or the winner's line, takes its room from the log and never resizes the board.
    - On a screen too short for it all, the player cards close up (`compact`).
- **The time played** (`GameTime`, the maintainer, 2026-11-26) is under the title, the same line as Statistics'
    (`PlayTimeLine`: "Time played: 12m", seconds for the first minute alone, the numbers gold): `StartedGame.playMillis` up
    to now, so it stops off screen and under the leave dialog, and at the end. Not in the tutorial.
    - It's in the app bar's spare height (`PageTopBar`'s `subtitle`), so the cards and the board are where they'd be
        without it, and so is the title in portrait at 100%. In landscape the title moves up 6dp for it. At a large font
        both shrink to fit the bar, to their 100% size at the least. It never leaves the bar: the background under that may
        be any colour.
    - The game's title is on one line, shrunk to fit, so a long custom mode's name leaves it room.
    - TalkBack hears "Time played: 12 minutes" on reaching it, never as it counts. `GameTimeTest`.
- **Landscape**: the board at the screen's height, and beside it, at least 280dp wide, the cards, the die, and the log.
- Over the cards: a Nearby game's notice (`NetplayNotice`), and in Survival how long until its next stage
    (`SuddenDeathNotice`), both polite live regions. The tutorial's coach card takes the cards' place (`TUTORIAL.md`).
- The background style fills the page from the foot of the app bar down (`ScreenScaffold`'s `underlay`).
- Tests: `MoveLogRoomTest`, `NetplayNoticeTest`, `TutorialScreenTest`.

## The board

- **`BoardLayout`** (`BoardLayout.kt`) places every square, yard, and home column: the classic 15x15 cross for 2 and 4
    players, a hexagon-like board for 3, and a star of arms for 5 to 8 (`BoardGeometry.forPlayers`). `BoardLayoutTest` and
    `StarBoardLayoutTest` hold it.
- **A round board's yards are round too**: the outer side of each, and of its court, is an arc about the board's middle (the
    maintainer, 2026-11-25). `BoardLayout.yard` gives it as many short sides, so a style that draws a yard side by side takes
    them from `strokes`, where an arc is one; `yardCorners` is its corners alone.
- **Where pawns wait** (`BoardLayout.yardSpotOf`; the maintainer, 2026-11-25): every whole spot is on its yard's court, whatever
    the number of pawns (`BoardLayoutTest`). A lone pawn is in the yard's middle.
    - Spots are as big as the yard has room for with its power-ups, never bigger than the classic board's 0.62 of a square, which
        every yard for 2 to 4 players has whatever the number of pawns. A pawn fills four fifths of its spot, or up to nine
        tenths of a small one, so that it's as big as on the track where it can be.
    - 2 and 4 players: the four near the court's corners, two on opposite ones, three one above two, and five or six in two
        rows.
    - 3 players: up to four in a line along the yard, and five or six in two rows.
    - 5 to 8: in pairs down the court's two straight sides, an odd one at its inner tip.
- **Where pawns stand home** (`BoardLayout.homeStand`, `HomeRows`; the maintainer, 2026-11-26): in rows, the first along the
    side the home column joins (2, 2+1, 3+1, 3+2, and 3+2+1; on the deep homes of 6 to 8 players, four are 2+1+1, and eight
    players' five are 2+2+1), spread and sized to fill their part of home for that board and number of pawns home
    (`BoardLayoutTest`). A pawn is as big as on the track where there's room, and never bigger.
    Those home share the room by how many there are now, so the first keeps its size, and they shrink and make room as each of the rest comes,
    in its last half hop, as on a shared square (`moveMarks`). A pawn fills a little less of its spot than in a yard, and stands a
    little above its middle where that keeps its foot off the lines (`PawnStyle.lift`).
- **`LudoBoard`** draws it in four parts, so a move redraws only the move (`BENCHMARKS.md`). What a mode adds is drawn
    under the pawns: portals (`PortalArt.kt`), power-ups (`PowerUpArt.kt`), Survival's arena (`SurvivalArt.kt`), and
    ghosts (`GhostArt.kt`).
- **A lone pawn on a turned square** stands a little above its middle (`BoardSquare.stand`), so its foot is as clear of the
    lines under it as its head is of those over it (the maintainer, 2026-11-26). Never below,
    so an upright square's is in its middle. A pawn style wide at its top rises less (`PawnStyle.lift`, `BoardAndPawnArtTest`).
- **Pawns on one square** share it, smaller (on a turned square, in its turned quarters, left and right first; the
    maintainer, 2026-11-26); two or more of a colour are one pawn with a count. A safe square (a
    star, or a start square's border), a blockade, and a shield each have their mark. They make room as the pawn joining
    them comes onto the square, in its last half hop, and it lands on its own share of it (`moveMarks`; the maintainer, 2026-11-25).
- **A shield goes with its pawn** as it hops, or is swapped, till it comes off the track or is taken; one it takes shows as
    it lands (`ShieldMotionTest`; the maintainer, 2026-11-25).
- **What's in force for a player is on their yard**, not their card (the maintainer, 2026-11-25): while their rolls count
    double, the Double power-up with how many are left, and while they have any banked, Call Up with how many (`drawInForce`,
    `YardPowerUpTest`). Each has its own place on the yard's court, never its coloured border, clear of its pawns
    on every board with any number of them (`BoardLayout.yardBadges`), and changes as the move that changes it has played out. TalkBack: the yard
    says it, "Yellow yard, 4 pawns waiting, 1 doubled roll left, 2 Call Ups banked".
- **A movable pawn is ringed** in violet. A tap on its square makes the move, and one anywhere in its yard the move of the
    nearest there (`yardPawnAt`); where a square holds more than one, a tap asks which.
    The player whose turn it is has a white ring, edged in ink, inside their yard.
- **A mode's own tiles** (`BoardTiles.kt`, `BoardTilesTest`; the maintainer, 2026-11-24). While a portal is pressed, it and
    its pair are bigger and ringed in gold, and everything else on the board fades to a fifth (`BOARD_FADE`). While a Swap's
    other pawn is picked, once its own has landed, everything fades as far but that pawn, unringed, and the pawns it can be
    with, ringed (the maintainer, 2026-11-25).
    A tap on a power-up shows its name in a tooltip, and a long
    press what it does too, up until a tap anywhere (never timed out), or until it's taken. A tap on a tile is the tile's,
    not a move, unless a movable pawn stands on it or is nearer (`pawnTakesTap`); a portal held is never a move. TalkBack: a
    power-up's square has the action "What Shield does", whose tooltip is a live region; a portal's square says its pair.
- **A yard held** (a long press anywhere in it, till it's let go; the maintainer, 2026-11-25): everything fades as far but
    its player's pawns, wherever they are, each with its number on it (its index and 1, as TalkBack and the log say it). Those on one
    square are told apart, in a ring about its middle (`PawnMark.apart`). They keep their numbers while a move or a ghost
    plays out. Nothing is ringed, and a pawn in the yard held is never moved. Survival's pawns lost are out of the game, so they
    fade with the rest. `BoardTilesTest`. TalkBack needs no action for it: each square says its pawns by number.
- **Moves** are played square by square (`GamePacing`, "Sound, haptics and motion" in `DESIGN.md`).

## The player cards

`PlayerCard` (`GameScreen.kt`): a disc of the player's colour with their rank on it, their name, a CPU's icon, a badge for their team,
and a dot for each pawn home (in Survival, each pawn left), or their place once they have one. The card of the player
whose turn it is has an outline in their colour.

- **Laid out as the yards are** round the board (`playerCardRows`, `PlayerCardRowsTest`): player 1 bottom left, and a yard
    straight above or below the middle on a row of its own. TalkBack reads them in player order, whatever the rows.
- **Rank** (`GameState.ranks`, `RanksTest`; the maintainer, 2026-11-24): where the player stands now, in every mode. Those
    placed keep their place; the rest go by pawns left in Survival (`lastStanding`), by squares in Colour Rush (`claims`, whose
    cards also have a count on a square of the player's colour), and otherwise by how far their pawns
    have come in all (`stepsOf`), as a game stopped at its first winner places them. Partners share their team's, a tie
    shares one, drawn with an equals sign as DiceFive's is ("=2", `game_player_rank_tied`, on a pill as wide as it needs;
    the maintainer, 2026-11-25), and nobody has one while everyone is level. TalkBack: "currently 2nd", "currently joint 2nd", or "4th place"
    once placed (`PlayerCardTest`).
- **A tap anywhere on the card** shows their captures and times captured, in all and against each opponent (`PlayerStats`,
    `PlayerCardTest`; the maintainer, 2026-11-26, in place of a tap's summary and a long press): the totals, headed "Captures" and
    "Captured by", then a bar either side of each opponent's name, in their colour, all on one scale. Partners are left out
    unless they capture each other, and a ghost's aren't captures. A name too long for its card is cut with an ellipsis, and the
    popup has all of it above the totals; one that fits isn't repeated. A tap anywhere only closes it, as a power-up's.
    TalkBack: the card is a button, "Show captures and rolls", and the popup reads "Lucky, 3 captures, captured by 1", then "Alex, 2
    captures, captured by 0" for each. The card itself doesn't say the counts.
    - The counts are `PlayerState.capturesOf`, kept in the saved game (`capturesOf`, absent with none). A game saved before it
        was kept has its totals, and rows for the captures since.
    - **Rolls** are under them (the maintainer, 2026-11-26): the total, headed "Rolls", then each face of the die in two columns, 1-3
        and 4-6, with a bar in the player's colour, all on one scale. Each of two dice counts, as do rolls with no move, and a doubled
        roll counts as its face. The faces are in the player's die style, drawn at the size its art is made for (44dp) and scaled
        down, since a style's pip margins are fixed. TalkBack: "36 rolls", then down each column, "Rolled a 1, 7 times".
    - The rolls are `PlayerState.rolls`, kept in the saved game (`rolls`, absent with none). A game saved before it was kept has
        those since.
    - The card whose popup is up has a dashed outline in its player's colour, as DiceFive's viewed
        tab had (the maintainer, 2026-11-25), in place of the solid one if it's also their turn. Its spoken twin is the name the popup's
        totals start with.
    - `AppTooltip(wide = true)`: a rich tooltip's width, 8dp clear of the screen's edges. It scrolls where a large font makes it
        taller than the screen.
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
    pawn's move, a ghost's drift, or squares vanishing). Two Dice draws two, at 0.8 of the size; a tap on one picks it for the next move (`TwoDice`).
- **A roll with no move** (or that ends a run of 6s) is seen before it counts (the maintainer, 2026-11-25): while the die
    tumbles (`GameViewModel.actionShown`) the turn stays its roller's, in the line ("Alex is rolling."), the die's ring, the
    card, and the yard, and the log doesn't tell of it. One that passes the turn on is shown for `TURN_PASSED_MILLIS` more.
    With the Die roll motion off, the turn passes at once.
- **A move that passes the turn on** keeps it its player's the same way until its pawn has landed, and what follows has
    played (a capture's flight, a flourish; the maintainer, 2026-11-26): the line ("Alex is moving.", not a live region, as the
    log's newest line says the move at once), the die's ring, the card, and the yard. A ghost's drift or
    squares vanishing after it play on the next player's turn. With the Pawn moves motion off, the turn passes at once.
- `StatusLines` says what to do now, names in their players' colours. It's a polite live region, and holds every legal
    move as a custom action, so TalkBack can make a move from there as well as from a pawn's square.

## The move log

`MoveLogPanel`: every move, newest first, back to who went first, in a card that scrolls back to its top as each comes.
Each round is under a heading ("Turn 3", `LogEntry.turn`: which of its player's turns each move was in), and who went first
under "Start". A roll again's moves are lines of the same turn. The card has no title (the maintainer, 2026-11-25: it only
took room). Where there isn't room for a line under the heading, it has no heading; where there isn't room for a line, or
with Settings' **Move log** off (`show_move_log`, on by default), nothing is drawn, and TalkBack still hears the newest; with
it off, the board has the log's room. Pawns are named by their numbers, a held yard's (the maintainer, 2026-11-25):
the one that moved ("Alex's Pawn 2 moved 4 squares.", which fits a line at a large font), a partner's with their own
name, and any captured, caught, swapped, or brought back out ("Alex captured Sam's Pawn 1 and Pawn 3 with Pawn 2."). A
player's last pawn home has no number. Every move is logged either way. A game saved before entries kept their turns
(1.1.0) has them counted back as it's loaded (`MoveLog.withTurns`). To TalkBack the headings are headings, and the
lines a list ("1 of 12"), read without their times.

## What TalkBack hears

`BoardSemantics.kt` and `PawnSpeech.kt` hold the board's spoken names: every square says what's on it, and a movable pawn's
square offers its move ("Move Red pawn 1 2 squares, capturing Green pawn 3"). `BoardSemanticsTest` and `PawnSpeechTest` pin
the words. A game's steps are spoken by the log's newest line and the status line, both polite.
