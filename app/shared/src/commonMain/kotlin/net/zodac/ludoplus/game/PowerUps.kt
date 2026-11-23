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

    /** [state], a game's start, with a power-up on each seat's stretch of the track. */
    fun start(state: GameState): GameState = (0..<state.board.seats).fold(state) { next, seat -> spawn(next, seat) }

    /** [state] as a roll is made: each power-up due a roll nearer, and any whose time has come on the board. */
    fun tick(state: GameState): GameState {
        if (!state.rules.powerUps || state.powerUpsDue.isEmpty()) return state
        val due = state.powerUpsDue.map { it - 1 }
        val ticked = state.copy(powerUpsDue = due.filter { it > 0 })
        return (0..<due.count { it <= 0 }).fold(ticked) { next, _ ->
            val (random, drawn) = draw(next)
            // On a stretch that has none.
            val bare = (0..<next.board.seats).filter { seat -> next.powerUps.keys.none { it / next.board.seatSpacing == seat } }
            if (bare.isEmpty()) drawn else spawn(drawn, bare.random(random))
        }
    }

    /** [state] with one of the current player's doubled rolls spent, if they have any. */
    fun spendDouble(state: GameState): GameState {
        if (!state.rules.powerUps || state.currentPlayer.doubledRolls == 0) return state
        return state.copy(players = state.players.replace(state.current) { it.copy(doubledRolls = it.doubledRolls - 1) })
    }

    /**
     * [state] with the power-up that [owner]'s pawn [pawn] has ended a move on taken, and what it did; as it is, and null,
     * with none there. One a move: a pawn a power-up moves onto another leaves it there.
     */
    fun take(state: GameState, owner: Int, pawn: Int): Pair<GameState, PowerUpEffect?> {
        val progress = state.players[owner].pawns[pawn]
        if (progress !in 0..<state.board.trackSteps) return state to null
        val square = state.board.trackSquare(state.players[owner].seat, progress)
        val powerUp = state.powerUps[square] ?: return state to null
        val taken = state.copy(powerUps = state.powerUps - square, powerUpsDue = state.powerUpsDue + PowerUp.RETURN_ROLLS)
        return when (powerUp) {
            // The roller's: the one whose rolls are counted.
            PowerUp.DOUBLE -> taken.copy(players = taken.players.replace(state.current) { it.copy(doubledRolls = PowerUp.DOUBLED_ROLLS) }) to PowerUpEffect(powerUp)
            PowerUp.SHIELD -> taken.copy(players = taken.players.replace(owner) { it.copy(shielded = it.shielded + pawn) }) to PowerUpEffect(powerUp)
            PowerUp.TELEPORT -> {
                val (random, drawn) = draw(taken)
                // Never past home: the last track square is a die's highest from it.
                val to = progress + random.nextInt(1, state.rules.dieFaces + 1)
                // A blockade can't be landed on: the pawn stays where it is.
                if (LudoEngine.isBlocked(drawn, LudoEngine.blockades(drawn), owner, to - 1, to)) return drawn to PowerUpEffect(powerUp)
                val landing = LudoEngine.land(drawn, owner, to, floor = progress)
                LudoEngine.place(drawn, owner, pawn, landing) to PowerUpEffect(powerUp, movedTo = landing.at, captures = landing.captures, brokenShields = landing.brokenShields)
            }
            PowerUp.ROLL_AGAIN -> taken to PowerUpEffect(powerUp)
            PowerUp.CALL_UP -> callUp(taken, owner)
            PowerUp.SWAP -> swap(taken, owner, pawn)
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

    private fun swap(state: GameState, owner: Int, pawn: Int): Pair<GameState, PowerUpEffect> {
        val board = state.board
        val mine = state.players[owner]
        val from = mine.pawns[pawn]
        val onTrack = state.players.flatMapIndexed { player, side ->
            side.pawns.mapIndexedNotNull { index, progress ->
                if (progress in 0..<board.trackSteps) PawnRef(player, index) to board.trackSquare(side.seat, progress) else null
            }
        }
        val square = board.trackSquare(mine.seat, from)
        val (other, steps) = onTrack
            .filter { (ref, at) ->
                ref.player != owner && !state.rules.arePartners(owner, ref.player) && !state.isShielded(ref.player, ref.pawn) &&
                    !state.isSafeFor(state.players[ref.player].seat, at) && onTrack.count { it.second == at } == 1
            }
            .map { (ref, at) -> ref to (at - square).mod(board.trackLength) }
            .filter { (ref, steps) -> steps > 0 && from + steps < board.trackSteps && state.players[ref.player].pawns[ref.pawn] - steps >= 0 }
            .minByOrNull { it.second } ?: return state to PowerUpEffect(PowerUp.SWAP)
        val players = state.players
            .replace(owner) { side -> side.copy(pawns = side.pawns.replace(pawn) { it + steps }) }
            .replace(other.player) { side -> side.copy(pawns = side.pawns.replace(other.pawn) { it - steps }) }
        return state.copy(players = players) to PowerUpEffect(PowerUp.SWAP, movedTo = from + steps, swapped = other)
    }

    /** [state] with a power-up of any kind on [seat]'s stretch of the track: never a safe square, a pawn's, or another's. */
    private fun spawn(state: GameState, seat: Int): GameState {
        val board = state.board
        val taken = state.players.flatMap { side -> side.pawns.filter { it in 0..<board.trackSteps }.map { board.trackSquare(side.seat, it) } }.toSet() + state.powerUps.keys
        val free = (0..<board.seatSpacing).map { board.startSquare(seat) + it }.filter { !board.isSafe(it) && it !in taken }
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
