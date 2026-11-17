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

    /**
     * Every legal move for the current player's [roll], in pawn order: their own pawns, or once every one is home in a
     * team game that rolls for partners, each partner's still racing, partner by partner.
     */
    fun legalMoves(state: GameState, roll: Int): List<Move> {
        val board = state.board
        val me = state.current
        val owners = if (state.players[me].isFinished(board) && state.rules.rollForPartners) {
            state.partnersOf(me).filter { !state.players[it].isFinished(board) }
        } else {
            listOf(me)
        }
        return owners.flatMap { owner ->
            state.players[owner].pawns.mapIndexedNotNull { pawn, from ->
                val to = when {
                    from == PawnPosition.YARD_PROGRESS -> if (roll in state.rules.yardExitRolls) 0 else null
                    from == board.homeProgress -> null
                    // Exact roll home: a pawn can't overshoot it.
                    from + roll > board.homeProgress -> null
                    else -> from + roll
                }
                to?.let { Move(pawn = pawn, from = from, to = it, captures = capturesAt(state, owner, it), owner = owner.takeIf { it != me }) }
            }
        }
    }

    /**
     * The current player moves pawn [pawn] (of [owner], a partner they roll for, or their own for null) by the roll
     * awaiting a move, capturing any opponent it lands on. They roll again after a roll-again roll, a capture, or a pawn
     * brought home (as the rules give), unless that move ended their part in the game; otherwise play passes on. A run of
     * roll-again rolls carries on through a roll earned another way.
     *
     * A player with every pawn home is placed then, or in a team game with their team once every partner's are too.
     */
    fun move(state: GameState, pawn: Int, owner: Int? = null): GameState {
        val phase = state.phase
        check(phase is TurnPhase.AwaitingMove) { "Not waiting for a move: $phase" }
        val move = requireNotNull(phase.moves.firstOrNull { it.pawn == pawn && it.owner == owner }) { "Pawn $pawn has no legal move for ${phase.roll}" }
        val player = state.current
        val whose = move.ownerIn(state)
        var players = state.players
            .replace(whose) { side -> side.copy(pawns = side.pawns.replace(pawn) { move.to }) }
            .replace(player) { mover -> mover.copy(captures = mover.captures + move.captures.size) }
        for (captured in move.captures) {
            players = players.replace(captured.player) { victim ->
                victim.copy(
                    pawns = victim.pawns.replace(captured.pawn) { PawnPosition.YARD_PROGRESS },
                    timesCaptured = victim.timesCaptured + 1,
                )
            }
        }
        val board = state.board
        val rules = state.rules
        val finished = players[whose].isFinished(board)
        var next = state.copy(players = players, phase = TurnPhase.AwaitingRoll)
        // A team is placed once all of it is home, its players together; a player alone is a team of one.
        val team = rules.teamOf(whose)
        val teamHome = finished && players.indices.filter { rules.teamOf(it) == team }.all { players[it].isFinished(board) }
        if (teamHome) next = next.copy(finishingOrder = next.finishingOrder + players.indices.filter { rules.teamOf(it) == team && it !in next.finishingOrder })
        val unfinishedTeams = next.players.indices.filter { it !in next.finishingOrder }.map(rules::teamOf).distinct()
        if (unfinishedTeams.size <= 1 || (teamHome && rules.stopAtFirstWinner)) {
            // The last team left takes last place; stopped at the first winner, the rest are placed as they stand.
            return next.copy(
                finishingOrder = next.finishingOrder + placedAsTheyStand(next, next.players.indices.filter { it !in next.finishingOrder }),
                phase = TurnPhase.Finished,
                rollAgainRun = 0,
                rollingAgain = false,
                lastEvent = GameEvent.Moved(player, phase.roll, move, finished),
            )
        }
        // The mover plays on unless they're placed, or home with no partner's pawns to roll for.
        val playsOn = player !in next.finishingOrder && (!players[player].isFinished(board) || (rules.teams != null && rules.rollForPartners))
        val rollsAgain = playsOn && (
            phase.roll in rules.rollAgainRolls ||
                (rules.rollAgainOnCapture && move.captures.isNotEmpty()) ||
                (rules.rollAgainOnHome && move.to == board.homeProgress)
            )
        next = if (rollsAgain) next.copy(rollingAgain = true) else nextPlayer(next)
        return next.copy(lastEvent = GameEvent.Moved(player, phase.roll, move, finished, rollsAgain))
    }

    /** The pawns a pawn of [owner] would capture on landing at progress [to]: opponents', and partners' if the rules say. */
    private fun capturesAt(state: GameState, owner: Int, to: Int): List<PawnRef> {
        val board = state.board
        // Only the shared track has opponents on it, and its safe squares protect them.
        if (to !in 0..<board.trackSteps) return emptyList()
        val square = board.trackSquare(state.players[owner].seat, to)
        if (board.isSafe(square)) return emptyList()
        return state.players.flatMapIndexed { index, other ->
            if (index == owner || (state.rules.arePartners(owner, index) && !state.rules.partnerCaptures)) {
                emptyList()
            } else {
                other.pawns.indices
                    .filter { other.pawns[it] in 0..<board.trackSteps && board.trackSquare(other.seat, other.pawns[it]) == square }
                    .map { PawnRef(index, it) }
            }
        }
    }

    /**
     * [unfinished] in the order they're placed when the game ends with them still racing, team by team: furthest along
     * first (each pawn's steps from its yard added up, a team's together), and a tie by who plays sooner after the
     * current player.
     */
    private fun placedAsTheyStand(state: GameState, unfinished: List<Int>): List<Int> {
        val count = state.players.size
        val rules = state.rules
        fun progress(player: Int) = state.players[player].pawns.sumOf { it - PawnPosition.YARD_PROGRESS }
        fun after(player: Int) = (player - state.current + count) % count
        return unfinished.groupBy(rules::teamOf).values
            .sortedWith(compareByDescending<List<Int>> { team -> team.sumOf(::progress) }.thenBy { team -> team.minOf(::after) })
            .flatMap { team -> team.sortedBy(::after) }
    }

    /**
     * Play passes to the next player still racing, who awaits a roll: not one placed, nor one home in a team game that
     * doesn't roll for partners.
     */
    private fun nextPlayer(state: GameState): GameState {
        val skipsHome = !(state.rules.teams != null && state.rules.rollForPartners)
        var next = state.current
        do {
            next = (next + 1) % state.players.size
        } while (next in state.finishingOrder || (skipsHome && state.players[next].isFinished(state.board)))
        return state.copy(current = next, phase = TurnPhase.AwaitingRoll, rollAgainRun = 0, rollingAgain = false)
    }

    private inline fun <T> List<T>.replace(index: Int, change: (T) -> T): List<T> =
        mapIndexed { i, item -> if (i == index) change(item) else item }
}
