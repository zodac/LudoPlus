package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.model.PawnPosition

/**
 * One mark drawn on the board: [pawns] (indices) of [player], standing together at [centre] with [radius], both in
 * board units ([BoardLayout]). More than one pawn draws as one with a count. A pawn [home] stands on an ivory spot, so
 * it shows against its own colour's triangle.
 */
data class PawnMark(val player: Int, val pawns: List<Int>, val centre: Offset, val radius: Float, val home: Boolean = false)

/** How big a lone pawn is, in squares across; pawns of several colours on one square share it, each a quarter. */
internal const val PAWN_RADIUS = 0.38f
/** A pawn home: sized so four stand side by side along a triangle's base ([BoardLayout.homeSpot]), all but touching. */
const val HOME_PAWN_RADIUS = 0.22f
/** A pawn in a classic yard; a star board's yards size theirs to fit ([BoardLayout.yardPawnRadius]). */
internal const val YARD_PAWN_RADIUS = 0.5f
private const val SHARED_PAWN_RADIUS = 0.24f
private const val SHARED_OFFSET = 0.24f
private const val SHARED_CORNERS = 4
private const val CROWDED_RING = 0.3f
private const val CROWDED_PAWN_RADIUS = 0.18f

/**
 * Every mark on the board for [state]: each yard pawn on its own spot, each home pawn on its own spot in its triangle
 * (each one seen, never a count), and each square's pawns by player, a quarter of the square each when colours share it
 * (a ring of them when more than four colours do).
 */
fun pawnMarks(state: GameState): List<PawnMark> {
    val layout = BoardLayout.of(state.board)
    val marks = mutableListOf<PawnMark>()
    val onSquares = mutableMapOf<BoardSquare, MutableMap<Int, MutableList<Int>>>()
    state.players.forEachIndexed { player, side ->
        side.pawns.indices.forEach { pawn ->
            when (val position = state.position(player, pawn)) {
                PawnPosition.Yard -> marks += PawnMark(player, listOf(pawn), layout.yardSpotOf(side.seat, pawn, side.pawns.size), layout.yardPawnRadiusOf(side.pawns.size))
                // Drawn in its yard's tally ([drawFallen]), not as a pawn.
                PawnPosition.Out -> Unit
                PawnPosition.Home -> marks += PawnMark(player, listOf(pawn), layout.homeSpot(side.seat, pawn, side.pawns.size), layout.homePawnRadiusOf(side.pawns.size), home = true)
                is PawnPosition.Track -> onSquares.square(layout.trackSquare(state.board.trackSquare(side.seat, position.steps)), player) += pawn
                is PawnPosition.HomeColumn -> onSquares.square(layout.homeColumnSquare(side.seat, position.index), player) += pawn
            }
        }
    }
    for ((cell, byPlayer) in onSquares) {
        if (byPlayer.size == 1) {
            val (player, pawns) = byPlayer.entries.single()
            marks += PawnMark(player, pawns, cell.centre, PAWN_RADIUS)
        } else {
            val crowded = byPlayer.size > SHARED_CORNERS
            byPlayer.entries.sortedBy { it.key }.forEachIndexed { i, (player, pawns) ->
                val offset = if (crowded) {
                    // More colours than corners (5-8 players on a safe square): a ring of them, smaller.
                    val angle = 2 * PI * i / byPlayer.size - PI / 2
                    Offset(cos(angle).toFloat(), sin(angle).toFloat()) * CROWDED_RING
                } else {
                    Offset(if (i % 2 == 0) -SHARED_OFFSET else SHARED_OFFSET, if (i < 2) -SHARED_OFFSET else SHARED_OFFSET)
                }
                marks += PawnMark(player, pawns, cell.centre + offset, if (crowded) CROWDED_PAWN_RADIUS else SHARED_PAWN_RADIUS)
            }
        }
    }
    return marks
}

private fun MutableMap<BoardSquare, MutableMap<Int, MutableList<Int>>>.square(cell: BoardSquare, player: Int): MutableList<Int> =
    getOrPut(cell) { mutableMapOf() }.getOrPut(player) { mutableListOf() }

/** How far from a movable pawn's centre a tap still picks it, in squares: wider than a square, for a 48dp target. */
const val PAWN_TAP_REACH = 1.6f

/**
 * The pawn a tap at [point] (board units) picks among [movable] (the pawns the current player can move: their own, or a
 * partner's they roll for): the nearest movable mark's first movable pawn, within [PAWN_TAP_REACH]. Null for a tap
 * nowhere near one.
 */
fun pawnAt(point: Offset, marks: List<PawnMark>, movable: Set<PawnRef>): PawnRef? =
    marks.asSequence()
        .filter { mark -> mark.pawns.any { PawnRef(mark.player, it) in movable } }
        .map { it to (it.centre - point).getDistance() }
        .filter { it.second <= PAWN_TAP_REACH }
        .minByOrNull { it.second }
        ?.first?.let { mark -> PawnRef(mark.player, mark.pawns.first { PawnRef(mark.player, it) in movable }) }

/** The pawn a tap picks this move by: the one it moves, or while [swapping], the one it swaps with. */
internal fun Move.picked(state: GameState, swapping: Boolean): PawnRef? = if (swapping) swap else PawnRef(ownerIn(state), pawn)

/** [pawnAt] for [player]'s own [movable] pawns, by index. */
fun pawnAt(point: Offset, marks: List<PawnMark>, player: Int, movable: Set<Int>): Int? =
    pawnAt(point, marks, movable.map { PawnRef(player, it) }.toSet())?.pawn
