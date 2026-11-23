package net.zodac.ludoplus.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.TeamMode

class MoveLogTest {

    /** Every state of a random game of [start], from its first, played with [random]. */
    private fun play(start: GameState, random: Random): List<GameState> = generateSequence(start) { state ->
        when (val phase = state.phase) {
            TurnPhase.Finished -> null
            is TurnPhase.AwaitingMove -> LudoEngine.move(state, phase.moves.random(random))
            TurnPhase.AwaitingRoll -> LudoEngine.roll(state, random)
        }
    }.toList()

    /** The log of [states], each state's event happening a second after the last's. */
    private fun logOf(states: List<GameState>): List<LogEntry> =
        states.withIndex().zipWithNext().fold(MoveLog.next(emptyList(), null, states.first(), 0)) { log, (before, after) ->
            MoveLog.next(log, before.value, after.value, after.index * 1_000L)
        }

    @Test
    fun `a game's log is every event but a roll waiting for its move - in order - each once`() {
        val states = play(GameState.new(GameMode.CLASSIC, 4), Random(3))
        val log = logOf(states)
        val events = states.mapNotNull { it.lastEvent }.filter { it !is GameEvent.Rolled }
        assertEquals(events, log.map { it.event })
        // Each at the time it happened: the state that brought it.
        assertEquals(states.indices.filter { i -> states[i].lastEvent.let { it != null && it !is GameEvent.Rolled } }.map { it * 1_000L }, log.map { it.atEpochMillis })
        assertTrue(log.any { it.event is GameEvent.NoMove } && log.any { it.event is GameEvent.Moved })
    }

    @Test
    fun `a state seen again adds nothing - and a game back at its start has an empty log`() {
        val states = play(GameState.new(GameMode.CLASSIC, 2), Random(5)).take(40)
        val log = logOf(states)
        assertEquals(log, MoveLog.next(log, states.last(), states.last(), 99))
        assertEquals(emptyList(), MoveLog.next(log, states.last(), states.first(), 99))
    }

    @Test
    fun `a move bringing a last pawn home keeps the place it had then - none for a player whose team wasn't yet placed`() {
        val rules = GameMode.CLASSIC.rules.copy(teams = TeamMode.TWO_TEAMS.teamsFor(4))
        val states = play(GameState.new(GameMode.CLASSIC, 4, rules = rules), Random(11))
        val finished = logOf(states).filter { (it.event as? GameEvent.Moved)?.finished == true }
        val last = states.last()
        // Each team's first player home waits for a place; its last takes the team's.
        assertTrue(finished.any { it.place == null } && finished.any { it.place == 1 })
        finished.filter { it.place != null }.forEach { entry ->
            val event = entry.event as GameEvent.Moved
            assertEquals(last.placeOf(event.move.owner ?: event.player), entry.place)
        }
    }
}
