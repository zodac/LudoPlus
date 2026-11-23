package net.zodac.ludoplus.game

import kotlin.random.Random
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.model.throughPortal

/**
 * The rules of play (`.claude/IMPLEMENTATION.md`, "The classic rules"), as pure functions from one [GameState] to the
 * next. Every rule a mode or modifier can change is read from
 * [GameState.rules]. An action the rules don't allow throws.
 */
object LudoEngine {

    /** Rolls [state]'s dice with [random], then plays the roll ([applyDice]). */
    fun roll(state: GameState, random: Random): GameState = applyDice(state, List(state.rules.dice) { random.nextInt(1, state.rules.dieFaces + 1) })

    /** The current player rolls [roll] on a game's one die ([applyDice]). */
    fun applyRoll(state: GameState, roll: Int): GameState = applyDice(state, listOf(roll))

    /**
     * The current player rolls [dice], one value a die the rules roll. A run of roll-again rolls (one of the rules'
     * roll-again rolls on one die, or with two, doubles where they roll again) that reaches the rules' limit ends the turn
     * unmoved, and so does a roll with no legal move, roll-again roll or not, unless the player has none on the board and
     * tries left to leave the yard ([net.zodac.ludoplus.model.GameRules.yardTries]): then they roll again. Otherwise the game
     * awaits one of the legal moves, with each die's moved on its own ([TurnPhase.AwaitingMove]).
     */
    fun applyDice(state: GameState, dice: List<Int>): GameState {
        check(state.phase == TurnPhase.AwaitingRoll) { "Not waiting for a roll: ${state.phase}" }
        val rules = state.rules
        require(dice.size == rules.dice) { "${rules.dice} dice roll ${rules.dice} values, not $dice" }
        require(dice.all { it in 1..rules.dieFaces }) { "A ${rules.dieFaces}-sided die can't roll $dice" }
        val roll = dice.first()
        val second = dice.getOrNull(1)
        val player = state.current
        // The first roll of a turn starts it; a roll again continues it.
        val started = PowerUps.tick(state).let { ticked ->
            if (state.rollingAgain) ticked else ticked.copy(players = ticked.players.replace(player) { it.copy(turns = it.turns + 1) })
        }
        val rollsAgain = if (dice.size == 1) roll in rules.rollAgainRolls else rules.rollAgainOnDoubles && dice.distinct().size == 1
        val run = if (rollsAgain) state.rollAgainRun + 1 else 0
        val limit = rules.rollAgainLimit
        if (rollsAgain && limit != null && run >= limit) {
            return nextPlayer(PowerUps.spendDouble(started)).copy(lastEvent = GameEvent.RollAgainLimit(player, roll, second))
        }
        val moves = movesForDice(started, dice)
        if (moves.isEmpty()) {
            // With no pawn on the board, a player may have tries left to leave the yard: the turn carries on.
            val triesLeft = if (movers(started).none { owner -> started.players[owner].pawns.any { it.isOnBoard(started) } }) {
                rules.yardTries - state.yardTriesUsed - 1
            } else {
                0
            }
            if (triesLeft > 0) {
                return PowerUps.spendDouble(started).copy(
                    rollAgainRun = run,
                    rollingAgain = true,
                    yardTriesUsed = state.yardTriesUsed + 1,
                    lastEvent = GameEvent.NoMove(player, roll, triesLeft, second),
                )
            }
            return nextPlayer(PowerUps.spendDouble(started)).copy(lastEvent = GameEvent.NoMove(player, roll, second = second))
        }
        return started.copy(
            phase = TurnPhase.AwaitingMove(roll, moves, dice = dice, earned = rollsAgain),
            rollAgainRun = run,
            lastEvent = GameEvent.Rolled(player, roll, second),
        )
    }

