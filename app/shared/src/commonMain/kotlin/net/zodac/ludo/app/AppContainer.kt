package net.zodac.ludo.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.room.RoomDatabase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.zodac.ludo.data.PreferencesFile
import net.zodac.ludo.data.createPreferencesDataStore
import net.zodac.ludo.data.game.InProgressGameRepository
import net.zodac.ludo.data.records.AppDatabase
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.records.buildAppDatabase
import net.zodac.ludo.data.settings.SettingsRepository

/**
 * The app's long-lived objects, built once per process by the platform's entry point and handed to
 * the UI through [LocalAppContainer]: the ViewModel factories take their repositories from here,
 * rather than each one reaching for an Android `Context` to build its own.
 *
 * The settings, the game in progress, and the game records for now. The repositories for achievements, online play, and
 * purchases join it in their phases of `.claude/IMPLEMENTATION.md`.
 */
class AppContainer(
    val settingsRepository: SettingsRepository,
    val inProgressGameRepository: InProgressGameRepository,
    val gameRecordRepository: GameRecordRepository,
    val buildInfo: BuildInfo,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /** The time the app is in front of the player, counted into [SettingsRepository.playSeconds]: resumed and paused by the app. */
    val playTime: PlayTimeTracker = PlayTimeTracker(settingsRepository::addPlaySeconds, scope)

    private val mutableDatabaseFailure = MutableStateFlow<String?>(null)

    /**
     * Why the game records database couldn't be opened (a migration missing or failing), as the error's stack trace:
     * null while it opens, or did. Opened as the container is built, so the app can say so in place of its pages, rather
     * than fail on the first one to read a record.
     */
    val databaseFailure: StateFlow<String?> = mutableDatabaseFailure.asStateFlow()

    init {
        // Tidies away settings an older version left behind.
        scope.launch { settingsRepository.forgetRetired() }
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

            return AppContainer(
                settingsRepository = SettingsRepository(dataStore(PreferencesFile.SETTINGS)),
                inProgressGameRepository = InProgressGameRepository(dataStore(PreferencesFile.IN_PROGRESS_GAME)),
                gameRecordRepository = GameRecordRepository(databaseBuilder.buildAppDatabase().gameResultDao(), computeContext = Dispatchers.Default),
                buildInfo = buildInfo,
            )
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided - the root composable provides the one the platform built")
}
