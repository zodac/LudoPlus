package net.zodac.ludoplus.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortalTest {

    private val boards = listOf(BoardGeometry.CLASSIC, BoardGeometry.THREE_SEAT) + (5..8).map(BoardGeometry::star)

    @Test
    fun `each seat has a short pair on its own stretch and a long pair into the next - the classic board's squares`() {
        assertEquals(
            listOf(
                Portal(0, long = false, first = 3, second = 10, steps = 7),
                Portal(0, long = true, first = 6, second = 22, steps = 16),
                Portal(1, long = false, first = 16, second = 23, steps = 7),
                Portal(1, long = true, first = 19, second = 35, steps = 16),
                Portal(2, long = false, first = 29, second = 36, steps = 7),
                Portal(2, long = true, first = 32, second = 48, steps = 16),
                Portal(3, long = false, first = 42, second = 49, steps = 7),
                // Round past the end of the numbering.
                Portal(3, long = true, first = 45, second = 9, steps = 16),
            ),
            BoardGeometry.CLASSIC.portals,
        )
    }

    @Test
    fun `on every board no two portals share a square - none is safe - and each end finds its pair`() {
        for (board in boards) {
            val ends = board.portals.flatMap { listOf(it.first, it.second) }
            assertEquals(ends.size, ends.toSet().size, "$board")
            assertTrue(ends.none(board::isSafe), "$board")
            for (portal in board.portals) {
                assertEquals(portal, board.portalAt(portal.first))
                assertEquals(portal, board.portalAt(portal.second))
                assertEquals(portal.second, (portal.first + portal.steps) % board.trackLength)
            }
            assertEquals(ends.size, (0..<board.trackLength).count { board.portalAt(it) != null })
        }
        assertNull(BoardGeometry.CLASSIC.portalAt(0))
    }

    @Test
    fun `a pawn comes out at the other end - either way - and stays where there is no portal`() {
        val board = BoardGeometry.CLASSIC
        assertEquals(10, board.throughPortal(seat = 0, progress = 3))
        assertEquals(3, board.throughPortal(seat = 0, progress = 10))
        assertEquals(22, board.throughPortal(seat = 0, progress = 6))
        assertEquals(6, board.throughPortal(seat = 0, progress = 22))
        assertEquals(4, board.throughPortal(seat = 0, progress = 4))
        // Off the track: the home column, and the yard.
        for (progress in listOf(-1, 51, 56)) assertEquals(progress, board.throughPortal(seat = 0, progress = progress))
    }

    @Test
    fun `a long portal is shut - both ways - to the one seat whose start it would take a pawn past`() {
        for (board in boards) {
            for (portal in board.portals) {
                for (seat in 0..<board.seats) {
                    val shut = portal.long && seat == (portal.seat + 1) % board.seats
                    for (square in listOf(portal.first, portal.second)) {
                        val progress = (square - board.startSquare(seat)).mod(board.trackLength)
                        val out = board.throughPortal(seat, progress)
                        assertEquals(shut, out == progress, "seat $seat at $square of $board")
                        if (!shut) assertEquals(if (square == portal.first) portal.second else portal.first, board.trackSquare(seat, out))
                    }
                }
            }
        }
        // No short pair is ever shut.
        assertFalse(BoardGeometry.CLASSIC.portals.any { !it.long && BoardGeometry.CLASSIC.throughPortal(0, it.first) == it.first })
    }
}
