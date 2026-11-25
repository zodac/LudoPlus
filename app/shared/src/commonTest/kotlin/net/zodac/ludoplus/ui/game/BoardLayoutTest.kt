package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.BoardGeometry

/** The least room any yard has for its second badge: with eight players. */
private const val SECOND_BADGE_ROOM = 0.52f

class BoardLayoutTest {

    private val board = BoardGeometry.CLASSIC

    // Side by side, or corner to corner where the track turns round the centre (5,6 to 6,5).
    private fun adjacent(a: Cell, b: Cell) = a != b && abs(a.column - b.column) <= 1 && abs(a.row - b.row) <= 1

    @Test
    fun `the track is 52 different squares - each touching the one before - round the cross`() {
        val cells = (0..<board.trackLength).map { ClassicBoardLayout.trackCell(it) }
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
        // Seat 0 (the first player) at the bottom left, the rest round from it clockwise.
        assertEquals(listOf(Cell(6, 13), Cell(1, 6), Cell(8, 1), Cell(13, 8)), (0..3).map { ClassicBoardLayout.trackCell(board.startSquare(it)) })
        assertEquals(listOf(Cell(2, 8), Cell(6, 2), Cell(12, 6), Cell(8, 12)), (0..3).map { ClassicBoardLayout.trackCell(board.startSquare(it) + 8) })
        assertEquals((13 downTo 9).map { Cell(7, it) }, (0..4).map { ClassicBoardLayout.homeColumnCell(0, it) })
        assertEquals((1..5).map { Cell(it, 7) }, (0..4).map { ClassicBoardLayout.homeColumnCell(1, it) })
        assertEquals((1..5).map { Cell(7, it) }, (0..4).map { ClassicBoardLayout.homeColumnCell(2, it) })
        assertEquals(listOf(Cell(0, 9), Cell(0, 0), Cell(9, 0), Cell(9, 9)), (0..3).map { ClassicBoardLayout.yardOrigin(it) })
    }

    @Test
    fun `a pawn's last track square leads straight into its home column`() {
        for (seat in 0..3) {
            assertTrue(adjacent(ClassicBoardLayout.trackCell(board.trackSquare(seat, board.trackSteps - 1)), ClassicBoardLayout.homeColumnCell(seat, 0)), "seat $seat")
        }
    }

    @Test
    fun `yard spots sit inside their seat's yard`() {
        for (seat in 0..3) {
            val origin = ClassicBoardLayout.yardOrigin(seat)
            for (slot in 0..3) {
                val spot = ClassicBoardLayout.yardSpot(seat, slot)
                assertTrue(spot.x > origin.column && spot.x < origin.column + 6 && spot.y > origin.row && spot.y < origin.row + 6, "$seat/$slot")
            }
        }
    }

    @Test
    fun `each pawn home has its own spot inside its seat's triangle - close together but never overlapping`() {
        for (seat in 0..3) {
            val (a, b, c) = ClassicBoardLayout.homeTriangle(seat)
            val spots = (0..3).map { ClassicBoardLayout.homeSpot(seat, it) }
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

    @Test
    fun `a yard's two badges have room in it - clear of its edge, every pawn's spot, and each other - on every board with any number of pawns`() {
        for (geometry in (2..8).map(BoardGeometry::forPlayers)) {
            val layout = BoardLayout.of(geometry)
            for (seat in 0..<geometry.seats) for (pawns in 1..6) {
                val badges = layout.yardBadges(seat, pawns, YARD_BADGE_ROOM)
                for ((i, badge) in badges.withIndex()) {
                    val about = "$geometry seat $seat, $pawns pawns, badge $i"
                    assertTrue(badge.room >= if (i == 0) YARD_BADGE_ROOM else SECOND_BADGE_ROOM, "$about: room for only ${badge.room}")
                    val yard = layout.yard(seat)
                    val turns = yard.indices.map { c ->
                        val side = yard[(c + 1) % yard.size] - yard[c]
                        (side.x * (badge.centre.y - yard[c].y) - side.y * (badge.centre.x - yard[c].x)) / side.getDistance()
                    }
                    assertTrue(turns.all { it >= badge.room - 0.01f } || turns.all { it <= 0.01f - badge.room }, "$about: $turns from its edges")
                    for (pawn in 0..<pawns) {
                        val gap = (layout.yardSpotOf(seat, pawn, pawns) - badge.centre).getDistance() - layout.yardSpotRadiusOf(pawns)
                        assertTrue(gap >= badge.room - 0.01f, "$about: $gap from pawn $pawn's spot")
                    }
                }
                val (first, second) = badges
                val apart = (first.centre - second.centre).getDistance()
                assertTrue(apart >= minOf(first.room, YARD_BADGE_ROOM) + minOf(second.room, YARD_BADGE_ROOM) - 0.01f, "$geometry seat $seat, $pawns pawns: $apart apart")
            }
        }
    }

    @Test
    fun `a yard of six has a spot a pawn - none overlapping - all on its court - on every board`() {
        for (geometry in (2..8).map(BoardGeometry::forPlayers)) {
            val layout = BoardLayout.of(geometry)
            assertEquals(layout, BoardLayout.of(geometry.copy(endless = true)))
            val radius = layout.yardSpotRadiusOf(6)
            for (seat in 0..<geometry.seats) {
                val spots = (0..5).map { layout.yardSpotOf(seat, it, 6) }
                val court = layout.yardCourt(seat)
                for ((i, spot) in spots.withIndex()) {
                    for (other in spots.drop(i + 1)) assertTrue((spot - other).getDistance() >= 2 * radius, "$geometry seat $seat")
                    // Inside every side of the (convex) court.
                    for (c in court.indices) {
                        val from = court[c]
                        val side = court[(c + 1) % court.size] - from
                        val distance = kotlin.math.abs(side.x * (spot.y - from.y) - side.y * (spot.x - from.x)) / side.getDistance()
                        assertTrue(distance >= radius * 0.9f, "$geometry seat $seat spot $i: $distance from its court's edge")
                    }
                }
            }
        }
    }
}
