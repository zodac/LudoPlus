package net.zodac.ludoplus.game

import kotlin.random.Random
import net.zodac.ludoplus.game.LudoEngine.replace
import net.zodac.ludoplus.model.PawnPosition

/**
 * A ghost's move, which nobody makes: the ghost numbered [ghost] went from the absolute track square [from] by [steps], in
 * the direction of play or back (negative), and sent the pawns [caught] on the square it ended on back to their yards.
 */
data class GhostMove(val ghost: Int, val from: Int, val steps: Int, val caught: List<PawnRef> = emptyList())

/**
 * The ghosts that haunt the track ([net.zodac.ludoplus.model.GameRules.ghosts]): one for every two players. After every
 * second turn a random one of them moves, a die's roll of squares, either way. Only a ghost's move catches a pawn: one that
 * lands where a ghost is is none the worse. Pure, drawing from the state's seed, as [PowerUps] does.
 */
internal object Ghosts {

    /** How many turns pass between one ghost's move and the next. */
    private const val TURNS = 2

    /** How many ghosts a game of [players] has. */
    fun count(players: Int): Int = players / 2

    /** [state], a game's start, with its ghosts on the track: never on a safe square, or two on one. */
    fun start(state: GameState): GameState {
        val (random, drawn) = draw(state)
        val squares = (0..<state.board.trackLength).filterNot(state.board::isSafe).shuffled(random)
        return drawn.copy(ghosts = squares.take(count(state.players.size)))
    }

    /** [state] with a turn just over: a ghost's move, if it's time for one. */
    fun afterTurn(state: GameState): GameState {
        if (!state.rules.ghosts) return state
        if (state.turnsSinceGhost + 1 < TURNS) return state.copy(turnsSinceGhost = state.turnsSinceGhost + 1)
        val board = state.board
        val (random, drawn) = draw(state)
        val ghost = state.ghosts.indices.random(random)
        val from = state.ghosts[ghost]
        // Any roll, either way, that doesn't end on another ghost: there are always more of those than ghosts.
        val steps = ((1..state.rules.dieFaces) + (-state.rules.dieFaces..-1)).filter { (from + it).mod(board.trackLength) !in state.ghosts }.random(random)
        val to = (from + steps).mod(board.trackLength)
        val blockaded = to in LudoEngine.blockades(state)
        val caught = if (blockaded) emptyList() else state.players.flatMapIndexed { player, side ->
            if (state.isSafeFor(side.seat, to)) return@flatMapIndexed emptyList()
            side.pawns.indices.filter { side.pawns[it] in 0..<board.trackSteps && board.trackSquare(side.seat, side.pawns[it]) == to }.map { PawnRef(player, it) }
        }
        var players = state.players
        for (pawn in caught) {
            players = players.replace(pawn.player) { side ->
                side.copy(pawns = side.pawns.replace(pawn.pawn) { PawnPosition.YARD_PROGRESS }, timesHaunted = side.timesHaunted + 1, shielded = side.shielded - pawn.pawn)
            }
        }
        return drawn.copy(
            players = players,
            ghosts = state.ghosts.replace(ghost) { to },
            turnsSinceGhost = 0,
            ghostMove = GhostMove(ghost, from, steps, caught),
        )
    }

    /** What to draw a chance from, and [state] with its seed moved on. */
    private fun draw(state: GameState): Pair<Random, GameState> {
        val random = Random(state.seed)
        val next = random.nextLong()
        return random to state.copy(seed = next)
    }
}
