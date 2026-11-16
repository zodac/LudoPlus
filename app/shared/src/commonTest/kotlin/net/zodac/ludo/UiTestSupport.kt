package net.zodac.ludo

import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.app.BuildInfo
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.ui.game.GameViewModel

/** A game view model for a screen test, over in-memory settings. */
internal fun gameViewModel(settings: SettingsRepository = SettingsRepository(InMemoryPreferences())) = GameViewModel(settings)

/** The app's container over in-memory settings, for a screen that reads it through `LocalAppContainer`. */
internal fun inMemoryAppContainer(settings: SettingsRepository = SettingsRepository(InMemoryPreferences())) = AppContainer(
    settingsRepository = settings,
    buildInfo = BuildInfo(versionName = "1.0.0", isDebug = false),
)