    /**
     * Every legal move for [dice], the dice still to be moved: [legalMoves] for each value, with two dice each move marked
     * with the die it uses ([Move.roll]; a double's moves once). Where the rules make captures compulsory and one of them
     * captures, only those that do.
     */
    fun movesForDice(state: GameState, dice: List<Int>): List<Move> {
        val moves = if (state.rules.dice == 1) {
            movesFor(state, dice.single())
        } else {
            dice.distinct().flatMap { die -> movesFor(state, die).map { it.copy(roll = die) } }
        }
        if (state.rules.mustCapture && moves.any { it.captures.isNotEmpty() }) return moves.filter { it.captures.isNotEmpty() }
        return moves
    }

    /**
     * Every legal move for the current player's [roll], in pawn order: their own pawns, or once every one is home in a
     * team game that rolls for partners, each partner's still racing, partner by partner. Where the rules make captures
     * compulsory and one of them captures, only those that do.
     */
    fun legalMoves(state: GameState, roll: Int): List<Move> {
        val moves = movesFor(state, roll)
        if (state.rules.mustCapture && moves.any { it.captures.isNotEmpty() }) return moves.filter { it.captures.isNotEmpty() }
        return moves
    }

    /**
     * The move made for the current player while they await a move with only one choice: the first of its moves. Moves of
     * one owner's pawns from one square by one die are one choice. Null with more than one choice, and when the one
     * brings a pawn out of the yard with none of its owner's on the board, or brings its owner's last pawn home: the
     * player makes those.
     */
    fun automaticMove(state: GameState): Move? {
        val phase = state.phase as? TurnPhase.AwaitingMove ?: return null
        val move = phase.moves.distinctBy { Triple(it.ownerIn(state), it.from, it.roll) }.singleOrNull() ?: return null
        val pawns = state.players[move.ownerIn(state)].pawns
        if (move.from == PawnPosition.YARD_PROGRESS && pawns.none { it.isOnBoard(state) }) return null
        if (move.to == state.board.homeProgress && pawns.count { it != state.board.homeProgress } == 1) return null
        return move
    }

    /** Every move for [roll], captures compulsory or not ([legalMoves]): a capture is only where the pawn ends up. */
    private fun movesFor(state: GameState, roll: Int): List<Move> {
        val me = state.current
        val blockades = blockades(state)
        return movers(state).flatMap { owner ->
            state.players[owner].pawns.mapIndexedNotNull { pawn, from ->
                val rolledTo = destination(state, owner, from, roll) ?: return@mapIndexedNotNull null
                if (isBlocked(state, blockades, owner, from, rolledTo)) return@mapIndexedNotNull null
                val to = throughPortals(state, owner, rolledTo)
                // A blockade at a portal's other end stops the move too.
                if (to != rolledTo && isBlocked(state, blockades, owner, to - 1, to)) return@mapIndexedNotNull null
                val via = rolledTo.takeIf { it != to }
                val landing = land(state, owner, to, floor = from)
                Move(
                    pawn = pawn,
                    from = from,
                    to = landing.at,
                    captures = landing.captures,
                    owner = owner.takeIf { it != me },
                    via = via,
                    bouncedFrom = to.takeIf { landing.brokenShields.isNotEmpty() },
                    brokenShields = landing.brokenShields,
                )
            }
        }
    }

    /**
     * Where a pawn of [owner] at progress [from] goes with [roll], blockades aside, or null if it can't move: out of the
     * yard onto its start square on a roll the rules allow, or that many steps on, never past home.
     */
    fun destination(state: GameState, owner: Int, from: Int, roll: Int): Int? {
        val board = state.board
        return when {
            from == PawnPosition.OUT_PROGRESS -> null
            from == PawnPosition.YARD_PROGRESS -> if (leavesYard(state, owner, roll)) 0 else null
            from == board.homeProgress -> null
            // A doubled roll, but for one that would take it past home, which counts once.
            from + 2 * roll <= board.homeProgress && isDoubled(state, owner) -> from + 2 * roll
            // Exact roll home: a pawn can't overshoot it.
            from + roll > board.homeProgress -> null
            else -> from + roll
        }
    }

