package net.zodac.ludoplus.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS
import net.zodac.ludoplus.model.TeamMode
import net.zodac.ludoplus.model.withHouseRules

private const val RED = 0
private const val GREEN = 1
private const val YELLOW = 2
private const val BLUE = 3

private const val Y = YARD_PROGRESS
private const val HOME = 56

/** A Colour Rush game of four with each player's pawns at the progress given and [claimed] squares, red to roll. */
private fun game(vararg pawns: List<Int>, claimed: Map<Int, Int> = emptyMap(), houseRules: Set<HouseRule> = emptySet(), teams: List<Int>? = null): GameState {
    val new = GameState.new(GameMode.COLOUR_RUSH, 4, rules = GameMode.COLOUR_RUSH.rules.copy(teams = teams).withHouseRules(houseRules))
    return new.copy(players = new.players.mapIndexed { i, player -> player.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }) }, claimed = claimed)
}

private fun GameState.play(roll: Int, pawn: Int = 0) = LudoEngine.move(LudoEngine.applyRoll(this, roll), pawn)

/** The Colour Rush mode: the squares of the track claimed, and the most of them wins. Red's progress is the square's own number. */
class ColourRushEngineTest {

    @Test
    fun `the first pawn to land on a square claims it - star squares too, but not a start - and those passed over not`() {
        assertEquals(mapOf(5 to RED), game(listOf(2, Y, Y, Y)).play(3).claimed)
        assertEquals(emptyMap(), game().play(6).claimed, "its own start, out of the yard")
        assertEquals(emptyMap(), game(listOf(10, Y, Y, Y)).play(3).claimed, "green's start")
        assertEquals(mapOf(8 to RED), game(listOf(2, Y, Y, Y)).play(6).claimed, "a star")
        assertEquals(emptyMap(), game(listOf(50, Y, Y, Y)).play(2).claimed, "the home column isn't the track's")
        assertEquals(emptyMap(), GameState.new(GameMode.CLASSIC, 4).let { it.copy(players = it.players.map { side -> side.copy(pawns = listOf(2, Y, Y, Y)) }) }.play(3).claimed)
    }

    @Test
    fun `landing on a claimed square changes nothing - and a capture there does - whoever is captured`() {
        // Green's square 5, with nobody on it.
        assertEquals(mapOf(5 to GREEN), game(listOf(2, Y, Y, Y), claimed = mapOf(5 to GREEN)).play(3).claimed)
        // Green's own pawn on it (44 is square 5), and yellow's (31 is).
        assertEquals(mapOf(5 to RED), game(listOf(2, Y, Y, Y), listOf(44, Y, Y, Y), claimed = mapOf(5 to GREEN)).play(3).claimed)
        assertEquals(mapOf(5 to RED), game(listOf(2, Y, Y, Y), emptyYard(), listOf(31, Y, Y, Y), claimed = mapOf(5 to GREEN)).play(3).claimed)
        // Red's own, by red: still red's.
        assertEquals(mapOf(5 to RED), game(listOf(2, Y, Y, Y), listOf(44, Y, Y, Y), claimed = mapOf(5 to RED)).play(3).claimed)
    }

    @Test
    fun `a claimed star stays its first player's - whoever lands there after - as no pawn there can be captured`() {
        // Green's pawn on the star (47 is square 8), red landing beside it.
        val shared = game(listOf(2, Y, Y, Y), listOf(47, Y, Y, Y), claimed = mapOf(8 to GREEN)).play(6)
        assertEquals(emptyList(), (shared.lastEvent as GameEvent.Moved).allCaptures)
        assertEquals(mapOf(8 to GREEN), shared.claimed)
        // Unclaimed, it's the next to land's, green's pawn there or not.
        assertEquals(mapOf(8 to RED), game(listOf(2, Y, Y, Y), listOf(47, Y, Y, Y)).play(6).claimed)
    }

    @Test
    fun `every square can be claimed but each seat's start`() {
        assertEquals(listOf(48, 45, 48, 60, 72, 84, 96), (2..8).map { BoardGeometry.forPlayers(it).claimableSquares })
        for (players in 2..8) {
            val board = BoardGeometry.forPlayers(players)
            assertEquals((0..<board.trackLength).count { !board.isStart(it) }, board.claimableSquares)
        }
    }

    @Test
    fun `a guarded start - or one no one sits at - can't be claimed either`() {
        assertEquals(emptyMap(), game(listOf(10, Y, Y, Y), houseRules = setOf(HouseRule.GUARDED_STARTS)).play(3).claimed)
        val two = GameState.new(GameMode.COLOUR_RUSH, 2)
        assertEquals(emptyMap(), two.copy(players = two.players.map { it.copy(pawns = listOf(10, Y, Y, Y)) }, current = RED).play(3).claimed)
    }

    @Test
    fun `a claimed square is no safer`() {
        val own = game(listOf(5, Y, Y, Y), listOf(42, Y, Y, Y), claimed = mapOf(5 to RED)).copy(current = GREEN).play(2)
        assertEquals(listOf(PawnRef(RED, 0)), (own.lastEvent as GameEvent.Moved).allCaptures)
        assertEquals(mapOf(5 to GREEN), own.claimed)
    }

    private fun emptyYard() = listOf(Y, Y, Y, Y)

