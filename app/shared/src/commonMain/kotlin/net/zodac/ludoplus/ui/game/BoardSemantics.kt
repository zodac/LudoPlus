package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.portalAt
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.game_move_action
import net.zodac.ludoplus.resources.game_move_bounce_clause
import net.zodac.ludoplus.resources.game_move_capture_clause
import net.zodac.ludoplus.resources.game_move_claim_clause
import net.zodac.ludoplus.resources.game_move_home_clause
import net.zodac.ludoplus.resources.game_move_out_action
import net.zodac.ludoplus.resources.game_move_out_free_action
import net.zodac.ludoplus.resources.game_move_portal_clause
import net.zodac.ludoplus.resources.game_move_power_up_clause
import net.zodac.ludoplus.resources.game_pawn_shielded_spoken
import net.zodac.ludoplus.resources.game_square_blockade_spoken
import net.zodac.ludoplus.resources.game_square_claimed_spoken
import net.zodac.ludoplus.resources.game_square_column_spoken
import net.zodac.ludoplus.resources.game_square_empty_spoken
import net.zodac.ludoplus.resources.game_square_ghost_spoken
import net.zodac.ludoplus.resources.game_square_home_spoken
import net.zodac.ludoplus.resources.game_square_portal_spoken
import net.zodac.ludoplus.resources.game_square_power_up_action
import net.zodac.ludoplus.resources.game_square_power_up_spoken
import net.zodac.ludoplus.resources.game_square_safe_for_spoken
import net.zodac.ludoplus.resources.game_square_safe_spoken
import net.zodac.ludoplus.resources.game_square_start_spoken
import net.zodac.ludoplus.resources.game_square_track_spoken
import net.zodac.ludoplus.resources.game_square_yard_call_ups_spoken
import net.zodac.ludoplus.resources.game_square_yard_doubled_spoken
import net.zodac.ludoplus.resources.game_square_yard_lost_spoken
import net.zodac.ludoplus.resources.game_square_yard_pawns_spoken
import net.zodac.ludoplus.resources.game_square_yard_spoken
import net.zodac.ludoplus.resources.game_swap_action
import net.zodac.ludoplus.ui.common.joinClauses
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.stringResource

/** A place on the board a screen reader stops at: where it is, in board units, and what's said and offered there ([powerUp]: its square, and the action that describes it). */
private class Spot(val bounds: Rect, val description: String, val move: Move?, val moveLabel: String?, val powerUp: Pair<Int, String>? = null)

/** The unit box round a square's centre: a turned square's own box would overlap its neighbours'. */
private fun BoardSquare.spotBounds(): Rect = Rect(centre - Offset(0.5f, 0.5f), Size(1f, 1f))

private fun List<Offset>.bounds(): Rect = Rect(minOf { it.x }, minOf { it.y }, maxOf { it.x }, maxOf { it.y })

/**
 * The board for a screen reader, laid over the drawn one: a node for each yard, each track square (numbered from red's
 * start), each home-column square, and home (none on an endless board), saying what's on it ("Square 14, Red pawn 2, 43 squares from home"). A
 * square holding a pawn the current player can move (theirs, or a partner's they roll for) is a button whose action
 * moves it ([moves], [onMove]), or while [swapping], one holding a pawn the move can swap with, whose action swaps them. One with a power-up
 * has an action that shows what it does ([onDescribe]).
 */
@Composable
fun BoardSemantics(state: GameState, seatColours: List<PlayerColour?>, moves: List<Move>, onMove: (Move) -> Unit, modifier: Modifier = Modifier, swapping: Boolean = false, onDescribe: (square: Int) -> Unit = {}) {
    val spots = boardSpots(state, seatColours, moves, swapping)
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
                        spot.powerUp?.let { (square, label) -> customActions = listOf(CustomAccessibilityAction(label) { onDescribe(square); true }) }
                    },
            )
        }
    }
}

