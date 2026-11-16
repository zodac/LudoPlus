package net.zodac.ludo

import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.app.BuildInfo
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.game.InProgressGameRepository
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.ui.game.GameViewModel

/** A game view model for a screen test, over in-memory settings and saved game. */
internal fun gameViewModel(
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    saved: InProgressGameRepository = InProgressGameRepository(InMemoryPreferences()),
) = GameViewModel(settings, saved)

/** The app's container over in-memory settings and saved game, for a screen that reads it through `LocalAppContainer`. */
internal fun inMemoryAppContainer(
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    saved: InProgressGameRepository = InProgressGameRepository(InMemoryPreferences()),
) = AppContainer(
    settingsRepository = settings,
    inProgressGameRepository = saved,
    buildInfo = BuildInfo(versionName = "1.0.0", isDebug = false),
)
