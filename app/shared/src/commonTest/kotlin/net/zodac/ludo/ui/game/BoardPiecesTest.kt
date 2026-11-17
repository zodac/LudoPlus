package net.zodac.ludo.ui.game

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludo.game.GameState
import net.zodac.ludo.model.GameMode

private const val Y = -1
private const val H = 56

class BoardPiecesTest {

    private fun state(vararg pawns: List<Int>): GameState {
        val new = GameState.new(GameMode.CLASSIC, 4)
        return new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }) })
    }

    @Test
    fun `yard and home pawns stand on their own spots - a square's pawns of one colour as one mark`() {
        val marks = pawnMarks(state(listOf(Y, 10, 10, H), listOf(H, H, Y, Y)))
        val red = marks.filter { it.player == 0 }
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), red.map { it.pawns }.sortedBy { it.first() })
        assertEquals(ClassicBoardLayout.yardSpot(0, 0), red.single { it.pawns == listOf(0) }.centre)
        assertEquals(ClassicBoardLayout.trackCell(10).centre, red.single { it.pawns == listOf(1, 2) }.centre)
        assertEquals(ClassicBoardLayout.homeSpot(0, 3), red.single { it.pawns == listOf(3) }.centre)
        // Only a pawn that's home stands on an ivory spot.
        assertEquals(listOf(listOf(3)), red.filter { it.home }.map { it.pawns })
        // Every pawn home is seen, each on its own spot: never one pawn with a count.
        assertEquals(listOf(ClassicBoardLayout.homeSpot(1, 0), ClassicBoardLayout.homeSpot(1, 1)), marks.filter { it.player == 1 && it.pawns.single() <= 1 }.map { it.centre })
        // Red's three marks, green's two home and two yard pawns, and yellow's and blue's four yard pawns each.
        assertEquals(3 + 4 + 4 + 4, marks.size)
    }

    @Test
    fun `colours sharing a safe square take a quarter each`() {
        // Red's 8th step and green's 47th both land on the star on square 8.
        val marks = pawnMarks(state(listOf(8, Y, Y, Y), listOf(47, Y, Y, Y)))
        val shared = marks.filter { it.pawns == listOf(0) && it.player in 0..1 && it.radius < 0.3f }
        assertEquals(2, shared.size)
        val centre = ClassicBoardLayout.trackCell(8).centre
        val offsets = shared.sortedBy { it.player }.map { it.centre - centre }
        assertEquals(listOf(Offset(-0.24f, -0.24f), Offset(0.24f, -0.24f)).map { it.x to it.y }, offsets.map { it.x.rounded() to it.y.rounded() })
    }

    @Test
    fun `a tap picks the nearest movable pawn within reach - only the current player's`() {
        val marks = pawnMarks(state(listOf(10, 10, 14, Y), listOf(1, Y, Y, Y)))
        val near10 = ClassicBoardLayout.trackCell(10).centre + Offset(0.6f, 0f)
        assertEquals(0, pawnAt(near10, marks, player = 0, movable = setOf(0, 1)))
        // A stack answers with its first pawn that can move.
        assertEquals(1, pawnAt(near10, marks, player = 0, movable = setOf(1, 2)))
        assertNull(pawnAt(ClassicBoardLayout.trackCell(30).centre, marks, player = 0, movable = setOf(0, 1)))
        // Green's pawn shares square 14 (green's start 13, plus 1): a tap there picks the pawn of whoever's turn it is.
        assertEquals(2, pawnAt(ClassicBoardLayout.trackCell(14).centre, marks, player = 0, movable = setOf(2)))
        assertNull(pawnAt(ClassicBoardLayout.trackCell(14).centre, marks, player = 0, movable = emptySet()))
        assertEquals(0, pawnAt(ClassicBoardLayout.trackCell(14).centre, marks, player = 1, movable = setOf(0)))
        // The yard pawn answers a tap beside its spot.
        assertEquals(3, pawnAt(ClassicBoardLayout.yardSpot(0, 3) + Offset(1f, 0f), marks, player = 0, movable = setOf(3)))
    }

    @Test
    fun `more than four colours on one safe square stand in a ring - each apart - all on the square`() {
        val rules = GameMode.CLASSIC.rules.forPlayers(6)
        val new = GameState.new(GameMode.CLASSIC, 6, rules = rules)
        // Every player's pawn 0 on the star on square 8: player i's progress to it is 8 less 13 a seat, round the track.
        val board = rules.board
        val state = new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = listOf((8 - 13 * i).mod(board.trackLength), Y, Y, Y)) })
        val centre = BoardLayout.of(board).trackSquare(8).centre
        val ring = pawnMarks(state).filter { it.pawns == listOf(0) }
        assertEquals(6, ring.size)
        assertTrue(ring.all { (it.centre - centre).getDistance() + it.radius <= 0.5f + 0.01f }, "off the square")
        val gaps = ring.indices.flatMap { i -> (i + 1..<ring.size).map { j -> (ring[i].centre - ring[j].centre).getDistance() } }
        assertTrue(gaps.min() >= ring.first().radius * 1.5f, "too close")
    }
}

private fun Float.rounded(): Float = kotlin.math.round(this * 100f) / 100f
