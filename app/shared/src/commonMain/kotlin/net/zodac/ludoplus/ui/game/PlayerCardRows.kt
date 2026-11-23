package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** How far from the board's middle, across, a yard's centre may lie and still count as straight above or below it. */
private const val MIDDLE_TOLERANCE = 0.1f

/**
 * The rows of player cards over the board, laid out as the players' yards are round [layout]: [seats] holds each
 * player's seat, in player order, and each row holds player indices left to right.
 *
 * A player whose yard is left of the board's middle has a card in the left column, and one right of it in the right,
 * each column top to bottom as the yards are; the columns pair up row by row, a missing card a null. A yard straight above
 * or below the middle has a row to itself, its card the row's whole width, over or under the pairs.
 */
fun playerCardRows(layout: BoardLayout, seats: List<Int>): List<List<Int?>> {
    val middle = layout.size / 2
    val yards = seats.map { seat -> layout.yard(seat).let { outline -> outline.reduce { a, b -> a + b } / outline.size.toFloat() } }
    fun column(test: (Offset) -> Boolean) = seats.indices.filter { test(yards[it]) }.sortedBy { yards[it].y }
    val left = column { it.x < middle - MIDDLE_TOLERANCE }
    val right = column { it.x > middle + MIDDLE_TOLERANCE }
    val centred = column { abs(it.x - middle) <= MIDDLE_TOLERANCE }
    val pairs = List(maxOf(left.size, right.size)) { listOf(left.getOrNull(it), right.getOrNull(it)) }
    val (above, below) = centred.partition { yards[it].y < middle }
    return above.map { listOf(it) } + pairs + below.map { listOf(it) }
}
