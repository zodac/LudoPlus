package net.zodac.ludoplus.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.GameRules
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS
import net.zodac.ludoplus.model.withHouseRules

/** Red, green, and yellow: players 0 to 2 at seats 0 to 2, so their start squares are 0, 13, and 26. */
private const val RED = 0
private const val GREEN = 1

private const val Y = YARD_PROGRESS
private const val HOME = 56

/** A classic game of four with each player's pawns at the progress given, red to roll, under [houseRules]. */
private fun game(vararg pawns: List<Int>, houseRules: Set<HouseRule> = emptySet()): GameState {
    val all = List(4) { pawns.getOrElse(it) { listOf(Y, Y, Y, Y) } }
    val new = GameState.new(GameMode.CLASSIC, playerCount = 4, rules = GameRules().withHouseRules(houseRules))
    return new.copy(players = new.players.mapIndexed { i, player -> player.copy(pawns = all[i]) })
}

private fun game(vararg pawns: List<Int>, rule: HouseRule) = game(*pawns, houseRules = setOf(rule))

private fun GameState.roll(value: Int) = LudoEngine.applyRoll(this, value)
private fun GameState.move(pawn: Int) = LudoEngine.move(this, pawn)
private val GameState.moves: List<Move> get() = (phase as TurnPhase.AwaitingMove).moves

/** The house rules (`HouseRule`), each against the classic rule it changes. */
class HouseRuleEngineTest {

    @Test
    fun `house rules are recorded on the rules they make - and none leaves a mode's rules as they were`() {
        assertEquals(GameRules(), GameRules().withHouseRules(emptySet()))
        assertFalse(GameRules().hasHouseRules)
        val rules = GameRules().withHouseRules(setOf(HouseRule.BLOCKADES, HouseRule.EXIT_ON_ONE))
        assertEquals(setOf(HouseRule.BLOCKADES, HouseRule.EXIT_ON_ONE), rules.houseRules)
        assertTrue(rules.hasHouseRules)
        assertTrue(rules.blockades)
        assertEquals(setOf(1, 6), rules.yardExitRolls)
    }

    @Test
    fun `No Capture Roll - a capture passes the turn`() {
        // Green's pawn 1 step on is on square 14, 4 ahead of red's.
        val pawns = arrayOf(listOf(10, Y, Y, Y), listOf(1, Y, Y, Y))
        assertEquals(RED, game(*pawns).roll(4).move(0).current)
        val after = game(*pawns, rule = HouseRule.NO_CAPTURE_ROLL).roll(4).move(0)
        assertEquals(listOf(Y, Y, Y, Y), after.players[GREEN].pawns)
        assertEquals(GREEN, after.current)
    }

    @Test
    fun `No Home Roll - a pawn home passes the turn`() {
        val pawns = arrayOf(listOf(HOME - 3, 10, Y, Y))
        assertEquals(RED, game(*pawns).roll(3).move(0).current)
        assertEquals(GREEN, game(*pawns, rule = HouseRule.NO_HOME_ROLL).roll(3).move(0).current)
    }

    @Test
    fun `Endless 6s - a third 6 in a row is moved and rolls again`() {
        val twoSixes = game(listOf(0, Y, Y, Y), rule = HouseRule.ENDLESS_SIXES).roll(6).move(0).roll(6).move(0)
        val third = twoSixes.roll(6).move(0)
        assertEquals(listOf(18, Y, Y, Y), third.players[RED].pawns)
        assertEquals(RED, third.current)
        assertEquals(3, third.rollAgainRun)
    }

    @Test
    fun `Out on a 1 - a 1 brings a pawn out of the yard - with no roll again`() {
        assertEquals(GREEN, game().roll(1).current)
        val one = game(rule = HouseRule.EXIT_ON_ONE).roll(1)
        assertEquals((0..3).map { Move(pawn = it, from = Y, to = 0) }, one.moves)
        assertEquals(GREEN, one.move(0).current)
    }

    @Test
    fun `Free Start - any roll brings a pawn out while none is on the board - pawns home don't count`() {
        val three = game(rule = HouseRule.FREE_START).roll(3)
        assertEquals((0..3).map { Move(pawn = it, from = Y, to = 0) }, three.moves)
        assertEquals(listOf(Move(pawn = 1, from = Y, to = 0)), game(listOf(HOME, Y, HOME, HOME), rule = HouseRule.FREE_START).roll(2).moves)
        // With a pawn on the track, or in its home column, only a 6 brings one out.
        assertEquals(listOf(Move(pawn = 0, from = 10, to = 13)), game(listOf(10, Y, Y, Y), rule = HouseRule.FREE_START).roll(3).moves)
        assertEquals(listOf(Move(pawn = 0, from = 52, to = 54)), game(listOf(52, Y, Y, Y), rule = HouseRule.FREE_START).roll(2).moves)
    }

