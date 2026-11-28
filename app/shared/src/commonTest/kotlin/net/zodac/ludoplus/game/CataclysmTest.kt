package net.zodac.ludoplus.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.AchievementCounter
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PawnPosition.Companion.OUT_PROGRESS
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.withHouseRules

/** Survival's last stage: squares vanish from the track, and pawns step over the gaps ([Cataclysm]). */
class CataclysmTest {

    /** A Survival game with [pawns] each (one by default), at the progress given, [rounds] in, and [turns] into Sudden Death. */
    private fun game(vararg at: Int, rounds: Int = 100, turns: Int = 10, lost: Set<Int> = emptySet(), seed: Long = 1, pawns: Int = 1): GameState {
        val new = GameState.new(GameMode.SURVIVAL, at.size, rules = GameMode.SURVIVAL.rules.forPlayers(at.size), seed = seed)
        return new.copy(
            players = new.players.mapIndexed { i, it -> it.copy(pawns = List(pawns) { at[i] } + List(6 - pawns) { OUT_PROGRESS }) },
            roundsPlayed = rounds,
            lostSquares = lost,
            suddenDeathTurns = turns,
        )
    }

    private fun GameState.turn(roll: Int = 1) = LudoEngine.move(LudoEngine.applyRoll(this, roll), 0)

    private fun GameState.squares() = players.flatMap { side -> side.pawns.filter { it >= 0 }.map { board.trackSquare(side.seat, it) } }

    @Test
    fun `after every fifth turn of the Cataclysm - one to three squares with no pawn on them vanish`() {
        val sizes = mutableSetOf<Int>()
        for (seed in 0L..<30) {
            var state = game(0, 0, 0, 0, seed = seed)
            assertTrue(state.cataclysm)
            assertEquals(5, state.turnsToLoss)
            repeat(4) {
                state = state.turn()
                assertEquals(emptySet(), state.lostSquares)
            }
            assertEquals(1, state.turnsToLoss)
            state = state.turn()
            assertTrue(state.justLost.size in 1..3)
            assertEquals(state.justLost.toSet(), state.lostSquares)
            assertTrue(state.squares().none { it in state.lostSquares })
            assertEquals(5, state.turnsToLoss)
            sizes += state.justLost.size
            // Shown once: the next roll is a new action.
            assertEquals(emptyList(), LudoEngine.applyRoll(state, 1).justLost)
            assertEquals(state, (0..4).fold(game(0, 0, 0, 0, seed = seed)) { it, _ -> it.turn() }, "the same from the same seed")
        }
        assertEquals(setOf(1, 2, 3), sizes)
    }

    @Test
    fun `the Cataclysm comes ten turns after Sudden Death, however many play - the turn that brings Sudden Death not counted - and takes squares as it comes`() {
        for (players in listOf(2, 4, 8)) {
            val rules = GameMode.SURVIVAL.rules
            val second = rules.suddenDeathRound(2, players)
            var state = game(*IntArray(players), rounds = second - 1, turns = 0, pawns = 2).copy(current = players - 1).turn()
            assertEquals(second, state.roundsPlayed)
            assertEquals(10, state.turnsToCataclysm, "$players players")
            repeat(9) { state = state.turn() }
            assertEquals(1, state.turnsToCataclysm)
            assertFalse(state.cataclysm)
            state = state.turn()
            assertTrue(state.cataclysm)
            assertTrue(state.suddenDeathJustCame)
            assertEquals(5, state.turnsToLoss)
            assertTrue(state.justLost.size in 1..3, "$players players")
            assertEquals(state.justLost.toSet(), state.lostSquares)
            assertTrue(state.squares().none { it in state.lostSquares })
        }
        var early = game(0, 0, 0, 0, rounds = 60, turns = 0)
        repeat(8) { early = early.turn() }
        assertEquals(0, early.suddenDeathTurns)
        assertNull(early.turnsToLoss)
        assertFalse(GameState.new(GameMode.CLASSIC, 4).copy(roundsPlayed = 500).cataclysm)
    }

    @Test
    fun `a game that starts with two pawns is in the Cataclysm from the start - squares lost as it comes`() {
        for (seed in 0L..<10) {
            val duel = GameState.new(GameMode.SURVIVAL, 2, rules = GameMode.SURVIVAL.rules.forPlayers(2).withPawns(1), seed = seed)
            assertTrue(duel.cataclysm)
            assertTrue(duel.suddenDeathJustCame)
            assertEquals(10, duel.suddenDeathTurns)
            assertTrue(duel.lostSquares.size in 1..3)
            assertTrue(duel.squares().none { it in duel.lostSquares })
            assertEquals(0, duel.overtimeRound)
            assertEquals(5, duel.turnsToLoss)
        }
        // Three pawns aren't few enough.
        assertFalse(GameState.new(GameMode.SURVIVAL, 3, rules = GameMode.SURVIVAL.rules.forPlayers(3).withPawns(1)).cataclysm)
    }

