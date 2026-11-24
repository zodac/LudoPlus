package net.zodac.ludoplus.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PawnPosition.Companion.OUT_PROGRESS
import net.zodac.ludoplus.model.TeamMode
import net.zodac.ludoplus.model.withHouseRules

private const val RED = 0
private const val GREEN = 1
private const val YELLOW = 2
private const val BLUE = 3

/** Out of the game. */
private const val X = OUT_PROGRESS

private fun one(progress: Int) = listOf(progress, X, X, X, X, X)
private val gone = List(6) { X }
private val start = List(6) { 0 }

/** A Survival game of four with each player's pawns at the progress given (all on their starts by default), red to roll. */
private fun game(vararg pawns: List<Int>, rounds: Int = 0, knockedOut: List<Int> = emptyList(), teams: List<Int>? = null): GameState {
    val new = GameState.new(GameMode.SURVIVAL, 4, rules = GameMode.SURVIVAL.rules.copy(teams = teams))
    return new.copy(
        players = new.players.mapIndexed { i, player -> player.copy(pawns = pawns.getOrElse(i) { start }) },
        roundsPlayed = rounds,
        knockedOut = knockedOut,
    )
}

private fun GameState.play(roll: Int, pawn: Int = 0) = LudoEngine.move(LudoEngine.applyRoll(this, roll), pawn)

/** The Survival mode: six pawns lapping the track, a captured one out of the game, the last player with one the winner. */
class SurvivalEngineTest {

    @Test
    fun `every pawn starts out on its start square - and any roll moves one`() {
        val state = GameState.new(GameMode.SURVIVAL, 4)
        assertTrue(state.players.all { it.pawns == start })
        assertEquals(PawnPosition.Track(0), state.position(RED, 0))
        assertEquals((0..5).map { Move(it, 0, 1) }, (LudoEngine.applyRoll(state, 1).phase as TurnPhase.AwaitingMove).moves)
        // A 6 needn't leave the stack, and gives another roll as ever.
        val six = game(listOf(10, 0, 0, 0, 0, 0)).play(6)
        assertEquals(listOf(16, 0, 0, 0, 0, 0), six.players[RED].pawns)
        assertTrue(six.rollingAgain)
    }

    @Test
    fun `a pawn goes round and round - over the square behind its own start and on past it`() {
        // Classic's red turns off at square 50: here it goes on, over 51, and round to 0.
        assertEquals(51, game(one(50)).play(1).players[RED].pawns[0])
        val lapped = game(one(51)).play(2)
        assertEquals(53, lapped.players[RED].pawns[0])
        assertEquals(1, lapped.board.trackSquare(0, 53))
        assertEquals(PawnPosition.Track(53), lapped.position(RED, 0))
        // A second lap's pawn is captured like any other: green's progress 40 is square 1.
        val caught = game(one(53), listOf(39, 0, 0, 0, 0, 0)).copy(current = GREEN).play(1)
        assertEquals(gone, caught.players[RED].pawns)
    }

    @Test
    fun `a captured pawn is out of the game - it never moves again - and its capture earns a roll`() {
        val state = game(listOf(1, 0, 0, 0, 0, 0), listOf(42, 0, 0, 0, 0, 0)).play(2)
        assertEquals(listOf(X, 0, 0, 0, 0, 0), state.players[GREEN].pawns)
        assertEquals(5, state.players[GREEN].pawnsLeft)
        assertEquals(1, state.players[GREEN].timesCaptured)
        assertEquals(1, state.players[RED].captures)
        assertTrue(state.rollingAgain)
        assertEquals(emptyList(), state.knockedOut)
        assertEquals((1..5).toList(), (LudoEngine.applyRoll(state.copy(current = GREEN), 6).phase as TurnPhase.AwaitingMove).moves.map { it.pawn })
    }

    @Test
    fun `a whole stack is captured together`() {
        // Four of green's on square 3.
        val state = game(listOf(1, 0, 0, 0, 0, 0), listOf(42, 42, 42, 42, 0, 0)).play(2)
        assertEquals(2, state.players[GREEN].pawnsLeft)
        assertEquals(4, state.players[RED].captures)
    }

