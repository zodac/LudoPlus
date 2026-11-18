package net.zodac.ludoplus.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.GameRules
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS
import net.zodac.ludoplus.model.TeamMode

private const val Y = YARD_PROGRESS
private const val HOME = 56

/** Players 0 and 2 against 1 and 3 (red and yellow, green and blue), as two teams of 4 players sit. */
private val TWO_TEAMS = GameRules(teams = TeamMode.TWO_TEAMS.teamsFor(4))

/** A 4-player team game under [rules], each player's pawns at the progress given, [current] to roll. */
private fun game(vararg pawns: List<Int>, current: Int = 0, rules: GameRules = TWO_TEAMS): GameState {
    val new = GameState.new(GameMode.CLASSIC, playerCount = 4, first = current, rules = rules)
    return new.copy(players = new.players.mapIndexed { i, player -> player.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }) })
}

private fun GameState.roll(value: Int) = LudoEngine.applyRoll(this, value)
private val GameState.moves: List<Move> get() = (phase as TurnPhase.AwaitingMove).moves

class TeamEngineTest {

    @Test
    fun `two teams alternate seats - pairs sit opposite - and only an even count of four or more has teams`() {
        assertEquals(listOf(0, 1, 0, 1, 0, 1), TeamMode.TWO_TEAMS.teamsFor(6))
        assertEquals(listOf(0, 1, 2, 0, 1, 2), TeamMode.PAIRS.teamsFor(6))
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 2, 3), TeamMode.PAIRS.teamsFor(8))
        assertEquals(TeamMode.TWO_TEAMS.teamsFor(4), TeamMode.PAIRS.teamsFor(4))
        listOf(2, 3, 5, 7).forEach { assertNull(TeamMode.TWO_TEAMS.teamsFor(it), "$it players") }
        assertNull(TeamMode.OFF.teamsFor(4))
    }

    @Test
    fun `a pawn landing on a partner's shares the square - unless partners capture - and still captures an opponent there`() {
        // Red's 14th step and yellow's 40th are both square 14, off a safe square; green's pawn 1 is there too.
        val state = game(listOf(10, Y, Y, Y), listOf(1, Y, Y, Y), listOf(40, Y, Y, Y))
        val move = state.roll(4).moves.single()
        assertEquals(listOf(PawnRef(1, 0)), move.captures)
        val friendly = game(listOf(10, Y, Y, Y), listOf(1, Y, Y, Y), listOf(40, Y, Y, Y), rules = TWO_TEAMS.copy(partnerCaptures = true))
        assertEquals(listOf(PawnRef(1, 0), PawnRef(2, 0)), friendly.roll(4).moves.single().captures)
    }

    @Test
    fun `a player home rolls for a partner - moving their pawns - unless the rules say they sit out`() {
        val home = listOf(HOME, HOME, HOME, HOME)
        val state = game(home, listOf(1, Y, Y, Y), listOf(10, Y, Y, Y), listOf(1, Y, Y, Y))
        val rolled = state.roll(3)
        assertEquals(listOf(Move(pawn = 0, from = 10, to = 13, owner = 2)), rolled.moves)
        val moved = LudoEngine.move(rolled, pawn = 0, owner = 2)
        assertEquals(13, moved.players[2].pawns[0])
        assertEquals(0, moved.players[0].captures)
        // Not placed: yellow isn't home yet.
        assertTrue(moved.finishingOrder.isEmpty())

        // Sitting out: blue's move passes play straight to green, past red.
        val sitOut = game(home, listOf(1, Y, Y, Y), listOf(10, Y, Y, Y), listOf(1, Y, Y, Y), current = 3, rules = TWO_TEAMS.copy(rollForPartners = false))
        assertEquals(1, LudoEngine.move(sitOut.roll(2), 0).current)
    }

    @Test
    fun `a team is placed when its last pawn is home - its players sharing the place - and a 6 that ends it rolls no more`() {
        val home = listOf(HOME, HOME, HOME, HOME)
        val state = game(home, listOf(1, Y, Y, Y), listOf(HOME, HOME, HOME, HOME - 6), listOf(1, Y, Y, Y))
        val done = LudoEngine.move(state.roll(6), pawn = 3, owner = 2)
        // Two teams: the other is last, so the game ends.
        assertEquals(TurnPhase.Finished, done.phase)
        assertEquals(listOf(0, 2), done.finishingOrder.take(2))
        assertEquals(listOf(1, 2, 1, 2), (0..3).map { done.placeOf(it) })
        val event = done.lastEvent as GameEvent.Moved
        assertTrue(event.finished)
        assertFalse(event.rollsAgain, "no roll after the game ends")
    }

    @Test
    fun `with three teams of pairs the others play on for second and third`() {
        val rules = GameMode.CLASSIC.rules.forPlayers(6).copy(teams = TeamMode.PAIRS.teamsFor(6))
        val new = GameState.new(GameMode.CLASSIC, playerCount = 6, rules = rules)
        val end = rules.board.homeProgress
        // Players 0 and 3 are partners: 0 is home, 3 one step short.
        val state = new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = if (i == 0) List(4) { end } else if (i == 3) listOf(end, end, end, end - 1) else p.pawns) })
        val placed = LudoEngine.move(state.roll(1), pawn = 3, owner = 3)
        assertEquals(TurnPhase.AwaitingRoll, placed.phase)
        assertEquals(listOf(0, 3), placed.finishingOrder)
        assertEquals(1, placed.placeOf(3))
        assertNull(placed.placeOf(1))
        // Play passes on to player 1, past nobody: the placed team no longer plays.
        assertEquals(1, placed.current)
    }

    @Test
    fun `a game without teams places each player alone as before`() {
        val solo = game(listOf(HOME, HOME, HOME, HOME - 1), rules = GameRules())
        val placed = LudoEngine.move(solo.roll(1), 3)
        assertEquals(listOf(0), placed.finishingOrder)
        assertEquals(1, placed.placeOf(0))
        assertEquals(1, placed.current)
    }
}
