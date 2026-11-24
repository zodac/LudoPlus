package net.zodac.ludoplus.game

/**
 * One line of a game's move log: an [event] that changed the game (a move, a roll with no move, or a turn ended by a run of
 * roll-again rolls), when it happened ([atEpochMillis], on the phone's clock), and for a move that brought its owner's
 * last pawn home, the [place] they had then (null while their team wasn't yet placed). [haunted] are the pawns a ghost caught as the turn
 * ended, and [vanished] how many squares were lost then. [turn] is which of its player's turns it was in, counted from 1, and so the
 * round of the table's turns (null where it isn't known).
 */
data class LogEntry(val event: GameEvent, val atEpochMillis: Long = 0, val place: Int? = null, val haunted: List<PawnRef> = emptyList(), val vanished: Int = 0, val turn: Int? = null)

/** The game screen's log of every move in a game, built up one state at a time. */
object MoveLog {

    /**
     * [log], the log up to [before], carried on to [after]: empty at a game's start (no event yet), as it was where
     * [after]'s event is [before]'s, only a roll waiting for its move, or the choice not to swap, and otherwise with [after]'s event added, as
     * happening [at] (epoch millis).
     */
    fun next(log: List<LogEntry>, before: GameState?, after: GameState, at: Long): List<LogEntry> {
        val event = after.lastEvent ?: return emptyList()
        if (event is GameEvent.Rolled || event == before?.lastEvent || (event is GameEvent.Moved && event.keptPlace)) return log
        val place = (event as? GameEvent.Moved)?.takeIf { it.finished }?.let { after.placeOf(it.move.owner ?: it.player) }
        return log + LogEntry(event, at, place, after.ghostMove?.caught.orEmpty(), after.justLost.size, after.players[event.player].turns)
    }

    /**
     * [log] with a turn for each entry that has none, counted back from [state], the game as the log leaves it: each player's
     * turns then, one fewer for each of their turns the log goes back over. For a game saved before entries kept their own.
     */
    fun withTurns(log: List<LogEntry>, state: GameState): List<LogEntry> {
        if (log.all { it.turn != null }) return log
        val turns = state.players.map { it.turns }.toIntArray()
        // A turn begun by a roll that is still waiting for its move has no entry yet.
        if (state.phase is TurnPhase.AwaitingMove && log.last().event.player != state.current) turns[state.current]--
        return log.indices.reversed().map { index ->
            val entry = log[index]
            val player = entry.event.player
            val turn = entry.turn ?: turns[player]
            // The same player's entries running on are one turn.
            if (index > 0 && log[index - 1].event.player != player) turns[player] = turn - 1
            entry.copy(turn = turn.takeIf { it > 0 })
        }.reversed()
    }
}
