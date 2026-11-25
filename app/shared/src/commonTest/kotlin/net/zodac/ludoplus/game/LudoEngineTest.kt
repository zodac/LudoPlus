package net.zodac.ludoplus.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.GameRules
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS

/** Red, green, yellow, and blue: players 0 to 3 at seats 0 to 3, so their start squares are 0, 13, 26, and 39. */
private const val RED = 0
private const val GREEN = 1
private const val YELLOW = 2
private const val BLUE = 3

private const val Y = YARD_PROGRESS
private const val HOME = 56

/** A classic game with each player's pawns at the progress given (four each), [current] to roll. */
private fun game(vararg pawns: List<Int>, current: Int = RED, rules: GameRules = GameRules()): GameState {
    val all = List(4) { pawns.getOrElse(it) { listOf(Y, Y, Y, Y) } }
    val new = GameState.new(GameMode.CLASSIC, playerCount = 4, first = current, rules = rules)
    return new.copy(players = new.players.mapIndexed { i, player -> player.copy(pawns = all[i]) })
}

private fun GameState.roll(value: Int) = LudoEngine.applyRoll(this, value)
private fun GameState.move(pawn: Int) = LudoEngine.move(this, pawn)
private val GameState.moves: List<Move> get() = (phase as TurnPhase.AwaitingMove).moves

class LudoEngineTest {

    @Test
    fun `only a 6 brings a pawn out of the yard - onto its start square - other rolls pass the turn`() {
        for (roll in 1..5) {
            val after = game().roll(roll)
            assertEquals(GREEN, after.current, "a $roll with every pawn in the yard passes")
            assertEquals(TurnPhase.AwaitingRoll, after.phase)
            assertEquals(GameEvent.NoMove(RED, roll), after.lastEvent)
        }
        val six = game().roll(6)
        assertEquals((0..3).map { Move(pawn = it, from = Y, to = 0) }, six.moves)
        val out = six.move(2)
        assertEquals(listOf(Y, Y, 0, Y), out.players[RED].pawns)
    }

    @Test
    fun `a pawn moves exactly the roll - and the player picks which`() {
        val rolled = game(listOf(10, 3, Y, Y)).roll(4)
        assertEquals(listOf(Move(pawn = 0, from = 10, to = 14), Move(pawn = 1, from = 3, to = 7)), rolled.moves)
        assertEquals(listOf(10, 7, Y, Y), rolled.move(1).players[RED].pawns)
    }

    @Test
    fun `a 6 gives another roll after the move - any other roll passes play clockwise`() {
        val afterSix = game(listOf(10, Y, Y, Y)).roll(6).move(0)
        assertEquals(RED, afterSix.current)
        assertEquals(TurnPhase.AwaitingRoll, afterSix.phase)

        val afterFive = afterSix.roll(5).move(0)
        assertEquals(GREEN, afterFive.current)
        assertEquals(listOf(21, Y, Y, Y), afterFive.players[RED].pawns)
        assertEquals(BLUE, game(current = YELLOW).roll(1).current)
        assertEquals(RED, game(current = BLUE).roll(1).current)
    }

    @Test
    fun `three 6s in a row end the turn - the third 6 isn't moved`() {
        val twoSixes = game(listOf(0, Y, Y, Y)).roll(6).move(0).roll(6).move(0)
        assertEquals(listOf(12, Y, Y, Y), twoSixes.players[RED].pawns)
        assertEquals(2, twoSixes.rollAgainRun)

        val third = twoSixes.roll(6)
        assertEquals(GameEvent.RollAgainLimit(RED, 6), third.lastEvent)
        assertEquals(GREEN, third.current)
        assertEquals(0, third.rollAgainRun)
        assertEquals(listOf(12, Y, Y, Y), third.players[RED].pawns)
    }

    @Test
    fun `a run broken by another roll starts counting again - and a mode without the limit never ends the run`() {
        // 6, 6, then a 2 passes; red's next turn begins a fresh run.
        val broken = game(listOf(0, Y, Y, Y)).roll(6).move(0).roll(6).move(0).roll(2).move(0)
        assertEquals(GREEN, broken.current)
        assertEquals(0, broken.rollAgainRun)

        // The limit is the rules': with none, a fourth 6 is still moved.
        var state = game(listOf(0, Y, Y, Y), rules = GameRules(rollAgainLimit = null))
        repeat(4) { state = state.roll(6).move(0) }
        assertEquals(listOf(24, Y, Y, Y), state.players[RED].pawns)
        assertEquals(RED, state.current)
    }

