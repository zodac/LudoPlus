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
    /** Where the rules have power-ups, how many turns until a new one comes, as the last of them starts. */
    val turnsToPowerUp: Int = 0,
    /** Where the rules have ghosts, the absolute track square each is on. */
    val ghosts: List<Int> = emptyList(),
    /** How many turns have ended since a ghost last moved. */
    val turnsSinceGhost: Int = 0,
    /** The ghost's move that came after the last action, or null where it ended no turn or none was due. */
    val ghostMove: GhostMove? = null,
    /** Where the rules have sudden death, the absolute track squares its last stage has taken: pawns step over them ([stepOn]). */
    val lostSquares: Set<Int> = emptySet(),
    /** The squares of [lostSquares] lost as the last action ended a turn: empty where it ended none, or took none. */
    val justLost: List<Int> = emptyList(),
    /** How many turns have ended since sudden death came to its second stage, which its last comes by ([Cataclysm]). */
    val suddenDeathTurns: Int = 0,
    /** Where the rules claim squares ([GameRules.claims]), the player each claimed one is, by its absolute track square. */
    val claimed: Map<Int, Int> = emptyMap(),
    /** What the power-ups', ghosts', and lost squares' chances are drawn from next, so that a game plays on the same wherever it's kept. */
    val seed: Long = 0,
    /** What the last action did, for the screen to show and say. Null before the first roll. */
    val lastEvent: GameEvent? = null,
) {
    val board: BoardGeometry get() = rules.board

    val currentPlayer: PlayerState get() = players[current]

    val isFinished: Boolean get() = phase == TurnPhase.Finished

    /**
     * How far sudden death has come: 0 for not yet; 1, Overtime, with the start squares unsafe; 2, Sudden Death, with the
     * stars unsafe too; and 3, the Cataclysm, with the track vanishing ([Cataclysm]).
     */
    val suddenDeathStage: Int
        get() = when {
            !rules.suddenDeath -> 0
            suddenDeathTurns >= Cataclysm.STARTS -> CATACLYSM_STAGE
            else -> (1..GameRules.NOWHERE_SAFE_STAGE).count { roundsPlayed >= rules.suddenDeathRound(it, players.size) }
        }

    /** Whether sudden death has come to its last stage, the Cataclysm. */
    val cataclysm: Boolean get() = suddenDeathStage == CATACLYSM_STAGE

    /** In sudden death's second stage, how many turns are left before the Cataclysm. */
    val turnsToCataclysm: Int? get() = if (suddenDeathStage == GameRules.NOWHERE_SAFE_STAGE) Cataclysm.STARTS - suddenDeathTurns else null

    /** In the Cataclysm, how many turns are left before squares next vanish, or null with no more to. */
    val turnsToLoss: Int? get() = if (cataclysm && !Cataclysm.isOver(this)) Cataclysm.TURNS - (suddenDeathTurns - Cataclysm.STARTS) % Cataclysm.TURNS else null

    /** The progress of a pawn of [player] at progress [from] (on the track, or home column) moved [steps] squares on, over any [lostSquares]. */
    fun stepOn(player: Int, from: Int, steps: Int): Int {
        if (lostSquares.isEmpty()) return from + steps
        var at = from
        repeat(steps) {
            do at++ while (isLost(player, at))
        }
        return at
    }

    /** Whether the track square at [progress] from [player]'s start is one of the [lostSquares]. */
    fun isLost(player: Int, progress: Int): Boolean = board.trackSquare(players[player].seat, progress) in lostSquares

    /** How many rounds are left before sudden death's next stage, or null with none to come by rounds. */
    val roundsToSuddenDeath: Int?
        get() = (suddenDeathStage + 1).takeIf { rules.suddenDeath && it <= GameRules.NOWHERE_SAFE_STAGE }?.let { rules.suddenDeathRound(it, players.size) - roundsPlayed }

    /** Whether [player]'s pawn [pawn] has a shield. */
    fun isShielded(player: Int, pawn: Int): Boolean = pawn in players[player].shielded

    /** Whether sudden death has just come to its stage: for the round its first comes in, its second's first few turns, and its last's first. */
    val suddenDeathJustCame: Boolean
        get() = when (suddenDeathStage) {
            0 -> false
            1 -> roundsPlayed == rules.suddenDeathRound(1, players.size)
            GameRules.NOWHERE_SAFE_STAGE -> suddenDeathTurns < JUST_CAME_TURNS
            else -> suddenDeathTurns == Cataclysm.STARTS
        }

    /** Whether a pawn of [seat] on the absolute track [square] is safe from capture now: by the rules, until sudden death. */
    fun isSafeFor(seat: Int, square: Int): Boolean = isSafe(square) && rules.isSafeFor(seat, square)

    /** Whether the absolute track [square] is a safe square now: a start or star square, until sudden death. */
    fun isSafe(square: Int): Boolean = board.isSafe(square) && suddenDeathStage < if (square % board.seatSpacing == 0) 1 else 2

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

    /**
     * The absolute track square a pawn of [player] with [progress] can claim, where squares are ([GameRules.claims]): null
     * off the track, and on a start or star square.
     */
    fun claimableSquare(player: Int, progress: Int): Int? {
        if (!rules.claims || progress !in 0..<board.trackSteps) return null
        return board.trackSquare(players[player].seat, progress).takeUnless(board::isSafe)
    }

    /** The most squares in a row [player] has claimed, round the whole track, start and star squares passed over. */
    fun longestRunOf(player: Int): Int {
        val mine = (0..<board.trackLength).filterNot(board::isSafe).map { claimed[it] == player }
        if (mine.all { it }) return mine.size
        var run = 0
        // Twice round, for a run over the end of the numbering.
        return (mine + mine).maxOf { held -> run = if (held) run + 1 else 0; run }
    }

    /** How many squares [player] has claimed. */
    fun squaresOf(player: Int): Int = claimed.count { it.value == player }

    /** How far [player]'s pawns have come: each one's steps from its yard, added up. */
    fun stepsOf(player: Int): Int = players[player].pawns.sumOf { it - PawnPosition.YARD_PROGRESS }

    /**
     * Each player's rank as the game stands, 1 for first, by index: their place once placed ([placeOf]), and after those
     * placed, the most pawns left first where the last standing wins, the most squares where they're claimed, or else the furthest along ([stepsOf]). A team's
     * players count together and share its rank, and a tie shares one, the next rank down left out. Null while everyone
     * is level.
     */
    fun ranks(): List<Int>? {
        val placedFirst = finishingOrder.map(rules::teamOf).distinct().size
        val score = players.indices.groupBy(rules::teamOf)
            .filterValues { team -> placeOf(team.first()) == null }
            .mapValues { (_, team) -> team.sumOf { scoreOf(it) } }
        val ranks = players.indices.map { player ->
            placeOf(player) ?: (placedFirst + 1 + score.values.count { it > score.getValue(rules.teamOf(player)) })
        }
        return ranks.takeIf { it.distinct().size > 1 }
    }

    /** What [player] is ranked by as the game stands ([ranks]). */
    private fun scoreOf(player: Int): Int = when {
        rules.lastStanding -> players[player].pawnsLeft
        rules.claims -> squaresOf(player)
        else -> stepsOf(player)
    }

    /** [player]'s partners, by index: none in a game without teams. */
    fun partnersOf(player: Int): List<Int> = players.indices.filter { rules.arePartners(player, it) }

    /** Where [player]'s pawn [pawn] is. */
    fun position(player: Int, pawn: Int): PawnPosition = PawnPosition.of(players[player].pawns[pawn], board)

    companion object {
        /** The last stage of sudden death, the Cataclysm. */
        private const val CATACLYSM_STAGE = 3

        /** How many turns sudden death's second stage has just come for. */
        private const val JUST_CAME_TURNS = 3

        /**
         * A new game of [mode] for [playerCount] players under [rules] (the mode's own by default), every pawn in its
         * yard (or where the rules start them out, on its start square), [first] to roll. The players sit at [seats], in clockwise order: spread round the board by default
         * ([BoardGeometry.seatsFor]). Where the rules have power-ups, ghosts, or sudden death, [seed] is what their chances are
         * drawn from.
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
            ).let { if (rules.powerUps) PowerUps.start(it) else it }.let { if (rules.ghosts) Ghosts.start(it) else it }
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
    /** How many of their pawns ghosts have sent back to their yard. */
    val timesHaunted: Int = 0,
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
     * already moved. [rolled] is the whole roll, every die, for the screen to show. While [swapping], a pawn has landed on a Swap,
     * and each of [moves] is it swapping with a pawn, or keeping its place ([Move.swap]): its die is still one of [dice].
     */
    data class AwaitingMove(
        val roll: Int,
        val moves: List<Move>,
        val dice: List<Int> = listOf(roll),
        val earned: Boolean = false,
        val rolled: List<Int> = dice,
        val swapping: Boolean = false,
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
    /**
     * For a pawn that has landed on a Swap ([TurnPhase.AwaitingMove.swapping]), [from] and [to] being where it stands, the pawn it
     * swaps places with: itself to leave every pawn where it is. Null for any other move. (A game of 1.1.0's logged the landing and its swap as one move.)
     */
    val swap: PawnRef? = null,
    /** The progress of each lost square it steps over ([GameState.lostSquares]), in order. */
    val skipped: List<Int> = emptyList(),
    /** The pawns whose shields it breaks, bouncing off each. */
    val brokenShields: List<PawnRef> = emptyList(),
) {
    /** The progress the roll brings the pawn to, before any portal or shield. */
    val rolledTo: Int get() = bouncedFrom ?: via ?: to

    /** How many squares the roll moves the pawn, the lost ones it steps over not counted. */
    val squares: Int get() = rolledTo - from - skipped.size

    /** Whose pawn moves in [state]: [owner], or the player whose turn it is. */
    fun ownerIn(state: GameState): Int = owner ?: state.current
}

/**
 * What a [powerUp] did for the pawn that ended its move on it: [movedTo] is where it took the pawn (Swap), as
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

        /** Whether it's no more than the choice not to swap: its pawn, landed on a Swap, keeps its place ([Move.swap]). */
        val keptPlace: Boolean get() = move.swap == PawnRef(move.owner ?: player, move.pawn)

        /** Every pawn captured, by the move and by a power-up it ended on. */
        val allCaptures: List<PawnRef> get() = move.captures + effect?.captures.orEmpty()
    }
}
