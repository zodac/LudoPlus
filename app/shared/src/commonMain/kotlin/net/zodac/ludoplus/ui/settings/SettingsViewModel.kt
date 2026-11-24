package net.zodac.ludoplus.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.ludoplus.app.AchievementUnlocker
import net.zodac.ludoplus.app.AppContainer
import net.zodac.ludoplus.data.achievements.AchievementStore
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.nowEpochMillis
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.online.ImportStep
import net.zodac.ludoplus.online.PROGRESS_FILE_EXTENSION
import net.zodac.ludoplus.online.ProgressImport
import net.zodac.ludoplus.online.ProgressTransfer
import net.zodac.ludoplus.platform.ProgressFileResult
import net.zodac.ludoplus.platform.ProgressFiles
import net.zodac.ludoplus.ui.common.formatTimestamp

/** The Settings screen's switches, as saved. */
data class SettingsToggles(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showMoveLog: Boolean = true,
    val confirmBeforeLeavingGame: Boolean = true,
    val stopAtFirstWinner: Boolean = false,
)

/**
 * Backs the Settings screen's switches, its progress files card, and the two achievements found there: opening About,
 * and tapping the version.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val achievementsRepository: AchievementStore,
    /** Where its achievements are earned: the app's one ([AppContainer.achievementUnlocker]); a test's own otherwise. */
    private val achievementUnlocker: AchievementUnlocker = AchievementUnlocker(achievementsRepository),
    /** This device's progress to and from a file, for a build with progress files ([net.zodac.ludoplus.platform.PlatformServices.progressFiles]). */
    private val progressTransfer: ProgressTransfer? = null,
    private val now: () -> Long = ::nowEpochMillis,
) : ViewModel() {

    /**
     * Null until every setting's saved value has loaded - all of them together, so the screen never
     * draws a setting in its default position for a frame before flipping it to the player's own.
     */
    val toggles: StateFlow<SettingsToggles?> = combine(
        settingsRepository.soundEnabled,
        settingsRepository.vibrationEnabled,
        settingsRepository.showMoveLog,
        settingsRepository.confirmBeforeLeavingGame,
        settingsRepository.stopAtFirstWinner,
        ::SettingsToggles,
    ).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setConfirmBeforeLeavingGame(confirm: Boolean) {
        viewModelScope.launch { settingsRepository.setConfirmBeforeLeavingGame(confirm) }
    }

    fun setShowMoveLog(show: Boolean) {
        viewModelScope.launch { settingsRepository.setShowMoveLog(show) }
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

    private val _progressFilesStatus = MutableStateFlow<ProgressFilesStatus?>(null)

    /**
     * What the progress files card shows under its rows: an import's step while it runs, then how the last export or
     * import ended, until the next starts. Null before any, and after a picker closed unchosen.
     */
    val progressFilesStatus: StateFlow<ProgressFilesStatus?> = _progressFilesStatus.asStateFlow()

    /** The export or import under way: one at a time, so a second tap while a picker opens starts nothing. */
    private var transfer: Job? = null

    private fun startTransfer(block: suspend (ProgressTransfer) -> ProgressOutcome?) {
        val progressTransfer = progressTransfer ?: return
        if (transfer?.isActive == true) return
        _progressFilesStatus.value = null
        transfer = viewModelScope.launch {
            _progressFilesStatus.value = block(progressTransfer)?.let(ProgressFilesStatus::Finished)
        }
    }

    /**
     * Saves everything a progress file holds to a file the player picks through [files], named for today
     * ([progressFileName]). Survives the screen being turned while the picker is open: the view model waits on it.
     */
    fun exportProgress(files: ProgressFiles) = startTransfer { progressTransfer ->
        when (files.save(progressFileName(now()), progressTransfer.export(files.key))) {
            is ProgressFileResult.Done -> ProgressOutcome.EXPORTED
            ProgressFileResult.Cancelled -> null
            ProgressFileResult.Failed -> ProgressOutcome.EXPORT_FAILED
        }
    }

    /**
     * Merges in a progress file the player picks through [files] ([ProgressTransfer.import]), telling each step as it
     * starts ([ProgressFilesStatus.Importing]): the card paces them ([nextShownStatus]).
     */
    fun importProgress(files: ProgressFiles) = startTransfer { progressTransfer ->
        when (val opened = files.open()) {
            is ProgressFileResult.Done -> {
                val imported = progressTransfer.import(opened.bytes, files.key) { _progressFilesStatus.value = ProgressFilesStatus.Importing(it) }
                when (imported) {
                    ProgressImport.IMPORTED -> ProgressOutcome.IMPORTED
                    ProgressImport.NOT_A_PROGRESS_FILE -> ProgressOutcome.NOT_A_PROGRESS_FILE
                    ProgressImport.FROM_NEWER_APP -> ProgressOutcome.FROM_NEWER_APP
                }
            }
            ProgressFileResult.Cancelled -> null
            ProgressFileResult.Failed -> ProgressOutcome.IMPORT_FAILED
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
                    progressTransfer = container.cloudSave?.let { ProgressTransfer(it, container.achievementsRepository) },
                )
            }
        }

        /** An exported file's suggested name, by the day it was made: "LudoPlus-2026-11-18.ludo". */
        internal fun progressFileName(epochMillis: Long): String =
            "LudoPlus-${formatTimestamp(epochMillis, "yyyy-MM-dd", "en")}.$PROGRESS_FILE_EXTENSION" // i18n: not translated - a file name, the brand and a date
    }
}

/** What the progress files card shows ([SettingsViewModel.progressFilesStatus]). */
sealed interface ProgressFilesStatus {

    /** An import on its [step]. */
    data class Importing(val step: ImportStep) : ProgressFilesStatus

    /** How the last export or import ended. */
    data class Finished(val outcome: ProgressOutcome) : ProgressFilesStatus
}

/** How an export or import ended, as the progress files card says it. */
enum class ProgressOutcome {
    EXPORTED,
    IMPORTED,
    NOT_A_PROGRESS_FILE,
    FROM_NEWER_APP,
    EXPORT_FAILED,
    IMPORT_FAILED;

    /** Whether it went wrong: said in red. */
    val failed: Boolean get() = this != EXPORTED && this != IMPORTED
}
