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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.records.GameResult
import net.zodac.ludo.data.records.PlayerStatistics
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.ui.game.DEFAULT_PLAYER_COUNT

/**
 * The Leaderboard's rows for [mode] and the [playerCount] picked: its fastest wins, read again whenever a game is
 * recorded or another count is picked. Null until first read. The count starts as [initialCount] gives it, the one New
 * Game last started with.
 */
class LeaderboardViewModel(
    records: GameRecordRepository,
    val mode: GameMode = GameMode.CLASSIC,
    initialCount: suspend () -> Int = { DEFAULT_PLAYER_COUNT },
) : ViewModel() {
    private val _playerCount = MutableStateFlow<Int?>(null)

    /** The player count whose board is shown: null until [initialCount] is read. */
    val playerCount: StateFlow<Int?> = _playerCount.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<GameResult>?> =
        combine(records.writes, _playerCount.filterNotNull()) { _, count -> count }
            .mapLatest { count -> records.leaderboard(mode, count) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val initial = initialCount()
            _playerCount.compareAndSet(null, initial)
        }
    }

    fun selectPlayerCount(count: Int) {
        _playerCount.value = count
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LeaderboardViewModel(container.gameRecordRepository) {
                    container.settingsRepository.snapshot().playerCount ?: DEFAULT_PLAYER_COUNT
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

    /** The player count whose games are added up, or null for every game. */
    val playerCount: StateFlow<Int?> = _playerCount.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val players: StateFlow<List<PlayerStatistics>?> =
        combine(records.writes, _playerCount) { _, count -> count }
            .mapLatest { count -> records.playerStatistics(count) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectPlayerCount(count: Int?) {
        _playerCount.value = count
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                // The time played: what's saved, and what's counted since.
                StatisticsViewModel(container.gameRecordRepository) { container.settingsRepository.playSeconds.first() + container.playTime.unsavedSeconds }
            }
        }
    }
}
