package net.zodac.ludoplus.game

import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCounter
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.PowerUp

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
class AchievementTracker(
    private val players: List<PlayerConfig>,
    /** The seat this device's player is in: seat 0, but for a guest's in a Nearby game (`.claude/NETPLAY.md`). */
    private val mySeat: Int = 0,
) {

    private val active = players.getOrNull(mySeat)?.type.let { it == PlayerType.HUMAN || it == PlayerType.NEARBY }

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

    /** Player 1's 6s this game. */
    private var gameSixes = 0

    /** Player 1's rolls in a row without a 6. */
    private var rollsWithoutSix = 0

    /** Player 1's 1s in a row. */
    private var onesInARow = 0

    /** Player 1's last roll, and how many times in a row they've rolled it. */
    private var lastRoll = 0
    private var sameInARow = 0

    /** Player 1's last few rolls, in order, one turn after another. */
    private val recentRolls = ArrayDeque<Int>()

    /** Whether this tracker has seen the game from player 1's first roll: [Achievement.WIRE_TO_WIRE] needs all of it. */
    private var seenFromStart = false

    /** Whether an opponent has had more pawns home than player 1. */
    private var everBehind = false

    /** Whether an opponent has had all but one pawn home while player 1 had none. */
    private var trailedBadly = false

    /** Whether player 1 has had all but one pawn home while no opponent had any. */
    private var ledBadly = false

    /** Who brought the game's first pawn home, if this tracker saw it. */
    private var firstHome: Int? = null

    /** Whether player 1 was the last to bring a pawn out of their yard. */
    private var lastOut = false

    /** The players who have captured a pawn of player 1's this game: for [Achievement.STOP_PICKING_ON_ME]. */
    private val capturedBy = mutableSetOf<Int>()

    /** Whether player 1 has never brought a pawn out with another on the track: for [Achievement.ONE_BY_ONE]. */
    private var oneByOne = true

    /** Player 1's moves by the dice of one roll that captured: for [Achievement.TWO_BIRDS_TWO_STONES]. */
    private val rollCaptures = mutableListOf<Move>()

    /** The portal squares each of player 1's pawns has gone in by since it left its yard. */
    private val portalsTaken = mutableMapOf<Int, MutableSet<Int>>()

    /** The power-ups player 1 has taken this game. */
    private val powerUpsTaken = mutableSetOf<PowerUp>()

    /** Whether player 1 has had every pawn on the board at once: for [Achievement.FULL_RETREAT]. */
    private var hadAllOnBoard = false

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
        // None on an endless board.
        val lastTrackSquare = if (board.endless) null else board.trackSteps - 1
        val startsTurn = event !is GameEvent.Moved && !before.rollingAgain
        if (event.player == mySeat) {
            if (event !is GameEvent.Moved) {
                if (startsTurn) {
                    turnCaptures = 0
                    turnHomes = 0
                }
                playerOneRolled(diceOf(event), before, startsTurn, feats)
                rollCaptures.clear()
            }
            when (event) {
                // Three doubles, with two dice, aren't three 6s.
                is GameEvent.RollAgainLimit -> if (event.second == null) feats += Achievement.TOO_MUCH_OF_A_GOOD_THING
                is GameEvent.NoMove -> {
                    val me = before.players[mySeat]
                    if (me.pawnsHome(board) == pawnsPerPlayer - 1 && me.pawns.any { it == board.homeProgress - 1 } && ++nearMisses >= NEAR_MISSES) {
                        feats += Achievement.SO_NEAR_YET_SO_FAR
                    }
                    val now = after.players[mySeat]
                    if (now.turns >= LOCKED_OUT_TURNS && now.pawns.all { it == PawnPosition.YARD_PROGRESS } && now.timesCaptured == 0 && now.timesHaunted == 0) {
                        feats += Achievement.LOCKED_OUT
                    }
                }
                is GameEvent.Moved -> playerOneMoved(event, before, after, lastTrackSquare, feats, counts)
                is GameEvent.Rolled -> Unit
            }
        } else {
            if (startsTurn) theirTurnCaptures = 0
            if (event is GameEvent.Moved) {
                val mine = event.allCaptures.filter { it.player == mySeat }
                if (mine.isNotEmpty()) {
                    capturedBy += event.player
                    if (mine.size >= 2) feats += Achievement.SITTING_DUCKS
                    theirTurnCaptures += mine.size
                    if (theirTurnCaptures >= 2) feats += Achievement.INSULT_TO_INJURY
                    if (mine.any { before.players[mySeat].pawns[it.pawn] == lastTrackSquare }) feats += Achievement.BACK_TO_SQUARE_ONE
                    for (pawn in mine) {
                        val times = (timesEachCaptured[pawn.pawn] ?: 0) + 1
                        timesEachCaptured[pawn.pawn] = times
                        if (times >= GROUNDHOG_CAPTURES) feats += Achievement.GROUNDHOG_DAY
                        sentBack(pawn.pawn)
                    }
                    for (pawn in event.recalled) if (pawn.player == mySeat) recalled(pawn.pawn, after)
                    payback = event.player to after.players[mySeat].turns + 1
                }
            }
        }
        for (caught in after.ghostMove?.caught.orEmpty()) if (caught.player == mySeat) sentBack(caught.pawn)
        if (after.players[mySeat].timesCaptured >= PUNCHING_BAG_CAPTURES) feats += Achievement.PUNCHING_BAG
        if (listOf(before, after).any { state -> state.players[mySeat].pawns.all { it in 0..<board.homeProgress } }) hadAllOnBoard = true
        if (hadAllOnBoard && after.players[mySeat].pawns.all { it == PawnPosition.YARD_PROGRESS }) feats += Achievement.FULL_RETREAT
        if (after.claimed != before.claimed && after.longestRunOf(mySeat) >= COORDINATED_SQUARES) feats += Achievement.COLOUR_COORDINATED
        if (event is GameEvent.Moved && everyPlayerSharesASquare(after)) feats += Achievement.WERE_ALL_FRIENDS_HERE
        if (event is GameEvent.Moved && isOnEveryStart(after)) feats += Achievement.VISITING_THE_NEIGHBOURS
        // A partner isn't an opponent: in a team game, they're racing for player 1's place too.
        val opponents = after.players.indices.filter { it != mySeat && !after.rules.arePartners(mySeat, it) }
        val myHome = after.players[mySeat].pawnsHome(board)
        if (opponents.any { after.players[it].pawnsHome(board) > myHome }) everBehind = true
        if (myHome == 0 && opponents.any { after.players[it].pawnsHome(board) == pawnsPerPlayer - 1 }) trailedBadly = true
        if (myHome == pawnsPerPlayer - 1 && opponents.all { after.players[it].pawnsHome(board) == 0 }) ledBadly = true
        if (before.players.all { it.pawnsHome(board) == 0 } && event is GameEvent.Moved && event.endedAt == board.homeProgress) firstHome = event.player

        var won: Boolean? = null
        if (before.winner == null && after.winner != null) {
            won = after.placeOf(mySeat) == 1
            feats += if (won) wonAchievements(after, opponents) else lostAchievements(after)
            if (won) counts.add(AchievementCounter.GAMES_WON)
        }
        if (before.placeOf(mySeat) == null && after.placeOf(mySeat) != null) {
            counts.add(AchievementCounter.GAMES_PLAYED)
            // The last of the teams, a player alone being a team of one: placed at once when knocked out first.
            if (after.placeOf(mySeat) == after.players.indices.map(after.rules::teamOf).distinct().size) {
                feats += Achievement.WOODEN_SPOON
                counts.add(AchievementCounter.LAST_PLACES)
            }
        }

        feats -= earned
        earned += feats
        if (after.rules.hasHouseRules) pending.clear()
        if (feats.isNotEmpty() || counts.isNotEmpty() || won != null || after.isFinished) {
            for ((counter, count) in pending) counts.add(counter, count)
            pending.clear()
        }
        return GameFeats(feats, counts, won).earnableIn(after)
    }

    /**
     * What [playMillis] of this game on screen earns, with the game now at [state]: [Achievement.TIME_FLIES] from an hour,
     * and [Achievement.SPEED_DEMON] for player 1 placed first within [SPEED_DEMON_MILLIS]. Each once.
     *
     * Shown the play time as each step is made, so a win is seen with the time it was won in: the clock stops then.
     */
    fun playTime(playMillis: Long, state: GameState): GameFeats {
        if (!active) return GameFeats()
        val feats = buildSet {
            if (playMillis >= TIME_FLIES_MILLIS) add(Achievement.TIME_FLIES)
            if (state.winner != null && state.placeOf(mySeat) == 1 && playMillis <= SPEED_DEMON_MILLIS) add(Achievement.SPEED_DEMON)
        } - earned
        earned += feats
        return GameFeats(feats).earnableIn(state)
    }

    /** Player 1's counts not yet handed over, now: for a game leaving the screen, which may never come back. */
    fun flush(): GameFeats = GameFeats(counts = pending.toMap()).also { pending.clear() }

    /** The dice [event] rolled: one, or two. */
    private fun diceOf(event: GameEvent): List<Int> = when (event) {
        is GameEvent.Rolled -> listOfNotNull(event.roll, event.second)
        is GameEvent.NoMove -> listOfNotNull(event.roll, event.second)
        is GameEvent.RollAgainLimit -> listOfNotNull(event.roll, event.second)
        is GameEvent.Moved -> listOf(event.roll)
    }

    /** Player 1's pawn [pawn] is back in its yard: what's kept of its way round starts again. */
    private fun sentBack(pawn: Int) {
        if (pawn == lapPawn) lapPawn = null
        leftYardOnTurn -= pawn
        portalsTaken -= pawn
    }

    /** Player 1's pawn [pawn] is straight back out of its yard, by a banked Call Up: out from their next turn. */
    private fun recalled(pawn: Int, after: GameState) {
        val me = after.players[mySeat]
        leftYardOnTurn[pawn] = me.turns + 1
        lapPawn = null
        if (me.pawns.withIndex().any { (other, progress) -> other != pawn && progress in 0..<after.board.trackSteps }) oneByOne = false
    }

    private fun playerOneRolled(dice: List<Int>, before: GameState, startsTurn: Boolean, feats: MutableSet<Achievement>) {
        pending.add(AchievementCounter.DIE_ROLLS, dice.size)
        // A run of rolls is of one die's: two dice's are kept out of ([Achievement.notEarnableIn]).
        val roll = dice.first()
        val sixes = dice.count { it == SIX }
        val first = startsTurn && before.players[mySeat].turns == 0
        if (first) {
            seenFromStart = true
            if (sixes > 0) feats += Achievement.OFF_THE_MARK
        }
        if (sixes > 0) {
            pending.add(AchievementCounter.SIXES_ROLLED, sixes)
            gameSixes += sixes
            if (gameSixes >= SIX_PACK_SIXES) feats += Achievement.SIX_PACK
            rollsWithoutSix = 0
        } else if (++rollsWithoutSix >= DRY_SPELL_ROLLS) {
            feats += Achievement.DRY_SPELL
        }
        recentRolls.addLast(roll)
        if (recentRolls.size > COUNTING_UP.size) recentRolls.removeFirst()
        if (recentRolls.toList() == COUNTING_UP) feats += Achievement.COUNTING_UP
        onesInARow = if (roll == 1) onesInARow + 1 else 0
        if (onesInARow >= ONES_IN_A_ROW) feats += Achievement.BOTTOM_OF_THE_BARREL
        sameInARow = if (roll == lastRoll) sameInARow + 1 else 1
        lastRoll = roll
        if (sameInARow >= SAME_IN_A_ROW) feats += Achievement.LOADED_DICE
    }

    private fun playerOneMoved(
        event: GameEvent.Moved,
        before: GameState,
        after: GameState,
        lastTrackSquare: Int?,
        feats: MutableSet<Achievement>,
        counts: MutableMap<AchievementCounter, Int>,
    ) {
        val board = after.board
        val move = event.move
        val pawnsPerPlayer = after.rules.pawnsPerPlayer
        val me = before.players[mySeat]
        val turn = after.players[mySeat].turns
        nearMisses = 0
        pending.add(AchievementCounter.SQUARES_MOVED, move.walked)
        val captures = event.allCaptures
        if (captures.isNotEmpty()) {
            counts.add(AchievementCounter.CAPTURES, captures.size)
            feats += Achievement.GOTCHA
            if (captures.size >= 2) feats += Achievement.TWO_FOR_ONE
            if (move.from == 0) feats += Achievement.STRAIGHT_OUT_THE_GATE
            if (me.pawnsHome(board) == pawnsPerPlayer - 1) feats += Achievement.PARTING_SHOT
            turnCaptures += captures.size
            move.roll?.let { rollCaptures += event.move }
            if (rollCaptures.size >= 2) feats += Achievement.TWO_BIRDS_TWO_STONES
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
            if (capturesOf.keys.containsAll(after.players.indices - mySeat)) feats += Achievement.NOBODY_IS_SAFE
            if (after.players[mySeat].captures >= WARPATH_CAPTURES) feats += Achievement.ON_THE_WARPATH
            val (capturer, paybackTurn) = payback ?: (null to null)
            if (captures.any { it.player == capturer } && turn == paybackTurn) feats += Achievement.PAYBACK
        }
        if (move.from == PawnPosition.YARD_PROGRESS) {
            val neverOut: (PlayerState) -> Boolean = { player ->
                player.pawns.all { it == PawnPosition.YARD_PROGRESS } && player.timesCaptured == 0
            }
            val opponents = before.players.indices.filter { it != mySeat && !before.rules.arePartners(mySeat, it) }
            if (neverOut(me)) {
                if (opponents.all { neverOut(before.players[it]) }) feats += Achievement.EARLY_BIRD
                if (opponents.none { neverOut(before.players[it]) }) lastOut = true
            }
            leftYardOnTurn[move.pawn] = turn
            if (me.pawns.any { it in 0..<board.trackSteps }) oneByOne = false
        }
        event.effect?.let { effect ->
            powerUpsTaken += effect.powerUp
            if (powerUpsTaken.size == PowerUp.entries.size) feats += Achievement.ONE_OF_EVERYTHING
            effect.calledUp?.let {
                leftYardOnTurn[it] = turn
                // The pawn that took it is on the track.
                oneByOne = false
            }
        }
        move.via?.let { via ->
            // In by one end having been in by the other: there, and back.
            val taken = portalsTaken.getOrPut(move.pawn) { mutableSetOf() }
            if (board.trackSquare(me.seat, move.to) in taken) feats += Achievement.THERE_AND_BACK_AGAIN
            taken += board.trackSquare(me.seat, via)
        }
        when {
            move.from == PawnPosition.YARD_PROGRESS -> lapPawn = move.pawn
            move.pawn != lapPawn || event.effect?.calledUp != null -> lapPawn = null
        }
        if (event.endedAt == board.homeProgress) {
            counts.add(AchievementCounter.PAWNS_HOME)
            feats += Achievement.HOME_SWEET_HOME
            if (event.roll == 1 && move.to == board.homeProgress) feats += Achievement.INCH_BY_INCH
            if (move.pawn == lapPawn) feats += Achievement.LAP_OF_HONOUR
            lapPawn = null
            if (++turnHomes >= 2) feats += Achievement.DOUBLE_DELIVERY
            leftYardOnTurn.remove(move.pawn)?.let { out ->
                val turns = turn - out + 1
                if (turns <= EXPRESS_TURNS) feats += Achievement.EXPRESS_DELIVERY
            }
        }
        val pawns = after.players[mySeat].pawns
        // The pawns out now, each still on its way since a turn this near: a partner's pawn isn't one of them.
        if (move.owner == null && leftYardOnTurn.size == pawnsPerPlayer && leftYardOnTurn.values.all { it > turn - ESCAPE_TURNS }) feats += Achievement.THE_GREAT_ESCAPE
        if (move.owner == null && seenFromStart && oneByOne && pawns.all { it == board.homeProgress }) feats += Achievement.ONE_BY_ONE
        val onBoard = pawns.filter { it in 0..<board.homeProgress }
        val othersNeverOut = before.players.withIndex().all { (seat, player) ->
            seat == mySeat || player.pawns.all { it == PawnPosition.YARD_PROGRESS } && player.timesCaptured == 0 && player.timesHaunted == 0
        }
        if (othersNeverOut && PawnPosition.YARD_PROGRESS in me.pawns && PawnPosition.YARD_PROGRESS !in pawns) feats += Achievement.HUNGRY_FOR_6S
        if (pawns.distinct().singleOrNull().let { it != null && it in 1..<board.homeProgress }) feats += Achievement.MY_FAVOURITE_SPOT
        if (onBoard.size == pawnsPerPlayer) feats += Achievement.ALL_HANDS_ON_DECK
        if (pawns.all { it in board.trackSteps..<board.homeProgress }) feats += Achievement.FULL_HOUSE
        if (passedOver(before, move) >= LEAPFROG_PAWNS) feats += Achievement.LEAPFROG
    }

    /** Whether one track square holds a pawn of every player in [state]. */
    private fun everyPlayerSharesASquare(state: GameState): Boolean {
        val board = state.board
        return state.players
            .map { player -> player.pawns.filter { it in 0..<board.trackSteps }.map { board.trackSquare(player.seat, it) }.toSet() }
            .reduce { shared, squares -> shared intersect squares }
            .isNotEmpty()
    }

    /** Whether player 1 has a pawn on the start square of every player in [state], their own included. */
    private fun isOnEveryStart(state: GameState): Boolean {
        val board = state.board
        val me = state.players[mySeat]
        val squares = me.pawns.filter { it in 0..<board.trackSteps }.map { board.trackSquare(me.seat, it) }.toSet()
        return state.players.all { board.startSquare(it.seat) in squares }
    }

    /** How many opponents' pawns stand on the track squares [move] passes over, between where it starts and lands. */
    private fun passedOver(state: GameState, move: Move): Int {
        val board = state.board
        val seat = state.players[mySeat].seat
        val squares = (maxOf(move.from + 1, 0) until minOf(move.rolledTo, board.trackSteps)).map { board.trackSquare(seat, it) }.toSet()
        return state.players.withIndex().filter { it.index != mySeat }.sumOf { (_, player) ->
            player.pawns.count { it in 0..<board.trackSteps && board.trackSquare(player.seat, it) in squares }
        }
    }

    /** What player 1's win of [after] earned, against [opponents]. */
    private fun wonAchievements(after: GameState, opponents: List<Int>): Set<Achievement> = buildSet {
        val board = after.board
        val me = after.players[mySeat]
        add(Achievement.FIRST_VICTORY)
        Achievement.entries.filterTo(this) { it.isModeWin && it.mode == after.mode }
        if (me.pawnsLeft >= SCRATCH_PAWNS) add(Achievement.BARELY_A_SCRATCH)
        if (me.timesHaunted == 0) add(Achievement.NOT_A_GHOST_OF_A_CHANCE)
        if (after.rules.houseRules.isNotEmpty() && after.rules.houseRules == HouseRule.entries.filter { it.appliesTo(after.mode.rules) }.toSet()) {
            add(Achievement.EVERYTHING_EVERYWHERE_ALL_AT_ONCE)
        }
        if (trailedBadly) add(Achievement.COMEBACK_KID)
        if (opponents.all { after.players[it].pawnsHome(board) == 0 }) add(Achievement.CLEAN_SWEEP)
        if (me.captures == 0) add(Achievement.PACIFIST)
        if (capturedBy.containsAll(opponents)) add(Achievement.STOP_PICKING_ON_ME)
        if (me.captures >= CONQUEROR_CAPTURES) add(Achievement.CONQUEROR)
        if (me.timesCaptured == 0) add(Achievement.UNTOUCHABLE)
        if (me.timesCaptured >= NEVER_SAY_DIE_CAPTURED) add(Achievement.NEVER_SAY_DIE)
        if (firstHome == mySeat) add(Achievement.FRONT_RUNNER)
        if (lastOut) add(Achievement.LATE_BLOOMER)
        if (seenFromStart && !everBehind) add(Achievement.WIRE_TO_WIRE)
        if (opponents.any { after.players[it].isOneFromFinishing(after) }) add(Achievement.PHOTO_FINISH)
        if (opponents.size == FULL_TABLE_OPPONENTS) {
            val table = opponents.map { players[it] }
            if (table.all { it.type == PlayerType.AI }) {
                if (table.all { it.difficulty == Difficulty.HARD }) add(Achievement.HOW_DO_YOU_DO_FELLOW_CPUS)
            }
        }
        // Whoever they are, people or CPUs, and in teams or not.
        if (after.players.size == BoardGeometry.MAX_PLAYERS) add(Achievement.LIFE_OF_THE_PARTY)
    }

    /** What another player's win of [after] cost player 1. */
    private fun lostAchievements(after: GameState): Set<Achievement> = buildSet {
        if (players[after.winner ?: return@buildSet].type == PlayerType.AI) add(Achievement.I_ROBOT)
        if (ledBadly) add(Achievement.DEFEAT_FROM_THE_JAWS_OF_VICTORY)
        val me = after.players[mySeat]
        if (me.isOneFromFinishing(after)) add(Achievement.PIPPED_TO_THE_POST)
        if (me.pawns.any { it == PawnPosition.YARD_PROGRESS }) add(Achievement.NEVER_LEFT_HOME)
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
        const val LOCKED_OUT_TURNS = 5
        const val PUNCHING_BAG_CAPTURES = 15
        const val COORDINATED_SQUARES = 8
        const val SIX_PACK_SIXES = 30
        const val DRY_SPELL_ROLLS = 15
        const val ONES_IN_A_ROW = 3
        const val SAME_IN_A_ROW = 5
        const val EXPRESS_TURNS = 15
        const val ESCAPE_TURNS = 4
        val COUNTING_UP = (1..SIX).toList()
        const val TIME_FLIES_MILLIS = 60 * 60 * 1_000L

        /** Five minutes of the game on screen (the maintainer, 2026-11-23). */
        const val SPEED_DEMON_MILLIS = 5 * 60 * 1_000L
        const val FULL_TABLE_OPPONENTS = 3
        const val SCRATCH_PAWNS = 4
    }
}