    @Test
    fun `a turn with more than one roll counts once`() {
        val state = game(0, 0, 0, 0)
        val rolledSix = state.turn(6)
        assertEquals(10, rolledSix.suddenDeathTurns)
        assertEquals(11, rolledSix.turn(1).suddenDeathTurns)
    }

    @Test
    fun `a pawn steps over the squares lost - and captures beyond them`() {
        // Red on square 8, and green on square 14: 6 on, but only 4 with squares 9 and 11 gone.
        val state = game(8, 1, 0, 0, lost = setOf(9, 11))
        assertEquals(14, state.board.trackSquare(state.players[1].seat, 1))
        assertEquals(10, LudoEngine.destination(state, 0, 8, 1))
        assertEquals(12, LudoEngine.destination(state, 0, 8, 2))
        val move = LudoEngine.legalMoves(state, 4).single()
        assertEquals(Move(0, 8, 14, captures = listOf(PawnRef(1, 0)), skipped = listOf(9, 11)), move)
        assertEquals(4, move.squares)
        val after = state.turn(4)
        assertEquals(listOf(1), after.knockedOut)
        // Round past square 0: a later lap's squares are the same ones.
        assertEquals(52 + 10, LudoEngine.destination(state, 0, 52 + 8, 1))
    }

    @Test
    fun `the squares walked don't count those stepped over`() {
        val state = game(8, 1, 0, 0, lost = setOf(9, 10, 11))
        val tracker = AchievementTracker(List(4) { PlayerConfig(it + 1, PlayerType.HUMAN, "P$it") })
        val rolled = LudoEngine.applyRoll(state, 2)
        tracker.observe(state, rolled)
        tracker.observe(rolled, LudoEngine.move(rolled, 0))
        assertEquals(2, tracker.flush().counts[AchievementCounter.SQUARES_MOVED])
    }

    @Test
    fun `seven squares are always left - so a lone rival is always within a roll`() {
        val left = setOf(8, 14, 20, 26, 32, 38, 44, 50)
        val state = game(8, 1, 12, 5, lost = (0..<52).toSet() - left).copy(suddenDeathTurns = 14)
        assertEquals(setOf(8, 14, 38, 44), state.squares().toSet())
        val after = state.turn(2)
        assertEquals(7, 52 - after.lostSquares.size)
        assertEquals(1, after.justLost.size)
        assertNull(after.turnsToLoss)
        val more = (0..9).fold(after) { it, _ -> if (it.isFinished) it else it.turn(2) }
        assertEquals(after.lostSquares, more.lostSquares)
        // Two pawns on seven squares, wherever they are, are one roll apart.
        for (gap in 1..6) {
            val squares = (0..<52).toList().take(7)
            val duel = game(0, 0, lost = (7..<52).toSet()).let { it.copy(players = listOf(it.players[0], it.players[1].copy(pawns = listOf(26 + gap) + List(5) { OUT_PROGRESS }))) }
            assertEquals(squares[gap], duel.board.trackSquare(duel.players[1].seat, 26 + gap))
            assertEquals(listOf(gap), (1..6).filter { roll -> LudoEngine.legalMoves(duel, roll).single().captures.isNotEmpty() })
        }
    }

    @Test
    fun `a duel that could go on for ever ends - with any house rules`() {
        val medium = CpuStrategy.of(Difficulty.MEDIUM)
        for (players in listOf(2, 8)) for (houseRules in listOf(emptySet(), setOf(HouseRule.BLOCKADES), HouseRule.entries.filter { it.appliesTo(GameMode.SURVIVAL.rules) }.toSet())) {
            repeat(10) { seed ->
                val random = Random(seed)
                val rules = GameMode.SURVIVAL.rules.forPlayers(players).withHouseRules(houseRules)
                var state = GameState.new(GameMode.SURVIVAL, players, rules = rules, seed = seed.toLong())
                while (!state.isFinished) {
                    state = when (val phase = state.phase) {
                        is TurnPhase.AwaitingMove -> LudoEngine.move(state, medium.choose(state, phase.moves, random))
                        else -> LudoEngine.roll(state, random)
                    }
                    assertTrue(state.squares().none { it in state.lostSquares })
                    assertTrue(state.board.trackLength - state.lostSquares.size >= 7)
                    assertTrue(state.roundsPlayed < 400, "$players players, $houseRules, seed $seed: still going")
                }
            }
        }
    }
}
