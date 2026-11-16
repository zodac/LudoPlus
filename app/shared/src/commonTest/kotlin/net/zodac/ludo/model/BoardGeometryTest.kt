package net.zodac.ludo.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoardGeometryTest {

    private val board = BoardGeometry.CLASSIC

    @Test
    fun `the classic board - 52 squares - starts 13 apart - a 57-step journey from the yard`() {
        assertEquals(listOf(0, 13, 26, 39), (0..3).map { board.startSquare(it) })
        // 51 track squares from the start square, 5 up the home column, 1 into home - and the step out of the yard.
        assertEquals(51, board.trackSteps)
        assertEquals(56, board.homeProgress)
        assertEquals(57, stepsToHome(PawnPosition.Yard, board))
    }

    @Test
    fun `the 8 safe squares are the four start squares and the stars 8 on from each`() {
        assertEquals(setOf(0, 8, 13, 21, 26, 34, 39, 47), (0..<board.trackLength).filter { board.isSafe(it) }.toSet())
    }

    @Test
    fun `a pawn's track square is counted from its own start - wrapping past square 51`() {
        assertEquals(13, board.trackSquare(seat = 1, progress = 0))
        // Green's 40th step is past the top of the numbering: 13 + 40 = 53, which is square 1.
        assertEquals(1, board.trackSquare(seat = 1, progress = 40))
        // The last track square before each home column is the one just behind the seat's start.
        assertEquals(listOf(50, 11, 24, 37), (0..3).map { board.trackSquare(it, board.trackSteps - 1) })
        assertFailsWith<IllegalArgumentException> { board.trackSquare(seat = 0, progress = board.trackSteps) }
    }

    @Test
    fun `progress and position convert both ways - and each kind of position covers its stretch`() {
        for (progress in PawnPosition.YARD_PROGRESS..board.homeProgress) {
            assertEquals(progress, PawnPosition.progressOf(PawnPosition.of(progress, board), board))
        }
        assertEquals(PawnPosition.Yard, PawnPosition.of(-1, board))
        assertEquals(PawnPosition.Track(0), PawnPosition.of(0, board))
        assertEquals(PawnPosition.Track(50), PawnPosition.of(50, board))
        assertEquals(PawnPosition.HomeColumn(0), PawnPosition.of(51, board))
        assertEquals(PawnPosition.HomeColumn(4), PawnPosition.of(55, board))
        assertEquals(PawnPosition.Home, PawnPosition.of(56, board))
        assertFailsWith<IllegalArgumentException> { PawnPosition.of(57, board) }
        assertEquals(listOf(56, 1, 0), listOf(PawnPosition.Track(0), PawnPosition.HomeColumn(4), PawnPosition.Home).map { stepsToHome(it, board) })
    }

    @Test
    fun `a board whose seats don't space evenly round the track is refused`() {
        assertFailsWith<IllegalArgumentException> { BoardGeometry(seats = 4, trackLength = 50, seatSpacing = 13, starOffset = 8, homeColumnLength = 5) }
        assertFailsWith<IllegalArgumentException> { BoardGeometry(seats = 4, trackLength = 52, seatSpacing = 13, starOffset = 13, homeColumnLength = 5) }
    }
}
