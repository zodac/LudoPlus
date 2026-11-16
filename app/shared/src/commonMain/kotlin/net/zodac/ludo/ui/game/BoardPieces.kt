package net.zodac.ludo.ui.game

import androidx.compose.ui.geometry.Offset
import net.zodac.ludo.game.GameState
import net.zodac.ludo.model.PawnPosition

/**
 * One mark drawn on the board: [pawns] (indices) of [player], standing together at [centre] with [radius], both in
 * board units ([BoardLayout]). More than one pawn draws as one with a count. A pawn [home] stands on an ivory spot, so
 * it shows against its own colour's triangle.
 */
data class PawnMark(val player: Int, val pawns: List<Int>, val centre: Offset, val radius: Float, val home: Boolean = false)

/** How big a lone pawn is, in squares across; pawns of several colours on one square share it, each a quarter. */
private const val PAWN_RADIUS = 0.38f
/** A pawn home: sized so four stand side by side along a triangle's base (`BoardLayout.homeSpot`), all but touching. */
const val HOME_PAWN_RADIUS = 0.22f
private const val YARD_PAWN_RADIUS = 0.5f
private const val SHARED_PAWN_RADIUS = 0.24f
private const val SHARED_OFFSET = 0.24f

/**
 * Every mark on the board for [state]: each yard pawn on its own spot, each home pawn on its own spot in its triangle
 * (each one seen, never a count), and each square's pawns by player, a quarter of the square each when colours share it.
 */
fun pawnMarks(state: GameState): List<PawnMark> {
    val marks = mutableListOf<PawnMark>()
    val onSquares = mutableMapOf<Cell, MutableMap<Int, MutableList<Int>>>()
    state.players.forEachIndexed { player, side ->
        side.pawns.indices.forEach { pawn ->
            when (val position = state.position(player, pawn)) {
                PawnPosition.Yard -> marks += PawnMark(player, listOf(pawn), BoardLayout.yardSpot(side.seat, pawn), YARD_PAWN_RADIUS)
                PawnPosition.Home -> marks += PawnMark(player, listOf(pawn), BoardLayout.homeSpot(side.seat, pawn), HOME_PAWN_RADIUS, home = true)
                is PawnPosition.Track -> onSquares.square(BoardLayout.trackCell(state.board.trackSquare(side.seat, position.steps)), player) += pawn
                is PawnPosition.HomeColumn -> onSquares.square(BoardLayout.homeColumnCell(side.seat, position.index), player) += pawn
            }
        }
    }
    for ((cell, byPlayer) in onSquares) {
        if (byPlayer.size == 1) {
            val (player, pawns) = byPlayer.entries.single()
            marks += PawnMark(player, pawns, cell.centre, PAWN_RADIUS)
        } else {
            byPlayer.entries.sortedBy { it.key }.forEachIndexed { i, (player, pawns) ->
                val offset = Offset(if (i % 2 == 0) -SHARED_OFFSET else SHARED_OFFSET, if (i < 2) -SHARED_OFFSET else SHARED_OFFSET)
                marks += PawnMark(player, pawns, cell.centre + offset, SHARED_PAWN_RADIUS)
            }
        }
    }
    return marks
}

private fun MutableMap<Cell, MutableMap<Int, MutableList<Int>>>.square(cell: Cell, player: Int): MutableList<Int> =
    getOrPut(cell) { mutableMapOf() }.getOrPut(player) { mutableListOf() }

/** How far from a movable pawn's centre a tap still picks it, in squares: wider than a square, for a 48dp target. */
const val PAWN_TAP_REACH = 1.6f

/**
 * The pawn a tap at [point] (board units) picks among [movable] (the current player's movable pawns): the nearest
 * movable mark's first movable pawn, within [PAWN_TAP_REACH]. Null for a tap nowhere near one.
 */
fun pawnAt(point: Offset, marks: List<PawnMark>, player: Int, movable: Set<Int>): Int? =
    marks.asSequence()
        .filter { it.player == player && it.pawns.any { pawn -> pawn in movable } }
        .map { it to (it.centre - point).getDistance() }
        .filter { it.second <= PAWN_TAP_REACH }
        .minByOrNull { it.second }
        ?.first?.pawns?.first { it in movable }
