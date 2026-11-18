package net.zodac.ludoplus.ui.settings

import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.achievements.AchievementStore
import net.zodac.ludoplus.data.achievements.InMemoryAchievementStore
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.nowEpochMillis
import net.zodac.ludoplus.online.ProgressTransfer

/** A Settings view model over in-memory settings and achievements, with any a test cares about passed in. */
fun settingsViewModel(
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    achievements: AchievementStore = InMemoryAchievementStore(),
    now: () -> Long = ::nowEpochMillis,
    progressTransfer: ProgressTransfer? = null,
) = SettingsViewModel(settings, achievements, progressTransfer = progressTransfer, now = now)
