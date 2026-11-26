package net.zodac.ludoplus.data.records

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** The [GameResult] rows: a finished game's written together, read back for the Leaderboard and Statistics. */
@Dao
interface GameResultDao {

    /** One finished game's rows, in one write: all of them or none. */
    @Insert
    suspend fun insertGame(results: List<GameResult>)

    /**
     * A page of [gameModeId]'s Leaderboard for games of [playerCount]: its wins played under its own rules, fewest turns
     * first, then most captures, then the earliest.
     */
    @Query(
        "SELECT * FROM results WHERE gameModeId = :gameModeId AND playedUnmodified = 1 AND won = 1 AND playerCount = :playerCount " +
            "ORDER BY turns ASC, captures DESC, endedAtEpochMillis ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun leaderboard(gameModeId: String, playerCount: Int, limit: Int, offset: Int): List<GameResult>

    /**
     * [leaderboard] ranked by the most squares walked, then most captures, then the earliest: the wins recorded before the
     * squares were kept (null) after every other.
     */
    @Query(
        "SELECT * FROM results WHERE gameModeId = :gameModeId AND playedUnmodified = 1 AND won = 1 AND playerCount = :playerCount " +
            "ORDER BY squaresMoved IS NULL, squaresMoved DESC, captures DESC, endedAtEpochMillis ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun leaderboardBySquaresMoved(gameModeId: String, playerCount: Int, limit: Int, offset: Int): List<GameResult>

    /** [leaderboard] ranked by the most squares claimed, then most captures, then the earliest. */
    @Query(
        "SELECT * FROM results WHERE gameModeId = :gameModeId AND playedUnmodified = 1 AND won = 1 AND playerCount = :playerCount " +
            "ORDER BY squares DESC, captures DESC, endedAtEpochMillis ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun leaderboardBySquares(gameModeId: String, playerCount: Int, limit: Int, offset: Int): List<GameResult>

    /** How many rows [gameModeId]'s Leaderboard for games of [playerCount] has. */
    @Query("SELECT COUNT(*) FROM results WHERE gameModeId = :gameModeId AND playedUnmodified = 1 AND won = 1 AND playerCount = :playerCount")
    suspend fun leaderboardCount(gameModeId: String, playerCount: Int): Int

    /** Every row, each player's together, in the order they were played: what Statistics adds up. */
    @Query("SELECT * FROM results ORDER BY playerKey, endedAtEpochMillis, id")
    suspend fun allByPlayer(): List<GameResult>

    /** Every row, oldest first: what the cloud save and a progress file hold. */
    @Query("SELECT * FROM results ORDER BY endedAtEpochMillis, id")
    suspend fun all(): List<GameResult>

    /** Another device's rows, in one write: each one this device has already (by its uuid) is left as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMerged(results: List<GameResult>)

    /**
     * The fewest turns, and the newest end, of player 1's wins in [gameModeId] with [playerCount] players under the mode's
     * own rules that ended after [afterEpochMillis]: what its online leaderboard hasn't been sent yet. A null score for none.
     */
    @Query(
        "SELECT MIN(turns) AS score, MAX(endedAtEpochMillis) AS newestEpochMillis FROM results WHERE gameModeId = :gameModeId " +
            "AND playerCount = :playerCount AND playedUnmodified = 1 AND won = 1 AND isPrimaryPlayer = 1 AND endedAtEpochMillis > :afterEpochMillis",
    )
    suspend fun unpostedFewestTurns(gameModeId: String, playerCount: Int, afterEpochMillis: Long): UnpostedWinRow

    /** [unpostedFewestTurns] for the most squares walked, from the wins that kept them. */
    @Query(
        "SELECT MAX(squaresMoved) AS score, MAX(endedAtEpochMillis) AS newestEpochMillis FROM results WHERE gameModeId = :gameModeId " +
            "AND playerCount = :playerCount AND playedUnmodified = 1 AND won = 1 AND isPrimaryPlayer = 1 AND endedAtEpochMillis > :afterEpochMillis " +
            "AND squaresMoved IS NOT NULL",
    )
    suspend fun unpostedMostSquaresMoved(gameModeId: String, playerCount: Int, afterEpochMillis: Long): UnpostedWinRow

    /** [unpostedFewestTurns] for the most squares held. */
    @Query(
        "SELECT MAX(squares) AS score, MAX(endedAtEpochMillis) AS newestEpochMillis FROM results WHERE gameModeId = :gameModeId " +
            "AND playerCount = :playerCount AND playedUnmodified = 1 AND won = 1 AND isPrimaryPlayer = 1 AND endedAtEpochMillis > :afterEpochMillis",
    )
    suspend fun unpostedMostSquares(gameModeId: String, playerCount: Int, afterEpochMillis: Long): UnpostedWinRow

    /** The modes player 1 has finished a game of, with no house rules on. */
    @Query("SELECT DISTINCT gameModeId FROM results WHERE isPrimaryPlayer = 1 AND houseRules = 0")
    suspend fun modesFinished(): List<String>

    @Query("SELECT COUNT(*) FROM results")
    suspend fun count(): Int

    @Query("DELETE FROM results")
    suspend fun clearAll()
}