    /** Whether a roll moving [owner]'s pawns counts double: it's whoever rolls it whose doubled rolls count, for a partner too. */
    private fun isDoubled(state: GameState, owner: Int): Boolean =
        state.rules.powerUps && state.players[if (owner in movers(state)) state.current else owner].doubledRolls > 0

    /** Where a landing ends: at the progress [at], capturing [captures], having broken [brokenShields] on the way back to it. */
    internal class Landing(val at: Int, val captures: List<PawnRef>, val brokenShields: List<PawnRef>)

    /**
     * A pawn of [owner]'s landing at [progress]: what it captures there, or where a shielded pawn would be captured, one
     * square back for each shield it meets and breaks, never past the progress [floor] it came from. Out of the yard, there's
     * nowhere to bounce to, so a shield doesn't hold.
     */
    internal fun land(state: GameState, owner: Int, progress: Int, floor: Int): Landing {
        val broken = mutableListOf<PawnRef>()
        var at = progress
        while (true) {
            val captures = capturesAt(state, owner, at)
            val shield = captures.firstOrNull { state.isShielded(it.player, it.pawn) }
            if (shield == null || floor < 0) return Landing(at, captures, broken)
            broken += shield
            at--
            // Back where it came from: nobody's there to meet.
            if (at <= floor) return Landing(floor, emptyList(), broken)
        }
    }

    /** Where a pawn of [owner] ends up after a move that brings it to [progress]: through a portal, where they're open. */
    private fun throughPortals(state: GameState, owner: Int, progress: Int): Int =
        if (state.rules.portals) state.board.throughPortal(state.players[owner].seat, progress) else progress

    /** Where a pawn of [owner] at progress [from] ends up with [roll], blockades aside: [destination], through any portal. */
    fun reach(state: GameState, owner: Int, from: Int, roll: Int): Int? =
        destination(state, owner, from, roll)?.let { throughPortals(state, owner, it) }

    /** Whether [roll] brings a pawn of [owner] out of the yard: one of the rules' exit rolls, or any with a free start. */
    private fun leavesYard(state: GameState, owner: Int, roll: Int): Boolean =
        roll in state.rules.yardExitRolls || (state.rules.freeStart && state.players[owner].pawns.none { it.isOnBoard(state) })

    /** Whether a pawn at [this] progress is on the board: on the track or in its home column, not in the yard or home. */
    private fun Int.isOnBoard(state: GameState): Boolean = this in 0..<state.board.homeProgress

    /**
     * Whose pawns the current player moves: their own, or once every one is home in a team game that rolls for
     * partners, each partner's still racing.
     */
    private fun movers(state: GameState): List<Int> {
        val board = state.board
        val me = state.current
        return if (state.players[me].isFinished(board) && state.rules.rollForPartners) {
            state.partnersOf(me).filter { !state.players[it].isFinished(board) }
        } else {
            listOf(me)
        }
    }

    /**
     * Each track square holding a blockade, to the player whose it is: two or more of one player's pawns there. None
     * without the rule.
     */
    fun blockades(state: GameState): Map<Int, Int> {
        if (!state.rules.blockades) return emptyMap()
        val board = state.board
        val squares = mutableMapOf<Int, Int>()
        state.players.forEachIndexed { index, player ->
            player.pawns.filter { it in 0..<board.trackSteps }
                .groupingBy { board.trackSquare(player.seat, it) }
                .eachCount()
                .forEach { (square, count) -> if (count >= 2) squares[square] = index }
        }
        return squares
    }

    /**
     * Whether another player's blockade, of [blockades], stands on a track square a pawn of [owner] would pass or land on
     * going from progress [from] to [to]: out of the yard, only its start square.
     */
    internal fun isBlocked(state: GameState, blockades: Map<Int, Int>, owner: Int, from: Int, to: Int): Boolean {
        if (blockades.isEmpty()) return false
        val board = state.board
        val seat = state.players[owner].seat
        val first = maxOf(from + 1, 0)
        val last = minOf(to, board.trackSteps - 1)
        return (first..last).any { progress -> blockades[board.trackSquare(seat, progress)]?.let { it != owner } == true }
    }

