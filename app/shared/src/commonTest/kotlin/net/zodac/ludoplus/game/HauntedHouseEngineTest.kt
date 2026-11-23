package net.zodac.ludoplus.game

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS
import net.zodac.ludoplus.model.withHouseRules

private const val RED = 0
private const val GREEN = 1

private const val Y = YARD_PROGRESS

/** A Haunted House game of four with each player's pawns at the progress given, red to roll, a ghost due as the turn ends. */
private fun game(vararg pawns: List<Int>, ghosts: List<Int> = listOf(30, 45), houseRules: Set<HouseRule> = emptySet(), seed: Long = 1, due: Boolean = true): GameState {
    val new = GameState.new(GameMode.HAUNTED_HOUSE, 4, rules = GameMode.HAUNTED_HOUSE.rules.withHouseRules(houseRules), seed = seed)
    return new.copy(
        players = new.players.mapIndexed { i, player -> player.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }) },
        ghosts = ghosts,
        turnsSinceGhost = if (due) 1 else 0,
    )
}

/** The states after red's pass, from every seed of a range: a ghost's move each, at random. */
private fun passes(vararg pawns: List<Int>, ghosts: List<Int> = listOf(30, 45), houseRules: Set<HouseRule> = emptySet()): List<GameState> =
    (0L..300L).map { seed -> LudoEngine.applyRoll(game(listOf(Y, Y, Y, Y), *pawns, ghosts = ghosts, houseRules = houseRules, seed = seed), 1) }

private val GameState.haunt: GhostMove get() = assertNotNull(ghostMove)

/** The Haunted House mode: after every second turn a random ghost moves, a random roll either way, catching what it lands on. */
class HauntedHouseEngineTest {

    @Test
    fun `there is a ghost for every two players - on the track - never a safe square or two on one`() {
        for (players in 2..8) {
            for (seed in 0L..30L) {
                val state = GameState.new(GameMode.HAUNTED_HOUSE, players, rules = GameMode.HAUNTED_HOUSE.rules.forPlayers(players), seed = seed)
                assertEquals(players / 2, state.ghosts.size)
                assertEquals(state.ghosts.size, state.ghosts.toSet().size)
                assertTrue(state.ghosts.none(state.board::isSafe))
            }
        }
        assertEquals(emptyList(), GameState.new(GameMode.CLASSIC, 4).ghosts)
    }

    @Test
    fun `one moves after every second turn - a turn's rolls again part of it - and no more than one`() {
        var state = game(due = false)
        state = LudoEngine.applyRoll(state, 1)
        assertNull(state.ghostMove)
        // Green's 6, and its roll again, are one turn.
        state = LudoEngine.move(LudoEngine.applyRoll(state, 6), 0)
        assertNull(state.ghostMove)
        state = LudoEngine.applyRoll(state, 3)
        assertNull(state.ghostMove)
        state = LudoEngine.move(state, 0)
        val moved = state.haunt
        assertEquals(1, state.ghosts.zip(listOf(30, 45)).count { (now, was) -> now != was })
        assertEquals((moved.from + moved.steps).mod(52), state.ghosts[moved.ghost])
        // The next roll is a new action, with no ghost's move of its own.
        assertNull(LudoEngine.applyRoll(state, 1).ghostMove)
    }

    @Test
    fun `any ghost may move - 1 to 6 squares - either way - but never onto another`() {
        val moves = passes().map { it.haunt }
        assertEquals(setOf(0, 1), moves.map { it.ghost }.toSet())
        assertEquals(((1..6) + (-6..-1)).toSet(), moves.map { it.steps }.toSet())
        // A ghost 3 squares on is never landed on.
        for (state in passes(ghosts = listOf(30, 33))) assertEquals(2, state.ghosts.toSet().size)
        // Past the track's last square and back round to its first.
        assertTrue(passes(ghosts = listOf(50, 20)).any { it.ghosts[0] in 0..3 })
    }

    @Test
    fun `it catches every pawn where it lands - whoever's - and passes over the rest`() {
        // Green's progress 19 is square 32 and 20 is 33; blue's 45 is 32 too.
        val states = passes(listOf(19, 19, 20, Y), listOf(Y, Y, Y, Y), listOf(45, Y, Y, Y), ghosts = listOf(30, 10))
        for (state in states) {
            val caught = when (state.ghosts[0]) {
                32 -> listOf(PawnRef(GREEN, 0), PawnRef(GREEN, 1), PawnRef(3, 0))
                33 -> listOf(PawnRef(GREEN, 2))
                else -> emptyList()
            }
            assertEquals(caught, state.haunt.caught)
            for ((player, side) in state.players.withIndex()) {
                val ofTheirs = caught.filter { it.player == player }
                assertEquals(ofTheirs.size, side.timesHaunted)
                assertTrue(ofTheirs.all { side.pawns[it.pawn] == Y })
                // A ghost's catch is nobody's capture.
                assertEquals(0, side.timesCaptured + side.captures)
            }
        }
        assertEquals(setOf(0, 1, 3), states.map { it.haunt.caught.size }.toSet())
    }

    @Test
    fun `a pawn on a safe square - in a home column - or in a blockade isn't caught`() {
        // The star on square 34 (green's 21), and green's home column.
        val safe = passes(listOf(21, 53, Y, Y), ghosts = listOf(31, 10))
        assertTrue(safe.any { it.ghosts[0] == 34 } && safe.all { it.haunt.caught.isEmpty() })
        val blockade = passes(listOf(19, 19, Y, Y), ghosts = listOf(30, 10), houseRules = setOf(HouseRule.BLOCKADES))
        assertTrue(blockade.any { it.ghosts[0] == 32 } && blockade.all { it.haunt.caught.isEmpty() })
        // Red's start is safe for red alone where starts are guarded: green's pawn there, 39 on, is caught.
        val guarded = passes(listOf(39, Y, Y, Y), ghosts = listOf(50, 10), houseRules = setOf(HouseRule.GUARDED_STARTS))
        assertTrue(guarded.any { it.haunt.caught == listOf(PawnRef(GREEN, 0)) })
    }

    @Test
    fun `landing on a ghost does no harm`() {
        val state = LudoEngine.move(LudoEngine.applyRoll(game(listOf(28, Y, Y, Y), due = false), 2), 0)
        assertEquals(30, state.players[RED].pawns[0])
        assertEquals(listOf(30, 45), state.ghosts)
    }

    @Test
    fun `Hard doesn't see where a ghost will go`() {
        val values = (0L..40L).map { seed ->
            val state = LudoEngine.applyRoll(game(listOf(28, Y, Y, Y), seed = seed), 4)
            ExpectimaxStrategy().value(state, (state.phase as TurnPhase.AwaitingMove).moves.single())
        }
        assertEquals(1, values.distinct().size)
    }

    @Test
    fun `games of every size play out - and the same from the same seed`() {
        fun play(players: Int): GameState {
            val random = Random(players)
            var state = GameState.new(GameMode.HAUNTED_HOUSE, players, rules = GameMode.HAUNTED_HOUSE.rules.forPlayers(players), seed = players.toLong())
            var steps = 0
            while (!state.isFinished) {
                check(++steps < 200_000) { "Still going after $steps steps" }
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random))
                    else -> LudoEngine.roll(state, random)
                }
                assertEquals(players / 2, state.ghosts.toSet().size)
                state.ghostMove?.let { assertTrue(abs(it.steps) in 1..6) }
            }
            return state
        }
        for (players in 2..8) assertEquals(play(players), play(players))
    }
}
