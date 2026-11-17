package net.zodac.ludo.app

import net.zodac.ludo.data.settings.savedStylesFlow
import net.zodac.ludo.data.settings.SavedStyles
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.room.RoomDatabase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.ludo.data.PreferencesFile
import net.zodac.ludo.data.achievements.AchievementStore
import net.zodac.ludo.data.achievements.AchievementsRepository
import net.zodac.ludo.data.createPreferencesDataStore
import net.zodac.ludo.data.game.InProgressGameRepository
import net.zodac.ludo.data.records.AppDatabase
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.records.buildAppDatabase
import net.zodac.ludo.data.orNullOnFailure
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.online.CloudSaveStore
import net.zodac.ludo.online.LocalCloudSave
import net.zodac.ludo.online.OnlineSettingsRepository

/**
 * The app's long-lived objects, built once per process by the platform's entry point and handed to
 * the UI through [LocalAppContainer]: the ViewModel factories take their repositories from here,
 * rather than each one reaching for an Android `Context` to build its own.
 *
 * The settings, the game in progress, the game records, the achievements, the saved styles, and the cloud save's store
 * over them. The repositories for online play and purchases join it in their phases of `.claude/IMPLEMENTATION.md`.
 */
class AppContainer(
    val settingsRepository: SettingsRepository,
    val inProgressGameRepository: InProgressGameRepository,
    val gameRecordRepository: GameRecordRepository,
    val achievementsRepository: AchievementStore,
    /** The online service's bookkeeping on this device ([net.zodac.ludo.online.OnlineSync]'s). */
    val onlineSettingsRepository: OnlineSettingsRepository,
    val buildInfo: BuildInfo,
    /** This device's side of a progress file (and, from Play Games, the cloud save): null where nothing can be merged in. */
    val cloudSave: CloudSaveStore? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /** The time the app is in front of the player, counted into [AchievementStore.playSeconds]: resumed and paused by the app. */
    val playTime: PlayTimeTracker = PlayTimeTracker(achievementsRepository::addPlaySeconds, scope)

    /** Debug builds only: the Styles page's "pretend all styles are owned" switch, for this run of the app. */
    val pretendOwnsAllStyles = MutableStateFlow(false)

    /**
     * Whether every style is unlocked without its achievements: in a debug build only, while [pretendOwnsAllStyles]. The
     * "Unlock all styles" purchase joins it in Phase 11.
     */
    val ownsAllStyles: Flow<Boolean> = pretendOwnsAllStyles.map { pretend -> pretend && buildInfo.isDebug }

    /** The saved Styles picks and what unlocks them, kept loaded for the process, so a screen has them as it opens. */
    val savedStyles: StateFlow<SavedStyles?> =
        savedStylesFlow(settingsRepository, achievementsRepository, ownsAllStyles).stateIn(scope, SharingStarted.Eagerly, null)

    /** The one way an achievement is earned, shared by every screen - see [AchievementUnlocker]. */
    val achievementUnlocker: AchievementUnlocker = AchievementUnlocker(achievementsRepository, ownsAllStyles)

    /** The Achievements page's Unearned switch, loaded with the container so the page opens already filtered: null until then. */
    val lockedOnlyAchievements: StateFlow<Boolean?> = settingsRepository.lockedOnlyAchievements.stateIn(scope, SharingStarted.Eagerly, null)

    /** The Styles page's Unlocked switch, loaded with the container so the page opens already filtered: null until then. */
    val unlockedOnlyStyles: StateFlow<Boolean?> = settingsRepository.unlockedOnlyStyles.stateIn(scope, SharingStarted.Eagerly, null)

    private val mutableDatabaseFailure = MutableStateFlow<String?>(null)

    /**
     * Why the game records database couldn't be opened (a migration missing or failing), as the error's stack trace:
     * null while it opens, or did. Opened as the container is built, so the app can say so in place of its pages, rather
     * than fail on the first one to read a record.
     */
    val databaseFailure: StateFlow<String?> = mutableDatabaseFailure.asStateFlow()

    init {
        scope.launch {
            // The time played an older version kept with the settings joins the counted time first, then the settings an
            // older version left behind are tidied away, that time among them: not if it couldn't be moved.
            val moved = orNullOnFailure { settingsRepository.legacyPlaySeconds().let { if (it > 0) achievementsRepository.addPlaySeconds(it) } }
            if (moved != null) settingsRepository.forgetRetired()
        }
        scope.launch {
            try {
                gameRecordRepository.open()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableDatabaseFailure.value = e.stackTraceToString()
            }
        }
    }

    companion object {
        /**
         * Builds the container every platform shares: the game records database from the platform's [databaseBuilder]
         * (its driver and path), and one DataStore per [PreferencesFile] in [dataDir].
         */
        fun create(dataDir: String, databaseBuilder: RoomDatabase.Builder<AppDatabase>, buildInfo: BuildInfo): AppContainer {
            fun dataStore(file: PreferencesFile) = createPreferencesDataStore("$dataDir/${file.fileName}")

            val settings = SettingsRepository(dataStore(PreferencesFile.SETTINGS))
            val inProgressGame = InProgressGameRepository(dataStore(PreferencesFile.IN_PROGRESS_GAME))
            val records = GameRecordRepository(databaseBuilder.buildAppDatabase().gameResultDao(), computeContext = Dispatchers.Default)
            val achievements = AchievementsRepository(dataStore(PreferencesFile.ACHIEVEMENTS))
            return AppContainer(
                settingsRepository = settings,
                inProgressGameRepository = inProgressGame,
                gameRecordRepository = records,
                achievementsRepository = achievements,
                onlineSettingsRepository = OnlineSettingsRepository(dataStore(PreferencesFile.ONLINE)),
                buildInfo = buildInfo,
                cloudSave = LocalCloudSave(achievements, records, settings, inProgressGame),
            )
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided - the root composable provides the one the platform built")
}
