package net.zodac.ludo.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.LudoEngine
import net.zodac.ludo.game.Move
import net.zodac.ludo.model.PawnPosition
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.game_move_action
import net.zodac.ludo.resources.game_move_capture_clause
import net.zodac.ludo.resources.game_move_home_clause
import net.zodac.ludo.resources.game_move_out_action
import net.zodac.ludo.resources.game_square_blockade_spoken
import net.zodac.ludo.resources.game_square_column_spoken
import net.zodac.ludo.resources.game_square_empty_spoken
import net.zodac.ludo.resources.game_square_home_spoken
import net.zodac.ludo.resources.game_square_safe_for_spoken
import net.zodac.ludo.resources.game_square_safe_spoken
import net.zodac.ludo.resources.game_square_start_spoken
import net.zodac.ludo.resources.game_square_track_spoken
import net.zodac.ludo.resources.game_square_yard_pawns_spoken
import net.zodac.ludo.resources.game_square_yard_spoken
import net.zodac.ludo.ui.common.joinClauses
import net.zodac.ludo.ui.common.pluralStringResource
import net.zodac.ludo.ui.common.stringResource

/** A place on the board a screen reader stops at: where it is, in board units, and what's said and offered there. */
private class Spot(val bounds: Rect, val description: String, val move: Move?, val moveLabel: String?)

/** The unit box round a square's centre: a turned square's own box would overlap its neighbours'. */
private fun BoardSquare.spotBounds(): Rect = Rect(centre - Offset(0.5f, 0.5f), Size(1f, 1f))

private fun List<Offset>.bounds(): Rect = Rect(minOf { it.x }, minOf { it.y }, maxOf { it.x }, maxOf { it.y })

/**
 * The board for a screen reader, laid over the drawn one: a node for each yard, each track square (numbered from red's
 * start), each home-column square, and home, saying what's on it ("Square 14, Red pawn 2, 43 squares from home"). A
 * square holding a pawn the current player can move (theirs, or a partner's they roll for) is a button whose action
 * moves it ([moves], [onMove]).
 */
@Composable
fun BoardSemantics(state: GameState, seatColours: List<PlayerColour?>, moves: List<Move>, onMove: (Move) -> Unit, modifier: Modifier = Modifier) {
    val spots = boardSpots(state, seatColours, moves)
    BoxWithConstraints(modifier = modifier.semantics { isTraversalGroup = true }) {
        val unit: Dp = maxWidth / BoardLayout.of(state.board).size
        spots.forEachIndexed { index, spot ->
            Box(
                Modifier
                    .offset(unit * spot.bounds.left, unit * spot.bounds.top)
                    .size(unit * spot.bounds.width, unit * spot.bounds.height)
                    .semantics {
                        contentDescription = spot.description
                        traversalIndex = index.toFloat()
                        val move = spot.move
                        if (move != null) onClick(label = spot.moveLabel) { onMove(move); true }
                    },
            )
        }
    }
}

