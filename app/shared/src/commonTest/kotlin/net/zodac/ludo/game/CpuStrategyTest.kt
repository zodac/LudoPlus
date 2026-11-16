package net.zodac.ludo.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.GameMode

private const val Y = -1

/** What each difficulty picks in positions where the right move is plain. */
class CpuStrategyTest {

    private fun rolled(roll: Int, vararg pawns: List<Int>): GameState {
        val new = GameState.new(GameMode.CLASSIC, 4)
        val state = new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }) })
        return LudoEngine.applyRoll(state, roll)
    }

    private fun GameState.pick(strategy: CpuStrategy, seed: Int = 0): Int =
        strategy.choose(this, (phase as TurnPhase.AwaitingMove).moves, Random(seed)).pawn

    private val medium = CpuStrategy.of(Difficulty.MEDIUM)
    private val hard = CpuStrategy.of(Difficulty.HARD)

    @Test
    fun `Easy picks every legal move sometimes`() {
        val state = rolled(3, listOf(5, 10, 20, Y))
        assertEquals(setOf(0, 1, 2), (0..200).map { state.pick(CpuStrategy.of(Difficulty.EASY), it) }.toSet())
    }

    @Test
    fun `Medium and Hard take a capture`() {
        // Red's pawn 2 on its 20th step lands on green's pawn (green's 10th step, square 23) with a 3.
        val state = rolled(3, listOf(5, 30, 20, Y), listOf(10, Y, Y, Y))
        assertEquals(2, state.pick(medium))
        assertEquals(2, state.pick(hard))
    }

    @Test
    fun `Medium and Hard bring a pawn out on a 6 rather than shuffle one along`() {
        val state = rolled(6, listOf(Y, Y, 30, Y))
        assertTrue(state.pick(medium) != 2)
        assertTrue(state.pick(hard) != 2)
    }

    @Test
    fun `Medium and Hard step a pawn out of reach rather than leave it in front of an opponent`() {
        // Red's pawn 0 on square 23 has green's pawn 2 squares behind it (green's 8th step, square 21, a star), which
        // reaches squares 22 to 27. A 5 takes it to square 28, out of reach; red's other pawn waits safely on its start.
        val state = rolled(5, listOf(23, 0, Y, Y), listOf(8, Y, Y, Y))
        assertEquals(0, state.pick(medium))
        assertEquals(0, state.pick(hard))
    }

    @Test
    fun `Medium and Hard finish a pawn when they can`() {
        val state = rolled(2, listOf(54, 10, Y, Y))
        assertEquals(0, state.pick(medium))
        assertEquals(0, state.pick(hard))
    }
}
