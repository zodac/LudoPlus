package net.zodac.ludo.game

import net.zodac.ludo.model.BoardGeometry
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.GameRules
import net.zodac.ludo.model.PawnPosition

/**
 * A whole game at one moment: immutable, and changed only by [LudoEngine]. Players are in turn order, which is the
 * board's clockwise order of their seats; [current] is the index of the one whose turn it is.
 */
data class GameState(
    val mode: GameMode,
    /** The rules this game follows: [mode]'s, changed by any modifiers. */
    val rules: GameRules,
    val players: List<PlayerState>,
    val current: Int,
    val phase: TurnPhase,
    /** How many of [GameRules.rollAgainRolls] the current player has rolled in a row this turn. */
    val rollAgainRun: Int = 0,
    /** Whether the current player's next roll carries on their turn: they earned it with their last move. */
    val rollingAgain: Boolean = false,
    /** The players who have brought every pawn home, by index, first place first. Every player once [TurnPhase.Finished]. */
    val finishingOrder: List<Int> = emptyList(),
    /** What the last action did, for the screen to show and say. Null before the first roll. */
    val lastEvent: GameEvent? = null,
) {
    val board: BoardGeometry get() = rules.board

    val currentPlayer: PlayerState get() = players[current]

    val isFinished: Boolean get() = phase == TurnPhase.Finished

    /** The player in first place, once someone has brought every pawn home. */
    val winner: Int? get() = finishingOrder.firstOrNull()

    /** Where [player]'s pawn [pawn] is. */
    fun position(player: Int, pawn: Int): PawnPosition = PawnPosition.of(players[player].pawns[pawn], board)

    companion object {
        /**
         * A new game of [mode] for [playerCount] players under [rules] (the mode's own by default), every pawn in its
         * yard, [first] to roll. The players sit at [seats], in clockwise order: spread round the board by default
         * ([BoardGeometry.seatsFor]).
         */
        fun new(
            mode: GameMode,
            playerCount: Int,
            first: Int = 0,
            rules: GameRules = mode.rules,
            seats: List<Int> = rules.board.seatsFor(playerCount),
        ): GameState {
            val board = rules.board
            require(playerCount in 2..board.seats) { "A ${board.seats}-seat board takes 2 to ${board.seats} players" }
            require(first in 0..<playerCount) { "No player $first to go first" }
            require(seats.size == playerCount && seats.zipWithNext().all { (a, b) -> a < b } && seats.all { it in 0..<board.seats }) {
                "Seats $seats don't seat $playerCount players clockwise"
            }
            return GameState(
                mode = mode,
                rules = rules,
                players = seats.map { seat ->
                    PlayerState(seat = seat, pawns = List(rules.pawnsPerPlayer) { PawnPosition.YARD_PROGRESS })
                },
                current = first,
                phase = TurnPhase.AwaitingRoll,
            )
        }
    }
}

/** One player's side of a [GameState]. */
data class PlayerState(
    /** The board seat they sit at, which fixes their start square and home column. */
    val seat: Int,
    /** Each pawn's progress from its start square ([PawnPosition.of]): [PawnPosition.YARD_PROGRESS] in the yard. */
    val pawns: List<Int>,
    /** How many turns they have started. A run of rolls again is one turn. */
    val turns: Int = 0,
    /** How many opponents' pawns they have sent back to a yard. */
    val captures: Int = 0,
    /** How many of their own pawns have been sent back to their yard. */
    val timesCaptured: Int = 0,
) {
    /** How many of their pawns are home on [board]. */
    fun pawnsHome(board: BoardGeometry): Int = pawns.count { it == board.homeProgress }

    /** Whether every one of their pawns is home on [board]. */
    fun isFinished(board: BoardGeometry): Boolean = pawns.all { it == board.homeProgress }
}

/** What the game is waiting for. */
sealed interface TurnPhase {
    /** The current player to roll the die. */
    data object AwaitingRoll : TurnPhase

    /** The current player to pick one of [moves] for their [roll]. Never empty: a roll with no move passes. */
    data class AwaitingMove(val roll: Int, val moves: List<Move>) : TurnPhase

    /** Every placing is decided. */
    data object Finished : TurnPhase
}

/**
 * A legal move: the current player's pawn [pawn] from progress [from] to [to]. [captures] lists the opponents' pawns it
 * sends back to their yards, as (player, pawn) pairs.
 */
data class Move(val pawn: Int, val from: Int, val to: Int, val captures: List<PawnRef> = emptyList())

/** One pawn in a game: [player]'s pawn [pawn], both indices. */
data class PawnRef(val player: Int, val pawn: Int)

/** What the last action in a game did. */
sealed interface GameEvent {
    /** The player who acted. */
    val player: Int

    /** [player] rolled [roll] and has a move to make. */
    data class Rolled(override val player: Int, val roll: Int) : GameEvent

    /** [player] rolled [roll] and had no legal move, so the turn passed (even on a roll-again roll). */
    data class NoMove(override val player: Int, val roll: Int) : GameEvent

    /** [player] rolled [roll], the roll-again roll that ran out [GameRules.rollAgainLimit], so their turn ended unmoved. */
    data class RollAgainLimit(override val player: Int, val roll: Int) : GameEvent

    /**
     * [player] moved with [roll]. [finished] is true if that brought their last pawn home; [rollsAgain] if the move earned
     * them another roll (a roll-again roll, a capture, or a pawn home, as the rules give).
     */
    data class Moved(override val player: Int, val roll: Int, val move: Move, val finished: Boolean, val rollsAgain: Boolean = false) : GameEvent
}