    /** The current player makes [move], one of the moves awaited ([move] by pawn, owner, and die). */
    fun move(state: GameState, move: Move): GameState = move(state, move.pawn, move.owner, move.roll)

    /**
     * The current player moves pawn [pawn] (of [owner], a partner they roll for, or their own for null) by the roll
     * awaiting a move (with two dice, by [die], or the first die with a move for it), capturing any opponent it lands on.
     * With a die still to move and a move for it, the player moves again. Once the roll's dice are moved, they roll again
     * after a roll-again roll, a capture, or a pawn brought home (as the rules give), unless that move ended their part in
     * the game; otherwise play passes on. A run of roll-again rolls carries on through a roll earned another way.
     *
     * A player with every pawn home is placed then, or in a team game with their team once every partner's are too. Where
     * the last standing wins, the game ends with one team left. A power-up the pawn ends on is taken ([PowerUps.take]).
     */
    fun move(state: GameState, pawn: Int, owner: Int? = null, die: Int? = null): GameState = move(state, pawn, owner, die, takePowerUp = true)

    /** [move], with a power-up it ends on left alone: for weighing a move by, whose chances nobody knows. */
    internal fun moveWithoutPowerUp(state: GameState, move: Move): GameState = move(state, move.pawn, move.owner, move.roll, takePowerUp = false)

