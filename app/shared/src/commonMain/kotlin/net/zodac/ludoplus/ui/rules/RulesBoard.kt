package net.zodac.ludoplus.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.ludoplus.game.Cataclysm
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.rules_chaos_board_cd
import net.zodac.ludoplus.resources.rules_colour_rush_board_cd
import net.zodac.ludoplus.resources.rules_haunted_house_board_cd
import net.zodac.ludoplus.resources.rules_portals_board_cd
import net.zodac.ludoplus.resources.rules_survival_board_cd
import net.zodac.ludoplus.resources.rules_two_dice_board_cd
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.game.GameDie
import net.zodac.ludoplus.ui.game.LudoBoard
import org.jetbrains.compose.resources.StringResource

/** How many players every mode's board shows: four, one at each corner of the classic board. */
private const val PLAYERS = 4

private val board = BoardGeometry.CLASSIC

/** The absolute track square [progress] steps on from [seat]'s start square. */
private fun square(seat: Int, progress: Int): Int = board.trackSquare(seat, progress)

/** [mode]'s new game for four, each player's pawns at the progress given (by seat), the rest as [GameState.new] sets them. */
private fun staged(mode: GameMode, vararg pawns: List<Int>, change: GameState.() -> GameState = { this }): GameState {
    val new = GameState.new(mode, PLAYERS)
    return new.copy(players = new.players.mapIndexed { index, player -> player.copy(pawns = pawns[index]) }).change()
}

private const val Y = PawnPosition.YARD_PROGRESS
private const val X = PawnPosition.OUT_PROGRESS

/** Colour Rush: each player's run of claimed squares behind their pawns, Red ahead with the most. */
private val COLOUR_RUSH_BOARD = staged(
    GameMode.COLOUR_RUSH,
    listOf(9, 12, Y, Y),
    listOf(7, Y, Y, Y),
    listOf(9, 3, Y, Y),
    listOf(5, Y, Y, Y),
) {
    val runs = mapOf(0 to (1..12), 1 to (1..7), 2 to (1..9), 3 to (1..5))
    copy(claimed = runs.flatMap { (seat, run) -> run.map { square(seat, it) to seat } }.filterNot { board.isStart(it.first) }.toMap())
}

/** Two Dice: the classic board, everyone out on the track, with the roll shown under it ([TWO_DICE_ROLL]). */
private val TWO_DICE_BOARD = staged(GameMode.TWO_DICE, listOf(4, 20, Y, Y), listOf(9, Y, Y, Y), listOf(14, 2, Y, Y), listOf(6, Y, Y, Y))

/** The roll the Two Dice board shows: a 6, to bring a pawn out, and a 3 to move it on. */
private val TWO_DICE_ROLL = listOf(6, 3)

/** Portals: every pair on the track, a pawn waiting on one, the others around them. */
private val PORTALS_BOARD = staged(GameMode.PORTALS, listOf(10, 1, Y, Y), listOf(17, Y, Y, Y), listOf(5, Y, Y, Y), listOf(20, 12, Y, Y))

/** Survival: the Cataclysm come, squares gone from the track, the start and star squares marked unsafe, and pawns lost. */
private val SURVIVAL_BOARD = staged(
    GameMode.SURVIVAL,
    listOf(60, 66, 71, 75, X, X),
    listOf(58, 64, X, X, X, X),
    listOf(55, 62, 69, X, X, X),
    listOf(57, 70, 74, 79, 81, X),
) {
    copy(
        suddenDeathTurns = Cataclysm.STARTS + 1,
        lostSquares = setOf(square(0, 3), square(0, 4), square(1, 9), square(2, 6), square(3, 2), square(3, 11)),
    )
}

/** Chaos: one of each power-up on the track, a shielded pawn, and Red's two doubled rolls and banked Call Up on its yard. */
private val CHAOS_BOARD = staged(GameMode.CHAOS, listOf(6, Y, Y, Y), listOf(11, 3, Y, Y), listOf(7, Y, Y, Y), listOf(16, Y, Y, Y)) {
    copy(
        powerUps = mapOf(
            square(0, 10) to PowerUp.DOUBLE,
            square(1, 6) to PowerUp.SHIELD,
            square(2, 3) to PowerUp.ROLL_AGAIN,
            square(2, 11) to PowerUp.CALL_UP,
            square(3, 5) to PowerUp.SWAP,
        ),
        players = players.mapIndexed { index, player ->
            when (index) {
                0 -> player.copy(doubledRolls = 2, callUps = 1)
                1 -> player.copy(shielded = setOf(0))
                else -> player
            }
        },
    )
}

/** Haunted House: its two ghosts on the track, one beside a red pawn, and pawns sent back filling the yards. */
private val HAUNTED_HOUSE_BOARD = staged(GameMode.HAUNTED_HOUSE, listOf(9, Y, Y, Y), listOf(4, 15, Y, Y), listOf(10, Y, Y, Y), listOf(3, Y, Y, Y)) {
    copy(ghosts = listOf(square(0, 11), square(2, 4)))
}

/**
 * The board each Game Modes page shows: a game of its mode, staged ([state]), as the game draws it, with [dice] under it
 * where the mode's roll needs showing. TalkBack hears it as one image, [description].
 */
enum class RulesBoardKind(internal val state: GameState, internal val description: StringResource, internal val dice: List<Int> = emptyList()) {
    COLOUR_RUSH(COLOUR_RUSH_BOARD, Res.string.rules_colour_rush_board_cd),
    TWO_DICE(TWO_DICE_BOARD, Res.string.rules_two_dice_board_cd, TWO_DICE_ROLL),
    PORTALS(PORTALS_BOARD, Res.string.rules_portals_board_cd),
    SURVIVAL(SURVIVAL_BOARD, Res.string.rules_survival_board_cd),
    CHAOS(CHAOS_BOARD, Res.string.rules_chaos_board_cd),
    HAUNTED_HOUSE(HAUNTED_HOUSE_BOARD, Res.string.rules_haunted_house_board_cd),
}

/** The widest a mode's board draws, as a diagram does ([RulesDiagram]). */
private val MAX_BOARD_WIDTH = 420.dp

/** How big each die under a board is. */
private val DIE_SIZE = 48.dp

/** Between the board and its dice. */
private val BOARD_DICE_GAP = 10.dp

/** Each seat's default colour, as the board's own diagrams have them. */
private val SEAT_COLOURS: List<PlayerColour?> = List(board.seats) { PlayerColour.defaultFor(it) }

/** The width [kind]'s board draws at to be [height] tall, its dice under it included. */
internal fun boardWidthFor(kind: RulesBoardKind, height: Dp): Dp = if (kind.dice.isEmpty()) height else height - BOARD_DICE_GAP - DIE_SIZE

/**
 * A Game Modes page's board ([kind]): the game's own board, in the default colours, as a game of the mode might stand,
 * with its roll's dice under it. It draws only; TalkBack hears it as one image described in words.
 */
@Composable
fun RulesBoard(kind: RulesBoardKind, modifier: Modifier = Modifier) {
    val description = stringResource(kind.description)
    Column(
        modifier = modifier
            .widthIn(max = MAX_BOARD_WIDTH)
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Image
            },
        verticalArrangement = Arrangement.spacedBy(BOARD_DICE_GAP),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            LudoBoard(
                state = kind.state,
                seatColours = SEAT_COLOURS,
                moves = emptyList(),
                onMove = {},
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
            )
        }
        if (kind.dice.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (value in kind.dice) GameDie(value = value, roll = null, canRoll = false, onRoll = {}, modifier = Modifier.size(DIE_SIZE))
            }
        }
    }
}
