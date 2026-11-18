package net.zodac.ludo

import org.jetbrains.compose.resources.getString
import net.zodac.ludo.app.UnlockedStyle
import net.zodac.ludo.ui.style.StyleFamily
import net.zodac.ludo.ui.style.StyleCatalog
import net.zodac.ludo.ui.style.TableArt
import net.zodac.ludo.ui.styles.StylesViewModel
import net.zodac.ludo.data.settings.SavedStyles
import kotlinx.coroutines.flow.StateFlow
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.app.BuildInfo
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.achievements.AchievementStore
import net.zodac.ludo.data.achievements.InMemoryAchievementStore
import net.zodac.ludo.data.game.InProgressGameRepository
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.records.InMemoryGameResultDao
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.online.OnlineSettingsRepository
import net.zodac.ludo.purchases.PurchasesRepository
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
    onlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()),
    purchasesRepository = PurchasesRepository(InMemoryPreferences()),
    buildInfo = BuildInfo(versionName = "1.0.0", isDebug = false),
)

/** A Styles view model over in-memory repositories; [saved] stands in for the app's own copy of the picks, as the container passes it. */
internal fun stylesViewModel(
    saved: StateFlow<SavedStyles?>? = null,
    settings: SettingsRepository = SettingsRepository(InMemoryPreferences()),
) = StylesViewModel(settings, InMemoryAchievementStore(), saved)

/** The family of this catalogue called [name] in the test's language. */
internal suspend fun <T : TableArt> StyleCatalog<T>.familyNamed(name: String): StyleFamily<T> =
    families.first { getString(it.name) == name }

/** A banner's entry for the style [name] of this catalogue. */
internal suspend fun StyleCatalog<*>.unlockedStyle(name: String): UnlockedStyle = UnlockedStyle(familyNamed(name).name, noun)
