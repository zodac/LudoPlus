package net.zodac.ludoplus.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.zodac.ludoplus.app.AppContainer
import net.zodac.ludoplus.data.orNullOnFailure
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.GameResult
import net.zodac.ludoplus.data.records.LeaderboardSource
import net.zodac.ludoplus.data.records.Statistics
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.ModePage
import net.zodac.ludoplus.ui.game.DEFAULT_PLAYER_COUNT

/** One of the Leaderboard's boards: [mode]'s, for games of [playerCount]. */
data class LeaderboardBoard(val mode: GameMode, val playerCount: Int)

/** The [board] on view, and its [rows]. */
data class LeaderboardUiState(val board: LeaderboardBoard, val rows: List<GameResult>)

/**
 * The Leaderboard's boards, read from [source] the first time each is picked, and kept until a game is recorded. It opens
 * on [savedMode]'s board once that has loaded, for [initialPlayerCount] (New Game's last), and hands each mode picked to
 * [saveMode] to keep.
 */
class LeaderboardViewModel(
    private val source: LeaderboardSource,
    private val savedMode: StateFlow<GameMode?> = MutableStateFlow(GameMode.DEFAULT),
    private val saveMode: suspend (GameMode) -> Unit = {},
    private val initialPlayerCount: suspend () -> Int = { DEFAULT_PLAYER_COUNT },
) : ViewModel() {

    private val _uiState = MutableStateFlow<LeaderboardUiState?>(null)

    /**
     * The board on view: null until the first is read. A board picked takes the last one's place once it's read, so its
     * name and its rows change together.
     */
    val uiState: StateFlow<LeaderboardUiState?> = _uiState.asStateFlow()

    /** The board last picked. */
    private val picked = MutableStateFlow<LeaderboardBoard?>(null)

    /** Every board read since the last game was recorded. */
    private val boards = HashMap<LeaderboardBoard, List<GameResult>>()

    init {
        viewModelScope.launch { picked.compareAndSet(null, LeaderboardBoard(savedMode.filterNotNull().first(), initialPlayerCount())) }
        viewModelScope.launch {
            source.writes.collectLatest {
                boards.clear()
                picked.filterNotNull().collectLatest { show(it) }
            }
        }
    }

    /**
     * Shows [board]. One that can't be read leaves the board on view as it is, or, with none on view yet, opens empty, so
     * the screen isn't left waiting, rather than taking the app down.
     */
    private suspend fun show(board: LeaderboardBoard) {
        val rows = boards[board] ?: orNullOnFailure { source.leaderboard(board.mode, board.playerCount) }?.also { boards[board] = it }
        val onView = _uiState.value
        when {
            rows != null -> _uiState.value = LeaderboardUiState(board, rows)
            onView == null -> _uiState.value = LeaderboardUiState(board, emptyList())
            // Picked again, it's tried again.
            else -> picked.compareAndSet(board, onView.board)
        }
    }

    /** Moves to [mode]'s board, and keeps it for the next visit: the move doesn't wait on the write. */
    fun selectMode(mode: GameMode) {
        picked.update { it?.copy(mode = mode) }
        viewModelScope.launch { saveMode(mode) }
    }

    fun selectPlayerCount(count: Int) {
        picked.update { it?.copy(playerCount = count) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LeaderboardViewModel(
                    source = container.gameRecordRepository,
                    savedMode = container.leaderboardMode,
                    saveMode = container.settingsRepository::setLeaderboardMode,
                    initialPlayerCount = { container.settingsRepository.snapshot().playerCount ?: DEFAULT_PLAYER_COUNT },
                )
            }
        }
    }
}

/**
 * Every player's [Statistics], read once, and again whenever a game is recorded: the [page] and [playerCount] picked show
 * their own of them, with nothing to read. It opens on [savedPage] once that has loaded, and hands each page picked to
 * [savePage] to keep.
 */
class StatisticsViewModel(
    records: GameRecordRepository,
    savedPage: StateFlow<ModePage?> = MutableStateFlow(ModePage.DEFAULT),
    private val savePage: suspend (ModePage) -> Unit = {},
    /** The time played on every device, in seconds, up to now. */
    val readPlaySeconds: suspend () -> Int = { 0 },
) : ViewModel() {

    private val _statistics = MutableStateFlow<Statistics?>(null)

    /** Null until first read. */
    val statistics: StateFlow<Statistics?> = _statistics.asStateFlow()

    /** The page picked on this visit: on view at once, whether or not the write that keeps it has landed or can be made. */
    private val pickedPage = MutableStateFlow<ModePage?>(null)

    /** The page on view: null until loaded. */
    val page: StateFlow<ModePage?> = combine(savedPage, pickedPage) { saved, picked -> picked ?: saved }
        .stateIn(viewModelScope, SharingStarted.Eagerly, savedPage.value)

    private val _playerCount = MutableStateFlow<Int?>(null)

    /** The player count whose games are added up, or null for every game. */
    val playerCount: StateFlow<Int?> = _playerCount.asStateFlow()

    init {
        viewModelScope.launch {
            // One that can't be read keeps the cards on view, or leaves the screen's empty message on a first read, rather
            // than taking the app down.
            records.writes.collectLatest { _statistics.value = orNullOnFailure { records.statistics() } ?: _statistics.value ?: Statistics.NONE }
        }
    }

    /** Moves to [page], and keeps it for the next visit. */
    fun selectPage(page: ModePage) {
        pickedPage.value = page
        viewModelScope.launch { savePage(page) }
    }

    fun selectPlayerCount(count: Int?) {
        _playerCount.value = count
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                StatisticsViewModel(
                    records = container.gameRecordRepository,
                    savedPage = container.statisticsPage,
                    savePage = container.settingsRepository::setStatisticsPage,
                    // The time played: what's saved, and what's counted since.
                    readPlaySeconds = { container.achievementsRepository.playSeconds.first() + container.playTime.unsavedSeconds },
                )
            }
        }
    }
}
