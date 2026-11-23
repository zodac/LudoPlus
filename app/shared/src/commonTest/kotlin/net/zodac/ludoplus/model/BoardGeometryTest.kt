package net.zodac.ludoplus.model

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

    @Test
    fun `each player count has its board - the classic one for 2 and 4 - a seat a player otherwise`() {
        assertEquals(BoardGeometry.CLASSIC, BoardGeometry.forPlayers(2))
        assertEquals(BoardGeometry.CLASSIC, BoardGeometry.forPlayers(4))
        assertEquals(listOf(3, 5, 6, 7, 8), listOf(3, 5, 6, 7, 8).map { BoardGeometry.forPlayers(it).seats })
        assertFailsWith<IllegalArgumentException> { BoardGeometry.forPlayers(1) }
        assertFailsWith<IllegalArgumentException> { BoardGeometry.forPlayers(9) }
    }

    @Test
    fun `the three-seat board - 16 squares a seat - its stars 11 on - a 53-step journey`() {
        val three = BoardGeometry.THREE_SEAT
        assertEquals(listOf(0, 16, 32), (0..2).map { three.startSquare(it) })
        assertEquals(setOf(0, 11, 16, 27, 32, 43), (0..<three.trackLength).filter { three.isSafe(it) }.toSet())
        assertEquals(53, stepsToHome(PawnPosition.Yard, three))
    }

    @Test
    fun `a 5-8 seat board is the classic arm a seat - its journey a lap of it - 70 steps to 109`() {
        for (seats in 5..8) {
            val star = BoardGeometry.star(seats)
            assertEquals(13 * seats, star.trackLength)
            assertEquals(2 * seats, (0..<star.trackLength).count { star.isSafe(it) })
            assertEquals(13 * seats + 5, stepsToHome(PawnPosition.Yard, star))
        }
    }

    @Test
    fun `players spread round the board - 2 on the classic board sit opposite`() {
        assertEquals(listOf(0, 2), board.seatsFor(2))
        assertEquals(listOf(0, 1, 2), BoardGeometry.THREE_SEAT.seatsFor(3))
        assertEquals(listOf(0, 1, 2, 3), board.seatsFor(4))
        assertEquals((0..7).toList(), BoardGeometry.star(8).seatsFor(8))
        assertFailsWith<IllegalArgumentException> { board.seatsFor(5) }
    }

    @Test
    fun `an endless board is all track - every square of it - lap after lap`() {
        val endless = board.copy(endless = true)
        assertEquals(listOf(50, 51, 0, 1), listOf(50, 51, 52, 53).map { endless.trackSquare(seat = 0, progress = it) })
        for (progress in listOf(0, 51, 56, 520)) assertEquals(PawnPosition.Track(progress), PawnPosition.of(progress, endless))
        assertEquals(PawnPosition.Out, PawnPosition.of(PawnPosition.OUT_PROGRESS, endless))
        assertEquals(PawnPosition.OUT_PROGRESS, PawnPosition.progressOf(PawnPosition.Out, endless))
    }
}
