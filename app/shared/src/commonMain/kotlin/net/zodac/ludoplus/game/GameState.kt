package net.zodac.ludoplus.game

import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.GameRules
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PowerUp

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
    /** How many of [GameRules.yardTries] the current player has rolled this turn without a move, no pawn on the board. */
    val yardTriesUsed: Int = 0,
    /** The players who have brought every pawn home, by index, first place first. Every player once [TurnPhase.Finished]. */
    val finishingOrder: List<Int> = emptyList(),
    /** How many whole rounds have been played: a turn for every player still in. Counted only where the rules have sudden death. */
    val roundsPlayed: Int = 0,
    /** Where the last standing wins ([GameRules.lastStanding]), the players who have lost every pawn, by index, the first to first. */
    val knockedOut: List<Int> = emptyList(),
    /** Where the rules have power-ups, each one on the board, by its absolute track square. */
    val powerUps: Map<Int, PowerUp> = emptyMap(),
    /** The rolls left before each power-up taken comes back, as another somewhere else. */
    val powerUpsDue: List<Int> = emptyList(),
    /** What the power-ups' chances are drawn from next ([PowerUps]), so that a game plays on the same wherever it's kept. */
    val seed: Long = 0,
    /** What the last action did, for the screen to show and say. Null before the first roll. */
    val lastEvent: GameEvent? = null,
) {
    val board: BoardGeometry get() = rules.board

    val currentPlayer: PlayerState get() = players[current]

    val isFinished: Boolean get() = phase == TurnPhase.Finished

    /** How far sudden death has come: 0 for not yet, 1 with the start squares unsafe, and 2 with the stars unsafe too. */
    val suddenDeathStage: Int
        get() = if (!rules.suddenDeath) 0 else (1..SUDDEN_DEATH_STAGES).count { roundsPlayed >= rules.suddenDeathRound(it, players.size) }

    /** How many rounds are left before sudden death's next stage, or null with none to come. */
    val roundsToSuddenDeath: Int?
        get() = (suddenDeathStage + 1).takeIf { rules.suddenDeath && it <= SUDDEN_DEATH_STAGES }?.let { rules.suddenDeathRound(it, players.size) - roundsPlayed }

    /** Whether [player]'s pawn [pawn] has a shield. */
    fun isShielded(player: Int, pawn: Int): Boolean = pawn in players[player].shielded

    /** Whether the round being played is a stage of sudden death's first. */
    val suddenDeathJustCame: Boolean get() = suddenDeathStage > 0 && roundsPlayed == rules.suddenDeathRound(suddenDeathStage, players.size)

    /** Whether a pawn of [seat] on the absolute track [square] is safe from capture now: by the rules, until sudden death. */
    fun isSafeFor(seat: Int, square: Int): Boolean = isSafe(square) && rules.isSafeFor(seat, square)

    /** Whether the absolute track [square] is a safe square now: a start or star square, until sudden death. */
    fun isSafe(square: Int): Boolean = board.isSafe(square) && suddenDeathStage < if (square % board.seatSpacing == 0) 1 else SUDDEN_DEATH_STAGES

    /** The player in first place (one of the first team's, in a team game), once someone has brought every pawn home. */
    val winner: Int? get() = finishingOrder.firstOrNull()

    /**
     * [player]'s place, 1 for first, once placed: their place in [finishingOrder], or in a team game their team's, its
     * players sharing it. A player (or team) knocked out has theirs at once.
     */
    fun placeOf(player: Int): Int? {
        if (player !in finishingOrder) {
            val out = teamsOut()
            val index = out.indexOf(rules.teamOf(player))
            return if (index < 0) null else players.indices.map(rules::teamOf).distinct().size - index
        }
        val teamsBefore = finishingOrder.takeWhile { rules.teamOf(it) != rules.teamOf(player) }.map(rules::teamOf).distinct()
        return teamsBefore.size + 1
    }

    /** The teams (a player alone is a team of one) whose players have all been knocked out, the first to be first. */
    fun teamsOut(): List<Int> = players.indices.groupBy(rules::teamOf)
        .filterValues { team -> team.all { it in knockedOut } }.entries
        .sortedBy { (_, team) -> team.maxOf(knockedOut::indexOf) }
        .map { it.key }

    /** [player]'s partners, by index: none in a game without teams. */
    fun partnersOf(player: Int): List<Int> = players.indices.filter { rules.arePartners(player, it) }

    /** Where [player]'s pawn [pawn] is. */
    fun position(player: Int, pawn: Int): PawnPosition = PawnPosition.of(players[player].pawns[pawn], board)

    companion object {
        private const val SUDDEN_DEATH_STAGES = 2

        /**
         * A new game of [mode] for [playerCount] players under [rules] (the mode's own by default), every pawn in its
         * yard (or where the rules start them out, on its start square), [first] to roll. The players sit at [seats], in clockwise order: spread round the board by default
         * ([BoardGeometry.seatsFor]). Where the rules have power-ups, [seed] is what their chances are drawn from.
         */
        fun new(
            mode: GameMode,
            playerCount: Int,
            first: Int = 0,
            rules: GameRules = mode.rules,
            seats: List<Int> = rules.board.seatsFor(playerCount),
            seed: Long = 0,
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
                    PlayerState(seat = seat, pawns = List(rules.pawnsPerPlayer) { if (rules.startsOut) 0 else PawnPosition.YARD_PROGRESS })
                },
                current = first,
                phase = TurnPhase.AwaitingRoll,
                seed = seed,
            ).let { if (rules.powerUps) PowerUps.start(it) else it }
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
    /** How many more of their rolls count double ([PowerUp.DOUBLE]). */
    val doubledRolls: Int = 0,
    /** Their pawns with a shield ([PowerUp.SHIELD]), by index. */
    val shielded: Set<Int> = emptySet(),
) {
    /** How many of their pawns are home on [board]. */
    fun pawnsHome(board: BoardGeometry): Int = pawns.count { it == board.homeProgress }

    /** How many of their pawns are still in the game. */
    val pawnsLeft: Int get() = pawns.count { it != PawnPosition.OUT_PROGRESS }

    /** Whether every one of their pawns is home on [board]. */
    fun isFinished(board: BoardGeometry): Boolean = pawns.all { it == board.homeProgress }
}