@Composable
private fun boardSpots(state: GameState, seatColours: List<PlayerColour?>, moves: List<Move>, swapping: Boolean): List<Spot> {
    val board = state.board
    val layout = BoardLayout.of(board)
    val colourOf = { player: Int -> seatColours[state.players[player].seat] ?: PlayerColour.defaultFor(state.players[player].seat) }
    val shielded = stringResource(Res.string.game_pawn_shielded_spoken)
    // Every pawn's spoken name and where it is, once.
    val pawns = state.players.indices.flatMap { player ->
        state.players[player].pawns.indices.map { pawn ->
            val position = state.position(player, pawn)
            Triple(player, pawn, position) to pawnSpoken(colourOf(player), pawn + 1, position, board).let { if (state.isShielded(player, pawn)) joinClauses(listOf(it, shielded)) else it }
        }
    }
    val moveLabels = moves.associateWith { if (swapping) swapLabel(it, colourOf) else moveLabel(state, it, colourOf) }
    val movableAt = { player: Int, pawnsHere: List<Int> ->
        moves.firstOrNull { move -> move.picked(state, swapping).let { it?.player == player && it.pawn in pawnsHere } }
    }
    val empty = stringResource(Res.string.game_square_empty_spoken)
    val spots = mutableListOf<Spot>()
    // The yards.
    for (player in state.players.indices) {
        val seat = state.players[player].seat
        val waiting = state.players[player].pawns.indices.filter { state.position(player, it) == PawnPosition.Yard }
        val description = joinClauses(
            listOfNotNull(
                stringResource(Res.string.game_square_yard_spoken, stringResource(colourOf(player).label)),
                if (state.rules.lastStanding) {
                    val (lost, all) = state.players[player].let { it.pawns.size - it.pawnsLeft to it.pawns.size }
                    pluralStringResource(Res.plurals.game_square_yard_lost_spoken, all, lost, all)
                } else if (waiting.isEmpty()) empty else pluralStringResource(Res.plurals.game_square_yard_pawns_spoken, waiting.size, waiting.size),
                state.players[player].doubledRolls.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.game_square_yard_doubled_spoken, it, it) },
                state.players[player].callUps.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.game_square_yard_call_ups_spoken, it, it) },
            ),
        )
        val move = movableAt(player, waiting)
        spots += Spot(layout.yard(seat).bounds(), description, move, move?.let(moveLabels::get))
    }
    // The track, from red's start.
    val blockades = LudoEngine.blockades(state)
    for (square in 0..<board.trackLength) {
        // One lost to the Cataclysm is no longer there to find.
        if (square in state.lostSquares) continue
        val here = pawns.filter { (key, _) ->
            val (player, _, position) = key
            position is PawnPosition.Track && board.trackSquare(state.players[player].seat, position.steps) == square
        }
        val startOf = state.players.indexOfFirst { board.startSquare(it.seat) == square }.takeIf { it >= 0 }
        // A guarded start is safe for its own colour alone; a start square no one sits at, for nobody.
        val guardedStart = state.rules.guardedStarts && square % board.seatSpacing == 0
        val safety = when {
            !state.isSafe(square) -> null
            !guardedStart -> stringResource(Res.string.game_square_safe_spoken)
            startOf != null -> stringResource(Res.string.game_square_safe_for_spoken, stringResource(colourOf(startOf).label))
            else -> null
        }
        val blockade = blockades[square]?.let { stringResource(Res.string.game_square_blockade_spoken, stringResource(colourOf(it).label)) }
        val clauses = listOfNotNull(
            stringResource(Res.string.game_square_track_spoken, square + 1),
            startOf?.let { stringResource(Res.string.game_square_start_spoken, stringResource(colourOf(it).label)) },
            safety,
            state.claimed[square]?.let { stringResource(Res.string.game_square_claimed_spoken, stringResource(colourOf(it).label)) },
            blockade,
        ) + portalClauses(state, square) + powerUpClauses(state, square) + ghostClauses(state, square) + here.map { it.second }.ifEmpty { listOf(empty) }
        val move = state.players.indices.firstNotNullOfOrNull { player -> movableAt(player, here.filter { it.first.first == player }.map { it.first.second }) }
        val powerUp = state.powerUps[square]?.let { square to stringResource(Res.string.game_square_power_up_action, stringResource(it.title)) }
        spots += Spot(layout.trackSquare(square).spotBounds(), joinClauses(clauses), move, move?.let(moveLabels::get), powerUp)
    }
    // Each home column, then home, where there's one.
    if (board.endless) return spots
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

