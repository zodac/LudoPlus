package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.game.PowerUpEffect
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PowerUp

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
    fun `two yard pawns wait on opposite corners of the yard's four spots`() {
        val marks = pawnMarks(GameState.new(GameMode.CLASSIC, 4, rules = GameMode.CLASSIC.rules.withPawns(2))).filter { it.player == 1 }
        assertEquals(listOf(ClassicBoardLayout.yardSpot(1, 0), ClassicBoardLayout.yardSpot(1, 3)), marks.sortedBy { it.pawns.single() }.map { it.centre })
    }

    @Test
    fun `with any number of pawns - on every board - each has a spot of its own in its yard and home - none on another`() {
        for (players in listOf(2, 3, 5, 6, 7, 8)) for (pawns in 1..6) {
            val rules = GameMode.CLASSIC.rules.forPlayers(players).withPawns(pawns)
            val new = GameState.new(GameMode.CLASSIC, players, rules = rules)
            for (at in listOf(Y, rules.board.homeProgress)) {
                val marks = pawnMarks(new.copy(players = new.players.map { it.copy(pawns = List(pawns) { at }) }))
                assertEquals(players * pawns, marks.size)
                for (a in marks) for (b in marks) if (a !== b) {
                    assertTrue((a.centre - b.centre).getDistance() >= (a.radius + b.radius) * 0.99f, "$players players, $pawns pawns, at $at: ${a.player}'s ${a.pawns} and ${b.player}'s ${b.pawns}")
                }
            }
        }
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

    /** [state] with player 1 to move pawn 0 by [roll], moved. */
    private fun moved(state: GameState, roll: Int): Pair<GameState, MoveMarks> {
        val after = LudoEngine.move(LudoEngine.applyRoll(state.copy(current = 0), roll), 0)
        return after to moveMarks(after, after.lastEvent as GameEvent.Moved)
    }

    @Test
    fun `a pawn joining another colour's square makes it shift - from the whole square to its share - and lands on its own`() {
        val (_, marks) = moved(state(listOf(5, Y, Y, Y), listOf(47, Y, Y, Y)), roll = 3)
        val centre = ClassicBoardLayout.trackCell(8).centre
        val mover = PawnRef(0, 0)
        val shift = marks.shifts { it == mover }.single()
        assertEquals(1 to listOf(0), shift.to.player to shift.to.pawns)
        assertEquals(mover, shift.arriving)
        assertEquals(centre to PAWN_RADIUS, shift.from.centre to shift.from.radius)
        assertEquals(marks.after(PawnRef(1, 0)), shift.to)
        assertTrue(shift.to.centre != centre && shift.to.radius < PAWN_RADIUS)
        assertTrue(marks.after(mover).centre != centre && marks.after(mover).radius < PAWN_RADIUS)
    }

    @Test
    fun `a pawn joining its own colour on a shared square shifts nothing - nor does joining its own colour alone, or a capture`() {
        val mover = PawnRef(0, 0)
        // Red's second pawn is on the star on square 8 with green's already: the quarters they have are kept.
        val (_, shared) = moved(state(listOf(5, 8, Y, Y), listOf(47, Y, Y, Y)), roll = 3)
        assertEquals(emptyList(), shared.shifts { it == mover })
        val (_, own) = moved(state(listOf(5, 9, Y, Y)), roll = 4)
        assertEquals(emptyList(), own.shifts { it == mover })
        val (after, capture) = moved(state(listOf(14, Y, Y, Y), listOf(4, Y, Y, Y)), roll = 3)
        val taken = (after.lastEvent as GameEvent.Moved).allCaptures.single()
        assertEquals(emptyList(), capture.shifts { it == mover || it == taken })
        // Where it stood till the capture, and the yard it's sent back to.
        assertEquals(ClassicBoardLayout.trackCell(17).centre, capture.before(taken).centre)
        assertEquals(ClassicBoardLayout.yardSpot(1, 0), capture.after(taken).centre)
    }

    @Test
    fun `a pawn passing over another colour's shifts nothing`() {
        val (_, marks) = moved(state(listOf(5, Y, Y, Y), listOf(47, Y, Y, Y)), roll = 4)
        assertEquals(emptyList(), marks.shifts { it == PawnRef(0, 0) })
    }

    @Test
    fun `a pawn swapped off a shared square leaves from its share - and the pawn it shared with keeps its own`() {
        // Red ends on a Swap on square 10, and swaps with green's pawn on the star on square 8, shared with yellow's.
        val before = state(listOf(10, Y, Y, Y), listOf(47, Y, Y, Y), listOf(34, Y, Y, Y))
        val swap = Move(pawn = 0, from = 7, to = 10, swap = PawnRef(1, 0))
        val effect = PowerUpEffect(PowerUp.SWAP, movedTo = 8, swapped = PawnRef(1, 0))
        val after = before.copy(players = before.players.mapIndexed { i, p -> p.copy(pawns = listOf(listOf(8, 49, 34, Y)[i], Y, Y, Y)) })
        val marks = moveMarks(after, GameEvent.Moved(0, 3, swap, finished = false, effect = effect))
        // Green's pawn leaves from its share of the star, and lands alone.
        assertEquals(ClassicBoardLayout.trackCell(8).centre, marks.before(PawnRef(1, 0)).square)
        assertTrue(marks.before(PawnRef(1, 0)).radius < PAWN_RADIUS)
        assertEquals(ClassicBoardLayout.trackCell(10).centre to PAWN_RADIUS, marks.after(PawnRef(1, 0)).let { it.centre to it.radius })
        // Yellow's keeps the share it had.
        assertEquals(marks.before(PawnRef(2, 0)), marks.after(PawnRef(2, 0)))
        assertEquals(emptyList(), marks.shifts { it == PawnRef(0, 0) || it == PawnRef(1, 0) })
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
