package net.zodac.ludo.data.records

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

    private fun row(name: String, won: Boolean, turns: Int, captures: Int, endedAt: Long, mode: String = "classic", unmodified: Boolean = true) =
        GameResult(
            playerName = name, playerKey = name.lowercase(), isPrimaryPlayer = false, gameModeId = mode, playedUnmodified = unmodified,
            playerCount = 4, endedAtEpochMillis = endedAt, playMillis = 1, place = if (won) 1 else 2, won = won, finishMillis = 1,
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
    )

    @Test
    fun `the Leaderboard is the mode's own wins fastest first - and Statistics reads each player's games in order`() = runBlocking {
        dao.insertGame(rows)

        // Fewest turns, then most captures, then the earliest.
        val board = dao.leaderboard("classic", limit = 10, offset = 0)
        assertEquals(listOf(2L, 3L, 1L, 4L), board.map { it.endedAtEpochMillis })
        assertEquals(board.drop(1).take(2), dao.leaderboard("classic", limit = 2, offset = 1))
        assertEquals(4, dao.leaderboardCount("classic"))

        // By player (whatever the name's case), then oldest first.
        assertEquals(listOf(2L, 3L, 1L, 4L, 5L, 6L, 7L), dao.allByPlayer().map { it.endedAtEpochMillis })
        assertEquals(7, dao.count())
        dao.clearAll()
        assertEquals(0, dao.count())
    }
}
