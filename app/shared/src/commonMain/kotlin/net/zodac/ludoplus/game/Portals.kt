package net.zodac.ludoplus.game

import kotlin.random.Random
import net.zodac.ludoplus.model.Portal
import net.zodac.ludoplus.model.canHavePortal
import net.zodac.ludoplus.model.portals

/**
 * The portals on the track ([net.zodac.ludoplus.model.GameRules.portals]): where they open. A game starts with the board's
 * own ([net.zodac.ludoplus.model.BoardGeometry.portals]), and a pair a pawn goes through closes and opens again at once
 * somewhere else. Pure, as [LudoEngine] is: every chance is drawn from the state's [GameState.seed], which the draw moves on.
 */
internal object Portals {

    /** [state], a game's start, with the board's own portals open. */
    fun start(state: GameState): GameState = state.copy(portals = state.board.portals)

    /**
     * [state] with [used], one of its portals that a pawn has just gone through, closed and open again anywhere on the track,
     * as long as ever: never where a portal may not be ([net.zodac.ludoplus.model.BoardGeometry.canHavePortal]), on a lost
     * square, a pawn's, another pair's, or either of its own ends. As it is with nowhere free. Where [chances] are off, as
     * for weighing a move, nobody knows where it will open: it's closed, and opens nowhere.
     */
    fun reopen(state: GameState, used: Portal, chances: Boolean): GameState {
        val others = state.portals.filterNot { it.isPair(used) }
        if (!chances) return state.copy(portals = others)
        val board = state.board
        val taken = state.players.flatMap { side -> side.pawns.filter { it in 0..<board.trackSteps }.map { board.trackSquare(side.seat, it) } }.toSet() +
            others.flatMap { listOf(it.first, it.second) } + used.first + used.second + state.lostSquares
        fun free(square: Int) = board.canHavePortal(square) && square !in taken
        val places = (0..<board.trackLength).filter { free(it) && free((it + used.steps) % board.trackLength) }
        if (places.isEmpty()) return state
        val random = Random(state.seed)
        val next = random.nextLong()
        val opened = used.openAt(board, places.random(random))
        return state.copy(portals = state.portals.map { if (it.isPair(used)) opened else it }, seed = next)
    }
}
