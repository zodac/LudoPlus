package net.zodac.ludoplus.app

import net.zodac.ludoplus.data.settings.savedStylesFlow
import net.zodac.ludoplus.data.settings.SavedStyles
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.annotation.VisibleForTesting
import androidx.room.RoomDatabase
import kotlin.random.Random
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.zodac.ludoplus.data.PreferencesFile
import net.zodac.ludoplus.data.achievements.AchievementStore
import net.zodac.ludoplus.data.achievements.AchievementsRepository
import net.zodac.ludoplus.data.createPreferencesDataStore
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.game.SavedGameDiscard
import net.zodac.ludoplus.data.records.AppDatabase
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.buildAppDatabase
import net.zodac.ludoplus.data.orNullOnFailure
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.ModePage
import net.zodac.ludoplus.online.CloudSaveStore
import net.zodac.ludoplus.online.LocalCloudSave
import net.zodac.ludoplus.online.OnlineSettingsRepository
import net.zodac.ludoplus.purchases.Entitlement
import net.zodac.ludoplus.purchases.PurchasesRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The app's long-lived objects, built once per process by the platform's entry point and handed to
 * the UI through [LocalAppContainer]: the ViewModel factories take their repositories from here,
 * rather than each one reaching for an Android `Context` to build its own.
 *
 * The settings, the game in progress, the game records, the achievements, the saved styles, and the cloud save's store
 * over them. The repositories for online play and purchases join it in their phases of `.claude/IMPLEMENTATION.md`.
 */