    @Test
    fun `a 6 with no legal move passes the turn like any other roll`() {
        // The one pawn left is 4 from home, and nothing waits in the yard.
        val stuck = game(listOf(HOME, HOME, HOME, 52)).roll(6)
        assertEquals(GameEvent.NoMove(RED, 6), stuck.lastEvent)
        assertEquals(GREEN, stuck.current)
        assertEquals(TurnPhase.AwaitingRoll, stuck.phase)
        assertEquals(0, stuck.rollAgainRun)

        // Mid-run too: a 6 moved, then a 6 that can't be, ends the turn.
        val run = game(listOf(HOME, HOME, 45, 52)).roll(6).move(2)
        assertEquals(RED, run.current)
        assertEquals(GREEN, run.roll(6).current)
    }

    @Test
    fun `a pawn needs the exact roll home - a bigger one can't move it`() {
        val nearlyHome = game(listOf(53, Y, Y, Y))
        assertEquals(GREEN, nearlyHome.roll(4).current)
        assertEquals(GameEvent.NoMove(RED, 4), nearlyHome.roll(4).lastEvent)
        assertEquals(listOf(Move(pawn = 0, from = 53, to = HOME)), nearlyHome.roll(3).moves)
        // A pawn already home never moves; the one behind it still can.
        assertEquals(listOf(1), game(listOf(HOME, 20, Y, Y)).roll(5).moves.map { it.pawn })
    }

    @Test
    fun `landing on an opponent off a safe square sends it back to its yard - and both players' tallies count it`() {
        // Red is on square 20. Green's pawn 3 steps from its start (13) is on square 16, so a 4 lands it on 20.
        val before = game(listOf(20, Y, Y, Y), listOf(3, Y, Y, Y), current = GREEN).roll(4)
        assertEquals(listOf(Move(pawn = 0, from = 3, to = 7, captures = listOf(PawnRef(RED, 0)))), before.moves)
        val after = before.move(0)
        assertEquals(listOf(Y, Y, Y, Y), after.players[RED].pawns)
        assertEquals(listOf(7, Y, Y, Y), after.players[GREEN].pawns)
        assertEquals(1, after.players[GREEN].captures)
        assertEquals(1, after.players[RED].timesCaptured)
        assertEquals(listOf(emptyMap(), mapOf(RED to 1)), listOf(RED, GREEN).map { after.players[it].capturesOf })
    }

    @Test
    fun `every opponent pawn on the square goes back - two of one colour can stand off a safe square`() {
        val after = game(listOf(20, 20, 5, Y), listOf(3, Y, Y, Y), current = GREEN).roll(4).move(0)
        assertEquals(listOf(Y, Y, 5, Y), after.players[RED].pawns)
        assertEquals(2, after.players[GREEN].captures)
        assertEquals(2, after.players[RED].timesCaptured)
        assertEquals(mapOf(RED to 2), after.players[GREEN].capturesOf)
    }

    @Test
    fun `nobody is captured on a star or a start square - pawns of different colours share it`() {
        // Square 21 is the star 8 on from green's start. Red's 21st step lands there; green's pawn is on its 8th.
        val onStar = game(listOf(17, Y, Y, Y), listOf(8, Y, Y, Y)).roll(4)
        assertEquals(emptyList(), onStar.moves.single().captures)
        val shared = onStar.move(0)
        assertEquals(listOf(21, Y, Y, Y), shared.players[RED].pawns)
        assertEquals(listOf(8, Y, Y, Y), shared.players[GREEN].pawns)

        // Red waits on green's start square (13): green coming out of the yard onto it captures nothing.
        val onStart = game(listOf(13, Y, Y, Y), current = GREEN).roll(6).move(0)
        assertEquals(listOf(13, Y, Y, Y), onStart.players[RED].pawns)
        assertEquals(listOf(0, Y, Y, Y), onStart.players[GREEN].pawns)
    }

