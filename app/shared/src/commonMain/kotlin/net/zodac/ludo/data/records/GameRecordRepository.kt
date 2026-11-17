package net.zodac.ludo.data.records

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import net.zodac.ludo.game.nowEpochMillis
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.model.playerNameKey
import net.zodac.ludo.ui.game.StartedGame

/** How many Leaderboard rows make up one page. */
const val LEADERBOARD_PAGE_SIZE = 100

/**
 * The game records: each finished game written once, and read back as each mode's Leaderboard and each player's
 * Statistics.
 */
class GameRecordRepository(
    private val dao: GameResultDao,
    /** Where [playerStatistics] adds up the rows it has read: off the main thread in the app. */
    private val computeContext: CoroutineContext = EmptyCoroutineContext,
) {

    private val writeCount = MutableStateFlow(0L)

    /** How many writes this has made since the app started: a screen showing the records reads them again when it changes. */
    val writes: StateFlow<Long> = writeCount.asStateFlow()

    /**
     * Records the finished [game]'s people, in one write: each one's place (their team's, in a team game), finish time,
     * turns, captures, times captured, and pawns home, with the game's mode, length, and [endedAtEpochMillis]. The
     * computer's players aren't recorded, and a game with no person in it writes nothing.
     */
    suspend fun recordGame(game: StartedGame, endedAtEpochMillis: Long = nowEpochMillis()) {
        val state = game.state
        require(state.isFinished) { "Only a finished game is recorded" }
        val rows = game.players.mapIndexedNotNull { index, player ->
            if (player.type != PlayerType.HUMAN) return@mapIndexedNotNull null
            val side = state.players[index]
            val place = requireNotNull(state.placeOf(index)) { "Player $index isn't placed" }
            GameResult(
                playerName = player.name,
                playerKey = playerNameKey(player.name),
                isPrimaryPlayer = index == 0,
                gameModeId = game.mode.id,
                // A team's win is its players' together, not a race of one: team games stay off the Leaderboard, and so
                // do games with house rules, which stay out of Statistics too.
                playedUnmodified = state.rules.teams == null && !state.rules.hasHouseRules,
                houseRules = state.rules.hasHouseRules,
                playerCount = game.players.size,
                endedAtEpochMillis = endedAtEpochMillis,
                playMillis = game.playMillis,
                place = place,
                won = place == 1,
                finishMillis = game.finishMillis[index] ?: game.playMillis,
                turns = side.turns,
                captures = side.captures,
                timesCaptured = side.timesCaptured,
                pawnsHome = side.pawnsHome(state.board),
            )
        }
        if (rows.isEmpty()) return
        dao.insertGame(rows)
        writeCount.update { it + 1 }
    }

    /**
     * Page [pageIndex] of [gameMode]'s Leaderboard for games of [playerCount]: its fastest wins (fewest turns, then most
     * captures). Each player count has its own, as a bigger board's lap is longer.
     */
    suspend fun leaderboard(gameMode: GameMode, playerCount: Int, pageIndex: Int = 0, pageSize: Int = LEADERBOARD_PAGE_SIZE): List<GameResult> =
        dao.leaderboard(gameMode.id, playerCount, limit = pageSize, offset = pageIndex * pageSize)

    suspend fun leaderboardCount(gameMode: GameMode, playerCount: Int): Int = dao.leaderboardCount(gameMode.id, playerCount)

    /**
     * Opens the database with a read, so it is migrated to this build's schema now: throws whatever the opening does (a
     * migration missing or failing, a schema this build doesn't know).
     */
    suspend fun open() {
        dao.count()
    }

    /** Whether any game has been recorded: a person has finished one. */
    suspend fun hasFinishedGame(): Boolean = dao.count() > 0

    /** Forgets every game: the Leaderboard and Statistics are both empty after. */
    suspend fun reset() {
        dao.clearAll()
        writeCount.update { it + 1 }
    }

    /**
     * Every player's Statistics, in name order: names differing only by case are one player, shown by the spelling of
     * their latest game. Only games of [playerCount] count, or every game for null, and never one with house rules.
     */
    suspend fun playerStatistics(playerCount: Int? = null): List<PlayerStatistics> {
        val rows = dao.allByPlayer().filter { !it.houseRules && (playerCount == null || it.playerCount == playerCount) }
        return withContext(computeContext) { rows.groupBy { it.playerKey }.values.map(::statisticsOf) }
    }
}

/** One player's Statistics: their games added up across every mode, and each mode's on its own ([modes]). */
data class PlayerStatistics(
    /** The name of their latest game. */
    val playerName: String,
    val totals: RecordTotals,
    /** Each mode they've played, in [GameMode] order. A mode this build doesn't know counts in [totals] only. */
    val modes: List<Pair<GameMode, RecordTotals>>,
)

/** A run of games added up. */
data class RecordTotals(
    val gamesPlayed: Int,
    val gamesWon: Int,
    /** The wins in a row at the end of their games: 0 if the latest wasn't a win. */
    val currentWinStreak: Int,
    /** The most wins in a row. */
    val bestWinStreak: Int,
    val captures: Int,
    val timesCaptured: Int,
    val pawnsHome: Int,
    /** The fewest turns of any win, or null with no win. */
    val fewestTurnsToWin: Int?,
) {
    /** The share of games won, from 0 to 1. Zero with no games. */
    val winRate: Double get() = if (gamesPlayed == 0) 0.0 else gamesWon.toDouble() / gamesPlayed
}

/** [rows], one player's, oldest first, added up. */
private fun statisticsOf(rows: List<GameResult>): PlayerStatistics = PlayerStatistics(
    playerName = rows.last().playerName,
    totals = totalsOf(rows),
    modes = GameMode.entries.mapNotNull { mode -> rows.filter { it.gameModeId == mode.id }.takeIf { it.isNotEmpty() }?.let { mode to totalsOf(it) } },
)

/** [rows], oldest first, added up. */
internal fun totalsOf(rows: List<GameResult>): RecordTotals {
    var run = 0
    var best = 0
    for (row in rows) {
        run = if (row.won) run + 1 else 0
        best = maxOf(best, run)
    }
    return RecordTotals(
        gamesPlayed = rows.size,
        gamesWon = rows.count { it.won },
        currentWinStreak = run,
        bestWinStreak = best,
        captures = rows.sumOf { it.captures },
        timesCaptured = rows.sumOf { it.timesCaptured },
        pawnsHome = rows.sumOf { it.pawnsHome },
        fewestTurnsToWin = rows.filter { it.won }.minOfOrNull { it.turns },
    )
}
