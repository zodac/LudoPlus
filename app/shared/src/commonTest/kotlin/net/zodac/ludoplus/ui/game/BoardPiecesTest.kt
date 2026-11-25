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
        assertEquals(ClassicBoardLayout.homeStand(0, 0, 1), red.single { it.pawns == listOf(3) }.centre)
        // Every pawn home is seen, each on its own spot, of as many as are home: never one pawn with a count.
        assertEquals(listOf(ClassicBoardLayout.homeStand(1, 0, 2), ClassicBoardLayout.homeStand(1, 1, 2)), marks.filter { it.player == 1 && it.pawns.single() <= 1 }.map { it.centre })
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
    fun `a lone pawn stands in the middle of an upright square - and a little above a turned one's`() {
        for (square in 0..<ClassicBoardLayout.geometry.trackLength) ClassicBoardLayout.trackSquare(square).let { assertEquals(it.centre, it.stand(1f)) }
        // Every other arm of the eight-seat board is turned an eighth.
        val layout = BoardLayout.of(GameMode.CLASSIC.rules.forPlayers(8).board)
        for (seat in 0..<8) for (index in 0..<layout.geometry.homeColumnLength) {
            val square = layout.homeColumnSquare(seat, index)
            assertEquals(square.centre.x, square.stand(1f).x, "seat $seat")
            assertEquals(if (seat % 2 == 0) 0f else 0.0925f, square.centre.y - square.stand(1f).y, 0.006f, "seat $seat")
        }
    }

    @Test
    fun `a pawn's hops and its mark stand at the same place on a turned square`() {
        val new = GameState.new(GameMode.CLASSIC, 8, rules = GameMode.CLASSIC.rules.forPlayers(8))
        val layout = BoardLayout.of(new.board)
        for (progress in listOf(3, new.board.homeProgress - 2)) {
            val state = new.copy(players = new.players.mapIndexed { i, side -> if (i == 1) side.copy(pawns = listOf(progress, Y, Y, Y)) else side })
            val mark = pawnMarks(state).single { it.player == 1 && it.pawns == listOf(0) }
            assertEquals(layout.centreOf(state.players[1].seat, state.position(1, 0), 0), mark.centre)
            assertTrue(mark.centre.y < mark.square.y, "not lifted at $progress")
            // Of a style too wide at its top to rise.
            val level = pawnMarks(state, lift = 0f).single { it.player == 1 && it.pawns == listOf(0) }
            assertEquals(level.square to level.square, level.centre to layout.centreOf(state.players[1].seat, state.position(1, 0), 0, lift = 0f))
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

    @Test
    fun `colours sharing a turned square stand left and right - then up and down - each inside it and clear of the rest`() {
        for (players in listOf(3, 5, 6, 7, 8)) {
            val new = GameState.new(GameMode.CLASSIC, players, rules = GameMode.CLASSIC.rules.forPlayers(players))
            val board = new.board
            val layout = BoardLayout.of(board)
            for (star in List(board.seats) { board.startSquare(it) + board.starOffset }) for (sharing in 2..minOf(4, players)) {
                val square = layout.trackSquare(star.mod(board.trackLength))
                if (!square.isTurned) continue
                val state = new.copy(
                    players = new.players.mapIndexed { i, side ->
                        val progress = board.progressAt(side.seat, star.mod(board.trackLength))
                        side.copy(pawns = listOf(if (i < sharing && progress != null) progress else Y, Y, Y, Y))
                    },
                )
                val drawn = pawnMarks(state).filter { it.square == square.centre }
                if (drawn.size < 2) continue
                val marks = drawn.sortedBy { it.player }
                val about = "$players players, star $star, ${marks.size} sharing"
                assertEquals(drawn.map { it.centre.y }.sorted(), drawn.map { it.centre.y }, "$about: drawn from the furthest up")
                assertTrue(marks[0].centre.x < marks[1].centre.x, about)
                assertTrue(marks.drop(2).all { it.centre.x > marks[0].centre.x && it.centre.x < marks[1].centre.x }, about)
                for ((i, mark) in marks.withIndex()) {
                    // The foot's two corners and the head, as [BoardSquare.stand] has them.
                    for ((at, reach) in listOf(Offset(-0.66f, 0.76f) to 0.205f, Offset(0.66f, 0.76f) to 0.205f, Offset(0f, -0.62f) to 0.425f)) {
                        val point = mark.centre + at * mark.radius
                        val room = square.corners.indices.minOf { c ->
                            val side = square.corners[(c + 1) % 4] - square.corners[c]
                            val inside = square.centre - square.corners[c]
                            val from = point - square.corners[c]
                            val way = if (side.x * inside.y - side.y * inside.x < 0) -1 else 1
                            way * (side.x * from.y - side.y * from.x) / side.getDistance()
                        }
                        assertTrue(room - reach * mark.radius > -0.03f, "$about: ${room - reach * mark.radius}")
                    }
                    for (other in marks.drop(i + 1)) assertTrue((mark.centre - other.centre).getDistance() > 0.47f, about)
                }
            }
        }
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
    fun `a pawn a banked Call Up brings back out leaves from where it's taken for its start - where others make room`() {
        // Red's pawn takes green's on square 4; blue's pawn stands on green's start, 13 (26 from blue's own).
        val new = GameState.new(GameMode.CHAOS, 4)
        val pawns = listOf(listOf(2, -1, -1, -1), listOf(43, -1, -1, -1), listOf(-1, -1, -1, -1), listOf(26, -1, -1, -1))
        val start = new.copy(players = new.players.mapIndexed { i, side -> side.copy(pawns = pawns[i], callUps = if (i == 1) 1 else 0) }, powerUps = emptyMap(), turnsToPowerUp = 5)
        val (after, marks) = moved(start, 2)
        assertEquals(0, after.players[1].pawns[0])
        val green = PawnRef(1, 0)
        assertEquals(ClassicBoardLayout.trackSquare(4).centre, marks.before(green).square)
        assertEquals(ClassicBoardLayout.trackSquare(13).centre, marks.after(green).square)
        val shift = marks.shifts { it == green || it == PawnRef(0, 0) }.single()
        assertEquals(3 to green, shift.to.player to shift.arriving)
    }

    @Test
    fun `a pawn coming home has its share of the room - those home before it making room - on every board`() {
        for (players in listOf(2, 3, 5, 6, 7, 8)) for (there in 1..5) {
            val rules = GameMode.CLASSIC.rules.forPlayers(players).withPawns(6)
            val new = GameState.new(GameMode.CLASSIC, players, rules = rules)
            val layout = BoardLayout.of(rules.board)
            val home = rules.board.homeProgress
            val from = home - 2
            // Player 0's last pawn comes home to those there; player 1 has pawns home too.
            val pawns = List(6) { if (it < there || it == 5) home else Y }
            val move = Move(5, from, home)
            val after = new.copy(
                players = new.players.mapIndexed { i, p -> p.copy(pawns = if (i == 0) pawns else List(6) { if (it < 2) home else Y }) },
                lastEvent = GameEvent.Moved(0, 2, move, finished = there == 5),
            )
            val marks = moveMarks(after, after.lastEvent as GameEvent.Moved)
            val mover = PawnRef(0, 5)
            val seat = after.players[0].seat
            assertEquals(layout.homeStand(seat, there, there + 1), marks.after(mover).centre)
            assertEquals(layout.homePawnRadiusOf(there + 1), marks.after(mover).radius)
            val shifts = marks.shifts { it == mover }
            // Only those that stand elsewhere, or at another size: none of player 1's.
            assertTrue(shifts.all { it.to.player == 0 && it.arriving == mover }, "$players players, $there home")
            val about = "$players players, $there home"
            for (pawn in 0..<there) {
                val (was, now) = layout.homeStand(seat, pawn, there) to layout.homeStand(seat, pawn, there + 1)
                val sizes = layout.homePawnRadiusOf(there) to layout.homePawnRadiusOf(there + 1)
                val shift = shifts.singleOrNull { it.to.pawns == listOf(pawn) }
                if (was == now && sizes.first == sizes.second) {
                    assertEquals(null, shift, about)
                } else {
                    assertEquals(was to now, shift?.from?.centre to shift?.to?.centre, about)
                    assertEquals(sizes, shift?.from?.radius to shift?.to?.radius, about)
                }
            }
        }
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
        val swap = Move(pawn = 0, from = 10, to = 10, swap = PawnRef(1, 0))
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