    @Test
    fun `a player's own pawns share a square - and any pawn passes over any other`() {
        val stacked = game(listOf(10, 14, Y, Y)).roll(4).move(0)
        assertEquals(listOf(14, 14, Y, Y), stacked.players[RED].pawns)
        // Red jumps green's pawn on square 18 (green's 5th step) to land on 20.
        val passed = game(listOf(16, Y, Y, Y), listOf(5, Y, Y, Y)).roll(4).move(0)
        assertEquals(listOf(20, Y, Y, Y), passed.players[RED].pawns)
        assertEquals(listOf(5, Y, Y, Y), passed.players[GREEN].pawns)
    }

    @Test
    fun `the home column is the owner's alone - a pawn there or home is never captured`() {
        // Red's home column starts at progress 51; green's 38th step (square 51, red's last track square) can't reach it.
        val board = GameRules().board
        assertEquals(51 % board.trackLength, board.trackSquare(GREEN, 38))
        val after = game(listOf(52, Y, Y, Y), listOf(34, Y, Y, Y), current = GREEN).roll(4).move(0)
        assertEquals(listOf(52, Y, Y, Y), after.players[RED].pawns)
    }

    @Test
    fun `bringing the last pawn home places the player - play skips them - and a 6 doesn't roll again for them`() {
        val done = game(listOf(HOME, HOME, HOME, 50)).roll(6).move(3)
        assertEquals(listOf(RED), done.finishingOrder)
        assertEquals(GameEvent.Moved(RED, 6, Move(pawn = 3, from = 50, to = HOME), finished = true), done.lastEvent)
        assertEquals(GREEN, done.current)
        assertEquals(RED, done.winner)

        // Blue's turn passes over red, who is home, to green.
        assertEquals(GREEN, done.copy(current = BLUE).roll(1).current)
    }

    @Test
    fun `the others play on for each place - the last left takes last place and the game ends`() {
        val all = listOf(HOME, HOME, HOME, HOME)
        val endgame = game(all, listOf(HOME, HOME, HOME, 55), listOf(10, Y, Y, Y), listOf(HOME, HOME, HOME, 54), current = GREEN)
            .copy(finishingOrder = listOf(RED))
        val second = endgame.roll(1).move(3)
        assertEquals(listOf(RED, GREEN), second.finishingOrder)
        assertEquals(YELLOW, second.current)
        val over = second.roll(3).move(0).roll(2).move(3)
        assertEquals(listOf(RED, GREEN, BLUE, YELLOW), over.finishingOrder)
        assertEquals(TurnPhase.Finished, over.phase)
        assertTrue(over.isFinished)
        assertFailsWith<IllegalStateException> { over.roll(1) }
    }

    @Test
    fun `stopped at the first winner the rest are placed as they stand - furthest along first - a tie to who plays sooner`() {
        // Green and blue have come 22 steps each, yellow 21; red brings their last pawn home with a 1.
        val pawns = arrayOf(listOf(HOME, HOME, HOME, 55), listOf(10, 10, Y, Y), listOf(20, Y, Y, Y), listOf(10, 10, Y, Y))
        val stopped = game(*pawns, rules = GameRules(stopAtFirstWinner = true)).roll(1).move(3)
        assertEquals(TurnPhase.Finished, stopped.phase)
        assertEquals(listOf(RED, GREEN, BLUE, YELLOW), stopped.finishingOrder)
        assertEquals(GameEvent.Moved(RED, 1, Move(pawn = 3, from = 55, to = HOME), finished = true), stopped.lastEvent)
        // Without the setting, the others race on for second.
        val raced = game(*pawns).roll(1).move(3)
        assertEquals(TurnPhase.AwaitingRoll, raced.phase)
        assertEquals(listOf(RED), raced.finishingOrder)
        assertEquals(GREEN, raced.current)
    }

    @Test
    fun `a turn is counted once however many times the player rolls again`() {
        val state = game(listOf(0, Y, Y, Y)).roll(6).move(0).roll(6).move(0).roll(3).move(0)
        assertEquals(1, state.players[RED].turns)
        assertEquals(1, state.roll(2).players[GREEN].turns)
    }

