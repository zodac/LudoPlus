package net.zodac.ludoplus.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS
import net.zodac.ludoplus.model.canHavePortal
import net.zodac.ludoplus.model.portalAt
import net.zodac.ludoplus.model.portals
import net.zodac.ludoplus.model.withHouseRules

private const val RED = 0
private const val GREEN = 1

private const val Y = YARD_PROGRESS

/** A Portals game of four with each player's pawns at the progress given, red to roll. */
private fun game(vararg pawns: List<Int>, houseRules: Set<HouseRule> = emptySet(), seed: Long = 0): GameState {
    val all = List(4) { pawns.getOrElse(it) { listOf(Y, Y, Y, Y) } }
    val new = GameState.new(GameMode.PORTALS, playerCount = 4, rules = GameMode.PORTALS.rules.withHouseRules(houseRules), seed = seed)
    return new.copy(players = new.players.mapIndexed { i, player -> player.copy(pawns = all[i]) })
}

private fun GameState.movesFor(roll: Int): List<Move> = (LudoEngine.applyRoll(this, roll).phase as? TurnPhase.AwaitingMove)?.moves.orEmpty()

/** The Portals mode: a move that ends on a portal comes out at its pair. Red's progress is the square's own number. */
class PortalsEngineTest {

    @Test
    fun `a move that ends on a portal comes out at its pair - forwards or back - and one that passes over it doesn't`() {
        // Red's short pair is 3 and 10; its long one 6 and 22.
        assertEquals(listOf(Move(0, 1, 10, via = 3)), game(listOf(1, Y, Y, Y)).movesFor(2))
        assertEquals(listOf(Move(0, 8, 3, via = 10)), game(listOf(8, Y, Y, Y)).movesFor(2))
        assertEquals(listOf(Move(0, 2, 22, via = 6)), game(listOf(2, Y, Y, Y)).movesFor(4))
        assertEquals(listOf(Move(0, 1, 4)), game(listOf(1, Y, Y, Y)).movesFor(3))
    }

    @Test
    fun `a game starts with the board's own portals - and Classic has none`() {
        assertEquals(BoardGeometry.CLASSIC.portals, game().portals)
        assertEquals(emptyList(), GameState.new(GameMode.CLASSIC, 4).portals)
    }

    @Test
    fun `the pair a pawn goes through closes - and opens again at once somewhere else - as long - the others where they were`() {
        for (seed in 0L..200L) {
            val before = game(listOf(1, 30, Y, Y), listOf(5, Y, Y, Y), seed = seed)
            val after = LudoEngine.move(LudoEngine.applyRoll(before, 2), 0)
            val used = BoardGeometry.CLASSIC.portals[0]
            val opened = after.portals[0]
            assertEquals(before.portals.drop(1), after.portals.drop(1), "seed $seed")
            assertTrue(opened.isPair(used) && opened.steps == used.steps, "seed $seed")
            assertEquals((opened.first + opened.steps) % 52, opened.second, "seed $seed")
            // Never on a square a portal can't be, a pawn's (red's at 10 and 30, green's at 18), another pair's, or where it was.
            val ends = listOf(opened.first, opened.second)
            val others = after.portals.drop(1).flatMap { listOf(it.first, it.second) }
            assertTrue(ends.all(BoardGeometry.CLASSIC::canHavePortal), "seed $seed: $opened")
            assertTrue(ends.none { it in listOf(10, 30, 18, 3) + others }, "seed $seed: $opened")
            assertTrue(after.seed != before.seed, "seed $seed")
        }
    }

    @Test
    fun `where a pair opens again is drawn from the game's seed - the same from the same seed - and not always the same`() {
        fun opened(seed: Long) = LudoEngine.move(LudoEngine.applyRoll(game(listOf(1, Y, Y, Y), seed = seed), 2), 0).portals[0]
        assertEquals(opened(7), opened(7))
        assertTrue((0L..50L).map(::opened).distinct().size > 10)
    }

    @Test
    fun `once its pair has gone a portal's square is an ordinary one`() {
        val out = LudoEngine.move(LudoEngine.applyRoll(game(listOf(1, Y, Y, Y)), 2), 0)
        assertEquals(null, out.portals.portalAt(3))
        assertEquals(null, out.portals.portalAt(10))
        // Red's other pawn, out on its start square, lands on 3 and stays.
        val next = out.copy(current = RED, players = out.players.mapIndexed { i, side -> if (i == RED) side.copy(pawns = listOf(10, 0, Y, Y)) else side })
        assertTrue(Move(1, 0, 3) in next.movesFor(3))
    }

    @Test
    fun `weighed - nobody knows where a pair will open - so it's closed`() {
        val rolled = LudoEngine.applyRoll(game(listOf(1, Y, Y, Y)), 2)
        val weighed = LudoEngine.moveWithoutChances(rolled, (rolled.phase as TurnPhase.AwaitingMove).moves.single())
        assertEquals(BoardGeometry.CLASSIC.portals.drop(1), weighed.portals)
        assertEquals(rolled.seed, weighed.seed)
    }

    @Test
    fun `Classic has no portals`() {
        val new = GameState.new(GameMode.CLASSIC, 4)
        val state = new.copy(players = new.players.mapIndexed { i, player -> if (i == RED) player.copy(pawns = listOf(1, Y, Y, Y)) else player })
        assertEquals(listOf(Move(0, 1, 3)), state.movesFor(2))
    }

