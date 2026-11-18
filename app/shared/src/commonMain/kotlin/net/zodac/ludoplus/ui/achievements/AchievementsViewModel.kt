package net.zodac.ludoplus.ui.achievements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
import net.zodac.ludoplus.app.AchievementEvent
import net.zodac.ludoplus.app.AchievementEvents
import net.zodac.ludoplus.app.AchievementUnlocker
import net.zodac.ludoplus.app.AppContainer
import net.zodac.ludoplus.data.achievements.AchievementStore
import net.zodac.ludoplus.data.achievements.AchievementsState
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.AchievementEngine
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCategory

/** One row on the achievements list. [unlockedAt] is null while it's still locked. */
data class AchievementItem(
    val achievement: Achievement,
    val unlockedAt: Long? = null,
    val progress: Int = 0,
) {
    val progressFraction: Float get() = progress.toFloat() / achievement.target
}

/** A themed run of achievements, in the order they should be attempted. */
data class AchievementGroup(val category: AchievementCategory, val items: List<AchievementItem>)

data class AchievementsUiState(
    val groups: List<AchievementGroup> = emptyList(),
    val unlockedCount: Int = 0,
    val totalCount: Int = 0,
    /** Whether the unlocks have been read yet - until then an empty [groups] (and "0 of 0") means "not known yet", and
     * the screen shows no list at all. */
    val isLoaded: Boolean = false,
)