    @Test
    fun `a capture gives another roll - one however many pawns it takes - and the turn is still counted once`() {
        // Green's 4 lands on square 20, where two of red's pawns stand.
        val captured = game(listOf(20, 20, Y, Y), listOf(3, Y, Y, Y), current = GREEN).roll(4).move(0)
        assertEquals(GREEN, captured.current)
        assertEquals(TurnPhase.AwaitingRoll, captured.phase)
        assertTrue(captured.rollingAgain)
        assertEquals(GameEvent.Moved(GREEN, 4, Move(0, 3, 7, listOf(PawnRef(RED, 0), PawnRef(RED, 1))), finished = false, rollsAgain = true), captured.lastEvent)
        // One roll, not two: the next roll's move passes the turn on.
        val next = captured.roll(2).move(0)
        assertEquals(YELLOW, next.current)
        assertEquals(1, next.players[GREEN].turns)

        // Off in the rules, a capture passes the turn like any other move.
        val plain = game(listOf(20, Y, Y, Y), listOf(3, Y, Y, Y), current = GREEN, rules = GameRules(rollAgainOnCapture = false)).roll(4).move(0)
        assertEquals(YELLOW, plain.current)
        assertEquals(false, (plain.lastEvent as GameEvent.Moved).rollsAgain)
    }

    @Test
    fun `bringing a pawn home gives another roll - but not the last pawn, which finishes the player`() {
        val home = game(listOf(53, 10, Y, Y)).roll(3).move(0)
        assertEquals(RED, home.current)
        assertTrue(home.rollingAgain)
        assertEquals(true, (home.lastEvent as GameEvent.Moved).rollsAgain)

        val last = game(listOf(HOME, HOME, HOME, 53)).roll(3).move(3)
        assertEquals(GREEN, last.current)
        assertEquals(false, last.rollingAgain)
        assertEquals(false, (last.lastEvent as GameEvent.Moved).rollsAgain)

        // Off in the rules, a pawn home passes the turn.
        assertEquals(GREEN, game(listOf(53, 10, Y, Y), rules = GameRules(rollAgainOnHome = false)).roll(3).move(0).current)
    }

    @Test
    fun `a roll earned by a capture keeps the run of 6s going - a 6 that captures is one roll again, not two`() {
        val board = GameRules().board
        // A 6 that captures (green's pawn on square 6) earns one roll again, counted in the run of 6s.
        val greenOn6 = 6 - board.startSquare(GREEN) + board.trackLength
        val sixCaptures = game(listOf(0, Y, Y, Y), listOf(greenOn6, Y, Y, Y)).roll(6).move(0)
        assertEquals(1, sixCaptures.players[RED].captures)
        assertEquals(1, sixCaptures.rollAgainRun)
        assertEquals(GREEN, sixCaptures.roll(6).move(0).roll(6).current, "the third 6 still ends the turn")
        // A 6, then a capture with a 4, then a 6: the 4 broke the run, so this 6 is the first of a new one.
        val greenOn10 = 10 - board.startSquare(GREEN) + board.trackLength
        val broken = game(listOf(0, Y, Y, Y), listOf(greenOn10, Y, Y, Y)).roll(6).move(0).roll(4).move(0)
        assertEquals(listOf(10, Y, Y, Y), broken.players[RED].pawns)
        assertEquals(RED, broken.current)
        assertEquals(0, broken.rollAgainRun)
        assertEquals(1, broken.roll(6).move(0).rollAgainRun)
        assertEquals(1, broken.players[RED].turns)
    }

    @Test
    fun `an action the rules don't allow throws`() {
        assertFailsWith<IllegalStateException> { game().move(0) }
        val rolled = game(listOf(10, Y, Y, Y)).roll(3)
        assertFailsWith<IllegalStateException> { rolled.roll(3) }
        assertFailsWith<IllegalArgumentException> { rolled.move(1) }
        assertFailsWith<IllegalArgumentException> { game().roll(7) }
        assertFailsWith<IllegalArgumentException> { game().roll(0) }
    }

    @Test
    fun `a move with no choice is made for the player - pawns on one square are one choice - a pawn that can't move is none`() {
        assertEquals(Move(pawn = 0, from = 10, to = 13), LudoEngine.automaticMove(game(listOf(10, Y, Y, Y)).roll(3)))
        assertEquals(Move(pawn = 1, from = 10, to = 13), LudoEngine.automaticMove(game(listOf(HOME, 10, 10, Y)).roll(3)))
        // Too far to move 3 more: only the other pawn can.
        assertEquals(Move(pawn = 1, from = 10, to = 13), LudoEngine.automaticMove(game(listOf(54, 10, Y, Y)).roll(3)))
        assertEquals(Move(pawn = 0, from = 50, to = 53), LudoEngine.automaticMove(game(listOf(50, HOME, HOME, Y)).roll(3)))

        assertNull(LudoEngine.automaticMove(game(listOf(10, 3, Y, Y)).roll(3)), "two pawns to pick from")
        assertNull(LudoEngine.automaticMove(game(listOf(10, Y, Y, Y)).roll(6)), "out of the yard, or on")
        assertNull(LudoEngine.automaticMove(game(listOf(10, Y, Y, Y))), "waiting for a roll")
    }

