package net.zodac.ludo.game

import net.zodac.ludo.model.Achievement
import net.zodac.ludo.model.AchievementCounter
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.PawnPosition
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType

/**
 * Watches one game being played, a step at a time ([observe]), and says what each step earned player 1 ([GameFeats]):
 * the feats done, the counters to move, and a won or lost game for the streaks. What a single [GameState] can't show (a
 * pawn's lap, the captures of a turn, who last captured player 1, a run of rolls) it keeps itself, for this game only.
 *
 * Made afresh for every game, and for a game continued from its save: what it kept is gone then, which only ever
 * makes an achievement harder, never easier. Each step is observed once, so nothing is counted twice.
 *
 * Player 1's rolls, 6s, and squares moved wait to be counted with the next step that earns anything else,
 * or until [flush], so a roll alone doesn't write the store.
 */
class AchievementTracker(private val players: List<PlayerConfig>) {

    private val active = players.firstOrNull()?.type == PlayerType.HUMAN

    /** Player 1's rolls, 6s, and squares moved not yet handed over: see the class doc. */
    private val pending = mutableMapOf<AchievementCounter, Int>()

    /** Opponents' pawns player 1 has captured in the turn they're playing. */
    private var turnCaptures = 0

    /** Player 1's pawns brought home in the turn they're playing. */
    private var turnHomes = 0

    /** Player 1's pawns captured in the opponent's turn being played. */
    private var theirTurnCaptures = 0

    /** How many of player 1's captures this game were of each opponent's pawns. */
    private val capturesOf = mutableMapOf<Int, Int>()

    /** The opponents' pawns player 1 has captured this game. */
    private val capturedPawns = mutableSetOf<PawnRef>()

    /** How many times each of player 1's pawns has been captured this game. */
    private val timesEachCaptured = mutableMapOf<Int, Int>()

    /** Who last captured one of player 1's pawns, and the number of player 1's turn after it: for [Achievement.PAYBACK]. */
    private var payback: Pair<Int, Int>? = null

    /** The pawn on a lap from its yard with no other pawn of player 1's moved since it left: for [Achievement.LAP_OF_HONOUR]. */
    private var lapPawn: Int? = null

    /** The turn each of player 1's pawns left its yard on, while it's out. */
    private val leftYardOnTurn = mutableMapOf<Int, Int>()

    /** Player 1's turns in a row missed with only their last pawn left, a square from home. */
    private var nearMisses = 0

    /** Player 1's rolls this game, from its first, while that's been seen: null for a game continued from its save. */
    private var firstRolls: MutableList<Int>? = null

    /** Player 1's 6s this game. */
    private var gameSixes = 0

    /** Player 1's rolls in a row without a 6. */
    private var rollsWithoutSix = 0

    /** Player 1's 1s in a row. */
    private var onesInARow = 0

    /** Player 1's turns in a row started with a 6. */
    private var turnsStartedWithSix = 0

    /** Whether an opponent has had all but one pawn home while player 1 had none. */
    private var trailedBadly = false

    /** Whether player 1 has had all but one pawn home while no opponent had any. */
    private var ledBadly = false

    /** Who brought the game's first pawn home, if this tracker saw it. */
    private var firstHome: Int? = null

    /** Whether player 1 was the last to bring a pawn out of their yard. */
    private var lastOut = false

    /** What's been earned already, so it isn't earned again. */
    private val earned = mutableSetOf<Achievement>()

