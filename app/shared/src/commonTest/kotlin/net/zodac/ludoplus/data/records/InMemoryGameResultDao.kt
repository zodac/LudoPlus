package net.zodac.ludoplus.data.records

/**
 * [GameResultDao] over a list, for tests: the same filters and order as the real queries (`GameResultDaoTest` checks those).
 * With [finishedGame], it starts with one game recorded, as after a first finished game (New Game's house rules unlocked).
 */
class InMemoryGameResultDao(finishedGame: Boolean = false) : GameResultDao {
    private val rows = mutableListOf<GameResult>()
    private var nextId = 1L

    init {
        if (finishedGame) {
            rows += GameResult(
                id = nextId++, playerName = "Earlier", playerKey = "earlier", isPrimaryPlayer = true, gameModeId = "classic", playerCount = 4,
                endedAtEpochMillis = 0, playMillis = 0, place = 1, won = true, finishMillis = 0, turns = 80, captures = 0, timesCaptured = 0,
                pawnsHome = 4,
            )
        }
    }

    override suspend fun insertGame(results: List<GameResult>) {
        results.forEach { rows += it.copy(id = nextId++) }
    }

    private fun leaderboardRows(gameModeId: String, playerCount: Int) = rows
        .filter { it.gameModeId == gameModeId && it.playedUnmodified && it.won && it.playerCount == playerCount }
        .sortedWith(compareBy<GameResult> { it.turns }.thenByDescending { it.captures }.thenBy { it.endedAtEpochMillis })

    override suspend fun leaderboard(gameModeId: String, playerCount: Int, limit: Int, offset: Int): List<GameResult> =
        leaderboardRows(gameModeId, playerCount).drop(offset).take(limit)

    override suspend fun leaderboardByPawnsLeft(gameModeId: String, playerCount: Int, limit: Int, offset: Int): List<GameResult> =
        leaderboardRows(gameModeId, playerCount)
            .sortedWith(compareBy<GameResult> { it.timesCaptured }.thenByDescending { it.captures }.thenBy { it.endedAtEpochMillis })
            .drop(offset).take(limit)

    override suspend fun leaderboardBySquares(gameModeId: String, playerCount: Int, limit: Int, offset: Int): List<GameResult> =
        leaderboardRows(gameModeId, playerCount)
            .sortedWith(compareByDescending<GameResult> { it.squares }.thenByDescending { it.captures }.thenBy { it.endedAtEpochMillis })
            .drop(offset).take(limit)

    override suspend fun modesFinished(): List<String> = rows.filter { it.isPrimaryPlayer && !it.houseRules }.map { it.gameModeId }.distinct()

    override suspend fun leaderboardCount(gameModeId: String, playerCount: Int): Int = leaderboardRows(gameModeId, playerCount).size

    override suspend fun allByPlayer(): List<GameResult> =
        rows.sortedWith(compareBy<GameResult> { it.playerKey }.thenBy { it.endedAtEpochMillis }.thenBy { it.id })

    override suspend fun all(): List<GameResult> = rows.sortedWith(compareBy<GameResult> { it.endedAtEpochMillis }.thenBy { it.id })

    override suspend fun insertMerged(results: List<GameResult>) {
        results.filter { row -> rows.none { it.uuid == row.uuid } }.distinctBy { it.uuid }.forEach { rows += it.copy(id = nextId++) }
    }

    override suspend fun unpostedWin(gameModeId: String, playerCount: Int, afterEpochMillis: Long): UnpostedWinRow {
        val wins = rows.filter {
            it.gameModeId == gameModeId && it.playerCount == playerCount && it.playedUnmodified && it.won && it.isPrimaryPlayer && it.endedAtEpochMillis > afterEpochMillis
        }
        return UnpostedWinRow(wins.minOfOrNull { it.turns }, wins.maxOfOrNull { it.endedAtEpochMillis })
    }

    override suspend fun count(): Int = rows.size

    override suspend fun clearAll() = rows.clear()
}