    @Test
    fun `losing the last pawn knocks a player out - placed at once - and their turns pass them by`() {
        val state = game(listOf(1, 0, 0, 0, 0, 0), one(42)).play(2)
        assertEquals(listOf(GREEN), state.knockedOut)
        assertEquals(listOf(GREEN), (state.lastEvent as GameEvent.Moved).knockedOut)
        assertEquals(4, state.placeOf(GREEN))
        assertNull(state.placeOf(RED))
        assertFalse(state.isFinished)
        assertEquals(YELLOW, state.play(1, pawn = 1).current)
    }

    @Test
    fun `the last player standing wins - the others placed by how late they went out`() {
        val state = game(listOf(1, 0, 0, 0, 0, 0), one(42), gone, gone, knockedOut = listOf(BLUE, YELLOW)).play(2)
        assertTrue(state.isFinished)
        assertEquals(listOf(RED, GREEN, YELLOW, BLUE), state.finishingOrder)
        assertEquals(RED, state.winner)
        assertEquals(listOf(1, 2, 3, 4), (0..3).map(state::placeOf))
        assertEquals(listOf(GREEN), (state.lastEvent as GameEvent.Moved).knockedOut)
    }

    @Test
    fun `a team lasts while any of its players does - the last standing wins together`() {
        val teams = listOf(0, 1, 0, 1)
        // Green goes out, but blue is still in.
        val out = game(listOf(1, 0, 0, 0, 0, 0), one(42), gone, one(5), knockedOut = listOf(YELLOW), teams = teams).play(2)
        assertFalse(out.isFinished)
        assertNull(out.placeOf(GREEN))
        // Blue's last, on square 3 too (39 + 16 = 55), ends it: red's partner shares first, though knocked out.
        val over = game(listOf(1, 0, 0, 0, 0, 0), gone, gone, one(16), knockedOut = listOf(YELLOW, GREEN), teams = teams).play(2)
        assertTrue(over.isFinished)
        assertEquals(listOf(1, 2, 1, 2), (0..3).map(over::placeOf))
    }

    @Test
    fun `sudden death comes later the more players there are - a half as long again to its second stage - and its third ten turns after, however many play`() {
        val rules = GameMode.SURVIVAL.rules
        assertEquals(listOf(40, 45, 50, 55, 60, 65, 70), (2..8).map { rules.suddenDeathRound(1, it) })
        assertEquals(listOf(60, 67, 75, 82, 90, 97, 105), (2..8).map { rules.suddenDeathRound(2, it) })
        assertEquals(listOf(0, 1, 1, 2, 2), listOf(49, 50, 74, 75, 500).map { game(rounds = it).suddenDeathStage })
        assertEquals(listOf(50, 1, 25, 1, null), listOf(0, 49, 50, 74, 75).map { game(rounds = it).roundsToSuddenDeath })
        assertEquals(listOf(false, true, false, true), listOf(49, 50, 51, 75).map { game(rounds = it).suddenDeathJustCame })
        val turns = listOf(0, 2, 3, 9, 10, 11).map { game(rounds = 76).copy(suddenDeathTurns = it) }
        assertEquals(listOf(2, 2, 2, 2, 3, 3), turns.map { it.suddenDeathStage })
        assertEquals(listOf(10, 8, 7, 1, null, null), turns.map { it.turnsToCataclysm })
        assertEquals(listOf(true, true, false, false, true, false), turns.map { it.suddenDeathJustCame })
        assertEquals(0, GameState.new(GameMode.CLASSIC, 4).copy(roundsPlayed = 500).suddenDeathStage)
    }

    @Test
    fun `sudden death makes the start squares unsafe - then the stars too`() {
        // Green's progress 39 is red's start, square 0, and 47 is the star on square 8. Red's own start stack is what's on 0.
        val onStart = game(start, listOf(38, 0, 0, 0, 0, 0)).copy(current = GREEN)
        assertEquals(6, onStart.play(1).players[RED].pawnsLeft)
        assertEquals(0, onStart.copy(roundsPlayed = 50).play(1).players[RED].pawnsLeft)
        val onStar = game(listOf(8, 0, 0, 0, 0, 0), listOf(46, 0, 0, 0, 0, 0)).copy(current = GREEN)
        assertEquals(6, onStar.copy(roundsPlayed = 74).play(1).players[RED].pawnsLeft)
        assertEquals(5, onStar.copy(roundsPlayed = 75).play(1).players[RED].pawnsLeft)
        assertEquals(listOf(true, true, false), listOf(0, 50, 75).map { game(rounds = it).isSafe(8) })
        assertEquals(listOf(true, false, false), listOf(0, 50, 75).map { game(rounds = it).isSafe(13) })
    }

