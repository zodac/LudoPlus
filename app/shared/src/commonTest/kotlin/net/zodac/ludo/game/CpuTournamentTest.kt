package net.zodac.ludo.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.GameMode

/**
 * Each difficulty beats the one below it: two-player games between them, over seeded dice, each strategy taking the
 * first seat in half of them (the first to roll has an edge).
 */
class CpuTournamentTest {

    /** How often [strong] wins against [weak] over [games] games. */
    private fun winRate(strong: CpuStrategy, weak: CpuStrategy, games: Int): Double {
        var wins = 0
        repeat(games) { game ->
            val random = Random(game)
            // The strong strategy sits first in even games, second in odd ones.
            val strategies = if (game % 2 == 0) listOf(strong, weak) else listOf(weak, strong)
            var state = GameState.new(GameMode.CLASSIC, playerCount = 2)
            while (!state.isFinished) {
                state = when (val phase = state.phase) {
                    TurnPhase.AwaitingRoll -> LudoEngine.roll(state, random)
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, strategies[state.current].choose(state, phase.moves, random).pawn)
                    TurnPhase.Finished -> state
                }
            }
            if (state.winner == game % 2) wins++
        }
        return wins.toDouble() / games
    }

    @Test
    fun `Medium beats Easy - Hard beats Medium - each by a clear margin`() {
        val easy = CpuStrategy.of(Difficulty.EASY)
        val medium = CpuStrategy.of(Difficulty.MEDIUM)
        val hard = CpuStrategy.of(Difficulty.HARD)
        val mediumOverEasy = winRate(medium, easy, GAMES)
        val hardOverMedium = winRate(hard, medium, GAMES)
        val hardOverEasy = winRate(hard, easy, GAMES)
        println("Medium over Easy $mediumOverEasy, Hard over Medium $hardOverMedium, Hard over Easy $hardOverEasy")
        assertTrue(mediumOverEasy >= MARGIN, "Medium won $mediumOverEasy of its games against Easy")
        assertTrue(hardOverMedium >= MARGIN, "Hard won $hardOverMedium of its games against Medium")
        assertTrue(hardOverEasy >= mediumOverEasy, "Hard won $hardOverEasy against Easy, less than Medium's $mediumOverEasy")
    }

    private companion object {
        /** Enough games that the luck of the dice evens out: at 1,000, a 55% result is over three standard errors from 50%. */
        const val GAMES = 1_000
        const val MARGIN = 0.55
    }
}
