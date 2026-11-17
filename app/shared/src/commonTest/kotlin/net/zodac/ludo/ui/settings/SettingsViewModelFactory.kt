package net.zodac.ludo.ui.settings

import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.achievements.AchievementStore
import net.zodac.ludo.data.achievements.InMemoryAchievementStore
import net.zodac.ludo.data.settings.SettingsRepository

/** A Settings view model over in-memory settings and achievements. */
fun settingsViewModel(
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    achievements: AchievementStore = InMemoryAchievementStore(),
) = SettingsViewModel(settings, achievements)
