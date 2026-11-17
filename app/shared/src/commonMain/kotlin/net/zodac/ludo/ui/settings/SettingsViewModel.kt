package net.zodac.ludo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.ludo.app.AchievementUnlocker
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.data.achievements.AchievementStore
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.nowEpochMillis
import net.zodac.ludo.model.Achievement

/** The Settings screen's switches, as saved. */
data class SettingsToggles(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val confirmBeforeLeavingGame: Boolean = true,
    val stopAtFirstWinner: Boolean = false,
)

/**
 * Backs the Settings screen's switches, and the two achievements found there: opening About, and tapping the version.
 * DiceFive's resets and progress files come back in their phases of `.claude/IMPLEMENTATION.md`.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val achievementsRepository: AchievementStore,
    /** Where its achievements are earned: the app's one ([AppContainer.achievementUnlocker]); a test's own otherwise. */
    private val achievementUnlocker: AchievementUnlocker = AchievementUnlocker(achievementsRepository),
) : ViewModel() {

    /**
     * Null until every setting's saved value has loaded - all of them together, so the screen never
     * draws a setting in its default position for a frame before flipping it to the player's own.
     */
    val toggles: StateFlow<SettingsToggles?> = combine(
        settingsRepository.soundEnabled,
        settingsRepository.vibrationEnabled,
        settingsRepository.confirmBeforeLeavingGame,
        settingsRepository.stopAtFirstWinner,
        ::SettingsToggles,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setConfirmBeforeLeavingGame(confirm: Boolean) {
        viewModelScope.launch { settingsRepository.setConfirmBeforeLeavingGame(confirm) }
    }

    fun setStopAtFirstWinner(stop: Boolean) {
        viewModelScope.launch { settingsRepository.setStopAtFirstWinner(stop) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundEnabled(enabled) }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVibrationEnabled(enabled) }
    }

    /** Earns the achievement for opening the About dialog ("Who Made This?"). */
    fun onAboutViewed() {
        viewModelScope.launch { achievementUnlocker.unlock(setOf(Achievement.WHO_MADE_THIS)) }
    }

    private var versionTaps = 0

    // A Channel, not a StateFlow: a one-shot toast rather than state to replay.
    private val _versionTapMessages = Channel<Int>(Channel.BUFFERED)

    /**
     * What to tell the player after a repeated tap on the version number, as the number of taps still needed for
     * "Are You A Developer?" - 0 once they have it. Android's own build number counts down the same way.
     */
    val versionTapMessages: Flow<Int> = _versionTapMessages.receiveAsFlow()

    private var lastVersionTapMillis = 0L

    /**
     * A tap on the version number, at [nowMillis]. The [VERSION_TAP_TARGET]th in a row - each within
     * [VERSION_TAP_GAP_MILLIS] of the last - earns "Are You A Developer?", as the same taps on Android's own build
     * number turn on its developer options. A slower tap starts the count again.
     *
     * From the [VERSION_TAP_MESSAGE_FROM]th tap in a row, each also emits on [versionTapMessages], except the one that
     * earns it (its banner is the message).
     */
    fun onVersionTapped(nowMillis: Long = nowEpochMillis()) {
        versionTaps = if (versionTaps > 0 && nowMillis - lastVersionTapMillis <= VERSION_TAP_GAP_MILLIS) versionTaps + 1 else 1
        lastVersionTapMillis = nowMillis
        if (versionTaps < VERSION_TAP_MESSAGE_FROM) return
        val stepsAway = VERSION_TAP_TARGET - versionTaps
        if (stepsAway <= 0) versionTaps = 0
        viewModelScope.launch {
            when {
                achievementsRepository.current().isUnlocked(Achievement.ARE_YOU_A_DEVELOPER) -> _versionTapMessages.trySend(0)
                stepsAway <= 0 -> achievementUnlocker.unlock(setOf(Achievement.ARE_YOU_A_DEVELOPER))
                else -> _versionTapMessages.trySend(stepsAway)
            }
        }
    }

    companion object {
        /** The tap on the version number from which each one gets a toast. */
        const val VERSION_TAP_MESSAGE_FROM = 2

        /** How many taps on the version number earn "Are You A Developer?" - Android's own count for its build number. */
        const val VERSION_TAP_TARGET = 7

        /** The longest a tap on the version number can come after the last and still count towards [VERSION_TAP_TARGET]. */
        const val VERSION_TAP_GAP_MILLIS = 1_500L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    achievementsRepository = container.achievementsRepository,
                    achievementUnlocker = container.achievementUnlocker,
                )
            }
        }
    }
}