    @Test
    fun `the player makes a move out of the yard with no pawn on the board - and the one bringing their last pawn home`() {
        assertNull(LudoEngine.automaticMove(game().roll(6)))
        assertNull(LudoEngine.automaticMove(game(listOf(HOME, HOME, HOME, Y)).roll(6)))
        assertNull(LudoEngine.automaticMove(game(listOf(HOME, HOME, HOME, 53)).roll(3)))
        // Home, but not the last: one is left in the yard.
        assertEquals(Move(pawn = 2, from = 53, to = HOME), LudoEngine.automaticMove(game(listOf(HOME, HOME, 53, Y)).roll(3)))
        // Out of the yard, with a pawn on the board.
        assertEquals(Move(pawn = 3, from = Y, to = 0), LudoEngine.automaticMove(game(listOf(HOME, HOME, 54, Y)).roll(6)))
    }

    @Test
    fun `a new game has every pawn in its yard and the chosen player to roll`() {
        val new = GameState.new(GameMode.CLASSIC, playerCount = 4, first = YELLOW)
        assertEquals(YELLOW, new.current)
        assertEquals(TurnPhase.AwaitingRoll, new.phase)
        assertEquals(List(4) { List(4) { Y } }, new.players.map { it.pawns })
        assertEquals(listOf(0, 1, 2, 3), new.players.map { it.seat })
        assertFailsWith<IllegalArgumentException> { GameState.new(GameMode.CLASSIC, playerCount = 5) }
    }

    @Test
    fun `two players sit opposite - and a game can seat them where it says - in clockwise order`() {
        assertEquals(listOf(0, 2), GameState.new(GameMode.CLASSIC, playerCount = 2).players.map { it.seat })
        assertEquals(listOf(0, 1), GameState.new(GameMode.CLASSIC, playerCount = 2, seats = listOf(0, 1)).players.map { it.seat })
        assertFailsWith<IllegalArgumentException> { GameState.new(GameMode.CLASSIC, playerCount = 2, seats = listOf(2, 0)) }
        assertFailsWith<IllegalArgumentException> { GameState.new(GameMode.CLASSIC, playerCount = 2, seats = listOf(0, 4)) }
    }

    @Test
    fun `on an eight-seat board a pawn captures the seat after's - and play passes clockwise round all eight`() {
        val rules = GameMode.CLASSIC.rules.forPlayers(8)
        val new = GameState.new(GameMode.CLASSIC, playerCount = 8, rules = rules)
        assertEquals(104, new.board.trackLength)
        // Player 1's pawn 2 squares past its start (square 15); player 0's pawn 4 short of it (square 11).
        val state = new.copy(players = new.players.mapIndexed { i, p -> if (i == 0) p.copy(pawns = listOf(11, Y, Y, Y)) else if (i == 1) p.copy(pawns = listOf(2, Y, Y, Y)) else p })
        val moved = state.roll(4).move(0)
        assertEquals(listOf(Y, Y, Y, Y), moved.players[1].pawns)
        // The capture rolls again; then a 1 that moves passes play to player 1.
        assertEquals(RED, moved.current)
        assertEquals(1, moved.roll(1).move(0).current)
    }

    @Test
    fun `the die rolls every face from 1 to 6 and nothing else`() {
        val random = kotlin.random.Random(7)
        val faces = (1..600).map { (LudoEngine.roll(game(), random).lastEvent as GameEvent).let { event -> roll(event) } }.toSet()
        assertEquals((1..6).toSet(), faces)
    }

    private fun roll(event: GameEvent): Int = when (event) {
        is GameEvent.Rolled -> event.roll
        is GameEvent.NoMove -> event.roll
        is GameEvent.RollAgainLimit -> event.roll
        is GameEvent.Moved -> event.roll
    }
}

