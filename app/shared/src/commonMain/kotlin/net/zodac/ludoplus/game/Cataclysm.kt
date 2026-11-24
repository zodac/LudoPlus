package net.zodac.ludoplus.game

import kotlin.random.Random

/**
 * The last stage of sudden death ([GameState.cataclysm]), [STARTS] turns after its second: after every [TURNS] turns, one to [MOST_LOST] random squares with
 * no pawn on them vanish from the track for good ([GameState.lostSquares]), and pawns step over the gaps, until
 * [fewestSquares] are left. Pure, drawing from the state's seed, as [PowerUps] does.
 */
internal object Cataclysm {

    /** How many turns, whoever's and however many are playing, end in sudden death's second stage before the Cataclysm. */
    const val STARTS = 10

    /** How many turns, whoever's, pass between one loss of squares and the next. */
    const val TURNS = 5

    /** The most squares that vanish at once. */
    const val MOST_LOST = 3

    /**
     * The fewest squares the track is left with: one more than the die's faces, so a roll never brings a pawn round to its
     * own square, and any pawn is always within a roll of a lone rival.
     */
    fun fewestSquares(state: GameState): Int = state.rules.dieFaces + 1

    /** Whether any more squares can vanish, whatever stands on them. */
    fun isOver(state: GameState): Boolean = state.board.trackLength - state.lostSquares.size <= fewestSquares(state)

    /** [state] with a turn just over, and counted: some of the track lost, if it's time. */
    fun afterTurn(state: GameState): GameState {
        val turns = state.suddenDeathTurns - STARTS
        if (turns <= 0 || turns % TURNS != 0 || isOver(state)) return state
        val random = Random(state.seed)
        val seed = random.nextLong()
        val board = state.board
        val taken = state.players.flatMap { side -> side.pawns.filter { it in 0..<board.trackSteps }.map { board.trackSquare(side.seat, it) } }.toSet()
        val empty = (0..<board.trackLength).filter { it !in state.lostSquares && it !in taken }
        val spare = board.trackLength - state.lostSquares.size - fewestSquares(state)
        val lost = empty.shuffled(random).take(minOf(random.nextInt(1, MOST_LOST + 1), spare)).sorted()
        return state.copy(lostSquares = state.lostSquares + lost, justLost = lost, seed = seed)
    }
}
