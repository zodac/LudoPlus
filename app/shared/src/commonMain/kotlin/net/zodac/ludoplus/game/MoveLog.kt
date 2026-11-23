package net.zodac.ludoplus.game

/**
 * One line of a game's move log: an [event] that changed the game (a move, a roll with no move, or a turn ended by a run of
 * roll-again rolls), when it happened ([atEpochMillis], on the phone's clock), and for a move that brought its owner's
 * last pawn home, the [place] they had then (null while their team wasn't yet placed). [haunted] are the pawns a ghost caught as the turn ended.
 */
data class LogEntry(val event: GameEvent, val atEpochMillis: Long = 0, val place: Int? = null, val haunted: List<PawnRef> = emptyList())

/** The game screen's log of every move in a game, built up one state at a time. */
object MoveLog {

    /**
     * [log], the log up to [before], carried on to [after]: empty at a game's start (no event yet), as it was where
     * [after]'s event is [before]'s or only a roll waiting for its move, and otherwise with [after]'s event added, as
     * happening [at] (epoch millis).
     */
    fun next(log: List<LogEntry>, before: GameState?, after: GameState, at: Long): List<LogEntry> {
        val event = after.lastEvent ?: return emptyList()
        if (event is GameEvent.Rolled || event == before?.lastEvent) return log
        val place = (event as? GameEvent.Moved)?.takeIf { it.finished }?.let { after.placeOf(it.move.owner ?: it.player) }
        return log + LogEntry(event, at, place, after.ghostMove?.caught.orEmpty())
    }
}