/** What the game is waiting for. */
sealed interface TurnPhase {
    /** The current player to roll the die. */
    data object AwaitingRoll : TurnPhase

    /**
     * The current player to pick one of [moves] for their [roll]. Never empty: a roll with no move passes. With two dice,
     * [dice] are those still to be moved, [roll] the first of them, and each move says which it uses ([Move.roll]).
     * [earned] is whether the roll has earned another already: a roll-again roll, or a capture or a pawn home with a die
     * already moved. [rolled] is the whole roll, every die, for the screen to show.
     */
    data class AwaitingMove(
        val roll: Int,
        val moves: List<Move>,
        val dice: List<Int> = listOf(roll),
        val earned: Boolean = false,
        val rolled: List<Int> = dice,
    ) : TurnPhase

    /** Every placing is decided. */
    data object Finished : TurnPhase
}

/**
 * A legal move: the current player's pawn [pawn] from progress [from] to [to], or in a team game where they roll for a
 * partner, [owner]'s (null for the mover's own). [captures] lists the opponents' pawns it sends back to their yards, as
 * (player, pawn) pairs.
 */
data class Move(
    val pawn: Int,
    val from: Int,
    val to: Int,
    val captures: List<PawnRef> = emptyList(),
    val owner: Int? = null,
    /** With two dice, the die it moves by; null with one, which is the roll's. */
    val roll: Int? = null,
    /** The progress of the portal it goes in at, coming out at [to]; null for a move through none. */
    val via: Int? = null,
    /** The progress of the shielded pawn it lands on and bounces off, settling at [to]; null for a move that meets none. */
    val bouncedFrom: Int? = null,
    /** The pawns whose shields it breaks, bouncing off each. */
    val brokenShields: List<PawnRef> = emptyList(),
) {
    /** The progress the roll brings the pawn to, before any portal or shield. */
    val rolledTo: Int get() = bouncedFrom ?: via ?: to


    /** Whose pawn moves in [state]: [owner], or the player whose turn it is. */
    fun ownerIn(state: GameState): Int = owner ?: state.current
}

/**
 * What a [powerUp] did for the pawn that ended its move on it: [movedTo] is where it took the pawn (Teleport, Swap), as
 * progress; [captures] and [brokenShields] what that landing did; [swapped] the opponent's pawn swapped with; and [calledUp]
 * the pawn brought out of the yard. Each null or empty where it did none.
 */
data class PowerUpEffect(
    val powerUp: PowerUp,
    val movedTo: Int? = null,
    val captures: List<PawnRef> = emptyList(),
    val brokenShields: List<PawnRef> = emptyList(),
    val swapped: PawnRef? = null,
    val calledUp: Int? = null,
)

/** One pawn in a game: [player]'s pawn [pawn], both indices. */
data class PawnRef(val player: Int, val pawn: Int)

/** What the last action in a game did. */
sealed interface GameEvent {
    /** The player who acted. */
    val player: Int

    /** [player] rolled [roll] (and [second] on a second die) and has a move to make. */
    data class Rolled(override val player: Int, val roll: Int, val second: Int? = null) : GameEvent

    /**
     * [player] rolled [roll] (and [second] on a second die) and had no legal move. With [triesLeft] (of
     * [GameRules.yardTries]) they roll again; otherwise the turn passed (even on a roll-again roll).
     */
    data class NoMove(override val player: Int, val roll: Int, val triesLeft: Int = 0, val second: Int? = null) : GameEvent

    /**
     * [player] rolled [roll] (and [second] on a second die), the roll-again roll that ran out [GameRules.rollAgainLimit],
     * so their turn ended unmoved.
     */
    data class RollAgainLimit(override val player: Int, val roll: Int, val second: Int? = null) : GameEvent

    /**
     * [player] moved with [roll] (with two dice, the die moved). [finished] is true if that brought the moved pawn's
     * owner's last pawn home (theirs, or a partner's they rolled for); [rollsAgain] if the roll, its dice all moved, earned
     * them another (a roll-again roll, a capture, or a pawn home, as the rules give). [knockedOut] are the players whose
     * last pawn it captured, where that puts them out of the game. [effect] is what a power-up it ended on did.
     */
    data class Moved(
        override val player: Int,
        val roll: Int,
        val move: Move,
        val finished: Boolean,
        val rollsAgain: Boolean = false,
        val knockedOut: List<Int> = emptyList(),
        val effect: PowerUpEffect? = null,
    ) : GameEvent {
        /** Where the pawn ended up, by its progress: where the move took it, or a power-up from there. */
        val endedAt: Int get() = effect?.movedTo ?: move.to

        /** Every pawn captured, by the move and by a power-up it ended on. */
        val allCaptures: List<PawnRef> get() = move.captures + effect?.captures.orEmpty()
    }
}