    private fun move(state: GameState, pawn: Int, owner: Int?, die: Int?, takePowerUp: Boolean): GameState {
        val phase = state.phase
        check(phase is TurnPhase.AwaitingMove) { "Not waiting for a move: $phase" }
        val move = requireNotNull(phase.moves.firstOrNull { it.pawn == pawn && it.owner == owner && (die == null || it.roll == die) }) {
            "Pawn $pawn has no legal move for ${phase.dice}"
        }
        val used = move.roll ?: phase.roll
        val player = state.current
        val whose = move.ownerIn(state)
        val rules = state.rules
        val board = state.board
        val moved = place(PowerUps.spendDouble(state), whose, pawn, Landing(move.to, move.captures, move.brokenShields))
        val (taken, effect) = if (takePowerUp) PowerUps.take(moved, whose, pawn) else moved to null
        val players = taken.players
        val captures = move.captures + effect?.captures.orEmpty()
        val endedAt = players[whose].pawns[pawn]
        val finished = players[whose].isFinished(board)
        val knockedOut = captures.map { it.player }.distinct().filter { players[it].pawnsLeft == 0 }
        var next = taken.copy(phase = TurnPhase.AwaitingRoll, yardTriesUsed = 0, knockedOut = state.knockedOut + knockedOut)
        if (rules.lastStanding) {
            val out = next.teamsOut()
            val standing = players.indices.map(rules::teamOf).distinct() - out.toSet()
            if (standing.size == 1) {
                // The last team standing wins, the others placed by how late they went out.
                return next.copy(
                    finishingOrder = (standing + out.reversed()).flatMap { team -> players.indices.filter { rules.teamOf(it) == team } },
                    phase = TurnPhase.Finished,
                    rollAgainRun = 0,
                    rollingAgain = false,
                    lastEvent = GameEvent.Moved(player, used, move, finished = false, knockedOut = knockedOut, effect = effect),
                )
            }
        }
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
                lastEvent = GameEvent.Moved(player, used, move, finished, effect = effect),
            )
        }
        // The mover plays on unless they're placed, or home with no partner's pawns to roll for.
        val playsOn = player !in next.finishingOrder && (!players[player].isFinished(board) || (rules.teams != null && rules.rollForPartners))
        val earned = phase.earned ||
            (rules.rollAgainOnCapture && captures.isNotEmpty()) ||
            (rules.rollAgainOnHome && endedAt == board.homeProgress) ||
            effect?.powerUp == PowerUp.ROLL_AGAIN
        // Another die to move, with a move for it: the same roll goes on.
        val left = phase.dice.toMutableList().apply { remove(used) }
        if (playsOn && left.isNotEmpty()) {
            val moves = movesForDice(next, left)
            if (moves.isNotEmpty()) {
                return next.copy(
                    phase = TurnPhase.AwaitingMove(left.first(), moves, dice = left, earned = earned, rolled = phase.rolled),
                    lastEvent = GameEvent.Moved(player, used, move, finished, knockedOut = knockedOut, effect = effect),
                )
            }
        }
        val rollsAgain = playsOn && earned
        next = if (rollsAgain) next.copy(rollingAgain = true) else nextPlayer(next)
        return next.copy(lastEvent = GameEvent.Moved(player, used, move, finished, rollsAgain, knockedOut, effect))
    }

    /**
     * [state] with [owner]'s pawn [pawn] come to rest by [landing]: the pawns it captured back in their yards (or out of the
     * game), credited to the player whose turn it is, and the shields it broke gone, with its own once it's off the track.
     */
    internal fun place(state: GameState, owner: Int, pawn: Int, landing: Landing): GameState {
        val rules = state.rules
        var players = state.players
            .replace(owner) { side ->
                side.copy(
                    pawns = side.pawns.replace(pawn) { landing.at },
                    shielded = if (landing.at < state.board.trackSteps) side.shielded else side.shielded - pawn,
                )
            }
            .replace(state.current) { mover -> mover.copy(captures = mover.captures + landing.captures.size) }
        for (captured in landing.captures) {
            players = players.replace(captured.player) { victim ->
                victim.copy(
                    pawns = victim.pawns.replace(captured.pawn) { if (rules.lastStanding) PawnPosition.OUT_PROGRESS else PawnPosition.YARD_PROGRESS },
                    timesCaptured = victim.timesCaptured + 1,
                    shielded = victim.shielded - captured.pawn,
                )
            }
        }
        for (broken in landing.brokenShields) players = players.replace(broken.player) { it.copy(shielded = it.shielded - broken.pawn) }
        return state.copy(players = players)
    }

    /**
     * The pawns a pawn of [owner] would capture on landing at progress [to]: opponents', and partners' if the rules say,
     * those that square isn't safe for.
     */
    internal fun capturesAt(state: GameState, owner: Int, to: Int): List<PawnRef> {
        val board = state.board
        // Only the shared track has opponents on it.
        if (to !in 0..<board.trackSteps) return emptyList()
        val square = board.trackSquare(state.players[owner].seat, to)
        return state.players.flatMapIndexed { index, other ->
            if (index == owner || (state.rules.arePartners(owner, index) && !state.rules.partnerCaptures) || state.isSafeFor(other.seat, square)) {
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
     * Play passes to the next player still racing, who awaits a roll: not one placed or knocked out, nor one home in a
     * team game that doesn't roll for partners. Coming back round to a player no later than this one, a round is played.
     */
    private fun nextPlayer(state: GameState): GameState {
        val skipsHome = !(state.rules.teams != null && state.rules.rollForPartners)
        var next = state.current
        do {
            next = (next + 1) % state.players.size
        } while (next in state.finishingOrder || next in state.knockedOut || (skipsHome && state.players[next].isFinished(state.board)))
        return state.copy(
            current = next,
            phase = TurnPhase.AwaitingRoll,
            rollAgainRun = 0,
            rollingAgain = false,
            yardTriesUsed = 0,
            roundsPlayed = if (state.rules.suddenDeath && next <= state.current) state.roundsPlayed + 1 else state.roundsPlayed,
        )
    }

    internal inline fun <T> List<T>.replace(index: Int, change: (T) -> T): List<T> =
        mapIndexed { i, item -> if (i == index) change(item) else item }
}
