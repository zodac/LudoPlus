package net.zodac.ludoplus.game

import kotlin.random.Random
import net.zodac.ludoplus.game.LudoEngine.replace
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PowerUp

/**
 * The power-ups on the track ([net.zodac.ludoplus.model.GameRules.powerUps]): where they come, and what taking one does.
 * Pure, as [LudoEngine] is: every chance is drawn from the state's [GameState.seed], which the draw moves on.
 */
internal object PowerUps {

    /** [state], a game's start, with a power-up on the track for each player, and the first new one due. */
    fun start(state: GameState): GameState = due(state.players.indices.fold(state) { next, _ -> spawn(next) })

    /** [state] as a turn starts: the next power-up a turn nearer, and if its time has come, on the track (unless it's full: [PowerUp.MOST_PER_PLAYER]), with another due. */
    fun tick(state: GameState): GameState {
        if (!state.rules.powerUps) return state
        val left = state.turnsToPowerUp - 1
        if (left > 0) return state.copy(turnsToPowerUp = left)
        val full = state.powerUps.size >= PowerUp.MOST_PER_PLAYER * state.players.size
        return due(if (full) state else spawn(state))
    }

    /** [state] with the next power-up due in a random number of turns. */
    private fun due(state: GameState): GameState {
        val (random, drawn) = draw(state)
        return drawn.copy(turnsToPowerUp = random.nextInt(1, PowerUp.MOST_TURNS + 1))
    }

    /** [state] with one of the current player's doubled rolls spent, if they have any. */
    fun spendDouble(state: GameState): GameState {
        if (!state.rules.powerUps || state.currentPlayer.doubledRolls == 0) return state
        return state.copy(players = state.players.replace(state.current) { it.copy(doubledRolls = it.doubledRolls - 1) })
    }

    /**
     * [state] with the power-up that [owner]'s pawn [pawn] has ended a move on taken, and what it did; as it is, and null,
     * with none there. One a move: a pawn a power-up moves onto another leaves it there. A Swap does nothing, taken so: it's
     * only with nobody to swap with that nobody is asked ([LudoEngine.swapMoves]).
     */
    fun take(state: GameState, owner: Int, pawn: Int): Pair<GameState, PowerUpEffect?> {
        val progress = state.players[owner].pawns[pawn]
        if (progress !in 0..<state.board.trackSteps) return state to null
        val square = state.board.trackSquare(state.players[owner].seat, progress)
        val powerUp = state.powerUps[square] ?: return state to null
        val taken = state.copy(powerUps = state.powerUps - square)
        return when (powerUp) {
            // The roller's: the one whose rolls are counted.
            PowerUp.DOUBLE -> taken.copy(players = taken.players.replace(state.current) { it.copy(doubledRolls = PowerUp.DOUBLED_ROLLS) }) to PowerUpEffect(powerUp)
            PowerUp.SHIELD -> taken.copy(players = taken.players.replace(owner) { it.copy(shielded = it.shielded + pawn) }) to PowerUpEffect(powerUp)
            PowerUp.ROLL_AGAIN -> taken to PowerUpEffect(powerUp)
            PowerUp.CALL_UP -> callUp(taken, owner)
            PowerUp.SWAP -> taken to PowerUpEffect(powerUp)
        }
    }

    private fun callUp(state: GameState, owner: Int): Pair<GameState, PowerUpEffect> {
        val waiting = state.players[owner].pawns.indexOf(PawnPosition.YARD_PROGRESS)
        if (waiting < 0 || LudoEngine.isBlocked(state, LudoEngine.blockades(state), owner, PawnPosition.YARD_PROGRESS, 0)) {
            return state to PowerUpEffect(PowerUp.CALL_UP)
        }
        val landing = LudoEngine.land(state, owner, 0, floor = PawnPosition.YARD_PROGRESS)
        return LudoEngine.place(state, owner, waiting, landing) to PowerUpEffect(PowerUp.CALL_UP, captures = landing.captures, calledUp = waiting)
    }

    /**
     * The pawns [owner]'s pawn [pawn], on the track, can swap places with: any other player's on the track, one for each
     * square, but where either's way never takes it over the other's square, or where the two would come to share a square
     * that isn't safe with a rival's.
     */
    fun swapTargets(state: GameState, owner: Int, pawn: Int): List<PawnRef> {
        val board = state.board
        val from = board.trackSquare(state.players[owner].seat, state.players[owner].pawns[pawn])
        val onTrack = state.players.flatMapIndexed { player, side ->
            side.pawns.mapIndexedNotNull { index, progress ->
                if (progress in 0..<board.trackSteps) PawnRef(player, index) to board.trackSquare(side.seat, progress) else null
            }
        }
        // Whether [player]'s pawn can stand on [square] with everyone there but [leaving].
        fun fits(player: Int, square: Int, leaving: PawnRef): Boolean =
            board.progressAt(state.players[player].seat, square) != null && onTrack.all { (ref, at) ->
                at != square || ref == leaving || ref.player == player || state.isSafeFor(state.players[ref.player].seat, square) ||
                    (state.rules.arePartners(player, ref.player) && !state.rules.partnerCaptures)
            }
        return onTrack
            .filter { (ref, at) -> ref.player != owner && fits(owner, at, leaving = ref) && fits(ref.player, from, leaving = PawnRef(owner, pawn)) }
            .distinctBy { (ref, at) -> ref.player to at }
            .map { it.first }
    }

    /** [state] with [owner]'s pawn [pawn] and [other] in each other's places, any shields with them, and what that did; as it is, and null, where [other] is that pawn itself. */
    fun swap(state: GameState, owner: Int, pawn: Int, other: PawnRef): Pair<GameState, PowerUpEffect?> {
        if (other == PawnRef(owner, pawn)) return state to null
        val board = state.board
        val (mine, theirs) = state.players[owner] to state.players[other.player]
        val to = checkNotNull(board.progressAt(mine.seat, board.trackSquare(theirs.seat, theirs.pawns[other.pawn])))
        val back = checkNotNull(board.progressAt(theirs.seat, board.trackSquare(mine.seat, mine.pawns[pawn])))
        val players = state.players
            .replace(owner) { side -> side.copy(pawns = side.pawns.replace(pawn) { to }) }
            .replace(other.player) { side -> side.copy(pawns = side.pawns.replace(other.pawn) { back }) }
        return state.copy(players = players) to PowerUpEffect(PowerUp.SWAP, movedTo = to, swapped = other)
    }

    /** [state] with a power-up of any kind anywhere on the track: never on a safe square, a pawn's, or another's. As it is with no square free. */
    private fun spawn(state: GameState): GameState {
        val board = state.board
        val taken = state.players.flatMap { side -> side.pawns.filter { it in 0..<board.trackSteps }.map { board.trackSquare(side.seat, it) } }.toSet() + state.powerUps.keys
        val free = (0..<board.trackLength).filter { !board.isSafe(it) && it !in taken }
        if (free.isEmpty()) return state
        val (random, drawn) = draw(state)
        return drawn.copy(powerUps = state.powerUps + (free.random(random) to PowerUp.entries.random(random)))
    }

    /** What to draw a chance from, and [state] with its seed moved on. */
    private fun draw(state: GameState): Pair<Random, GameState> {
        val random = Random(state.seed)
        val next = random.nextLong()
        return random to state.copy(seed = next)
    }
}
