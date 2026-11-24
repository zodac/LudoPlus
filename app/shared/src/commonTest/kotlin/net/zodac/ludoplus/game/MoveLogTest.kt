package net.zodac.ludoplus.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PowerUp
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
    fun `a Swap's landing and its swap are a line each - and keeping its place is none`() {
        val new = GameState.new(GameMode.CHAOS, 4)
        val pawns = listOf(listOf(1, -1, -1, -1), listOf(9, -1, -1, -1))
        val start = new.copy(players = new.players.mapIndexed { i, it -> pawns.getOrNull(i)?.let { at -> it.copy(pawns = at) } ?: it }, powerUps = mapOf(3 to PowerUp.SWAP))
        val rolled = LudoEngine.applyRoll(start, 2)
        val landed = LudoEngine.move(rolled, 0)
        val swapped = LudoEngine.move(landed, 0, swap = PawnRef(1, 0))
        assertEquals(listOf(landed.lastEvent, swapped.lastEvent), logOf(listOf(start, rolled, landed, swapped)).map { it.event })
        val kept = LudoEngine.move(landed, 0, swap = PawnRef(0, 0))
        assertEquals(listOf(landed.lastEvent), logOf(listOf(start, rolled, landed, kept)).map { it.event })
    }

    @Test
    fun `every player still in has their moves in each turn - in the same order round the table`() {
        for ((players, first) in listOf(2 to 1, 4 to 0, 4 to 2, 8 to 5)) {
            val log = logOf(play(GameState.new(GameMode.CLASSIC, players, first = first, rules = GameMode.CLASSIC.rules.forPlayers(players)), Random(7)))
            val turns = log.map { it.turn!! }
            assertEquals(turns.sorted(), turns, "a turn's entries are together")
            assertEquals((1..turns.last()).toList(), turns.distinct())
            // Until someone is home, all of them, from who went first round.
            val order = List(players) { (first + it) % players }
            val allIn = log.takeWhile { (it.event as? GameEvent.Moved)?.finished != true }.groupBy { it.turn }.values.toList().dropLast(1)
            assertTrue(allIn.size > 5)
            for (round in allIn) assertEquals(order, round.map { it.event.player }.distinct())
        }
    }

    @Test
    fun `a roll again's moves are the same turn`() {
        val log = logOf(play(GameState.new(GameMode.CLASSIC, 4), Random(3)))
        val again = log.zipWithNext().filter { (a, b) -> a.event.player == b.event.player }
        assertTrue(again.isNotEmpty())
        for ((one, next) in again) assertEquals(one.turn, next.turn)
    }

    @Test
    fun `entries saved without a turn are counted back from the game`() {
        val teams = GameMode.CLASSIC.rules.copy(teams = TeamMode.TWO_TEAMS.teamsFor(4))
        for (start in listOf(GameState.new(GameMode.CLASSIC, 4), GameState.new(GameMode.CLASSIC, 4, rules = teams))) {
            val states = play(start, Random(11))
            for (length in listOf(1, 2, 30, 31, 32, 33, 34, 35, 200, states.size)) {
                val log = logOf(states.take(length))
                val state = states[length - 1]
                assertEquals(log, MoveLog.withTurns(log.map { it.copy(turn = null) }, state), "$length states")
                // A log that doesn't go back to the game's start, and one that kept turns only lately.
                assertEquals(log.drop(9), MoveLog.withTurns(log.drop(9).map { it.copy(turn = null) }, state), "$length states, less the first entries")
                assertEquals(log, MoveLog.withTurns(log.mapIndexed { i, entry -> if (i < 9) entry.copy(turn = null) else entry }, state), "$length states, the first entries' turns not kept")
            }
        }
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
