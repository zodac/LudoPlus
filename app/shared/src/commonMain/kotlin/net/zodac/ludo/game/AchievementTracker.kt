package net.zodac.ludo.game

import net.zodac.ludo.model.Achievement
import net.zodac.ludo.model.AchievementCounter
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.PawnPosition
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType

/**
 * Watches one game being played, a step at a time ([observe]), and says what each step earned player 1 ([GameFeats]):
 * the feats done, the counters to move, and a won or broken win streak. What a single [GameState] can't show (a
 * pawn's lap, the captures of a turn, who last captured player 1) it keeps itself, for this game only.
 *
 * Made afresh for every game, and for a game continued from its save: what it kept is gone then, which only ever
 * makes an achievement harder, never easier. Each step is observed once, so nothing is counted twice.
 *
 * Player 1's rolls ([AchievementCounter.DIE_ROLLS]) wait to be counted with the next step that earns anything else,
 * or until [flush], so a roll alone doesn't write the store.
 */
class AchievementTracker(private val players: List<PlayerConfig>) {

    private val active = players.firstOrNull()?.type == PlayerType.HUMAN

    /** Player 1's rolls not yet counted. */
    private var pendingRolls = 0

    /** Opponents' pawns player 1 has captured in the turn they're playing. */
    private var turnCaptures = 0

    /** The opponents player 1 has captured a pawn of this game. */
    private val capturedOpponents = mutableSetOf<Int>()

    /** The opponents' pawns player 1 has captured this game. */
    private val capturedPawns = mutableSetOf<PawnRef>()

    /** Who last captured one of player 1's pawns, and the number of player 1's turn after it: for [Achievement.PAYBACK]. */
    private var payback: Pair<Int, Int>? = null

    /** The pawn on a lap from its yard with no other pawn of player 1's moved since it left: for [Achievement.LAP_OF_HONOUR]. */
    private var lapPawn: Int? = null

    /** Player 1's turns in a row missed with only their last pawn left, a square from home. */
    private var nearMisses = 0

    /** Whether an opponent has had all but one pawn home while player 1 had none. */
    private var trailedBadly = false

    /** Whether player 1 has had all but one pawn home while no opponent had any. */
    private var ledBadly = false

    /** What's been earned already, so it isn't earned again. */
    private val earned = mutableSetOf<Achievement>()

    /** What the step from [before] to [after] (one roll or one move, by [LudoEngine]) earned player 1. */
    fun observe(before: GameState, after: GameState): GameFeats {
        val event = after.lastEvent
        if (!active || event == null || before == after) return GameFeats()
        val feats = mutableSetOf<Achievement>()
        val counts = mutableMapOf<AchievementCounter, Int>()
        fun count(counter: AchievementCounter, by: Int = 1) {
            counts[counter] = (counts[counter] ?: 0) + by
        }
        val board = after.board
        val pawnsPerPlayer = after.rules.pawnsPerPlayer
        val lastTrackSquare = board.trackSteps - 1
        if (event.player == PLAYER_ONE) {
            if (event !is GameEvent.Moved) {
                pendingRolls++
                if (!before.rollingAgain) turnCaptures = 0
            }
            when (event) {
                is GameEvent.RollAgainLimit -> feats += Achievement.TOO_MUCH_OF_A_GOOD_THING
                is GameEvent.NoMove -> {
                    val me = before.players[PLAYER_ONE]
                    if (me.pawnsHome(board) == pawnsPerPlayer - 1 && me.pawns.any { it == board.homeProgress - 1 } && ++nearMisses >= NEAR_MISSES) {
                        feats += Achievement.SO_NEAR_YET_SO_FAR
                    }
                    val now = after.players[PLAYER_ONE]
                    if (now.turns >= LOCKED_OUT_TURNS && now.pawns.all { it == PawnPosition.YARD_PROGRESS } && now.timesCaptured == 0) {
                        feats += Achievement.LOCKED_OUT
                    }
                }
                is GameEvent.Moved -> playerOneMoved(event, before, after, lastTrackSquare, feats, counts)
                is GameEvent.Rolled -> Unit
            }
        } else if (event is GameEvent.Moved) {
            val mine = event.move.captures.filter { it.player == PLAYER_ONE }
            if (mine.isNotEmpty()) {
                if (mine.size >= 2) feats += Achievement.SITTING_DUCKS
                if (mine.any { before.players[PLAYER_ONE].pawns[it.pawn] == lastTrackSquare }) feats += Achievement.BACK_TO_SQUARE_ONE
                if (mine.any { it.pawn == lapPawn }) lapPawn = null
                payback = event.player to after.players[PLAYER_ONE].turns + 1
            }
        }
        if (after.players[PLAYER_ONE].timesCaptured >= PUNCHING_BAG_CAPTURES) feats += Achievement.PUNCHING_BAG
        val opponents = after.players.indices.filter { it != PLAYER_ONE }
        val myHome = after.players[PLAYER_ONE].pawnsHome(board)
        if (myHome == 0 && opponents.any { after.players[it].pawnsHome(board) == pawnsPerPlayer - 1 }) trailedBadly = true
        if (myHome == pawnsPerPlayer - 1 && opponents.all { after.players[it].pawnsHome(board) == 0 }) ledBadly = true

        var streak: Boolean? = null
        if (before.winner == null && after.winner != null) {
            streak = after.winner == PLAYER_ONE
            feats += if (streak) wonAchievements(after, opponents) else lostAchievements(after)
            if (streak) count(AchievementCounter.GAMES_WON)
        }
        if (PLAYER_ONE !in before.finishingOrder && PLAYER_ONE in after.finishingOrder) {
            count(AchievementCounter.GAMES_PLAYED)
            if (after.isFinished && after.finishingOrder.last() == PLAYER_ONE) feats += Achievement.WOODEN_SPOON
        }

        feats -= earned
        earned += feats
        if (feats.isNotEmpty() || counts.isNotEmpty() || streak != null || after.isFinished) {
            if (pendingRolls > 0) count(AchievementCounter.DIE_ROLLS, pendingRolls)
            pendingRolls = 0
        }
        return GameFeats(feats, counts, streak)
    }

