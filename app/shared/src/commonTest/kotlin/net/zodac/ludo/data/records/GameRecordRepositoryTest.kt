package net.zodac.ludo.data.records

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.PlayerState
import net.zodac.ludo.game.RollOff
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.ui.game.StartedGame

class GameRecordRepositoryTest {

    /**
     * A finished game: [names]' people at seats 1 and 4 (CPUs at 2 and 3), placed in [order], each with [turns] and 1
     * capture per place; the last has 2 pawns home.
     */
    private fun game(order: List<Int>, names: Pair<String, String> = "Alex" to "Sam", turns: List<Int> = listOf(40, 41, 42, 43)): StartedGame {
        val players = listOf(
            PlayerConfig(1, PlayerType.HUMAN, names.first, colour = PlayerColour.RED),
            PlayerConfig(2, PlayerType.AI, "Gambit", Difficulty.HARD, PlayerColour.GREEN),
            PlayerConfig(3, PlayerType.AI, "Pip", Difficulty.EASY, PlayerColour.YELLOW),
            PlayerConfig(4, PlayerType.HUMAN, names.second, colour = PlayerColour.BLUE),
        )
        val state = GameState.new(GameMode.CLASSIC, 4).let { new ->
            new.copy(
                players = new.players.mapIndexed { index, player ->
                    val last = index == order.last()
                    PlayerState(
                        seat = player.seat,
                        pawns = if (last) listOf(56, 56, 3, -1) else List(4) { 56 },
                        turns = turns[index],
                        captures = order.indexOf(index) + 1,
                        timesCaptured = index,
                    )
                },
                phase = TurnPhase.Finished,
                finishingOrder = order,
            )
        }
        return StartedGame(
            mode = GameMode.CLASSIC,
            players = players,
            state = state,
            rollOff = RollOff(emptyList(), 0),
            playMillis = 600_000,
            finishMillis = order.withIndex().associate { (place, player) -> player to 100_000L * (place + 1) },
        )
    }

    @Test
    fun `a finished game records its people only - each with their place, figures, and finish time`() = runTest {
        val dao = InMemoryGameResultDao()
        val repository = GameRecordRepository(dao)
        repository.recordGame(game(order = listOf(3, 1, 0, 2)), endedAtEpochMillis = 5_000)
        val rows = dao.allByPlayer()
        assertEquals(
            listOf(
                GameResult(
                    id = 1, playerName = "Alex", playerKey = "alex", isPrimaryPlayer = true, gameModeId = "classic", playerCount = 4,
                    endedAtEpochMillis = 5_000, playMillis = 600_000, place = 3, won = false, finishMillis = 300_000, turns = 40, captures = 3,
                    timesCaptured = 0, pawnsHome = 4,
                ),
                GameResult(
                    id = 2, playerName = "Sam", playerKey = "sam", isPrimaryPlayer = false, gameModeId = "classic", playerCount = 4,
                    endedAtEpochMillis = 5_000, playMillis = 600_000, place = 1, won = true, finishMillis = 100_000, turns = 43, captures = 1,
                    timesCaptured = 3, pawnsHome = 4,
                ),
            ),
            rows,
        )
        assertEquals(1, repository.writes.value)
    }

    @Test
    fun `a finished game is known once one is recorded - for New Game's tutorial link`() = runTest {
        val repository = GameRecordRepository(InMemoryGameResultDao())
        assertEquals(false, repository.hasFinishedGame())
        repository.recordGame(game(order = listOf(3, 1, 0, 2)))
        assertEquals(true, repository.hasFinishedGame())
    }

    @Test
    fun `the Leaderboard is each mode's wins - fewest turns first, then most captures`() = runTest {
        val repository = GameRecordRepository(InMemoryGameResultDao())
        // Sam wins in 43 turns, Alex in 40, then Alex in 43 again: level with Sam on turns and captures, so after, as later.
        repository.recordGame(game(order = listOf(3, 1, 2, 0)), endedAtEpochMillis = 1)
        repository.recordGame(game(order = listOf(0, 1, 2, 3)), endedAtEpochMillis = 2)
        repository.recordGame(game(order = listOf(0, 1, 2, 3), turns = listOf(43, 41, 42, 43)), endedAtEpochMillis = 3)
        // A loss for both: not on the board.
        repository.recordGame(game(order = listOf(1, 2, 0, 3)), endedAtEpochMillis = 4)
        val board = repository.leaderboard(GameMode.CLASSIC)
        assertEquals(listOf("Alex" to 40, "Sam" to 43, "Alex" to 43), board.map { it.playerName to it.turns })
        assertEquals(listOf(2L, 1L, 3L), board.map { it.endedAtEpochMillis })
        assertEquals(3, repository.leaderboardCount(GameMode.CLASSIC))
        assertEquals(listOf("Sam"), repository.leaderboard(GameMode.CLASSIC, pageIndex = 1, pageSize = 1).map { it.playerName })
    }

    @Test
    fun `Statistics add each player's games up - wins, streaks, and figures - one player whatever the name's case`() = runTest {
        val repository = GameRecordRepository(InMemoryGameResultDao())
        // Alex: win, win, loss, win, win, win (best run 3, and 3 now); "ALEX" in the last game is the same player.
        val alexPlaces = listOf(0, 0, 2, 0, 0, 0)
        alexPlaces.forEachIndexed { index, place ->
            val order = when (place) {
                0 -> listOf(0, 1, 2, 3)
                else -> listOf(1, 2, 0, 3)
            }
            val names = (if (index == alexPlaces.lastIndex) "ALEX" else "Alex") to "Sam"
            repository.recordGame(game(order = order, names = names), endedAtEpochMillis = index.toLong())
        }
        val (alex, sam) = repository.playerStatistics()
        assertEquals("ALEX", alex.playerName)
        assertEquals(
            RecordTotals(
                gamesPlayed = 6, gamesWon = 5, currentWinStreak = 3, bestWinStreak = 3, captures = 5 * 1 + 3, timesCaptured = 0,
                pawnsHome = 24, fewestTurnsToWin = 40,
            ),
            alex.totals,
        )
        assertEquals(5.0 / 6, alex.totals.winRate)
        assertEquals(listOf(GameMode.CLASSIC to alex.totals), alex.modes)
        // Sam came last every time: 2 pawns home a game, no wins.
        assertEquals(0, sam.totals.gamesWon)
        assertEquals(0, sam.totals.bestWinStreak)
        assertEquals(12, sam.totals.pawnsHome)
        assertNull(sam.totals.fewestTurnsToWin)
        assertEquals(0.0, RecordTotals(0, 0, 0, 0, 0, 0, 0, null).winRate)
    }

    @Test
    fun `a game with no person in it writes nothing - and a reset forgets every game`() = runTest {
        val dao = InMemoryGameResultDao()
        val repository = GameRecordRepository(dao)
        val cpusOnly = game(order = listOf(0, 1, 2, 3)).let { it.copy(players = it.players.map { player -> player.copy(type = PlayerType.AI) }) }
        repository.recordGame(cpusOnly)
        assertEquals(0, dao.count())
        assertEquals(0, repository.writes.value)
        repository.recordGame(game(order = listOf(0, 1, 2, 3)))
        repository.reset()
        assertEquals(0, dao.count())
        assertEquals(emptyList(), repository.playerStatistics())
        assertEquals(2, repository.writes.value)
    }
}