    /** What the step from [before] to [after] (one roll or one move, by [LudoEngine]) earned player 1. */
    fun observe(before: GameState, after: GameState): GameFeats {
        val event = after.lastEvent
        if (!active || event == null || before == after) return GameFeats()
        val feats = mutableSetOf<Achievement>()
        val counts = mutableMapOf<AchievementCounter, Int>()
        val board = after.board
        val pawnsPerPlayer = after.rules.pawnsPerPlayer
        val lastTrackSquare = board.trackSteps - 1
        val startsTurn = event !is GameEvent.Moved && !before.rollingAgain
        if (event.player == PLAYER_ONE) {
            if (event !is GameEvent.Moved) {
                if (startsTurn) {
                    turnCaptures = 0
                    turnHomes = 0
                }
                playerOneRolled(rollOf(event), before, startsTurn, feats)
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
        } else {
            if (startsTurn) theirTurnCaptures = 0
            if (event is GameEvent.Moved) {
                val mine = event.move.captures.filter { it.player == PLAYER_ONE }
                if (mine.isNotEmpty()) {
                    if (mine.size >= 2) feats += Achievement.SITTING_DUCKS
                    theirTurnCaptures += mine.size
                    if (theirTurnCaptures >= 2) feats += Achievement.INSULT_TO_INJURY
                    if (mine.any { before.players[PLAYER_ONE].pawns[it.pawn] == lastTrackSquare }) feats += Achievement.BACK_TO_SQUARE_ONE
                    for (pawn in mine) {
                        val times = (timesEachCaptured[pawn.pawn] ?: 0) + 1
                        timesEachCaptured[pawn.pawn] = times
                        if (times >= GROUNDHOG_CAPTURES) feats += Achievement.GROUNDHOG_DAY
                        if (pawn.pawn == lapPawn) lapPawn = null
                        leftYardOnTurn -= pawn.pawn
                    }
                    payback = event.player to after.players[PLAYER_ONE].turns + 1
                }
            }
        }
        if (after.players[PLAYER_ONE].timesCaptured >= PUNCHING_BAG_CAPTURES) feats += Achievement.PUNCHING_BAG
        val opponents = after.players.indices.filter { it != PLAYER_ONE }
        val myHome = after.players[PLAYER_ONE].pawnsHome(board)
        if (myHome == 0 && opponents.any { after.players[it].pawnsHome(board) == pawnsPerPlayer - 1 }) trailedBadly = true
        if (myHome == pawnsPerPlayer - 1 && opponents.all { after.players[it].pawnsHome(board) == 0 }) ledBadly = true
        if (before.players.all { it.pawnsHome(board) == 0 } && event is GameEvent.Moved && event.move.to == board.homeProgress) firstHome = event.player

        var won: Boolean? = null
        if (before.winner == null && after.winner != null) {
            won = after.winner == PLAYER_ONE
            feats += if (won) wonAchievements(after, opponents) else lostAchievements(after)
            if (won) counts.add(AchievementCounter.GAMES_WON)
        }
        if (PLAYER_ONE !in before.finishingOrder && PLAYER_ONE in after.finishingOrder) {
            counts.add(AchievementCounter.GAMES_PLAYED)
            if (after.isFinished && after.finishingOrder.last() == PLAYER_ONE) {
                feats += Achievement.WOODEN_SPOON
                counts.add(AchievementCounter.LAST_PLACES)
            }
        }

        feats -= earned
        earned += feats
        if (feats.isNotEmpty() || counts.isNotEmpty() || won != null || after.isFinished) {
            for ((counter, count) in pending) counts.add(counter, count)
            pending.clear()
        }
        return GameFeats(feats, counts, won)
    }

    /** Player 1's counts not yet handed over, now: for a game leaving the screen, which may never come back. */
    fun flush(): GameFeats = GameFeats(counts = pending.toMap()).also { pending.clear() }

    private fun rollOf(event: GameEvent): Int = when (event) {
        is GameEvent.Rolled -> event.roll
        is GameEvent.NoMove -> event.roll
        is GameEvent.RollAgainLimit -> event.roll
        is GameEvent.Moved -> event.roll
    }

    private fun playerOneRolled(roll: Int, before: GameState, startsTurn: Boolean, feats: MutableSet<Achievement>) {
        pending.add(AchievementCounter.DIE_ROLLS)
        val first = startsTurn && before.players[PLAYER_ONE].turns == 0
        if (first) {
            firstRolls = mutableListOf()
            if (roll == SIX) feats += Achievement.OFF_THE_MARK
        }
        firstRolls?.let { rolls ->
            rolls += roll
            if (rolls.size == FULL_SPREAD_ROLLS && rolls.toSet() == (1..SIX).toSet()) feats += Achievement.FULL_SPREAD
            if (rolls.size >= FULL_SPREAD_ROLLS) firstRolls = null
        }
        if (roll == SIX) {
            pending.add(AchievementCounter.SIXES_ROLLED)
            if (++gameSixes >= SIX_PACK_SIXES) feats += Achievement.SIX_PACK
            rollsWithoutSix = 0
        } else if (++rollsWithoutSix >= DRY_SPELL_ROLLS) {
            feats += Achievement.DRY_SPELL
        }
        onesInARow = if (roll == 1) onesInARow + 1 else 0
        if (onesInARow >= ONES_IN_A_ROW) feats += Achievement.BOTTOM_OF_THE_BARREL
        if (startsTurn) {
            turnsStartedWithSix = if (roll == SIX) turnsStartedWithSix + 1 else 0
            if (turnsStartedWithSix >= HOT_HAND_TURNS) feats += Achievement.HOT_HAND
        }
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
        val pawnsPerPlayer = after.rules.pawnsPerPlayer
        val me = before.players[PLAYER_ONE]
        val turn = after.players[PLAYER_ONE].turns
        nearMisses = 0
        pending.add(AchievementCounter.SQUARES_MOVED, move.to - move.from)
        val captures = move.captures
        if (captures.isNotEmpty()) {
            counts.add(AchievementCounter.CAPTURES, captures.size)
            feats += Achievement.GOTCHA
            if (captures.size >= 2) feats += Achievement.TWO_FOR_ONE
            if (move.from == 0) feats += Achievement.STRAIGHT_OUT_THE_GATE
            if (me.pawnsHome(board) == pawnsPerPlayer - 1) feats += Achievement.PARTING_SHOT
            turnCaptures += captures.size
            if (turnCaptures >= HAT_TRICK_CAPTURES) feats += Achievement.HAT_TRICK
            for (captured in captures) {
                val of = (capturesOf[captured.player] ?: 0) + 1
                capturesOf[captured.player] = of
                if (of >= PICKING_ON_CAPTURES) feats += Achievement.PICKING_ON_YOU
                if (!capturedPawns.add(captured)) feats += Achievement.DEJA_VU
                val victim = before.players[captured.player]
                val progress = victim.pawns[captured.pawn]
                if (progress == lastTrackSquare) feats += Achievement.NOT_SO_FAST
                if (progress in 1..WELCOME_PARTY_SQUARES) feats += Achievement.WELCOME_PARTY
                if (victim.pawnsHome(board) == pawnsPerPlayer - 1) feats += Achievement.TALL_POPPY
            }
            if (capturesOf.keys.containsAll(after.players.indices - PLAYER_ONE)) feats += Achievement.NOBODY_IS_SAFE
            if (after.players[PLAYER_ONE].captures >= WARPATH_CAPTURES) feats += Achievement.ON_THE_WARPATH
            val (capturer, paybackTurn) = payback ?: (null to null)
            if (captures.any { it.player == capturer } && turn == paybackTurn) feats += Achievement.PAYBACK
        }
        if (move.from == PawnPosition.YARD_PROGRESS) {
            val neverOut: (PlayerState) -> Boolean = { player ->
                player.pawns.all { it == PawnPosition.YARD_PROGRESS } && player.timesCaptured == 0
            }
            val opponents = before.players.indices - PLAYER_ONE
            if (neverOut(me)) {
                if (opponents.all { neverOut(before.players[it]) }) feats += Achievement.EARLY_BIRD
                if (opponents.none { neverOut(before.players[it]) }) lastOut = true
            }
            leftYardOnTurn[move.pawn] = turn
        }
        when {
            move.from == PawnPosition.YARD_PROGRESS -> lapPawn = move.pawn
            move.pawn != lapPawn -> lapPawn = null
        }
        if (move.to == board.homeProgress) {
            counts.add(AchievementCounter.PAWNS_HOME)
            feats += Achievement.HOME_SWEET_HOME
            if (event.roll == 1) feats += Achievement.INCH_BY_INCH
            if (move.pawn == lapPawn) feats += Achievement.LAP_OF_HONOUR
            lapPawn = null
            if (++turnHomes >= 2) feats += Achievement.DOUBLE_DELIVERY
            leftYardOnTurn.remove(move.pawn)?.let { out ->
                val turns = turn - out + 1
                if (turns <= EXPRESS_TURNS) feats += Achievement.EXPRESS_DELIVERY
                if (turns >= SCENIC_TURNS) feats += Achievement.SCENIC_ROUTE
            }
        }
        val pawns = after.players[PLAYER_ONE].pawns
        val onBoard = pawns.filter { it in 0..<board.homeProgress }
        if (onBoard.size != onBoard.toSet().size) feats += Achievement.BUDDY_SYSTEM
        if (onBoard.size == pawnsPerPlayer) feats += Achievement.ALL_HANDS_ON_DECK
        if (pawns.all { it in board.trackSteps..<board.homeProgress }) feats += Achievement.FULL_HOUSE
        if (pawns.all { it in 0..<board.trackSteps && board.isSafe(board.trackSquare(after.players[PLAYER_ONE].seat, it)) }) {
            feats += Achievement.SAFETY_FIRST
        }
        if (passedOver(before, move) >= LEAPFROG_PAWNS) feats += Achievement.LEAPFROG
    }

    /** How many opponents' pawns stand on the track squares [move] passes over, between where it starts and lands. */
    private fun passedOver(state: GameState, move: Move): Int {
        val board = state.board
        val seat = state.players[PLAYER_ONE].seat
        val squares = (maxOf(move.from + 1, 0) until minOf(move.to, board.trackSteps)).map { board.trackSquare(seat, it) }.toSet()
        return state.players.withIndex().filter { it.index != PLAYER_ONE }.sumOf { (_, player) ->
            player.pawns.count { it in 0..<board.trackSteps && board.trackSquare(player.seat, it) in squares }
        }
    }

    /** What player 1's win of [after] earned, against [opponents]. */
    private fun wonAchievements(after: GameState, opponents: List<Int>): Set<Achievement> = buildSet {
        val board = after.board
        val me = after.players[PLAYER_ONE]
        add(Achievement.FIRST_VICTORY)
        if (trailedBadly) add(Achievement.COMEBACK_KID)
        if (opponents.all { after.players[it].pawnsHome(board) == 0 }) add(Achievement.CLEAN_SWEEP)
        if (me.captures == 0) add(Achievement.PACIFIST)
        if (me.captures >= CONQUEROR_CAPTURES) add(Achievement.CONQUEROR)
        if (me.timesCaptured == 0) add(Achievement.UNTOUCHABLE)
        if (me.timesCaptured >= NEVER_SAY_DIE_CAPTURED) add(Achievement.NEVER_SAY_DIE)
        if (me.turns <= SPEED_DEMON_TURNS) add(Achievement.SPEED_DEMON)
        if (me.turns <= LIGHTNING_TURNS) add(Achievement.LIGHTNING_FAST)
        if (firstHome == PLAYER_ONE) add(Achievement.FRONT_RUNNER)
        if (lastOut) add(Achievement.LATE_BLOOMER)
        if (opponents.any { after.players[it].isOneFromFinishing(after) }) add(Achievement.PHOTO_FINISH)
        if (opponents.size == FULL_TABLE_OPPONENTS) {
            val table = opponents.map { players[it] }
            if (table.all { it.type == PlayerType.HUMAN }) add(Achievement.LIFE_OF_THE_PARTY)
            if (table.all { it.type == PlayerType.AI }) {
                add(Achievement.LAST_HUMAN_STANDING)
                if (table.all { it.difficulty == Difficulty.HARD }) add(Achievement.HOW_DO_YOU_DO_FELLOW_CPUS)
                if (table.all { it.difficulty == Difficulty.EASY }) add(Achievement.TRAINING_WHEELS)
            }
        }
    }

    /** What another player's win of [after] cost player 1. */
    private fun lostAchievements(after: GameState): Set<Achievement> = buildSet {
        if (players[after.winner ?: return@buildSet].type == PlayerType.AI) add(Achievement.I_ROBOT)
        if (ledBadly) add(Achievement.DEFEAT_FROM_THE_JAWS_OF_VICTORY)
        val me = after.players[PLAYER_ONE]
        if (me.isOneFromFinishing(after)) add(Achievement.PIPPED_TO_THE_POST)
        if (me.pawns.all { it == PawnPosition.YARD_PROGRESS }) add(Achievement.NEVER_LEFT_HOME)
    }

    /** Whether this player has every pawn home but one, and that one in their home column. */
    private fun PlayerState.isOneFromFinishing(state: GameState): Boolean {
        val board = state.board
        return pawnsHome(board) == state.rules.pawnsPerPlayer - 1 && pawns.any { it in board.trackSteps..<board.homeProgress }
    }

    private fun MutableMap<AchievementCounter, Int>.add(counter: AchievementCounter, by: Int = 1) {
        this[counter] = (this[counter] ?: 0) + by
    }

    private companion object {
        const val PLAYER_ONE = 0
        const val SIX = 6

        const val HAT_TRICK_CAPTURES = 3
        const val PICKING_ON_CAPTURES = 4
        const val WARPATH_CAPTURES = 10
        const val CONQUEROR_CAPTURES = 10
        const val NEVER_SAY_DIE_CAPTURED = 10
        const val GROUNDHOG_CAPTURES = 3
        const val WELCOME_PARTY_SQUARES = 6
        const val LEAPFROG_PAWNS = 3
        const val NEAR_MISSES = 3
        const val LOCKED_OUT_TURNS = 15
        const val PUNCHING_BAG_CAPTURES = 15
        const val FULL_SPREAD_ROLLS = 6
        const val SIX_PACK_SIXES = 30
        const val DRY_SPELL_ROLLS = 30
        const val ONES_IN_A_ROW = 3
        const val HOT_HAND_TURNS = 3
        const val EXPRESS_TURNS = 12
        const val SCENIC_TURNS = 60

        /** About the fastest tenth of wins against Medium CPUs (simulated, 2026-11-17): the average is about 80. */
        const val SPEED_DEMON_TURNS = 60

        /** About the fastest hundredth of wins against Medium CPUs (simulated, 2026-11-17). */
        const val LIGHTNING_TURNS = 50
        const val FULL_TABLE_OPPONENTS = 3
    }
}
