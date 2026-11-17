package net.zodac.ludo.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.records.GameResult
import net.zodac.ludo.data.records.PlayerStatistics
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.ui.game.DEFAULT_PLAYER_COUNT

/**
 * The Leaderboard's rows for the [mode] and [playerCount] picked: its fastest wins, read again whenever a game is
 * recorded or another is picked. Null until first read. Both start as [initial] gives them, the ones New Game last
 * started with.
 */
class LeaderboardViewModel(
    records: GameRecordRepository,
    initial: suspend () -> Pair<GameMode, Int> = { GameMode.DEFAULT to DEFAULT_PLAYER_COUNT },
) : ViewModel() {
    private val _board = MutableStateFlow<Pair<GameMode, Int>?>(null)

    /** The mode whose boards are shown: null until [initial] is read. */
    val mode: StateFlow<GameMode?> = _board.map { it?.first }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The player count whose board is shown: null until [initial] is read. */
    val playerCount: StateFlow<Int?> = _board.map { it?.second }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<GameResult>?> =
        combine(records.writes, _board.filterNotNull()) { _, board -> board }
            .mapLatest { (mode, count) -> records.leaderboard(mode, count) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch { _board.compareAndSet(null, initial()) }
    }

    fun selectMode(mode: GameMode) {
        _board.update { it?.copy(first = mode) }
    }

    fun selectPlayerCount(count: Int) {
        _board.update { it?.copy(second = count) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LeaderboardViewModel(container.gameRecordRepository) {
                    val saved = container.settingsRepository.snapshot()
                    saved.gameMode to (saved.playerCount ?: DEFAULT_PLAYER_COUNT)
                }
            }
        }
    }
}

/**
 * Every player's Statistics, for games of the [playerCount] picked or every game (null), read again whenever a game is
 * recorded or another count is picked. Null until first read.
 */
class StatisticsViewModel(records: GameRecordRepository, val readPlaySeconds: suspend () -> Int = { 0 }) : ViewModel() {
    private val _playerCount = MutableStateFlow<Int?>(null)
    private val _mode = MutableStateFlow<GameMode?>(null)

    /** The player count whose games are added up, or null for every game. */
    val playerCount: StateFlow<Int?> = _playerCount.asStateFlow()

    /** The mode whose games are added up, or null for every mode's. */
    val mode: StateFlow<GameMode?> = _mode.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val players: StateFlow<List<PlayerStatistics>?> =
        combine(records.writes, _playerCount, _mode) { _, count, mode -> count to mode }
            .mapLatest { (count, mode) -> records.playerStatistics(count, mode) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectPlayerCount(count: Int?) {
        _playerCount.value = count
    }

    fun selectMode(mode: GameMode?) {
        _mode.value = mode
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // The time played: what's saved, and what's counted since.
                StatisticsViewModel(container.gameRecordRepository) { container.achievementsRepository.playSeconds.first() + container.playTime.unsavedSeconds }
            }
        }
    }
}