/** Backs the Achievements page: every achievement, grouped, with its progress, and the debug builds' superuser mode. */
class AchievementsViewModel(
    private val achievementsRepository: AchievementStore,
    /** Whether this is a debug build - superuser mode never activates without it. */
    private val isDebugBuild: Boolean = false,
    private val settingsRepository: SettingsRepository,
    /** The app's own copy of the Unearned switch ([AppContainer.lockedOnlyAchievements]); without one, it's read from [settingsRepository]. */
    lockedOnly: StateFlow<Boolean?>? = null,
    /** Where its achievements are earned: the app's one ([AppContainer.achievementUnlocker]); a test's own otherwise. */
    private val achievementUnlocker: AchievementUnlocker = AchievementUnlocker(achievementsRepository),
) : ViewModel() {

    /** Whether only the achievements not yet unlocked are shown - null until it has loaded, when the page shows them all. */
    val lockedOnly: StateFlow<Boolean?> = lockedOnly
        ?: settingsRepository.lockedOnlyAchievements.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setLockedOnly(lockedOnly: Boolean) {
        viewModelScope.launch { settingsRepository.setLockedOnlyAchievements(lockedOnly) }
    }

    // ---- Superuser mode (debug-only) -----------------------------------------------------------
    // A hidden tester's cheat: tap the unlocked-count banner SUPERUSER_TAP_TARGET times to enter
    // it, then long-press any row to force it locked/unlocked - see onUnlockedCountTapped and
    // onSuperuserLongPressTick. Entirely [isDebugBuild]-gated: it isn't a discoverable easter egg
    // tied to an achievement, so there's no reason to track anything toward it in a release build.
    private val _superuserModeActive = MutableStateFlow(false)
    val superuserModeActive: StateFlow<Boolean> = _superuserModeActive.asStateFlow()
    private var unlockedCountTapCount = 0

    // A counter achievement's progress is a real, persisted number shared with other achievements
    // on the same counter (e.g. GAMES_PLAYED backs GETTING_COMFORTABLE/50/100) - superuser mode doesn't fake it. Instead each ticked
    // achievement gets its own purely in-memory bump, added on top of its real progress only for
    // display and for deciding when to force-unlock it; nothing here is ever persisted, so it can't
    // corrupt a shared counter.
    private val superuserProgressOverride = MutableStateFlow<Map<Achievement, Int>>(emptyMap())


    private val _toastMessages = Channel<String>(Channel.BUFFERED)
    val toastMessages: Flow<String> = _toastMessages.receiveAsFlow()

    val uiState: StateFlow<AchievementsUiState> =
        combine(achievementsRepository.state, superuserProgressOverride, ::toUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), AchievementsUiState())

    /** The unlocked-count banner's tap-5-times entry point into superuser mode. */
    fun onUnlockedCountTapped() {
        if (!isDebugBuild || _superuserModeActive.value) return
        unlockedCountTapCount++
        if (unlockedCountTapCount >= SUPERUSER_TAP_TARGET) {
            unlockedCountTapCount = 0
            _superuserModeActive.value = true
            _toastMessages.trySend("Superuser mode activated!") // i18n: not translated - a debug-build developer toast
        }
    }

    /**
     * One 500ms step of a long press on [achievement]'s row, [tickCount] counting up from 1 for
     * as long as the press is held (see [AchievementsScreen]'s gesture handler). What it does
     * depends on the achievement's current state:
     *  - already unlocked -> the *first* tick reverts it to locked; later ticks on the same press
     *    are a no-op so holding longer doesn't flip it back and forth.
     *  - locked, no progress bar -> the first tick unlocks it outright, same reasoning.
     *  - locked, with a progress bar -> each tick nudges it one step closer (a progress banner
     *    pops, same as real play) until either it reaches its target or [tickCount] reaches
     *    [SUPERUSER_FORCE_UNLOCK_TICKS] (10s of holding) - Well Rolled's target is
     *    10,000, and nobody's holding a row for that long one tick at a time.
     */
    fun onSuperuserLongPressTick(achievement: Achievement, tickCount: Int) {
        if (!isDebugBuild || !_superuserModeActive.value) return
        viewModelScope.launch {
            val before = achievementsRepository.current()
            when {
                before.isUnlocked(achievement) -> if (tickCount == 1) forceLock(achievement)
                !achievement.hasProgressBar -> if (tickCount == 1) forceUnlock(achievement)
                tickCount >= SUPERUSER_FORCE_UNLOCK_TICKS -> forceUnlock(achievement)
                else -> bumpProgress(achievement, before)
            }
        }
    }

    /**
     * Superuser-mode-only: a long press on the unlocked-count banner unlocks every achievement
     * still locked, all at once; the *next* long press, once none are left locked, relocks every
     * single one instead - including whatever was genuinely earned before superuser mode was ever
     * entered, the same "this row's real history doesn't matter, only its current state does"
     * reasoning [onSuperuserLongPressTick] uses per-row. Which of the two it does is decided fresh
     * each press from what's actually locked right now, so it's a real toggle, not a remembered
     * flag - if the two ever disagree (a row was individually unlocked in between), the banner
     * just does whichever of "unlock the rest" or "lock everything" the current state calls for.
     */
    fun onBannerLongPress() {
        if (!isDebugBuild || !_superuserModeActive.value) return
        viewModelScope.launch {
            val before = achievementsRepository.current()
            val locked = Achievement.entries.filterNot(before::isUnlocked)
            if (locked.isNotEmpty()) {
                achievementUnlocker.unlock(locked.toSet())
            } else {
                Achievement.entries.forEach { achievementsRepository.forceLock(it) }
            }
            superuserProgressOverride.value = emptyMap()
        }
    }

    private suspend fun forceLock(achievement: Achievement) {
        achievementsRepository.forceLock(achievement)
        superuserProgressOverride.value -= achievement
    }

    private suspend fun forceUnlock(achievement: Achievement) {
        if (achievementUnlocker.unlock(setOf(achievement)).isEmpty) return
        superuserProgressOverride.value -= achievement
    }

    private fun bumpProgress(achievement: Achievement, before: AchievementsState) {
        val overrides = superuserProgressOverride.value
        val baseProgress = AchievementEngine.progressOf(achievement, before.counters)
        val previousOverride = overrides[achievement] ?: 0
        val newOverride = previousOverride + 1
        val previous = (baseProgress + previousOverride).coerceAtMost(achievement.target)
        val current = (baseProgress + newOverride).coerceAtMost(achievement.target)
        superuserProgressOverride.value = overrides + (achievement to newOverride)
        if (current >= achievement.target) {
            // The unlock banner says it all - same reason AchievementEngine.progressEvent()
            // suppresses a progress event once an achievement is about to be reported unlocked.
            viewModelScope.launch { forceUnlock(achievement) }
        } else if (current > previous) {
            AchievementEvents.emit(AchievementEvent.Progressed(achievement, previous, current))
        }
    }

    private fun toUiState(state: AchievementsState, progressOverride: Map<Achievement, Int>): AchievementsUiState {
        val items = Achievement.entries
            .map {
                val progress = (AchievementEngine.progressOf(it, state.counters) + (progressOverride[it] ?: 0)).coerceAtMost(it.target)
                AchievementItem(it, state.unlockedAt[it], progress)
            }

        return AchievementsUiState(
            // Grouped by theme and, within a theme, easiest first - which is just the catalogue's
            // own declaration order, so a ladder stays a ladder whether its achievements are
            // earned yet or not. Alphabetical would scatter "Getting Comfortable", "Regular" and
            // "Centurion" across the list; splitting earned ones into their own section would
            // scatter the ladders themselves.
            groups = items
                .groupBy { it.achievement.category }
                .map { (category, categoryItems) -> AchievementGroup(category, categoryItems) },
            unlockedCount = items.count { it.unlockedAt != null },
            totalCount = items.count { it.achievement.category != AchievementCategory.EASTER_EGGS },
            isLoaded = true,
        )
    }

    companion object {
        /** How many taps on the unlocked-count banner enter superuser mode. */
        const val SUPERUSER_TAP_TARGET = 5

        /** How often a held long-press ticks - read by [AchievementsScreen]'s gesture handler too,
         * so the 500ms in the feature's spec only lives in one place. */
        const val SUPERUSER_TICK_MILLIS = 500L

        /** [SUPERUSER_TICK_MILLIS] * this many ticks is 10s - past that, a held long-press jumps
         * straight to a full unlock rather than continuing to add 1 per tick, which would take
         * forever on a target like Well Rolled's 10,000. */
        const val SUPERUSER_FORCE_UNLOCK_TICKS = 20

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AchievementsViewModel(
                    achievementsRepository = container.achievementsRepository,
                    isDebugBuild = container.buildInfo.isDebug,
                    settingsRepository = container.settingsRepository,
                    lockedOnly = container.lockedOnlyAchievements,
                    achievementUnlocker = container.achievementUnlocker,
                )
            }
        }
    }
}
