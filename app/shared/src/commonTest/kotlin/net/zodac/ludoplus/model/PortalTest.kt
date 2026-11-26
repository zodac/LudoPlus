package net.zodac.ludoplus.model

import kotlin.test.Test
import kotlin.test.assertEquals
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
            assertTrue(ends.all(board::canHavePortal), "$board")
            for (portal in board.portals) {
                assertEquals(portal, board.portals.portalAt(portal.first))
                assertEquals(portal, board.portals.portalAt(portal.second))
                assertEquals(portal.second, (portal.first + portal.steps) % board.trackLength)
            }
            assertEquals(ends.size, (0..<board.trackLength).count { board.portals.portalAt(it) != null })
        }
        assertNull(BoardGeometry.CLASSIC.portals.portalAt(0))
    }

    @Test
    fun `a portal may open anywhere but a safe square - or the square before a start - which its seat's pawns never stand on`() {
        val board = BoardGeometry.CLASSIC
        assertEquals((0..<52).filter { it % 13 !in setOf(0, 8, 12) }, (0..<52).filter(board::canHavePortal))
    }

    @Test
    fun `a pair opened somewhere else keeps its name and length - round past the end of the numbering`() {
        val short = BoardGeometry.CLASSIC.portals.first()
        assertEquals(Portal(0, long = false, first = 48, second = 3, steps = 7), short.openAt(BoardGeometry.CLASSIC, 48))
        assertTrue(short.isPair(short.openAt(BoardGeometry.CLASSIC, 20)))
        assertTrue(!short.isPair(BoardGeometry.CLASSIC.portals[1]))
    }

    @Test
    fun `a pawn comes out at the other end - either way - and stays where there is no portal`() {
        val board = BoardGeometry.CLASSIC
        assertEquals(10, board.throughPortal(board.portals, seat = 0, progress = 3))
        assertEquals(3, board.throughPortal(board.portals, seat = 0, progress = 10))
        assertEquals(22, board.throughPortal(board.portals, seat = 0, progress = 6))
        assertEquals(6, board.throughPortal(board.portals, seat = 0, progress = 22))
        assertEquals(4, board.throughPortal(board.portals, seat = 0, progress = 4))
        // Off the track: the home column, and the yard.
        for (progress in listOf(-1, 51, 56)) assertEquals(progress, board.throughPortal(board.portals, seat = 0, progress = progress))
    }

    @Test
    fun `every portal is open to every colour - the pawn's progress counted from its own start wherever it comes out`() {
        for (board in boards) {
            for (portal in board.portals) {
                for (seat in 0..<board.seats) {
                    for (square in listOf(portal.first, portal.second)) {
                        val progress = (square - board.startSquare(seat)).mod(board.trackLength)
                        val out = board.throughPortal(board.portals, seat, progress)
                        assertEquals(portal.otherEnd(square), board.trackSquare(seat, out), "seat $seat at $square of $board")
                        assertEquals(progress, board.throughPortal(board.portals, seat, out))
                    }
                }
            }
        }
        // Green's own start is between squares 6 and 22: 9 steps on is most of the way round, and back.
        assertEquals(45, BoardGeometry.CLASSIC.throughPortal(BoardGeometry.CLASSIC.portals, seat = 1, progress = 9))
        assertEquals(9, BoardGeometry.CLASSIC.throughPortal(BoardGeometry.CLASSIC.portals, seat = 1, progress = 45))
    }

    @Test
    fun `progress on a square is counted from a seat's own start - and there is one square its way never takes it over`() {
        val board = BoardGeometry.CLASSIC
        assertEquals(listOf(0, 9, 45, 50, null), listOf(13, 22, 6, 11, 12).map { board.progressAt(1, it) })
    }
}
