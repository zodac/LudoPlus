package net.zodac.ludo

import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.app.BuildInfo
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.achievements.AchievementStore
import net.zodac.ludo.data.achievements.InMemoryAchievementStore
import net.zodac.ludo.data.game.InProgressGameRepository
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.records.InMemoryGameResultDao
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.ui.achievements.AchievementsViewModel
import net.zodac.ludo.ui.game.GameViewModel

/** A game view model for a screen test, over in-memory settings, saved game, and records. */
internal fun gameViewModel(
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    saved: InProgressGameRepository = InProgressGameRepository(InMemoryPreferences()),
    records: GameRecordRepository = GameRecordRepository(InMemoryGameResultDao()),
) = GameViewModel(settings, saved, records)

/** An Achievements view model over in-memory repositories: nothing unlocked, unless a test passes its own. */
internal fun achievementsViewModel(
    store: AchievementStore = InMemoryAchievementStore(),
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
) = AchievementsViewModel(store, settingsRepository = settings)

/** The app's container over in-memory settings, saved game, records, and achievements, for a screen that reads it through `LocalAppContainer`. */
internal fun inMemoryAppContainer(
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
    saved: InProgressGameRepository = InProgressGameRepository(InMemoryPreferences()),
    records: GameRecordRepository = GameRecordRepository(InMemoryGameResultDao()),
    achievements: AchievementStore = InMemoryAchievementStore(),
) = AppContainer(
    settingsRepository = settings,
    inProgressGameRepository = saved,
    gameRecordRepository = records,
    achievementsRepository = achievements,
    buildInfo = BuildInfo(versionName = "1.0.0", isDebug = false),
)
