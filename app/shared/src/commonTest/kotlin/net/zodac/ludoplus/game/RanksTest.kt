package net.zodac.ludoplus.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PawnPosition

/** Where each player stands while a game is played ([GameState.ranks]). */
class RanksTest {

    private fun survival(vararg left: Int, teams: List<Int>? = null, knockedOut: List<Int> = emptyList()): GameState {
        val rules = GameMode.SURVIVAL.rules.forPlayers(left.size).copy(teams = teams)
        val new = GameState.new(GameMode.SURVIVAL, left.size, rules = rules)
        return new.copy(
            players = new.players.mapIndexed { i, it -> it.copy(pawns = List(6) { pawn -> if (pawn < left[i]) pawn else PawnPosition.OUT_PROGRESS }) },
            knockedOut = knockedOut,
        )
    }

    private fun classic(vararg pawns: List<Int>, teams: List<Int>? = null, finished: List<Int> = emptyList()): GameState {
        val new = GameState.new(GameMode.CLASSIC, pawns.size, rules = GameMode.CLASSIC.rules.copy(teams = teams))
        return new.copy(players = new.players.mapIndexed { i, it -> it.copy(pawns = pawns[i]) }, finishingOrder = finished)
    }

    private val home = GameMode.CLASSIC.rules.board.homeProgress
    private val yard = PawnPosition.YARD_PROGRESS

    @Test
    fun `nobody has a rank while everyone is level`() {
        GameMode.entries.forEach { assertNull(GameState.new(it, 4).ranks(), "$it") }
        assertNull(survival(3, 3, 3).ranks())
    }

    @Test
    fun `in Survival the most pawns left is first - a tie shares a rank and the next is left out`() {
        // Where the pawns stand doesn't come into it.
        assertEquals(listOf(3, 5, 1, 1, 4), survival(3, 1, 4, 4, 2).ranks())
        assertEquals(listOf(3, 1, 1, 4, 4), survival(3, 4, 4, 1, 1).ranks())
    }

    @Test
    fun `a player knocked out of Survival keeps their place - the rest are ranked above it`() {
        // Player 2 was out first, then player 4.
        assertEquals(listOf(2, 4, 1, 3), survival(1, 0, 4, 0, knockedOut = listOf(1, 3)).ranks())
        // All level that are left, but not with those who are out.
        assertEquals(listOf(1, 4, 1, 1), survival(2, 0, 2, 2, knockedOut = listOf(1)).ranks())
    }

    @Test
    fun `in a race the furthest along is first - by the steps of every pawn together`() {
        // Steps from the yard: 23, 22, 22, and 0.
        val state = classic(listOf(10, 11, yard, yard), listOf(21, yard, yard, yard), listOf(9, 11, yard, yard), listOf(yard, yard, yard, yard))
        assertEquals(listOf(23, 22, 22, 0), state.players.indices.map(state::stepsOf))
        assertEquals(listOf(1, 2, 2, 4), state.ranks())
    }

    @Test
    fun `a player home keeps their place - the rest are ranked after it`() {
        val state = classic(listOf(5, yard, yard, yard), List(4) { home }, listOf(40, 3, yard, yard), List(4) { home }, finished = listOf(3, 1))
        assertEquals(listOf(4, 2, 3, 1), state.ranks())
    }

    @Test
    fun `a team's players count together and share its rank`() {
        val teams = listOf(0, 1, 0, 1)
        // Team 1 has 7 pawns left to team 2's 6, though player 2 has the most of anyone.
        assertEquals(listOf(1, 2, 1, 2), survival(4, 5, 3, 1, teams = teams).ranks())
        val race = classic(listOf(3, yard, yard, yard), listOf(9, yard, yard, yard), listOf(20, yard, yard, yard), listOf(9, yard, yard, yard), teams = teams)
        assertEquals(listOf(1, 2, 1, 2), race.ranks())
    }
}