    /** Player 1's rolls not yet counted, now: for a game leaving the screen, which may never come back. */
    fun flush(): GameFeats {
        if (pendingRolls == 0) return GameFeats()
        return GameFeats(counts = mapOf(AchievementCounter.DIE_ROLLS to pendingRolls)).also { pendingRolls = 0 }
    }

    private fun playerOneMoved(
        event: GameEvent.Moved,
        before: GameState,
        after: GameState,
        lastTrackSquare: Int,
        feats: MutableSet<Achievement>,
        counts: MutableMap<AchievementCounter, Int>,
    ) {
        val board = after.board
        val move = event.move
        nearMisses = 0
        val captures = move.captures
        if (captures.isNotEmpty()) {
            counts[AchievementCounter.CAPTURES] = (counts[AchievementCounter.CAPTURES] ?: 0) + captures.size
            feats += Achievement.GOTCHA
            if (captures.size >= 2) feats += Achievement.TWO_FOR_ONE
            turnCaptures += captures.size
            if (turnCaptures >= HAT_TRICK_CAPTURES) feats += Achievement.HAT_TRICK
            for (captured in captures) {
                capturedOpponents += captured.player
                if (!capturedPawns.add(captured)) feats += Achievement.DEJA_VU
                if (before.players[captured.player].pawns[captured.pawn] == lastTrackSquare) feats += Achievement.NOT_SO_FAST
            }
            if (capturedOpponents.containsAll(after.players.indices - PLAYER_ONE)) feats += Achievement.NOBODY_IS_SAFE
            val (capturer, turn) = payback ?: (null to null)
            if (captures.any { it.player == capturer } && after.players[PLAYER_ONE].turns == turn) feats += Achievement.PAYBACK
        }
        when {
            move.from == PawnPosition.YARD_PROGRESS -> lapPawn = move.pawn
            move.pawn != lapPawn -> lapPawn = null
        }
        if (move.to == board.homeProgress) {
            counts[AchievementCounter.PAWNS_HOME] = (counts[AchievementCounter.PAWNS_HOME] ?: 0) + 1
            feats += Achievement.HOME_SWEET_HOME
            if (event.roll == 1) feats += Achievement.INCH_BY_INCH
            if (move.pawn == lapPawn) feats += Achievement.LAP_OF_HONOUR
            lapPawn = null
        }
        val onBoard = after.players[PLAYER_ONE].pawns.filter { it in 0..<board.homeProgress }
        if (onBoard.size != onBoard.toSet().size) feats += Achievement.BUDDY_SYSTEM
        if (onBoard.size == after.rules.pawnsPerPlayer) feats += Achievement.ALL_HANDS_ON_DECK
    }

    /** What player 1's win of [after] earned, against [opponents]. */
    private fun wonAchievements(after: GameState, opponents: List<Int>): Set<Achievement> = buildSet {
        val board = after.board
        val me = after.players[PLAYER_ONE]
        add(Achievement.FIRST_VICTORY)
        if (trailedBadly) add(Achievement.COMEBACK_KID)
        if (opponents.all { after.players[it].pawnsHome(board) == 0 }) add(Achievement.CLEAN_SWEEP)
        if (me.captures == 0) add(Achievement.PACIFIST)
        if (me.timesCaptured == 0) add(Achievement.UNTOUCHABLE)
        if (me.turns <= SPEED_DEMON_TURNS) add(Achievement.SPEED_DEMON)
        if (opponents.any { after.players[it].isOneFromFinishing(after) }) add(Achievement.PHOTO_FINISH)
        val cpus = opponents.map { players[it] }.filter { it.type == PlayerType.AI }
        if (opponents.size == FULL_TABLE_OPPONENTS && cpus.size == opponents.size) {
            add(Achievement.LAST_HUMAN_STANDING)
            if (cpus.all { it.difficulty == Difficulty.HARD }) add(Achievement.HOW_DO_YOU_DO_FELLOW_CPUS)
        }
    }

    /** What another player's win of [after] cost player 1. */
    private fun lostAchievements(after: GameState): Set<Achievement> = buildSet {
        if (players[after.winner ?: return@buildSet].type == PlayerType.AI) add(Achievement.I_ROBOT)
        if (ledBadly) add(Achievement.DEFEAT_FROM_THE_JAWS_OF_VICTORY)
        if (after.players[PLAYER_ONE].isOneFromFinishing(after)) add(Achievement.PIPPED_TO_THE_POST)
    }

    /** Whether this player has every pawn home but one, and that one in their home column. */
    private fun PlayerState.isOneFromFinishing(state: GameState): Boolean {
        val board = state.board
        return pawnsHome(board) == state.rules.pawnsPerPlayer - 1 && pawns.any { it in board.trackSteps..<board.homeProgress }
    }

    private companion object {
        const val PLAYER_ONE = 0
        const val HAT_TRICK_CAPTURES = 3
        const val NEAR_MISSES = 3
        const val LOCKED_OUT_TURNS = 15
        const val PUNCHING_BAG_CAPTURES = 15

        /** About the fastest tenth of wins against Medium CPUs (simulated, 2026-11-17): the average is about 80. */
        const val SPEED_DEMON_TURNS = 60
        const val FULL_TABLE_OPPONENTS = 3
    }
}