    @Test
    fun `a pawn waits where it comes out - and its next move is an ordinary one`() {
        val out = LudoEngine.move(LudoEngine.applyRoll(game(listOf(1, Y, Y, Y)), 2), 0)
        assertEquals(10, out.players[RED].pawns[0])
        assertEquals(GameEvent.Moved(RED, 2, Move(0, 1, 10, via = 3), finished = false), out.lastEvent)
        assertEquals(GREEN, out.current)
        assertEquals(listOf(Move(0, 10, 11)), out.copy(current = RED).movesFor(1))
        // The squares it walked are to the portal, not through it.
        assertEquals(2, out.players[RED].squaresMoved)
    }

    @Test
    fun `a capture is only where the pawn comes out - never where it goes in`() {
        // Green's progress 42 is square 3 (13 + 42 = 55), and 49 is square 10.
        assertEquals(listOf(Move(0, 1, 10, via = 3)), game(listOf(1, Y, Y, Y), listOf(42, Y, Y, Y)).movesFor(2))
        assertEquals(listOf(Move(0, 1, 10, listOf(PawnRef(GREEN, 0)), via = 3)), game(listOf(1, Y, Y, Y), listOf(49, Y, Y, Y)).movesFor(2))
    }

    @Test
    fun `a long portal past a pawn's own start takes it most of the way round - or back - capturing where it comes out`() {
        // Green's start is between squares 6 and 22: at square 22 it's 9 steps on, and square 6 is 45 steps on.
        val onwards = game(listOf(6, Y, Y, Y), listOf(8, Y, Y, Y)).copy(current = GREEN)
        assertEquals(listOf(Move(0, 8, 45, listOf(PawnRef(RED, 0)), via = 9)), onwards.movesFor(1))
        val back = game(listOf(Y, Y, Y, Y), listOf(44, Y, Y, Y)).copy(current = GREEN)
        assertEquals(listOf(Move(0, 44, 9, via = 45)), back.movesFor(1))
    }

    @Test
    fun `with Blockades a blockade at either end stops the move`() {
        val rules = setOf(HouseRule.BLOCKADES)
        assertEquals(emptyList(), game(listOf(1, Y, Y, Y), listOf(42, 42, Y, Y), houseRules = rules).movesFor(2))
        assertEquals(emptyList(), game(listOf(1, Y, Y, Y), listOf(49, 49, Y, Y), houseRules = rules).movesFor(2))
        // Past the far end it's no matter.
        assertEquals(listOf(Move(0, 1, 10, via = 3)), game(listOf(1, Y, Y, Y), listOf(48, 48, Y, Y), houseRules = rules).movesFor(2))
    }

    @Test
    fun `Medium and Hard go through a portal forwards - and not back`() {
        for (difficulty in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val strategy = CpuStrategy.of(difficulty)
            // A 2 takes the pawn on 1 on 9 squares, or the pawn on 23 on two.
            val forwards = LudoEngine.applyRoll(game(listOf(1, 23, Y, Y)), 2)
            assertEquals(0, strategy.choose(forwards, (forwards.phase as TurnPhase.AwaitingMove).moves, Random(1)).pawn, "$difficulty")
            // A 2 takes the pawn on 8 back to 3, or the pawn on 23 on two.
            val back = LudoEngine.applyRoll(game(listOf(8, 23, Y, Y)), 2)
            assertEquals(1, strategy.choose(back, (back.phase as TurnPhase.AwaitingMove).moves, Random(1)).pawn, "$difficulty")
        }
    }

    @Test
    fun `Medium leaves a portal's way out that a rival can reach through it`() {
        // Red's pawn on 10 is where green's, on square 1 (progress 40), comes out with a 2; nothing else of green's reaches it.
        val state = LudoEngine.applyRoll(game(listOf(10, 30, Y, Y), listOf(40, Y, Y, Y)), 1)
        assertEquals(0, HeuristicStrategy().choose(state, (state.phase as TurnPhase.AwaitingMove).moves, Random(1)).pawn)
    }

    @Test
    fun `random games of every size play out - every pawn on the board or home`() {
        for (players in 2..8) {
            val random = Random(players)
            var state = GameState.new(GameMode.PORTALS, players, rules = GameMode.PORTALS.rules.forPlayers(players))
            var portals = 0
            val pairs = state.board.portals
            while (!state.isFinished) {
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random))
                    else -> LudoEngine.roll(state, random)
                }
                if ((state.lastEvent as? GameEvent.Moved)?.move?.via != null) portals++
                assertTrue(state.players.all { side -> side.pawns.all { it in Y..state.board.homeProgress } })
                // Every pair open, once, as long as it ever was, where a portal can be, no two sharing a square.
                val ends = state.portals.flatMap { listOf(it.first, it.second) }
                assertTrue(pairs.all { pair -> state.portals.single(pair::isPair).steps == pair.steps } && state.portals.size == pairs.size)
                assertTrue(ends.distinct().size == ends.size && ends.all(state.board::canHavePortal))
            }
            assertTrue(portals > 0, "No portal was gone through with $players players")
        }
    }
}
