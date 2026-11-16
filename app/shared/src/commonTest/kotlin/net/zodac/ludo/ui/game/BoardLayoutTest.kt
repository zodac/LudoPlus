package net.zodac.ludo.ui.game

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludo.model.BoardGeometry

class BoardLayoutTest {

    private val board = BoardGeometry.CLASSIC

    // Side by side, or corner to corner where the track turns round the centre (5,6 to 6,5).
    private fun adjacent(a: Cell, b: Cell) = a != b && abs(a.column - b.column) <= 1 && abs(a.row - b.row) <= 1

    @Test
    fun `the track is 52 different squares - each touching the one before - round the cross`() {
        val cells = (0..<board.trackLength).map { BoardLayout.trackCell(it) }
        assertEquals(board.trackLength, cells.toSet().size)
        for (i in cells.indices) {
            val (a, b) = cells[i] to cells[(i + 1) % cells.size]
            assertTrue(adjacent(a, b), "$a to $b")
        }
        // Every track square is on an arm of the cross, and none in the centre.
        assertTrue(cells.all { it.column in 6..8 || it.row in 6..8 })
        assertTrue(cells.none { it.column in 6..8 && it.row in 6..8 })
    }

    @Test
    fun `each seat's start - star - home column - and yard are where the classic board has them`() {
        assertEquals(listOf(Cell(1, 6), Cell(8, 1), Cell(13, 8), Cell(6, 13)), (0..3).map { BoardLayout.trackCell(board.startSquare(it)) })
        assertEquals(listOf(Cell(6, 2), Cell(12, 6), Cell(8, 12), Cell(2, 8)), (0..3).map { BoardLayout.trackCell(board.startSquare(it) + 8) })
        assertEquals((1..5).map { Cell(it, 7) }, (0..4).map { BoardLayout.homeColumnCell(0, it) })
        assertEquals((1..5).map { Cell(7, it) }, (0..4).map { BoardLayout.homeColumnCell(1, it) })
        assertEquals((13 downTo 9).map { Cell(it, 7) }, (0..4).map { BoardLayout.homeColumnCell(2, it) })
        assertEquals(listOf(Cell(0, 0), Cell(9, 0), Cell(9, 9), Cell(0, 9)), (0..3).map { BoardLayout.yardOrigin(it) })
    }

    @Test
    fun `a pawn's last track square leads straight into its home column`() {
        for (seat in 0..3) {
            assertTrue(adjacent(BoardLayout.trackCell(board.trackSquare(seat, board.trackSteps - 1)), BoardLayout.homeColumnCell(seat, 0)), "seat $seat")
        }
    }

    @Test
    fun `yard spots sit inside their seat's yard`() {
        for (seat in 0..3) {
            val origin = BoardLayout.yardOrigin(seat)
            for (slot in 0..3) {
                val spot = BoardLayout.yardSpot(seat, slot)
                assertTrue(spot.x > origin.column && spot.x < origin.column + 6 && spot.y > origin.row && spot.y < origin.row + 6, "$seat/$slot")
            }
        }
    }

    @Test
    fun `each pawn home has its own spot inside its seat's triangle - close together but never overlapping`() {
        for (seat in 0..3) {
            val (a, b, c) = BoardLayout.homeTriangle(seat)
            val spots = (0..3).map { BoardLayout.homeSpot(seat, it) }
            for (centre in spots) {
                // Inside: on the same side of each edge as the third corner.
                fun side(p: Offset, q: Offset, r: Offset) = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
                // The whole pawn inside, not just its centre: each edge at least a radius away.
                fun distanceToEdge(p: Offset, q: Offset) = abs(side(p, q, centre)) / (q - p).getDistance()
                assertTrue(listOf(Triple(a, b, c), Triple(b, c, a), Triple(c, a, b)).all { (p, q, r) -> side(p, q, centre) * side(p, q, r) > 0 }, "$seat: $centre")
                assertTrue(listOf(a to b, b to c, c to a).all { (p, q) -> distanceToEdge(p, q) >= HOME_PAWN_RADIUS - 0.01f }, "$seat: $centre crosses an edge")
            }
            val gaps = spots.indices.flatMap { i -> (i + 1..<spots.size).map { j -> (spots[i] - spots[j]).getDistance() } }
            assertTrue(gaps.all { it >= 2 * HOME_PAWN_RADIUS - 0.01f }, "$seat: pawns overlap")
            // Close: neighbours all but touch.
            assertTrue(gaps.min() <= 2 * HOME_PAWN_RADIUS + 0.1f, "$seat: pawns spread apart")
        }
    }
}
