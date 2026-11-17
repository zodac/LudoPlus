package net.zodac.ludo.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.records.GameResult
import net.zodac.ludo.data.records.PlayerStatistics
import net.zodac.ludo.model.GameMode

/** The Leaderboard's rows for [mode]: its fastest wins, read again whenever a game is recorded. Null until first read. */
class LeaderboardViewModel(records: GameRecordRepository, val mode: GameMode = GameMode.CLASSIC) : ViewModel() {
    val rows: StateFlow<List<GameResult>?> =
        records.writes.map { records.leaderboard(mode) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LeaderboardViewModel(container.gameRecordRepository) }
        }
    }
}

/** Every player's Statistics, read again whenever a game is recorded. Null until first read. */
class StatisticsViewModel(records: GameRecordRepository) : ViewModel() {
    val players: StateFlow<List<PlayerStatistics>?> =
        records.writes.map { records.playerStatistics() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { StatisticsViewModel(container.gameRecordRepository) }
        }
    }
}