    @Test
    fun `a pawn home everywhere ends the game - the most squares first - and not the player who is home`() {
        val state = game(listOf(HOME - 2, HOME, HOME, HOME), listOf(10, Y, Y, Y), listOf(20, Y, Y, Y), claimed = mapOf(1 to GREEN, 2 to GREEN, 3 to RED, 4 to BLUE))
        val over = state.play(2)
        assertTrue(over.isFinished)
        // Red and blue tie for second: red has come further. Yellow has none.
        assertEquals(listOf(GREEN, RED, BLUE, YELLOW), over.finishingOrder)
        assertEquals(listOf(2, 1, 4, 3), (0..3).map(over::placeOf))
        assertTrue((over.lastEvent as GameEvent.Moved).finished)
        // A pawn short of it, the game goes on.
        assertFalse(game(listOf(HOME - 2, HOME, HOME, 30), claimed = mapOf(1 to GREEN)).play(2).isFinished)
    }

    @Test
    fun `the last square claimed ends the game`() {
        val board = GameState.new(GameMode.COLOUR_RUSH, 4).board
        val all = (0..<52).filterNot(board::isStart).withIndex().associate { (i, square) -> square to if (square == 5 || i % 2 == 0) RED else GREEN }
        val over = game(listOf(2, Y, Y, Y), claimed = all - 5).play(3)
        assertTrue(over.isFinished)
        assertEquals(listOf(RED, GREEN), over.finishingOrder.take(2))
        assertEquals(48, over.claimed.size)
        assertFalse((over.lastEvent as GameEvent.Moved).finished)
        assertFalse(game(listOf(2, Y, Y, Y), claimed = all - 5 - 6).play(3).isFinished)
    }

    @Test
    fun `a tie to the last goes to whoever plays sooner after the one who ended it`() {
        val over = game(listOf(HOME - 2, HOME, HOME, HOME), listOf(10, Y, Y, Y), listOf(10, Y, Y, Y), claimed = mapOf(1 to YELLOW, 2 to GREEN)).play(2)
        assertEquals(listOf(GREEN, YELLOW, RED, BLUE), over.finishingOrder)
    }

    @Test
    fun `a team's squares count together - it ends the game with all its pawns home - and its players share their place`() {
        val teams = TeamMode.TWO_TEAMS.teamsFor(4)
        val claimed = mapOf(1 to RED, 2 to GREEN, 3 to GREEN, 4 to YELLOW, 5 to YELLOW)
        val half = game(listOf(HOME - 2, HOME, HOME, HOME), claimed = claimed, teams = teams).play(2)
        assertFalse(half.isFinished)
        val over = game(listOf(HOME - 2, HOME, HOME, HOME), emptyYard(), List(4) { HOME }, claimed = claimed, teams = teams).play(2)
        assertTrue(over.isFinished)
        assertEquals(listOf(1, 2, 1, 2), (0..3).map(over::placeOf))
    }

    @Test
    fun `players are ranked by their squares as the game stands`() {
        assertNull(game(listOf(40, Y, Y, Y)).ranks())
        assertEquals(listOf(2, 1, 2, 4), game(listOf(40, Y, Y, Y), claimed = mapOf(1 to GREEN, 2 to GREEN, 3 to RED, 4 to YELLOW)).ranks())
    }

    @Test
    fun `Medium and Hard claim a square over a bigger step that claims none`() {
        for (difficulty in listOf(Difficulty.MEDIUM, Difficulty.HARD)) {
            val state = LudoEngine.applyRoll(game(listOf(20, 2, Y, Y), claimed = mapOf(23 to GREEN)), 3)
            assertEquals(1, CpuStrategy.of(difficulty).choose(state, (state.phase as TurnPhase.AwaitingMove).moves, Random(1)).pawn, "$difficulty")
        }
    }

    @Test
    fun `Hard beats Medium - and Medium beats Easy`() {
        fun winRate(strong: CpuStrategy, weak: CpuStrategy, games: Int): Double = (0..<games).count { game ->
            val random = Random(game)
            val strategies = if (game % 2 == 0) listOf(strong, weak) else listOf(weak, strong)
            var state = GameState.new(GameMode.COLOUR_RUSH, 2, first = game / 2 % 2)
            while (!state.isFinished) {
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, strategies[state.current].choose(state, phase.moves, random))
                    else -> LudoEngine.roll(state, random)
                }
            }
            state.winner == game % 2
        }.toDouble() / games
        val (easy, medium, hard) = Difficulty.entries.map(CpuStrategy::of)
        val mediumOverEasy = winRate(medium, easy, 300)
        val hardOverMedium = winRate(hard, medium, 400)
        println("SIM Colour Rush: Medium over Easy $mediumOverEasy, Hard over Medium $hardOverMedium")
        assertTrue(mediumOverEasy > 0.55, "Medium won $mediumOverEasy")
        assertTrue(hardOverMedium > 0.5, "Hard won $hardOverMedium")
    }

    @Test
    fun `games of every size - with any house rules and number of pawns - play out - every player placed by their squares`() {
        for (players in 2..8) for (pawns in 1..6) for (houseRules in listOf(emptySet(), HouseRule.entries.toSet())) {
            val random = Random(players)
            val rules = GameMode.COLOUR_RUSH.rules.forPlayers(players).withHouseRules(houseRules).withPawns(pawns)
            var state = GameState.new(GameMode.COLOUR_RUSH, players, rules = rules)
            var steps = 0
            while (!state.isFinished) {
                check(++steps < 100_000) { "Still going after $steps steps: $players players, $pawns pawns, $houseRules" }
                val before = state.claimed
                state = when (val phase = state.phase) {
                    is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random))
                    else -> LudoEngine.roll(state, random)
                }
                assertTrue(state.claimed.keys.containsAll(before.keys))
                assertTrue(state.claimed.keys.none(state.board::isStart))
            }
            val squares = state.finishingOrder.map(state::squaresOf)
            assertEquals(squares.sortedDescending(), squares)
            assertEquals(players, state.finishingOrder.size)
        }
    }
}
