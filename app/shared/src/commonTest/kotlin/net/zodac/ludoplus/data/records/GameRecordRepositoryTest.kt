package net.zodac.ludoplus.data.records

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.PlayerState
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.TeamMode
import net.zodac.ludoplus.model.withHouseRules
import net.zodac.ludoplus.ui.game.StartedGame

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
                        squaresMoved = 200 + index,
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
            first = 0,
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
                    timesCaptured = 0, pawnsHome = 4, uuid = "", squaresMoved = 200,
                ),
                GameResult(
                    id = 2, playerName = "Sam", playerKey = "sam", isPrimaryPlayer = false, gameModeId = "classic", playerCount = 4,
                    endedAtEpochMillis = 5_000, playMillis = 600_000, place = 1, won = true, finishMillis = 100_000, turns = 43, captures = 1,
                    timesCaptured = 3, pawnsHome = 4, uuid = "", squaresMoved = 203,
                ),
            ),
            // Each row's uuid is its own, random: compared apart.
            rows.map { it.copy(uuid = "") },
        )
        assertEquals(2, rows.map { it.uuid }.toSet().size)
        rows.forEach { assertTrue(Regex("[0-9a-f]{32}").matches(it.uuid), it.uuid) }
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
        val board = repository.leaderboard(GameMode.CLASSIC, playerCount = 4)
        assertEquals(listOf("Alex" to 40, "Sam" to 43, "Alex" to 43), board.map { it.playerName to it.turns })
        assertEquals(listOf(2L, 1L, 3L), board.map { it.endedAtEpochMillis })
        assertEquals(3, repository.leaderboardCount(GameMode.CLASSIC, playerCount = 4))
        assertEquals(listOf("Sam"), repository.leaderboard(GameMode.CLASSIC, playerCount = 4, pageIndex = 1, pageSize = 1).map { it.playerName })
        // Each player count has its own board: none of these were of 2 or 8.
        assertEquals(0, repository.leaderboardCount(GameMode.CLASSIC, playerCount = 2))
        assertEquals(emptyList(), repository.leaderboard(GameMode.CLASSIC, playerCount = 8))
        // Nor does Statistics count them for another count - only for every game, or theirs.
        assertEquals(emptyList(), repository.playerStatistics(playerCount = 3))
        assertEquals(repository.playerStatistics(), repository.playerStatistics(playerCount = 4))
    }

    @Test
    fun `a team game records each player at their team's place - and stays off the Leaderboard`() = runTest {
        val repository = GameRecordRepository(InMemoryGameResultDao())
        // Alex (player 1) and Pip (player 3) against Gambit and Sam: Alex's team home first.
        val solo = game(order = listOf(0, 2, 1, 3))
        val teamGame = solo.copy(state = solo.state.copy(rules = solo.state.rules.copy(teams = TeamMode.TWO_TEAMS.teamsFor(4))))
        repository.recordGame(teamGame)
        val (alex, sam) = repository.playerStatistics()
        assertEquals(1, alex.totals.gamesWon)
        // Sam's team is second, whatever the order its players were placed in.
        assertEquals(0, sam.totals.gamesWon)
        assertEquals(0, repository.leaderboardCount(GameMode.CLASSIC, playerCount = 4))
    }

    @Test
    fun `a game with house rules is recorded - but stays off the Leaderboard and out of Statistics`() = runTest {
        val dao = InMemoryGameResultDao()
        val repository = GameRecordRepository(dao)
        val plain = game(order = listOf(0, 1, 2, 3))
        val housed = plain.copy(state = plain.state.copy(rules = plain.state.rules.withHouseRules(setOf(HouseRule.BLOCKADES))))
        repository.recordGame(housed)
        assertEquals(2, dao.count())
        assertEquals(true, repository.hasFinishedGame())
        assertEquals(listOf(true, true), dao.allByPlayer().map { it.houseRules })
        assertEquals(listOf(false, false), dao.allByPlayer().map { it.playedUnmodified })
        assertEquals(0, repository.leaderboardCount(GameMode.CLASSIC, playerCount = 4))
        assertEquals(emptyList(), repository.playerStatistics())
        // A game without them counts as ever.
        repository.recordGame(plain)
        assertEquals(1, repository.leaderboardCount(GameMode.CLASSIC, playerCount = 4))
        assertEquals(listOf(1, 1), repository.playerStatistics().map { it.totals.gamesPlayed })
    }

    @Test
    fun `a Portals game has its own Leaderboard - and Statistics adds up every mode's games or one mode's`() = runTest {
        val repository = GameRecordRepository(InMemoryGameResultDao())
        repository.recordGame(game(order = listOf(0, 1, 2, 3)))
        val portals = game(order = listOf(0, 1, 2, 3), turns = listOf(25, 30, 31, 32)).let { it.copy(mode = GameMode.PORTALS) }
        repository.recordGame(portals)
        assertEquals(listOf(40), repository.leaderboard(GameMode.CLASSIC, playerCount = 4).map { it.turns })
        assertEquals(listOf(25), repository.leaderboard(GameMode.PORTALS, playerCount = 4).map { it.turns })
        assertEquals(listOf(2, 2), repository.playerStatistics().map { it.totals.gamesPlayed })
        assertEquals(listOf(1, 1), repository.playerStatistics(mode = GameMode.PORTALS).map { it.totals.gamesPlayed })
        assertEquals(25, repository.playerStatistics(mode = GameMode.PORTALS).first().totals.fewestTurnsToWin)
        assertEquals(emptyList(), repository.playerStatistics(playerCount = 2, mode = GameMode.PORTALS))
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
