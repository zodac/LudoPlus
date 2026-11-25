package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine.replace
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.model.PawnPosition

/**
 * One mark drawn on the board: [pawns] (indices) of [player], standing together at [centre] with [radius], both in
 * board units ([BoardLayout]). More than one pawn draws as one with a count. [square] is the centre of the square it
 * stands on, which it may share, and stands a little above if it's turned ([BoardSquare.stand]); a yard's is its own spot,
 * and a home's, shared by its player's pawns there, the middle of their part of it.
 */
data class PawnMark(val player: Int, val pawns: List<Int>, val centre: Offset, val radius: Float, val square: Offset = centre)

/** How big a lone pawn is, in squares across; pawns of several colours on one square share it, each a quarter. */
internal const val PAWN_RADIUS = 0.38f
/** A pawn in a classic yard; another board's yards size theirs to fit ([BoardLayout.yardPawnRadiusOf]), as every home does ([BoardLayout.homePawnRadiusOf]). */
internal const val YARD_PAWN_RADIUS = 0.5f
private const val SHARED_PAWN_RADIUS = 0.24f
private const val SHARED_OFFSET = 0.24f
private const val SHARED_CORNERS = 4
private const val CROWDED_RING = 0.3f
private const val CROWDED_PAWN_RADIUS = 0.18f

/**
 * Every mark on the board for [state]: each yard pawn on its own spot, each home pawn on its own spot in its triangle,
 * of as many as are home (each one seen, never a count), and each square's pawns by player, a quarter of the square each when colours share it
 * (a ring of them when more than four colours do). [without] is a pawn left out, as if it stood nowhere.
 * [lift] is how far the pawns' style rises on a turned square ([BoardSquare.stand]), or home ([BoardLayout.homeStand]).
 */
fun pawnMarks(state: GameState, without: PawnRef? = null, lift: Float = 1f): List<PawnMark> {
    val layout = BoardLayout.of(state.board)
    val marks = mutableListOf<PawnMark>()
    val onSquares = mutableMapOf<BoardSquare, MutableMap<Int, MutableList<Int>>>()
    state.players.forEachIndexed { player, side ->
        val placed = side.pawns.indices.filter { PawnRef(player, it) != without }
        val home = placed.filter { state.position(player, it) == PawnPosition.Home }
        placed.forEach { pawn ->
            when (val position = state.position(player, pawn)) {
                PawnPosition.Yard -> marks += PawnMark(player, listOf(pawn), layout.yardSpotOf(side.seat, pawn, side.pawns.size), layout.yardPawnRadiusOf(side.pawns.size))
                // Drawn in its yard's tally ([drawFallen]), not as a pawn.
                PawnPosition.Out -> Unit
                PawnPosition.Home -> marks += PawnMark(
                    player,
                    listOf(pawn),
                    layout.homeStand(side.seat, home.indexOf(pawn), home.size, lift),
                    layout.homePawnRadiusOf(home.size),
                    square = layout.homeSpot(side.seat, 0, 1),
                )
                is PawnPosition.Track -> onSquares.square(layout.trackSquare(state.board.trackSquare(side.seat, position.steps)), player) += pawn
                is PawnPosition.HomeColumn -> onSquares.square(layout.homeColumnSquare(side.seat, position.index), player) += pawn
            }
        }
    }
    for ((cell, byPlayer) in onSquares) {
        if (byPlayer.size == 1) {
            val (player, pawns) = byPlayer.entries.single()
            marks += PawnMark(player, pawns, cell.stand(lift), PAWN_RADIUS, square = cell.centre)
        } else {
            val crowded = byPlayer.size > SHARED_CORNERS
            val radius = if (crowded) CROWDED_PAWN_RADIUS else SHARED_PAWN_RADIUS
            val quarters = cell.quarters()
            // The furthest up first, so a pawn's foot is never drawn over the head of one below it.
            marks += byPlayer.entries.sortedBy { it.key }.mapIndexed { i, (player, pawns) ->
                val offset = if (crowded) {
                    // More colours than corners (5-8 players on a safe square): a ring of them, smaller.
                    val angle = 2 * PI * i / byPlayer.size - PI / 2
                    Offset(cos(angle).toFloat(), sin(angle).toFloat()) * CROWDED_RING
                } else {
                    quarters[i]
                }
                PawnMark(player, pawns, cell.stand(lift, radius) + offset, radius, square = cell.centre)
            }.sortedBy { it.centre.y }
        }
    }
    return marks
}

/**
 * Where the pawns of up to [SHARED_CORNERS] colours stand about the middle of a square they share, in turn: a quarter of it each, its top left,
 * top right, bottom left, and bottom right; or where it's turned, the quarters turned with it, those furthest left and right, then up and down.
 */
private fun BoardSquare.quarters(): List<Offset> {
    if (!isTurned) return List(SHARED_CORNERS) { Offset(if (it % 2 == 0) -SHARED_OFFSET else SHARED_OFFSET, if (it < 2) -SHARED_OFFSET else SHARED_OFFSET) }
    val turned = List(SHARED_CORNERS) {
        val angle = (turn + 45f + 90f * it) * PI / 180
        Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (SHARED_OFFSET * sqrt(2f))
    }
    val right = turned.maxBy { it.x }
    val down = turned.maxBy { it.y }
    return listOf(-right, right, -down, down)
}

