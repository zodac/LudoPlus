package net.zodac.ludoplus.data.records

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real queries, on Android's SQLite: the Leaderboard's filter and order, and Statistics' row order. The shared
 * tests run on an in-memory stand-in (`InMemoryGameResultDao`) held to the same orders by `GameRecordRepositoryTest`.
 */
@RunWith(AndroidJUnit4::class)
class GameResultDaoTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext<Context>())
        .allowMainThreadQueries()
        .buildAppDatabase()
    private val dao = database.gameResultDao()

    @After
    fun close() = database.close()

    private fun row(name: String, won: Boolean, turns: Int, captures: Int, endedAt: Long, mode: String = "classic", unmodified: Boolean = true, players: Int = 4) =
        GameResult(
            playerName = name, playerKey = name.lowercase(), isPrimaryPlayer = false, gameModeId = mode, playedUnmodified = unmodified,
            playerCount = players, endedAtEpochMillis = endedAt, playMillis = 1, place = if (won) 1 else 2, won = won, finishMillis = 1,
            turns = turns, captures = captures, timesCaptured = 0, pawnsHome = 4,
        )

    private val rows = listOf(
        row("Sam", won = true, turns = 43, captures = 1, endedAt = 1),
        row("alex", won = true, turns = 40, captures = 0, endedAt = 2),
        row("Alex", won = true, turns = 43, captures = 2, endedAt = 3),
        row("Sam", won = true, turns = 43, captures = 1, endedAt = 4),
        // Not on the board: a loss, another mode's win, and a modified game's win.
        row("Sam", won = false, turns = 30, captures = 9, endedAt = 5),
        row("Sam", won = true, turns = 30, captures = 9, endedAt = 6, mode = "other"),
        row("Sam", won = true, turns = 30, captures = 9, endedAt = 7, unmodified = false),
        // On the 6-player board, not the 4-player one.
        row("Sam", won = true, turns = 30, captures = 9, endedAt = 8, players = 6),
    )

    @Test
    fun `the Leaderboard is the mode's own wins fastest first - and Statistics reads each player's games in order`() = runBlocking {
        dao.insertGame(rows)

        // Fewest turns, then most captures, then the earliest.
        val board = dao.leaderboard("classic", playerCount = 4, limit = 10, offset = 0)
        assertEquals(listOf(2L, 3L, 1L, 4L), board.map { it.endedAtEpochMillis })
        assertEquals(board.drop(1).take(2), dao.leaderboard("classic", playerCount = 4, limit = 2, offset = 1))
        assertEquals(4, dao.leaderboardCount("classic", playerCount = 4))
        // Each player count's board is its own: the 6-player win is on that one only.
        assertEquals(listOf(8L), dao.leaderboard("classic", playerCount = 6, limit = 10, offset = 0).map { it.endedAtEpochMillis })
        assertEquals(1, dao.leaderboardCount("classic", playerCount = 6))

        // By pawns left: the fewest captured, then most captures, then the earliest.
        dao.insertGame(listOf(5 to 9, 1 to 2, 1 to 4, 1 to 4).mapIndexed { i, (lost, captures) ->
            row("Jo", won = true, turns = 50 - i, captures = captures, endedAt = 20L + i, mode = "survival").copy(timesCaptured = lost)
        })
        assertEquals(listOf(22L, 23L, 21L, 20L), dao.leaderboardByPawnsLeft("survival", playerCount = 4, limit = 10, offset = 0).map { it.endedAtEpochMillis })
        dao.clearAll()
        dao.insertGame(rows)

        // By player (whatever the name's case), then oldest first.
        assertEquals(listOf(2L, 3L, 1L, 4L, 5L, 6L, 7L, 8L), dao.allByPlayer().map { it.endedAtEpochMillis })
        assertEquals(8, dao.count())
        dao.clearAll()
        assertEquals(0, dao.count())
    }
}
