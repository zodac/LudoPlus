package net.zodac.ludo.app

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.zodac.ludo.data.PreferencesFile
import net.zodac.ludo.data.createPreferencesDataStore
import net.zodac.ludo.data.settings.SettingsRepository

/**
 * The app's long-lived objects, built once per process by the platform's entry point and handed to
 * the UI through [LocalAppContainer]: the ViewModel factories take their repositories from here,
 * rather than each one reaching for an Android `Context` to build its own.
 *
 * Only the settings for now. The repositories for a game in progress, scores, achievements, online play and purchases
 * join it in their phases of `.claude/IMPLEMENTATION.md`.
 */
class AppContainer(
    val settingsRepository: SettingsRepository,
    val buildInfo: BuildInfo,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    init {
        // Tidies away settings an older version left behind.
        scope.launch { settingsRepository.forgetRetired() }
    }

    companion object {
        /** Builds the container every platform shares: one DataStore per [PreferencesFile] in [dataDir]. */
        fun create(dataDir: String, buildInfo: BuildInfo): AppContainer {
            fun dataStore(file: PreferencesFile) = createPreferencesDataStore("$dataDir/${file.fileName}")

            return AppContainer(
                settingsRepository = SettingsRepository(dataStore(PreferencesFile.SETTINGS)),
                buildInfo = buildInfo,
            )
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided - the root composable provides the one the platform built")
}
