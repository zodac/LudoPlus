package net.zodac.ludo.data.records

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** The [GameResult] rows: a finished game's written together, read back for the Leaderboard and Statistics. */
@Dao
interface GameResultDao {

    /** One finished game's rows, in one write: all of them or none. */
    @Insert
    suspend fun insertGame(results: List<GameResult>)

    /**
     * A page of [gameModeId]'s Leaderboard: its wins played under its own rules, fewest turns first, then most
     * captures, then the earliest.
     */
    @Query(
        "SELECT * FROM results WHERE gameModeId = :gameModeId AND playedUnmodified = 1 AND won = 1 " +
            "ORDER BY turns ASC, captures DESC, endedAtEpochMillis ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun leaderboard(gameModeId: String, limit: Int, offset: Int): List<GameResult>

    /** How many rows [gameModeId]'s Leaderboard has. */
    @Query("SELECT COUNT(*) FROM results WHERE gameModeId = :gameModeId AND playedUnmodified = 1 AND won = 1")
    suspend fun leaderboardCount(gameModeId: String): Int

    /** Every row, each player's together, in the order they were played: what Statistics adds up. */
    @Query("SELECT * FROM results ORDER BY playerKey, endedAtEpochMillis, id")
    suspend fun allByPlayer(): List<GameResult>

    @Query("SELECT COUNT(*) FROM results")
    suspend fun count(): Int

    @Query("DELETE FROM results")
    suspend fun clearAll()
}
