package net.zodac.ludo.ui.settings

import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.achievements.AchievementStore
import net.zodac.ludo.data.achievements.InMemoryAchievementStore
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.nowEpochMillis
import net.zodac.ludo.online.ProgressTransfer

/** A Settings view model over in-memory settings and achievements, with any a test cares about passed in. */
fun settingsViewModel(
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    achievements: AchievementStore = InMemoryAchievementStore(),
    now: () -> Long = ::nowEpochMillis,
    progressTransfer: ProgressTransfer? = null,
) = SettingsViewModel(settings, achievements, progressTransfer = progressTransfer, now = now)
