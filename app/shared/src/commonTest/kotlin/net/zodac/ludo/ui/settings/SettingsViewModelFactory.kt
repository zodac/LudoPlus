package net.zodac.ludo.ui.settings

import kotlinx.coroutines.flow.flowOf
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.achievements.AchievementStore
import net.zodac.ludo.data.achievements.InMemoryAchievementStore
import net.zodac.ludo.data.scores.InMemoryScoreDao
import net.zodac.ludo.data.scores.ScoreRepository
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.nowEpochMillis
import net.zodac.ludo.online.OnlineSettingsRepository
import net.zodac.ludo.online.ProgressTransfer

/** A Settings view model over in-memory repositories, with any a test cares about passed in. */
fun settingsViewModel(
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    achievements: AchievementStore = InMemoryAchievementStore(),
    scores: ScoreRepository = ScoreRepository(InMemoryScoreDao()),
    online: OnlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()),
    now: () -> Long = ::nowEpochMillis,
    progressTransfer: ProgressTransfer? = null,
    ownsAllStyles: Boolean = false,
) = SettingsViewModel(settings, achievements, scores, online, now, progressTransfer, flowOf(ownsAllStyles))
