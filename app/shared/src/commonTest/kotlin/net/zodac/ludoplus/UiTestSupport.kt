package net.zodac.ludoplus

import org.jetbrains.compose.resources.getString
import net.zodac.ludoplus.app.UnlockedStyle
import net.zodac.ludoplus.ui.style.StyleFamily
import net.zodac.ludoplus.ui.style.StyleCatalog
import net.zodac.ludoplus.ui.style.TableArt
import net.zodac.ludoplus.ui.styles.StylesViewModel
import net.zodac.ludoplus.data.settings.SavedStyles
import kotlinx.coroutines.flow.StateFlow
import net.zodac.ludoplus.app.AppContainer
import net.zodac.ludoplus.app.BuildInfo
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.achievements.AchievementStore
import net.zodac.ludoplus.data.achievements.InMemoryAchievementStore
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.online.OnlineSettingsRepository
import net.zodac.ludoplus.purchases.PurchasesRepository
import net.zodac.ludoplus.ui.achievements.AchievementsViewModel
import net.zodac.ludoplus.ui.game.GameViewModel

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
    purchases: PurchasesRepository = PurchasesRepository(InMemoryPreferences()),
) = AppContainer(
    settingsRepository = settings,
    inProgressGameRepository = saved,
    gameRecordRepository = records,
    achievementsRepository = achievements,
    onlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()),
    purchasesRepository = purchases,
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