class AppContainer(
    val settingsRepository: SettingsRepository,
    val inProgressGameRepository: InProgressGameRepository,
    val gameRecordRepository: GameRecordRepository,
    val achievementsRepository: AchievementStore,
    /** The online service's bookkeeping on this device ([net.zodac.ludoplus.online.OnlineSync]'s). */
    val onlineSettingsRepository: OnlineSettingsRepository,
    /** What the store last said the player has bought. */
    val purchasesRepository: PurchasesRepository,
    val buildInfo: BuildInfo,
    /** This device's side of a progress file (and, from Play Games, the cloud save): null where nothing can be merged in. */
    val cloudSave: CloudSaveStore? = null,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    /** The time the app is in front of the player, counted into [AchievementStore.playSeconds]: resumed and paused by the app. */
    val playTime: PlayTimeTracker = PlayTimeTracker(achievementsRepository::addPlaySeconds, scope)

    /** Debug builds only: the Styles page's "pretend all styles are owned" switch, for this run of the app. */
    val pretendOwnsAllStyles = MutableStateFlow(false)

    /**
     * Whether every style is unlocked without its achievements: the store's last word on the purchases
     * ([PurchasesRepository]), or, in a debug build only, [pretendOwnsAllStyles].
     */
    val ownsAllStyles: Flow<Boolean> =
        combine(purchasesRepository.has(Entitlement.ALL_STYLES), pretendOwnsAllStyles) { owns, pretend -> owns || (pretend && buildInfo.isDebug) }
            .distinctUntilChanged()

    /** The saved Styles picks and what unlocks them, kept loaded for the process, so a screen has them as it opens. */
    val savedStyles: StateFlow<SavedStyles?> =
        savedStylesFlow(settingsRepository, achievementsRepository, ownsAllStyles).stateIn(scope, SharingStarted.Eagerly, null)

    /** Whether the player has had the ads removed, loaded with the container so the first page is laid out for it: null until then. */
    val adsRemoved: StateFlow<Boolean?> = purchasesRepository.has(Entitlement.NO_ADS).stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Whether a person has finished a game on this device ([GameRecordRepository.hasFinishedGame]), which the ad banner
     * waits for: read again with each write to the records, and loaded with the container as [adsRemoved] is. Null until
     * then; a read that fails counts as none.
     */
    val gameFinished: StateFlow<Boolean?> =
        gameRecordRepository.writes.map { orNullOnFailure { gameRecordRepository.hasFinishedGame() } ?: false }
            .distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    /** The one way an achievement is earned, shared by every screen - see [AchievementUnlocker]. */
    val achievementUnlocker: AchievementUnlocker = AchievementUnlocker(achievementsRepository, ownsAllStyles)

    /** The Achievements page's Unearned switch, loaded with the container so the page opens already filtered: null until then. */
    val lockedOnlyAchievements: StateFlow<Boolean?> = settingsRepository.lockedOnlyAchievements.stateIn(scope, SharingStarted.Eagerly, null)

    /** The Styles page's Unlocked switch, loaded with the container so the page opens already filtered: null until then. */
    val unlockedOnlyStyles: StateFlow<Boolean?> = settingsRepository.unlockedOnlyStyles.stateIn(scope, SharingStarted.Eagerly, null)

    /** The Leaderboard's board, loaded with the container so the page opens straight on it: null until then. */
    val leaderboardMode: StateFlow<GameMode?> = settingsRepository.leaderboardMode.stateIn(scope, SharingStarted.Eagerly, null)

    /** The Statistics page, loaded as [leaderboardMode] is. */
    val statisticsPage: StateFlow<ModePage?> = settingsRepository.statisticsPage.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * The die every game rolls ([net.zodac.ludoplus.ui.game.GameViewModel]'s random). Replaced only by the store-asset
     * generator, to deal the rolls its promo video shows: see `.claude/STORE_ASSETS.md`.
     */
    @set:VisibleForTesting
    var diceRandom: Random = Random.Default

    private val mutableDatabaseFailure = MutableStateFlow<String?>(null)

    /**
     * Why the game records database couldn't be opened (a migration missing or failing), as the error's stack trace:
     * null while it opens, or did. Opened as the container is built, so the app can say so in place of its pages, rather
     * than fail on the first one to read a record.
     */
    val databaseFailure: StateFlow<String?> = mutableDatabaseFailure.asStateFlow()

    init {
        scope.launch {
            // The time played an older version kept with the settings joins the counted time first, then the settings an
            // older version left behind are tidied away, that time among them: not if it couldn't be moved.
            val moved = orNullOnFailure { settingsRepository.legacyPlaySeconds().let { if (it > 0) achievementsRepository.addPlaySeconds(it) } }
            if (moved != null) settingsRepository.forgetRetired()
        }
        scope.launch {
            try {
                gameRecordRepository.open()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableDatabaseFailure.value = e.stackTraceToString()
            }
        }
    }

    companion object {
        /**
         * Builds the container every platform shares: the game records database from the platform's [databaseBuilder]
         * (its driver and path), and one DataStore per [PreferencesFile] in [dataDir]. A saved game [savedGameDiscard] asks to be
         * removed goes first.
         */
        fun create(
            dataDir: String,
            databaseBuilder: RoomDatabase.Builder<AppDatabase>,
            buildInfo: BuildInfo,
            savedGameDiscard: SavedGameDiscard = SavedGameDiscard.None,
        ): AppContainer {
            fun dataStore(file: PreferencesFile) = createPreferencesDataStore("$dataDir/${file.fileName}")

            val settings = SettingsRepository(dataStore(PreferencesFile.SETTINGS))
            val inProgressGame = InProgressGameRepository(dataStore(PreferencesFile.IN_PROGRESS_GAME), discard = savedGameDiscard)
            val records = GameRecordRepository(databaseBuilder.buildAppDatabase().gameResultDao(), computeContext = Dispatchers.Default)
            val achievements = AchievementsRepository(dataStore(PreferencesFile.ACHIEVEMENTS))
            return AppContainer(
                settingsRepository = settings,
                inProgressGameRepository = inProgressGame,
                gameRecordRepository = records,
                achievementsRepository = achievements,
                onlineSettingsRepository = OnlineSettingsRepository(dataStore(PreferencesFile.ONLINE)),
                purchasesRepository = PurchasesRepository(dataStore(PreferencesFile.PURCHASES)),
                buildInfo = buildInfo,
                cloudSave = LocalCloudSave(achievements, records, settings, inProgressGame),
            )
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("No AppContainer provided - the root composable provides the one the platform built")
}