    @Test
    fun `a round is a turn for everyone still in - rolls again part of it`() {
        var state = game()
        state = state.play(1).play(2).play(6)
        assertEquals(YELLOW, state.current)
        assertEquals(0, state.roundsPlayed)
        state = state.play(1, pawn = 1).play(3)
        assertEquals(RED, state.current)
        assertEquals(1, state.roundsPlayed)
        // Red knocked out: green's turn starts the round after blue's.
        val without = game(gone, knockedOut = listOf(RED)).copy(current = BLUE).play(1)
        assertEquals(GREEN, without.current)
        assertEquals(1, without.roundsPlayed)
        // No other mode counts them.
        assertEquals(0, LudoEngine.applyRoll(GameState.new(GameMode.CLASSIC, 2, first = 1), 1).roundsPlayed)
    }

    @Test
    fun `Medium and Hard take a capture - and step out of a rival's reach`() {
        for (difficulty in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val strategy = CpuStrategy.of(difficulty)
            fun choice(state: GameState) = strategy.choose(state, (state.phase as TurnPhase.AwaitingMove).moves, Random(1)).pawn
            assertEquals(1, choice(LudoEngine.applyRoll(game(listOf(20, 1, 0, 0, 0, 0), listOf(42, 0, 0, 0, 0, 0)), 2)), "$difficulty")
            // Green's on square 1 is 3 behind red's on 4; red's on 30 is nobody's to reach.
            assertEquals(0, choice(LudoEngine.applyRoll(game(listOf(4, 28, X, X, X, X), listOf(40, X, X, X, X, X), one(13), one(0)), 4)), "$difficulty")
        }
    }

    @Test
    fun `Hard beats Medium - and Medium beats Easy`() {
        fun winRate(strong: CpuStrategy, weak: CpuStrategy, games: Int): Double {
            val wins = (0..<games).count { game ->
                val random = Random(game)
                val strategies = if (game % 2 == 0) listOf(strong, weak) else listOf(weak, strong)
                val over = playOut(GameState.new(GameMode.SURVIVAL, 2, rules = GameMode.SURVIVAL.rules.forPlayers(2)), random, strategies)
                over.winner == game % 2
            }
            return wins.toDouble() / games
        }
        val (easy, medium, hard) = Difficulty.entries.map(CpuStrategy::of)
        val mediumOverEasy = winRate(medium, easy, 300)
        val hardOverMedium = winRate(hard, medium, 600)
        println("Survival: Medium over Easy $mediumOverEasy, Hard over Medium $hardOverMedium")
        assertTrue(mediumOverEasy > 0.55, "Medium won $mediumOverEasy")
        assertTrue(hardOverMedium > 0.5, "Hard won $hardOverMedium")
    }

    @Test
    fun `games of every size and with any house rules end - one player or team left - every pawn on the track or out`() {
        val medium = CpuStrategy.of(Difficulty.MEDIUM)
        for (players in 2..8) {
            for (houseRules in listOf(emptySet(), setOf(HouseRule.BLOCKADES, HouseRule.MUST_CAPTURE), setOf(HouseRule.GUARDED_STARTS, HouseRule.ENDLESS_SIXES))) {
                val rules = GameMode.SURVIVAL.rules.forPlayers(players).copy(teams = TeamMode.TWO_TEAMS.teamsFor(players).takeIf { houseRules.isEmpty() && players == 6 })
                val state = playOut(GameState.new(GameMode.SURVIVAL, players, rules = rules.withHouseRules(houseRules)), Random(players), List(players) { medium })
                assertEquals(players, state.finishingOrder.size, "$players players, $houseRules")
                assertTrue(state.players[state.winner!!].let { winner -> winner.pawnsLeft > 0 || rules.teams != null })
            }
        }
    }

    private fun playOut(start: GameState, random: Random, strategies: List<CpuStrategy>): GameState {
        var state = start
        var steps = 0
        while (!state.isFinished) {
            check(++steps < 50_000) { "Still going after $steps steps" }
            state = when (val phase = state.phase) {
                is TurnPhase.AwaitingMove -> LudoEngine.move(state, strategies[state.current].choose(state, phase.moves, random))
                else -> LudoEngine.roll(state, random)
            }
            assertTrue(state.players.all { side -> side.pawns.all { it == X || it >= 0 } })
        }
        return state
    }
}
