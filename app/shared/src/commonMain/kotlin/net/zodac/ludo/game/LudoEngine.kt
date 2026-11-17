package net.zodac.ludo.game

import kotlin.random.Random
import net.zodac.ludo.model.PawnPosition

/**
 * The rules of play (`.claude/IMPLEMENTATION.md`, "The classic rules"), as pure functions from one [GameState] to the
 * next. Every rule a mode or modifier can change is read from
 * [GameState.rules]. An action the rules don't allow throws.
 */
object LudoEngine {

    /** Rolls [state]'s die with [random], then plays the roll ([applyRoll]). */
    fun roll(state: GameState, random: Random): GameState = applyRoll(state, random.nextInt(1, state.rules.dieFaces + 1))

    /**
     * The current player rolls [roll]. A run of roll-again rolls that reaches the rules' limit ends the turn unmoved,
     * and so does a roll with no legal move, roll-again roll or not. Otherwise the game awaits one of the legal moves.
     */
    fun applyRoll(state: GameState, roll: Int): GameState {
        check(state.phase == TurnPhase.AwaitingRoll) { "Not waiting for a roll: ${state.phase}" }
        require(roll in 1..state.rules.dieFaces) { "A ${state.rules.dieFaces}-sided die can't roll $roll" }
        val rules = state.rules
        val player = state.current
        // The first roll of a turn starts it; a roll again continues it.
        val started = if (!state.rollingAgain) {
            state.copy(players = state.players.replace(player) { it.copy(turns = it.turns + 1) })
        } else {
            state
        }
        val rollsAgain = roll in rules.rollAgainRolls
        val run = if (rollsAgain) state.rollAgainRun + 1 else 0
        val limit = rules.rollAgainLimit
        if (rollsAgain && limit != null && run >= limit) {
            return nextPlayer(started).copy(lastEvent = GameEvent.RollAgainLimit(player, roll))
        }
        val moves = legalMoves(started, roll)
        if (moves.isEmpty()) return nextPlayer(started).copy(lastEvent = GameEvent.NoMove(player, roll))
        return started.copy(
            phase = TurnPhase.AwaitingMove(roll, moves),
            rollAgainRun = run,
            lastEvent = GameEvent.Rolled(player, roll),
        )
    }

    /** Every legal move for the current player's [roll], in pawn order. */
    fun legalMoves(state: GameState, roll: Int): List<Move> {
        val board = state.board
        val player = state.players[state.current]
        return player.pawns.mapIndexedNotNull { pawn, from ->
            val to = when {
                from == PawnPosition.YARD_PROGRESS -> if (roll in state.rules.yardExitRolls) 0 else null
                from == board.homeProgress -> null
                // Exact roll home: a pawn can't overshoot it.
                from + roll > board.homeProgress -> null
                else -> from + roll
            }
            to?.let { Move(pawn = pawn, from = from, to = it, captures = capturesAt(state, it)) }
        }
    }

    /**
     * The current player moves pawn [pawn] by the roll awaiting a move, capturing any opponent it lands on. They roll
     * again after a roll-again roll, a capture, or a pawn brought home (as the rules give), unless that move finished
     * them; otherwise play passes on. A run of roll-again rolls carries on through a roll earned another way.
     */
    fun move(state: GameState, pawn: Int): GameState {
        val phase = state.phase
        check(phase is TurnPhase.AwaitingMove) { "Not waiting for a move: $phase" }
        val move = requireNotNull(phase.moves.firstOrNull { it.pawn == pawn }) { "Pawn $pawn has no legal move for ${phase.roll}" }
        val player = state.current
        var players = state.players.replace(player) { mover ->
            mover.copy(
                pawns = mover.pawns.replace(pawn) { move.to },
                captures = mover.captures + move.captures.size,
            )
        }
        for (captured in move.captures) {
            players = players.replace(captured.player) { victim ->
                victim.copy(
                    pawns = victim.pawns.replace(captured.pawn) { PawnPosition.YARD_PROGRESS },
                    timesCaptured = victim.timesCaptured + 1,
                )
            }
        }
        val finished = players[player].isFinished(state.board)
        var next = state.copy(players = players, phase = TurnPhase.AwaitingRoll)
        if (finished) next = next.copy(finishingOrder = next.finishingOrder + player)
        val unfinished = next.players.indices.filter { it !in next.finishingOrder }
        if (unfinished.size <= 1 || (finished && state.rules.stopAtFirstWinner)) {
            // The last player left takes last place; stopped at the first winner, the rest are placed as they stand.
            return next.copy(
                finishingOrder = next.finishingOrder + placedAsTheyStand(next, unfinished),
                phase = TurnPhase.Finished,
                rollAgainRun = 0,
                rollingAgain = false,
                lastEvent = GameEvent.Moved(player, phase.roll, move, finished),
            )
        }
        val rules = state.rules
        val rollsAgain = !finished && (
            phase.roll in rules.rollAgainRolls ||
                (rules.rollAgainOnCapture && move.captures.isNotEmpty()) ||
                (rules.rollAgainOnHome && move.to == state.board.homeProgress)
            )
        next = if (rollsAgain) next.copy(rollingAgain = true) else nextPlayer(next)
        return next.copy(lastEvent = GameEvent.Moved(player, phase.roll, move, finished, rollsAgain))
    }

    /** The opponents' pawns a pawn of the current player would capture on landing at progress [to]. */
    private fun capturesAt(state: GameState, to: Int): List<PawnRef> {
        val board = state.board
        // Only the shared track has opponents on it, and its safe squares protect them.
        if (to !in 0..<board.trackSteps) return emptyList()
        val square = board.trackSquare(state.currentPlayer.seat, to)
        if (board.isSafe(square)) return emptyList()
        return state.players.flatMapIndexed { index, other ->
            if (index == state.current) {
                emptyList()
            } else {
                other.pawns.indices
                    .filter { other.pawns[it] in 0..<board.trackSteps && board.trackSquare(other.seat, other.pawns[it]) == square }
                    .map { PawnRef(index, it) }
            }
        }
    }

    /**
     * [unfinished] in the order they're placed when the game ends with them still racing: furthest along first (each
     * pawn's steps from its yard added up), and a tie by who plays sooner after the current player.
     */
    private fun placedAsTheyStand(state: GameState, unfinished: List<Int>): List<Int> {
        val count = state.players.size
        return unfinished.sortedWith(
            compareByDescending<Int> { player -> state.players[player].pawns.sumOf { it - PawnPosition.YARD_PROGRESS } }
                .thenBy { player -> (player - state.current + count) % count },
        )
    }

    /** Play passes to the next player still racing, who awaits a roll. */
    private fun nextPlayer(state: GameState): GameState {
        var next = state.current
        do {
            next = (next + 1) % state.players.size
        } while (next in state.finishingOrder)
        return state.copy(current = next, phase = TurnPhase.AwaitingRoll, rollAgainRun = 0, rollingAgain = false)
    }

    private inline fun <T> List<T>.replace(index: Int, change: (T) -> T): List<T> =
        mapIndexed { i, item -> if (i == index) change(item) else item }
}