/** What's said of the portal on track [square], where there's one: the square it leads to. */
@Composable
private fun portalClauses(state: GameState, square: Int): List<String> {
    val portal = state.board.portalAt(square)?.takeIf { state.rules.portals } ?: return emptyList()
    return listOf(stringResource(Res.string.game_square_portal_spoken, portal.otherEnd(square) + 1))
}

/** What's said of the power-up on track [square], if there's one: "Shield power-up". */
@Composable
private fun powerUpClauses(state: GameState, square: Int): List<String> =
    listOfNotNull(state.powerUps[square]?.let { stringResource(Res.string.game_square_power_up_spoken, stringResource(it.title)) })

/** What's said of a ghost on track [square]. */
@Composable
private fun ghostClauses(state: GameState, square: Int): List<String> =
    if (square in state.ghosts) listOf(stringResource(Res.string.game_square_ghost_spoken)) else emptyList()

/** Whether [move] claims the square it ends on: nobody's, or another player's it captures on. */
private fun claimsSquare(state: GameState, move: Move): Boolean {
    val owner = move.ownerIn(state)
    val square = state.claimableSquare(owner, move.to) ?: return false
    val holder = state.claimed[square]
    return holder == null || holder != owner && move.captures.isNotEmpty()
}

/** What TalkBack offers for [move] while its swap is picked: "Swap places with Green pawn 2". */
@Composable
internal fun swapLabel(move: Move, colourOf: (Int) -> PlayerColour): String {
    val swap = checkNotNull(move.swap)
    return stringResource(Res.string.game_swap_action, stringResource(colourOf(swap.player).label), swap.pawn + 1)
}

/**
 * What TalkBack offers for [move]: "Move Red pawn 2 4 squares, capturing Green pawn 1", or "Bring Red pawn 1 out of the
 * yard". A partner's pawn is named by its own colour.
 */
@Composable
fun moveLabel(state: GameState, move: Move, colourOf: (Int) -> PlayerColour): String {
    val colour = stringResource(colourOf(move.ownerIn(state)).label)
    val number = move.pawn + 1
    val roll = move.squares
    val main = if (move.free) {
        stringResource(Res.string.game_move_out_free_action, colour, number)
    } else if (move.from == PawnPosition.YARD_PROGRESS) {
        stringResource(Res.string.game_move_out_action, colour, number)
    } else {
        pluralStringResource(Res.plurals.game_move_action, roll, colour, number, roll)
    }
    val portal = if (move.via == null) {
        emptyList()
    } else {
        listOf(stringResource(Res.string.game_move_portal_clause, state.board.trackSquare(state.players[move.ownerIn(state)].seat, move.to) + 1))
    }
    val bounces = move.brokenShields.map { stringResource(Res.string.game_move_bounce_clause, stringResource(colourOf(it.player).label), it.pawn + 1) }
    val powerUp = if (move.to !in 0..<state.board.trackSteps) {
        emptyList()
    } else {
        listOfNotNull(state.powerUps[state.board.trackSquare(state.players[move.ownerIn(state)].seat, move.to)]?.let { stringResource(Res.string.game_move_power_up_clause, stringResource(it.title)) })
    }
    val captures = move.captures.map {
        stringResource(Res.string.game_move_capture_clause, stringResource(colourOf(it.player).label), it.pawn + 1)
    }
    val home = if (move.to == state.board.homeProgress) listOf(stringResource(Res.string.game_move_home_clause)) else emptyList()
    val claim = if (claimsSquare(state, move)) listOf(stringResource(Res.string.game_move_claim_clause)) else emptyList()
    return joinClauses(listOf(main) + portal + bounces + captures + claim + powerUp + home)
}
