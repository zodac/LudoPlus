package net.zodac.ludoplus.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS

private const val RED = 0
private const val GREEN = 1

private const val Y = YARD_PROGRESS
private const val HOME = 56

/** A Two Dice game of four with each player's pawns at the progress given, red to roll. */
private fun game(vararg pawns: List<Int>): GameState {
    val all = List(4) { pawns.getOrElse(it) { listOf(Y, Y, Y, Y) } }
    val new = GameState.new(GameMode.TWO_DICE, playerCount = 4)
    return new.copy(players = new.players.mapIndexed { i, player -> player.copy(pawns = all[i]) })
}

private fun GameState.roll(a: Int, b: Int) = LudoEngine.applyDice(this, listOf(a, b))
private fun GameState.move(pawn: Int, die: Int) = LudoEngine.move(this, pawn, die = die)
private val GameState.waiting: TurnPhase.AwaitingMove get() = phase as TurnPhase.AwaitingMove

/** The Two Dice mode: each die moved on its own, a 6 on either brings a pawn out, and doubles roll again. */
class TwoDiceEngineTest {

    @Test
    fun `each die is moved on its own - in either order - by any pawn - then play passes`() {
        val rolled = game(listOf(10, 20, Y, Y)).roll(3, 5)
        assertEquals(listOf(3, 5), rolled.waiting.dice)
        assertEquals(GameEvent.Rolled(RED, 3, 5), rolled.lastEvent)
        assertEquals(
            listOf(Move(0, 10, 13, roll = 3), Move(1, 20, 23, roll = 3), Move(0, 10, 15, roll = 5), Move(1, 20, 25, roll = 5)),
            rolled.waiting.moves,
        )
        // The 5 first: the 3 is left, for either pawn, the one just moved included.
        val first = rolled.move(0, die = 5)
        assertEquals(RED, first.current)
        assertEquals(listOf(3), first.waiting.dice)
        assertEquals(listOf(3, 5), first.waiting.rolled)
        assertEquals(listOf(Move(0, 15, 18, roll = 3), Move(1, 20, 23, roll = 3)), first.waiting.moves)
        val second = first.move(0, die = 3)
        assertEquals(listOf(18, 20, Y, Y), second.players[RED].pawns)
        assertEquals(GREEN, second.current)
        assertEquals(1, second.players[RED].turns)
    }

    @Test
    fun `each die counts as a roll of its face - a double as two`() {
        assertEquals(mapOf(3 to 1, 5 to 1), game(listOf(10, 20, Y, Y)).roll(3, 5).players[RED].rolls)
        assertEquals(mapOf(4 to 2), game().roll(4, 4).players[RED].rolls)
    }

    @Test
    fun `a 6 on either die brings a pawn out with that die - the other then moves any pawn - a 6 alone doesn't roll again`() {
        val rolled = game().roll(2, 6)
        assertEquals((0..3).map { Move(it, Y, 0, roll = 6) }, rolled.waiting.moves)
        val out = rolled.move(1, die = 6)
        assertEquals(listOf(Move(1, 0, 2, roll = 2)), out.waiting.moves)
        val after = out.move(1, die = 2)
        assertEquals(listOf(Y, 2, Y, Y), after.players[RED].pawns)
        assertEquals(GREEN, after.current)
    }

    @Test
    fun `no move for either die passes the turn - and a die left with no move ends the roll`() {
        val none = game().roll(2, 3)
        assertEquals(GameEvent.NoMove(RED, 2, second = 3), none.lastEvent)
        assertEquals(GREEN, none.current)
        // The 2 brings the last pawn on the board home; the 5 has no move left, and home earns a roll.
        val home = game(listOf(HOME - 2, Y, Y, Y)).roll(5, 2)
        assertEquals(listOf(Move(0, HOME - 2, HOME, roll = 2)), home.waiting.moves)
        val after = home.move(0, die = 2)
        assertEquals(RED, after.current)
        assertTrue(after.rollingAgain)
        assertEquals(TurnPhase.AwaitingRoll, after.phase)
    }

    @Test
    fun `doubles roll again once both dice are moved - their moves offered once - and three in a row end the turn`() {
        val doubles = game(listOf(10, Y, Y, Y)).roll(4, 4)
        assertEquals(listOf(Move(0, 10, 14, roll = 4)), doubles.waiting.moves)
        val first = doubles.move(0, die = 4)
        assertEquals(listOf(4), first.waiting.dice)
        val second = first.move(0, die = 4)
        assertEquals(RED, second.current)
        assertTrue(second.rollingAgain)
        assertEquals(1, second.rollAgainRun)
        val third = second.roll(1, 1).move(0, die = 1).move(0, die = 1)
        assertEquals(2, third.rollAgainRun)
        val limit = third.roll(5, 5)
        assertEquals(GameEvent.RollAgainLimit(RED, 5, 5), limit.lastEvent)
        assertEquals(GREEN, limit.current)
        assertEquals(listOf(20, Y, Y, Y), limit.players[RED].pawns)
    }

    @Test
    fun `a capture with the first die earns a roll once the second is moved`() {
        // Green's pawn 1 step on is on square 14, 4 ahead of red's.
        val captured = game(listOf(10, 30, Y, Y), listOf(1, Y, Y, Y)).roll(4, 2).move(0, die = 4)
        assertTrue(captured.waiting.earned)
        val after = captured.move(1, die = 2)
        assertEquals(RED, after.current)
        assertTrue(after.rollingAgain)
        assertFalse(game(listOf(10, 30, Y, Y)).roll(4, 2).move(0, die = 4).move(1, die = 2).rollingAgain)
    }

    @Test
    fun `with two dice each die is a choice - one pawn's move by one die left is made for the player - and a double's moves once`() {
        val rolled = game(listOf(10, Y, Y, Y)).roll(3, 5)
        assertNull(LudoEngine.automaticMove(rolled), "the 3 or the 5 first")
        assertEquals(Move(0, 13, 18, roll = 5), LudoEngine.automaticMove(rolled.move(0, die = 3)))
        assertEquals(Move(0, 10, 13, roll = 3), LudoEngine.automaticMove(game(listOf(10, Y, Y, Y)).roll(3, 3)))
    }

    @Test
    fun `a roll is both dice`() {
        assertFailsWith<IllegalArgumentException> { LudoEngine.applyRoll(game(), 6) }
        // A fair roll rolls both: whatever it shows, the second die is there.
        val event = LudoEngine.roll(game(), kotlin.random.Random(1)).lastEvent
        assertTrue(event is GameEvent.NoMove && event.second != null || event is GameEvent.Rolled && event.second != null, "$event")
    }
}
