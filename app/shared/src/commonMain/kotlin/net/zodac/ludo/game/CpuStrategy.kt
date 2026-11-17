package net.zodac.ludo.game

import kotlin.random.Random
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.PawnPosition

/** How a CPU player picks one of its legal moves. Pure: everything it knows is the state and the moves it is offered. */
fun interface CpuStrategy {
    /**
     * One of [moves] (never empty) for the current player of [state], which is awaiting one of them; any tie broken
     * with [random].
     */
    fun choose(state: GameState, moves: List<Move>, random: Random): Move

    companion object {
        /** The strategy a CPU of [difficulty] plays. */
        fun of(difficulty: Difficulty): CpuStrategy = when (difficulty) {
            Difficulty.EASY -> RandomStrategy
            Difficulty.MEDIUM -> HeuristicStrategy
            Difficulty.HARD -> ExpectimaxStrategy()
        }
    }
}

/** Easy: any legal move, at random. */
object RandomStrategy : CpuStrategy {
    override fun choose(state: GameState, moves: List<Move>, random: Random): Move = moves.random(random)
}

/**
 * Medium: each move scored on its own, by what it does now. A capture first, then leaving the yard, reaching home or
 * safety, getting out of an opponent's reach, and advancing the furthest pawn; ending where an opponent can reach costs.
 */
object HeuristicStrategy : CpuStrategy {
    private const val CAPTURE = 100.0
    private const val LEAVE_YARD = 60.0
    private const val REACH_HOME = 80.0
    private const val REACH_SAFETY = 40.0
    private const val ESCAPE = 25.0
    private const val IN_REACH = 30.0
    private const val ADVANCE = 0.3

    override fun choose(state: GameState, moves: List<Move>, random: Random): Move =
        moves.shuffled(random).maxBy { score(state, it) }

    internal fun score(state: GameState, move: Move): Double {
        val board = state.board
        val owner = move.ownerIn(state)
        val seat = state.players[owner].seat
        // Sending a partner home (where partners capture) costs what taking an opponent gains.
        val (partners, opponents) = move.captures.partition { state.rules.arePartners(state.current, it.player) }
        var score = CAPTURE * (opponents.size - partners.size)
        if (move.from == PawnPosition.YARD_PROGRESS) score += LEAVE_YARD
        if (move.to == board.homeProgress) score += REACH_HOME
        if (isSafe(state, seat, move.to)) score += REACH_SAFETY
        if (threats(state, owner, move.from) > 0) score += ESCAPE
        score -= IN_REACH * threats(state, owner, move.to, ignoring = move.captures)
        score += ADVANCE * maxOf(move.from, 0)
        return score
    }
}

/**
 * Hard: an expectimax one ply deep. Each move's result is valued by how far every pawn has come, for this player and
 * against the others, less what this player can expect to lose over each opponent's next roll (for every roll from 1
 * to 6, each a sixth likely, the opponent captures the most valuable pawn it can reach), plus what it can expect to
 * capture with its own next roll, reckoned the same way.
 */
class ExpectimaxStrategy(private val weights: Weights = Weights()) : CpuStrategy {
    /**
     * The tuned weights: a pawn's worth is its progress, plus [outOfYard] once out, [homeBonus] once home, and
     * [safeBonus] on a safe square; an opponent's worth counts [opponent] times against this player's own, an expected
     * loss [risk] times, and an expected capture on the next roll [chance] times. Tuned (2026-11-17) over 1,000
     * two-player games a setting against Medium; `CpuTournamentTest` checks them on other seeds.
     */
    data class Weights(
        val outOfYard: Double = 25.0,
        val homeBonus: Double = 20.0,
        val safeBonus: Double = 4.0,
        val opponent: Double = 1.5,
        val risk: Double = 0.9,
        val chance: Double = 0.8,
    )

    override fun choose(state: GameState, moves: List<Move>, random: Random): Move =
        moves.shuffled(random).maxBy { value(state, it) }

    internal fun value(state: GameState, move: Move): Double {
        val after = LudoEngine.move(state, move.pawn, move.owner)
        val me = state.current
        var value = 0.0
        after.players.forEachIndexed { index, player ->
            val worth = player.pawns.sumOf { pawnWorth(after, player.seat, it) }
            // A partner's pawns are as good as this player's own.
            value += if (index == me || state.rules.arePartners(me, index)) worth else -weights.opponent * worth
        }
        return value - weights.risk * expectedLoss(after, me) + weights.chance * expectedGain(after, me)
    }

    private fun pawnWorth(state: GameState, seat: Int, progress: Int): Double = when {
        progress == PawnPosition.YARD_PROGRESS -> 0.0
        progress == state.board.homeProgress -> progress + weights.outOfYard + weights.homeBonus
        else -> progress + weights.outOfYard + if (isSafe(state, seat, progress)) weights.safeBonus else 0.0
    }

