package net.zodac.ludoplus.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode

/**
 * Each difficulty beats the one below it, over seeded dice: two-player games between them (sitting opposite, as a
 * two-player game does), each strategy taking the first seat in half of them (the first to roll has an edge); and Hard
 * among Mediums in bigger games, taking each seat in turn.
 */
class CpuTournamentTest {

    /** How often [strong] wins a [players]-player game against [weak] in every other seat, over [games] games. */
    private fun winRate(strong: CpuStrategy, weak: CpuStrategy, games: Int, players: Int = 2): Double {
        var wins = 0
        repeat(games) { game ->
            val random = Random(game)
            // The strong strategy takes each seat in turn.
            val strongAt = game % players
            val strategies = List(players) { if (it == strongAt) strong else weak }
            var state = GameState.new(GameMode.CLASSIC, players, rules = GameMode.CLASSIC.rules.forPlayers(players))
            while (!state.isFinished) {
                state = when (val phase = state.phase) {
                    TurnPhase.AwaitingRoll -> LudoEngine.roll(state, random)
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, strategies[state.current].choose(state, phase.moves, random).pawn)
                    TurnPhase.Finished -> state
                }
            }
            if (state.winner == strongAt) wins++
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
        assertTrue(mediumOverEasy >= EASY_MARGIN, "Medium won $mediumOverEasy of its games against Easy")
        assertTrue(hardOverMedium >= HARD_MARGIN, "Hard won $hardOverMedium of its games against Medium")
        assertTrue(hardOverEasy >= mediumOverEasy, "Hard won $hardOverEasy against Easy, less than Medium's $mediumOverEasy")
    }

    @Test
    fun `Hard among Mediums wins more than its share of 3- and 6-player games`() {
        val medium = CpuStrategy.of(Difficulty.MEDIUM)
        val hard = CpuStrategy.of(Difficulty.HARD)
        for ((players, games) in listOf(3 to 900, 6 to 600)) {
            val rate = winRate(hard, medium, games, players)
            println("Hard among ${players - 1} Mediums: $rate")
            assertTrue(rate >= SHARE_MARGIN / players, "Hard won $rate of $players-player games among Mediums")
        }
    }

    private companion object {
        /** Enough games that the luck of the dice evens out: at 2,000, 53% is over two and a half standard errors from 50%. */
        const val GAMES = 2_000

        /**
         * Two players sit opposite, so their pawns meet less and the dice decide more: Hard's edge over Medium is smaller
         * than side by side (about 55% over 3,000 games, 2026-11-18).
         */
        const val HARD_MARGIN = 0.53
        const val EASY_MARGIN = 0.55

        /** A strategy's fair share of wins is one in the player count; Hard's is at least this many times that. */
        const val SHARE_MARGIN = 1.1
    }
}