@Composable
private fun boardSpots(state: GameState, seatColours: List<PlayerColour?>, moves: List<Move>): List<Spot> {
    val board = state.board
    val layout = BoardLayout.of(board)
    val colourOf = { player: Int -> seatColours[state.players[player].seat] ?: PlayerColour.defaultFor(state.players[player].seat) }
    // Every pawn's spoken name and where it is, once.
    val pawns = state.players.indices.flatMap { player ->
        state.players[player].pawns.indices.map { pawn ->
            val position = state.position(player, pawn)
            Triple(player, pawn, position) to pawnSpoken(colourOf(player), pawn + 1, position, board)
        }
    }
    val moveLabels = moves.associateWith { moveLabel(state, it, colourOf) }
    val movableAt = { player: Int, pawnsHere: List<Int> ->
        moves.firstOrNull { it.ownerIn(state) == player && it.pawn in pawnsHere }
    }
    val empty = stringResource(Res.string.game_square_empty_spoken)
    val spots = mutableListOf<Spot>()
    // The yards.
    for (player in state.players.indices) {
        val seat = state.players[player].seat
        val waiting = state.players[player].pawns.indices.filter { state.position(player, it) == PawnPosition.Yard }
        val description = joinClauses(
            listOf(
                stringResource(Res.string.game_square_yard_spoken, stringResource(colourOf(player).label)),
                if (waiting.isEmpty()) empty else pluralStringResource(Res.plurals.game_square_yard_pawns_spoken, waiting.size, waiting.size),
            ),
        )
        val move = movableAt(player, waiting)
        spots += Spot(layout.yard(seat).bounds(), description, move, move?.let(moveLabels::get))
    }
    // The track, from red's start.
    val blockades = LudoEngine.blockades(state)
    for (square in 0..<board.trackLength) {
        val here = pawns.filter { (key, _) ->
            val (player, _, position) = key
            position is PawnPosition.Track && board.trackSquare(state.players[player].seat, position.steps) == square
        }
        val startOf = state.players.indexOfFirst { board.startSquare(it.seat) == square }.takeIf { it >= 0 }
        // A guarded start is safe for its own colour alone; a start square no one sits at, for nobody.
        val guardedStart = state.rules.guardedStarts && square % board.seatSpacing == 0
        val safety = when {
            !board.isSafe(square) -> null
            !guardedStart -> stringResource(Res.string.game_square_safe_spoken)
            startOf != null -> stringResource(Res.string.game_square_safe_for_spoken, stringResource(colourOf(startOf).label))
            else -> null
        }
        val blockade = blockades[square]?.let { stringResource(Res.string.game_square_blockade_spoken, stringResource(colourOf(it).label)) }
        val clauses = listOfNotNull(
            stringResource(Res.string.game_square_track_spoken, square + 1),
            startOf?.let { stringResource(Res.string.game_square_start_spoken, stringResource(colourOf(it).label)) },
            safety,
            blockade,
        ) + here.map { it.second }.ifEmpty { listOf(empty) }
        val move = state.players.indices.firstNotNullOfOrNull { player -> movableAt(player, here.filter { it.first.first == player }.map { it.first.second }) }
        spots += Spot(layout.trackSquare(square).spotBounds(), joinClauses(clauses), move, move?.let(moveLabels::get))
    }
    // Each home column, then home.
    for (player in state.players.indices) {
        val seat = state.players[player].seat
        for (index in 0..<board.homeColumnLength) {
            val here = pawns.filter { (key, _) -> key.first == player && key.third == PawnPosition.HomeColumn(index) }
            val clauses = listOf(stringResource(Res.string.game_square_column_spoken, stringResource(colourOf(player).label), index + 1)) +
                here.map { it.second }.ifEmpty { listOf(empty) }
            val move = movableAt(player, here.map { it.first.second })
            spots += Spot(layout.homeColumnSquare(seat, index).spotBounds(), joinClauses(clauses), move, move?.let(moveLabels::get))
        }
    }
    val home = pawns.filter { it.first.third == PawnPosition.Home }.map { it.second }
    spots += Spot(layout.homeBounds, joinClauses(listOf(stringResource(Res.string.game_square_home_spoken)) + home.ifEmpty { listOf(empty) }), null, null)
    return spots
}

/**
 * What TalkBack offers for [move]: "Move Red pawn 2 4 squares, capturing Green pawn 1", or "Bring Red pawn 1 out of the
 * yard". A partner's pawn is named by its own colour.
 */
@Composable
fun moveLabel(state: GameState, move: Move, colourOf: (Int) -> PlayerColour): String {
    val colour = stringResource(colourOf(move.ownerIn(state)).label)
    val number = move.pawn + 1
    val roll = move.to - move.from
    val main = if (move.from == PawnPosition.YARD_PROGRESS) {
        stringResource(Res.string.game_move_out_action, colour, number)
    } else {
        pluralStringResource(Res.plurals.game_move_action, roll, colour, number, roll)
    }
    val captures = move.captures.map {
        stringResource(Res.string.game_move_capture_clause, stringResource(colourOf(it.player).label), it.pawn + 1)
    }
    val home = if (move.to == state.board.homeProgress) listOf(stringResource(Res.string.game_move_home_clause)) else emptyList()
    return joinClauses(listOf(main) + captures + home)
}
