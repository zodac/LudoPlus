package net.zodac.ludoplus

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.GameResult
import net.zodac.ludoplus.data.records.GameResultDao
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.data.records.LeaderboardSource
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.ModePage
import net.zodac.ludoplus.ui.records.LeaderboardBoard
import net.zodac.ludoplus.ui.records.LeaderboardViewModel
import net.zodac.ludoplus.ui.records.StatisticsViewModel

/** What a pick from the Leaderboard's or Statistics' drop-down reads, shows, and keeps. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModelsTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun row(mode: GameMode, players: Int = 4) = GameResult(
        playerName = mode.id, playerKey = mode.id, isPrimaryPlayer = true, gameModeId = mode.id, playerCount = players, endedAtEpochMillis = 1,
        playMillis = 1, place = 1, won = true, finishMillis = 1, turns = 40, captures = 1, timesCaptured = 1, pawnsHome = 4,
    )

    /** A row a board, noting each read; one in [gates] waits for its own, and one in [failing] can't be read. */
    private inner class Boards : LeaderboardSource {
        override val writes = MutableStateFlow(0L)
        val reads = mutableListOf<LeaderboardBoard>()
        val gates = mutableMapOf<GameMode, CompletableDeferred<Unit>>()
        val failing = mutableSetOf<GameMode>()

        override suspend fun leaderboard(gameMode: GameMode, playerCount: Int, pageIndex: Int, pageSize: Int): List<GameResult> {
            reads += LeaderboardBoard(gameMode, playerCount)
            gates[gameMode]?.await()
            check(gameMode !in failing) { "Unreadable" }
            return listOf(row(gameMode, playerCount))
        }
    }

    private fun LeaderboardViewModel.shown() = uiState.value?.let { state -> state.board to state.rows.map { it.gameModeId to it.playerCount } }

    private fun board(mode: GameMode, players: Int = 4) = LeaderboardBoard(mode, players) to listOf(mode.id to players)

    @Test
    fun `the Leaderboard opens on the saved mode - once it has loaded - and a pick is kept`() = runTest(dispatcher) {
        val saved = MutableStateFlow<GameMode?>(null)
        val kept = mutableListOf<GameMode>()
        val viewModel = LeaderboardViewModel(Boards(), saved, { kept += it }) { 6 }
        testScheduler.runCurrent()
        assertNull(viewModel.shown())
        saved.value = GameMode.PORTALS
        testScheduler.runCurrent()
        assertEquals(board(GameMode.PORTALS, 6), viewModel.shown())
        assertEquals(emptyList(), kept)
        viewModel.selectMode(GameMode.QUICK)
        testScheduler.runCurrent()
        assertEquals(board(GameMode.QUICK, 6), viewModel.shown())
        assertEquals(listOf(GameMode.QUICK), kept)
    }

    @Test
    fun `the board on view stays up until the one picked is read - then its name and rows change together`() = runTest(dispatcher) {
        val boards = Boards()
        val gate = CompletableDeferred<Unit>()
        boards.gates[GameMode.QUICK] = gate
        val viewModel = LeaderboardViewModel(boards)
        testScheduler.runCurrent()
        viewModel.selectMode(GameMode.QUICK)
        testScheduler.runCurrent()
        assertEquals(board(GameMode.CLASSIC), viewModel.shown())
        gate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(board(GameMode.QUICK), viewModel.shown())
    }

    @Test
    fun `a later pick is never replaced by an earlier one still being read`() = runTest(dispatcher) {
        val boards = Boards()
        val gate = CompletableDeferred<Unit>()
        boards.gates[GameMode.QUICK] = gate
        val viewModel = LeaderboardViewModel(boards)
        testScheduler.runCurrent()
        viewModel.selectMode(GameMode.QUICK)
        testScheduler.runCurrent()
        viewModel.selectMode(GameMode.PORTALS)
        testScheduler.runCurrent()
        gate.complete(Unit)
        testScheduler.runCurrent()
        assertEquals(board(GameMode.PORTALS), viewModel.shown())
    }

    @Test
    fun `each board is read once - until a game is recorded, when the one on view is read again`() = runTest(dispatcher) {
        val boards = Boards()
        val viewModel = LeaderboardViewModel(boards)
        testScheduler.runCurrent()
        viewModel.selectMode(GameMode.QUICK)
        testScheduler.runCurrent()
        viewModel.selectMode(GameMode.CLASSIC)
        testScheduler.runCurrent()
        viewModel.selectPlayerCount(2)
        testScheduler.runCurrent()
        assertEquals(board(GameMode.CLASSIC, 2), viewModel.shown())
        val read = listOf(LeaderboardBoard(GameMode.CLASSIC, 4), LeaderboardBoard(GameMode.QUICK, 4), LeaderboardBoard(GameMode.CLASSIC, 2))
        assertEquals(read, boards.reads)
        boards.writes.value++
        testScheduler.runCurrent()
        assertEquals(read + LeaderboardBoard(GameMode.CLASSIC, 2), boards.reads)
        viewModel.selectMode(GameMode.QUICK)
        testScheduler.runCurrent()
        assertEquals(LeaderboardBoard(GameMode.QUICK, 2), boards.reads.last())
    }

    @Test
    fun `a board that can't be read leaves the one on view - is tried again when picked again - and opens empty with none`() = runTest(dispatcher) {
        val boards = Boards().apply { failing += GameMode.QUICK }
        val viewModel = LeaderboardViewModel(boards)
        testScheduler.runCurrent()
        viewModel.selectMode(GameMode.QUICK)
        testScheduler.runCurrent()
        assertEquals(board(GameMode.CLASSIC), viewModel.shown())
        boards.failing.clear()
        viewModel.selectMode(GameMode.QUICK)
        testScheduler.runCurrent()
        assertEquals(board(GameMode.QUICK), viewModel.shown())

        boards.failing += GameMode.PORTALS
        val opened = LeaderboardViewModel(boards, MutableStateFlow(GameMode.PORTALS))
        testScheduler.runCurrent()
        assertEquals(LeaderboardBoard(GameMode.PORTALS, 4) to emptyList<Pair<String, Int>>(), opened.shown())
    }

    /** Counts Statistics' reads, or fails them. */
    private class CountingDao(private val dao: GameResultDao = InMemoryGameResultDao()) : GameResultDao by dao {
        var reads = 0
        var failing = false
        override suspend fun allByPlayer(): List<GameResult> {
            reads++
            check(!failing) { "Unreadable" }
            return dao.allByPlayer()
        }
    }

    @Test
    fun `Statistics reads the records once - however many pages are picked - and again when one is recorded`() = runTest(dispatcher) {
        val dao = CountingDao()
        dao.insertGame(listOf(row(GameMode.CLASSIC), row(GameMode.QUICK, players = 2)))
        val repository = GameRecordRepository(dao)
        val kept = mutableListOf<ModePage>()
        val viewModel = StatisticsViewModel(repository, savePage = { kept += it })
        testScheduler.runCurrent()
        assertEquals(ModePage.Mode(GameMode.CLASSIC), viewModel.page.value)
        viewModel.selectPage(ModePage.AllModes)
        viewModel.selectPlayerCount(2)
        testScheduler.runCurrent()
        assertEquals(ModePage.AllModes, viewModel.page.value)
        assertEquals(listOf<ModePage>(ModePage.AllModes), kept)
        assertEquals(listOf("quick"), viewModel.statistics.value!!.players(2, null).map { it.playerName })
        assertEquals(1, dao.reads)
        repository.reset()
        testScheduler.runCurrent()
        assertTrue(viewModel.statistics.value!!.isEmpty)
        assertEquals(2, dao.reads)
    }

    @Test
    fun `Statistics that can't be read keeps what's on view - or opens empty with none`() = runTest(dispatcher) {
        val dao = CountingDao()
        dao.insertGame(listOf(row(GameMode.CLASSIC)))
        val repository = GameRecordRepository(dao)
        val viewModel = StatisticsViewModel(repository)
        testScheduler.runCurrent()
        dao.failing = true
        repository.reset()
        testScheduler.runCurrent()
        assertEquals(1, viewModel.statistics.value!!.players().size)
        val opened = StatisticsViewModel(repository)
        testScheduler.runCurrent()
        assertTrue(opened.statistics.value!!.isEmpty)
    }
}