    /**
     * What [me] can expect to take with their own next roll, by the same reckoning as [expectedLoss]: each opponent's
     * exposed pawns within a roll ahead of one of theirs, the most valuable taken for each roll.
     */
    private fun expectedGain(state: GameState, me: Int): Double {
        val board = state.board
        val mine = state.players[me]
        val targets = mutableMapOf<Int, Double>()
        state.players.forEachIndexed { index, opponent ->
            if (index == me || state.rules.arePartners(me, index)) return@forEachIndexed
            for (progress in opponent.pawns) {
                if (progress !in 0..<board.trackSteps || isSafe(state, opponent.seat, progress)) continue
                val square = board.trackSquare(opponent.seat, progress)
                targets[square] = (targets[square] ?: 0.0) + pawnWorth(state, opponent.seat, progress)
            }
        }
        if (targets.isEmpty()) return 0.0
        var gain = 0.0
        for (roll in 1..state.rules.dieFaces) {
            val reachable = mine.pawns.mapNotNull { progress ->
                if (progress !in 0..<board.trackSteps || progress + roll >= board.trackSteps) null else targets[board.trackSquare(mine.seat, progress + roll)]
            }
            gain += (reachable.maxOrNull() ?: 0.0) / state.rules.dieFaces
        }
        return gain
    }

    /** What [me] can expect to lose to each opponent's next roll, each capturing the most valuable pawn it can reach. */
    private fun expectedLoss(state: GameState, me: Int): Double {
        val board = state.board
        val mine = state.players[me]
        // Each of my pawns on an unsafe track square, by square, with what losing it would cost.
        val exposed = mine.pawns.filter { it in 0..<board.trackSteps && !isSafe(state, mine.seat, it) }
            .groupBy { board.trackSquare(mine.seat, it) }
            .mapValues { (_, pawns) -> pawns.sumOf { pawnWorth(state, mine.seat, it) } }
        if (exposed.isEmpty()) return 0.0
        var loss = 0.0
        state.players.forEachIndexed { index, opponent ->
            if (index == me || index in state.finishingOrder || isFriendly(state, me, index)) return@forEachIndexed
            for (roll in 1..state.rules.dieFaces) {
                val reachable = opponent.pawns.mapNotNull { progress ->
                    val to = LudoEngine.destination(state, index, progress, roll)?.takeIf { it < board.trackSteps }
                    to?.let { exposed[board.trackSquare(opponent.seat, it)] }
                }
                loss += (reachable.maxOrNull() ?: 0.0) / state.rules.dieFaces
            }
        }
        return loss
    }
}

/**
 * Whether a pawn of [seat] at [progress] is safe: in its yard, its home column, home, on a track square safe for it, or
 * where blockades are played, in one: two or more of its seat's pawns on that square in [state].
 */
private fun isSafe(state: GameState, seat: Int, progress: Int): Boolean {
    val board = state.board
    if (progress !in 0..<board.trackSteps) return true
    val square = board.trackSquare(seat, progress)
    if (state.rules.isSafeFor(seat, square)) return true
    if (!state.rules.blockades) return false
    val player = state.players.first { it.seat == seat }
    return player.pawns.count { it in 0..<board.trackSteps && board.trackSquare(seat, it) == square } >= 2
}

/** Whether [other]'s pawns can't capture [me]'s: [me] themselves, or a partner where partners don't capture. */
private fun isFriendly(state: GameState, me: Int, other: Int): Boolean =
    other == me || (state.rules.arePartners(me, other) && !state.rules.partnerCaptures)

/**
 * How many opponents' pawns could land on [owner]'s pawn at [progress] with their next roll, not counting the pawns in
 * [ignoring] (captured by the move being weighed). None for a safe pawn.
 */
private fun threats(state: GameState, owner: Int, progress: Int, ignoring: List<PawnRef> = emptyList()): Int {
    val seat = state.players[owner].seat
    if (isSafe(state, seat, progress)) return 0
    val board = state.board
    val square = board.trackSquare(seat, progress)
    var count = 0
    state.players.forEachIndexed { index, opponent ->
        if (index == state.current || isFriendly(state, owner, index)) return@forEachIndexed
        opponent.pawns.forEachIndexed { pawn, at ->
            if (PawnRef(index, pawn) in ignoring || at !in 0..<board.trackSteps) return@forEachIndexed
            val reach = (1..state.rules.dieFaces).any { roll -> at + roll < board.trackSteps && board.trackSquare(opponent.seat, at + roll) == square }
            if (reach) count++
        }
    }
    return count
}
