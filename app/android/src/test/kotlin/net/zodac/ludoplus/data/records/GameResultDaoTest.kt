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

        // By squares travelled: the most, then most captures, then the earliest; a win from before they were kept last.
        dao.insertGame(listOf(null to 9, 300 to 9, 520 to 2, 520 to 4, 520 to 4).mapIndexed { i, (travelled, captures) ->
            row("Jo", won = true, turns = 50 - i, captures = captures, endedAt = 20L + i, mode = "survival").copy(squaresMoved = travelled)
        })
        assertEquals(listOf(23L, 24L, 22L, 21L, 20L), dao.leaderboardBySquaresMoved("survival", playerCount = 4, limit = 10, offset = 0).map { it.endedAtEpochMillis })

        // By squares: the most, then most captures, then the earliest.
        dao.insertGame(listOf(20 to 1, 31 to 2, 31 to 4, 31 to 4).mapIndexed { i, (squares, captures) ->
            row("Jo", won = true, turns = 50 - i, captures = captures, endedAt = 30L + i, mode = "colour_rush").copy(squares = squares)
        })
        assertEquals(listOf(32L, 33L, 31L, 30L), dao.leaderboardBySquares("colour_rush", playerCount = 4, limit = 10, offset = 0).map { it.endedAtEpochMillis })
        dao.clearAll()
        dao.insertGame(rows)

        // By player (whatever the name's case), then oldest first.
        assertEquals(listOf(2L, 3L, 1L, 4L, 5L, 6L, 7L, 8L), dao.allByPlayer().map { it.endedAtEpochMillis })
        assertEquals(8, dao.count())
        dao.clearAll()
        assertEquals(0, dao.count())
    }

    @Test
    fun `a board's unsent best is player 1's by its mode's ranking - from their own-rules wins after the time - the squares moved from wins that kept them`() = runBlocking {
        val win = { mode: String, turns: Int, endedAt: Long -> row("Sam", won = true, turns = turns, captures = 0, endedAt = endedAt, mode = mode).copy(isPrimaryPlayer = true) }
        dao.insertGame(
            listOf(
                win("classic", 44, 10), win("classic", 38, 20), win("classic", 30, 5),
                // Not player 1's, a loss, and not under the mode's own rules: none of them sent.
                win("classic", 20, 30).copy(isPrimaryPlayer = false), win("classic", 20, 31).copy(won = false), win("classic", 20, 32).copy(playedUnmodified = false),
                win("colour_rush", 40, 10).copy(squares = 18), win("colour_rush", 40, 20).copy(squares = 25), win("colour_rush", 40, 30).copy(squares = 20),
                win("survival", 90, 10).copy(squaresMoved = 410), win("survival", 90, 20).copy(squaresMoved = 380), win("survival", 90, 40).copy(squaresMoved = null),
            ),
        )
        assertEquals(UnpostedWinRow(38, 20), dao.unpostedFewestTurns("classic", 4, afterEpochMillis = 5))
        assertEquals(UnpostedWinRow(25, 30), dao.unpostedMostSquares("colour_rush", 4, afterEpochMillis = 0))
        assertEquals(UnpostedWinRow(20, 30), dao.unpostedMostSquares("colour_rush", 4, afterEpochMillis = 20))
        assertEquals(UnpostedWinRow(410, 20), dao.unpostedMostSquaresMoved("survival", 4, afterEpochMillis = 0))
        assertEquals(UnpostedWinRow(null, null), dao.unpostedMostSquaresMoved("survival", 4, afterEpochMillis = 20))
        assertEquals(UnpostedWinRow(null, null), dao.unpostedFewestTurns("classic", 6, afterEpochMillis = 0))
    }
}
