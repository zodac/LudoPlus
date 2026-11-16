package net.zodac.ludo.ui.settings

import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.settings.SettingsRepository

/** A Settings view model over in-memory settings. */
fun settingsViewModel(settings: SettingsRepository = SettingsRepository(InMemoryPreferences())) = SettingsViewModel(settings)