private fun MutableMap<BoardSquare, MutableMap<Int, MutableList<Int>>>.square(cell: BoardSquare, player: Int): MutableList<Int> =
    getOrPut(cell) { mutableMapOf() }.getOrPut(player) { mutableListOf() }

/** How far from its square's middle each of two or more pawns told apart is ([apart]), and how big, in squares. */
private const val APART_OFFSET = 0.5f
private const val APART_PAWN_RADIUS = 0.34f
private const val APART_CROWD = 4
private const val APART_CROWD_OFFSET = 0.6f
private const val APART_CROWD_RADIUS = 0.28f

/**
 * This mark's pawns told apart: itself where it's one pawn, and otherwise each in a ring of their own about the square's
 * middle, the first at its top left, or with two, at its left.
 */
internal fun PawnMark.apart(): List<PawnMark> {
    if (pawns.size == 1) return listOf(this)
    val crowd = pawns.size > APART_CROWD
    val first = if (pawns.size == 2) PI else -PI / 2 - PI / pawns.size
    return pawns.mapIndexed { i, pawn ->
        val angle = first + 2 * PI * i / pawns.size
        val out = Offset(cos(angle).toFloat(), sin(angle).toFloat()) * if (crowd) APART_CROWD_OFFSET else APART_OFFSET
        copy(pawns = listOf(pawn), centre = square + out, radius = if (crowd) APART_CROWD_RADIUS else APART_PAWN_RADIUS)
    }
}

/**
 * A mark that stands still through a move, but shifts [from] where it stood [to] where it stands after, as the pawn
 * [arriving] comes onto its square.
 */
internal data class PawnShift(val from: PawnMark, val to: PawnMark, val arriving: PawnRef)

/**
 * The marks on the board round a move: [before] it, without the pawn that makes it, and [after] it, but before
 * anything that came after the turn (a ghost, or squares lost).
 */
internal class MoveMarks(private val before: List<PawnMark>, private val after: List<PawnMark>, private val arrivals: List<PawnRef>) {
    /** The mark [pawn] stood in before the move. */
    fun before(pawn: PawnRef): PawnMark = before.of(pawn)

    /** The mark [pawn] stands in after the move. */
    fun after(pawn: PawnRef): PawnMark = after.of(pawn)

    /** The marks that shift, to make room or to take it up, but for the pawns that travel: each with the pawn it shifts for. */
    fun shifts(isTravelling: (PawnRef) -> Boolean): List<PawnShift> = after.mapNotNull { mark ->
        val pawns = mark.pawns.filterNot { isTravelling(PawnRef(mark.player, it)) }
        if (pawns.isEmpty()) return@mapNotNull null
        val from = before(PawnRef(mark.player, pawns.first()))
        val arriving = arrivals.firstOrNull { after(it).square == mark.square }
        if (arriving == null || (from.centre == mark.centre && from.radius == mark.radius)) return@mapNotNull null
        PawnShift(from.copy(pawns = pawns), mark.copy(pawns = pawns), arriving)
    }

    private fun List<PawnMark>.of(pawn: PawnRef): PawnMark = first { it.player == pawn.player && pawn.pawn in it.pawns }
}

/** The marks round [event]'s move, [state] being the game after it, of pawns whose style takes [lift] ([pawnMarks]). */
internal fun moveMarks(state: GameState, event: GameEvent.Moved, lift: Float = 1f): MoveMarks {
    val move = event.move
    val effect = event.effect
    val mover = PawnRef(move.owner ?: event.player, move.pawn)
    val called = effect?.calledUp?.let { PawnRef(mover.player, it) }
    fun GameState.put(pawn: PawnRef, progress: Int): GameState =
        copy(players = players.replace(pawn.player) { side -> side.copy(pawns = side.pawns.replace(pawn.pawn) { progress }) })

    // On the track square the mover's side has [progress] to.
    fun GameState.putOnSquare(pawn: PawnRef, progress: Int): GameState {
        val square = board.trackSquare(players[mover.player].seat, progress)
        return put(pawn, checkNotNull(board.progressAt(players[pawn.player].seat, square)))
    }

    var before = state
    for (taken in move.captures) before = before.putOnSquare(taken, move.to)
    for (taken in effect?.captures.orEmpty()) before = before.putOnSquare(taken, if (called != null) 0 else event.endedAt)
    var after = state.put(mover, event.endedAt)
    effect?.swapped?.let {
        before = before.putOnSquare(it, event.endedAt)
        after = after.putOnSquare(it, move.to)
    }
    if (called != null) {
        before = before.put(called, PawnPosition.YARD_PROGRESS)
        after = after.put(called, 0)
    }
    // A capture's flight ends in its yard, whether it waits there or is out of the game; but for one brought straight back out.
    for (taken in event.allCaptures) after = after.put(taken, if (taken in event.recalled) 0 else PawnPosition.YARD_PROGRESS)
    return MoveMarks(pawnMarks(before, without = mover, lift = lift), pawnMarks(after, lift = lift), listOfNotNull(mover, effect?.swapped, called) + event.recalled)
}

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