    @Test
    fun `Three Tries - with no pawn on the board a roll without a move rolls again - three in all - in one turn`() {
        val first = game(rule = HouseRule.THREE_TRIES).roll(2)
        assertEquals(GameEvent.NoMove(RED, 2, triesLeft = 2), first.lastEvent)
        assertEquals(RED, first.current)
        assertTrue(first.rollingAgain)
        val second = first.roll(3)
        assertEquals(GameEvent.NoMove(RED, 3, triesLeft = 1), second.lastEvent)
        assertEquals(RED, second.current)
        val third = second.roll(4)
        assertEquals(GameEvent.NoMove(RED, 4), third.lastEvent)
        assertEquals(GREEN, third.current)
        assertEquals(0, third.yardTriesUsed)
        // One turn, however many tries.
        assertEquals(1, third.players[RED].turns)

        // A 6 on a try brings a pawn out, and rolls again as ever, its tries spent.
        val out = first.roll(6).move(0)
        assertEquals(RED, out.current)
        assertEquals(0, out.yardTriesUsed)
        // Pawns home don't count as on the board.
        assertEquals(RED, game(listOf(HOME, Y, Y, Y), rule = HouseRule.THREE_TRIES).roll(1).current)
        // With a pawn on the board, a roll without a move passes the turn.
        assertEquals(GREEN, game(listOf(54, Y, Y, Y), rule = HouseRule.THREE_TRIES).roll(5).current)
    }

    @Test
    fun `Blockades - two pawns of one player on a square can't be passed or landed on by another's`() {
        // Green's two pawns 1 step on are on square 14.
        val green = listOf(1, 1, Y, Y)
        assertEquals(listOf(Move(pawn = 1, from = 20, to = 24)), game(listOf(10, 20, Y, Y), green, rule = HouseRule.BLOCKADES).roll(4).moves)
        assertEquals(listOf(Move(pawn = 1, from = 20, to = 25)), game(listOf(10, 20, Y, Y), green, rule = HouseRule.BLOCKADES).roll(5).moves)
        // Without the rule, landing there captures both.
        assertEquals(2, game(listOf(10, 20, Y, Y), green).roll(4).moves.first().captures.size)
        // Its own player's pawns pass it and join it.
        val own = game(listOf(1, 1, 12, Y), green.map { Y }, rule = HouseRule.BLOCKADES).roll(2)
        assertEquals(listOf(Move(0, 1, 3), Move(1, 1, 3), Move(2, 12, 14)), own.moves)
        // One on a start square keeps that seat's pawns in the yard: green's 39 steps on are on red's start square.
        val blocked = game(listOf(Y, Y, Y, Y), listOf(39, 39, Y, Y), rule = HouseRule.BLOCKADES).roll(6)
        assertEquals(GameEvent.NoMove(RED, 6), blocked.lastEvent)
        // A single pawn is no blockade.
        assertEquals(2, game(listOf(10, 20, Y, Y), listOf(1, Y, Y, Y), rule = HouseRule.BLOCKADES).roll(5).moves.size)
        assertEquals(mapOf(14 to GREEN), LudoEngine.blockades(game(listOf(10, Y, Y, Y), green, rule = HouseRule.BLOCKADES)))
        assertEquals(emptyMap(), LudoEngine.blockades(game(listOf(10, Y, Y, Y), green)))
    }

    @Test
    fun `Must Capture - a roll with a capture allows only captures`() {
        val pawns = arrayOf(listOf(10, 30, Y, Y), listOf(1, Y, Y, Y))
        assertEquals(2, game(*pawns).roll(4).moves.size)
        val must = game(*pawns, rule = HouseRule.MUST_CAPTURE).roll(4)
        assertEquals(listOf(0), must.moves.map { it.pawn })
        // A roll without one allows every move.
        assertEquals(2, game(*pawns, rule = HouseRule.MUST_CAPTURE).roll(3).moves.size)
    }

    @Test
    fun `Guarded Starts - a start square is safe only for its own seat's pawns`() {
        // Green's pawn 39 steps on is on red's start square: red coming out captures it.
        val onRedStart = arrayOf(listOf(Y, Y, Y, Y), listOf(39, Y, Y, Y))
        assertTrue(game(*onRedStart).roll(6).moves.all { it.captures.isEmpty() })
        assertEquals(listOf(PawnRef(GREEN, 0)), game(*onRedStart, rule = HouseRule.GUARDED_STARTS).roll(6).moves.first().captures)
        // Green's pawn 13 steps on is on yellow's start square, 4 ahead of red's pawn: captured there too.
        val onYellowStart = arrayOf(listOf(22, Y, Y, Y), listOf(13, Y, Y, Y))
        assertTrue(game(*onYellowStart).roll(4).moves.single().captures.isEmpty())
        assertEquals(listOf(PawnRef(GREEN, 0)), game(*onYellowStart, rule = HouseRule.GUARDED_STARTS).roll(4).moves.single().captures)
        // Red's own start keeps red's pawn: green's 4 short of it lands beside it.
        val redHome = game(listOf(0, Y, Y, Y), listOf(35, Y, Y, Y), rule = HouseRule.GUARDED_STARTS).copy(current = GREEN).roll(4)
        assertTrue(redHome.moves.single().captures.isEmpty())
        // Star squares stay safe for everyone.
        val star = GameRules().withHouseRules(setOf(HouseRule.GUARDED_STARTS))
        assertTrue(star.isSafeFor(seat = 1, square = 8))
        assertFalse(star.isSafeFor(seat = 1, square = 0))
        assertTrue(star.isSafeFor(seat = 0, square = 0))
    }

    @Test
    fun `a house rule about the yard or home changes nothing in Survival - every other applies everywhere`() {
        val idle = setOf(HouseRule.NO_HOME_ROLL, HouseRule.EXIT_ON_ONE, HouseRule.FREE_START, HouseRule.THREE_TRIES)
        for (mode in GameMode.entries) {
            assertEquals(if (mode == GameMode.SURVIVAL) idle else emptySet(), HouseRule.entries.filterNot { it.appliesTo(mode.rules) }.toSet())
        }
    }
}
