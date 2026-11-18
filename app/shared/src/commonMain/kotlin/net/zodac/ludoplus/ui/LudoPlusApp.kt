package net.zodac.ludoplus.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import net.zodac.ludoplus.ads.AchievementBannersShowing
import net.zodac.ludoplus.ads.Ads
import net.zodac.ludoplus.ads.LocalAds
import net.zodac.ludoplus.ads.NoAds
import net.zodac.ludoplus.app.AppContainer
import net.zodac.ludoplus.app.Breadcrumbs
import net.zodac.ludoplus.app.LocalAppContainer
import net.zodac.ludoplus.data.orNullOnFailure
import net.zodac.ludoplus.data.settings.AnimationLevel
import net.zodac.ludoplus.navigation.AchievementScrollRequest
import net.zodac.ludoplus.navigation.AchievementScrollRequests
import net.zodac.ludoplus.navigation.LudoPlusNavHost
import net.zodac.ludoplus.netplay.LocalNearby
import net.zodac.ludoplus.netplay.Nearby
import net.zodac.ludoplus.netplay.NoNearby
import net.zodac.ludoplus.navigation.Screen
import net.zodac.ludoplus.navigation.StyleScrollRequest
import net.zodac.ludoplus.navigation.StyleScrollRequests
import net.zodac.ludoplus.online.GameServices
import net.zodac.ludoplus.online.GameServicesState
import net.zodac.ludoplus.online.LocalGameServices
import net.zodac.ludoplus.online.LocalOnlineSyncStatus
import net.zodac.ludoplus.online.NoGameServices
import net.zodac.ludoplus.online.OnlineSync
import net.zodac.ludoplus.online.OnlineSyncRequests
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.PlatformServices
import net.zodac.ludoplus.purchases.Entitlement
import net.zodac.ludoplus.purchases.LocalStore
import net.zodac.ludoplus.purchases.NoStore
import net.zodac.ludoplus.purchases.Store
import net.zodac.ludoplus.ui.achievements.AchievementBannerHost
import net.zodac.ludoplus.ui.common.DatabaseFailurePage
import net.zodac.ludoplus.ui.common.DriftState
import net.zodac.ludoplus.ui.common.FramePaces
import net.zodac.ludoplus.ui.common.LastMotionSettings
import net.zodac.ludoplus.ui.common.LocalDriftState
import net.zodac.ludoplus.ui.common.LocalFramePace
import net.zodac.ludoplus.ui.common.LocalFramePaces
import net.zodac.ludoplus.ui.common.LocalMotionSettings
import net.zodac.ludoplus.ui.common.LocalReduceMotion
import net.zodac.ludoplus.ui.common.LocalScreenIdle
import net.zodac.ludoplus.ui.common.ScreenIdle
import net.zodac.ludoplus.ui.common.delayWhileResumed
import net.zodac.ludoplus.ui.common.rememberPageCover
import net.zodac.ludoplus.ui.common.wakesScreen
import net.zodac.ludoplus.ui.game.LeaveGameConfirmation
import net.zodac.ludoplus.ui.game.LeaveGameConfirmationDialog
import net.zodac.ludoplus.ui.game.LocalGameHaptics
import net.zodac.ludoplus.ui.game.LocalLeaveGameConfirmation
import net.zodac.ludoplus.ui.game.LocalSoundEffects
import net.zodac.ludoplus.ui.game.rememberAppHaptics
import net.zodac.ludoplus.ui.game.rememberAppSoundEffects
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/**
 * The whole app, on whatever platform hosts it: each platform's entry point builds its [AppContainer] and
 * [PlatformServices] (and, where it has them, its [GameServices] and [Store]) and hands them in here, and everything
 * below is shared.
 *
 * The achievement banners, the online sync, and the purchases' cache sit here, over every page.
 */
@OptIn(FlowPreview::class)
@Composable
fun LudoPlusApp(
    container: AppContainer,
    platform: PlatformServices,
    /** The platform's online game service, where it has one. */
    gameServices: GameServices = NoGameServices,
    /** The platform's app store, where it has one: what the player can buy, and has. */
    store: Store = NoStore,
    /** The platform's ad network, where it has one: the banner at the foot of the screen. */
    ads: Ads = NoAds,
    /** The platform's way of playing with phones in the same room, where it has one. */
    nearby: Nearby = NoNearby,
    /** What freezes the window's infinite animations, where it's the policy its recomposer runs under. */
    screenIdle: ScreenIdle = remember { ScreenIdle() },
) {
    // Held here, above the NavHost, so the backdrop's drift carries on unbroken across every screen.
    val driftState = remember { DriftState() }
    // Lifecycle-aware, so the system-settings observer behind it is only registered while the app is in front.
    val systemReduceMotion by remember(platform) { platform.reduceMotion() }.collectAsStateWithLifecycle(initialValue = false)
    // The player's own motion (the "Animations" level, or its Advanced settings): Menus & pages Off asks for the same as the
    // system's reduced motion, and the window's frames are capped at the fastest group's rate - see
    // PlatformServices.capFrameRate. Only the player's choice caps it: the system's reduced motion stops the motion,
    // not the frames.
    val savedMotion by container.settingsRepository.motionSettings.collectAsStateWithLifecycle(initialValue = AnimationLevel.default.motion)
    LaunchedEffect(platform, savedMotion) { platform.capFrameRate(savedMotion.maxFramesPerSecond) }
    // Null until read, so a saved lock isn't first undone for a frame by the default.
    val orientation by container.settingsRepository.screenOrientation.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(platform, orientation) { orientation?.let(platform::lockOrientation) }
    val motion = if (systemReduceMotion) AnimationLevel.OFF.motion else savedMotion
    val reduceMotion = !motion.menusMotion
    val framePaces = remember(motion) { FramePaces(motion) }
    SideEffect { LastMotionSettings.value = motion }
    // Infinite animations freeze after the motion's Pause when idle with nobody there (never, where it's off), and move on
    // again at the next touch or on coming back.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(screenIdle, lifecycle) { screenIdle.runTimer(lifecycle::delayWhileResumed) }
    LaunchedEffect(screenIdle, motion.idleTimeout) { screenIdle.setTimeout(motion.idleTimeout) }
    // Only while in front of the player: pausing covers the background, the screen going off, and a system sheet on top.
    LifecycleResumeEffect(container) {
        container.playTime.resume()
        onPauseOrDispose { container.playTime.pause() }
    }
    LifecycleResumeEffect(screenIdle) {
        screenIdle.wake()
        onPauseOrDispose { }
    }
    val leaveConfirmation = remember { LeaveGameConfirmation() }
    // Achievements, wins, and the cloud save are synced with the online service on signing in (and at each launch once
    // signed in), after every recorded change (one with no new unlock waits if the game screen is up), a few seconds after
    // a synced setting changes (a Styles pick), on leaving the game screen, on going to the background with something new
    // to save, and on coming back at least 60 seconds after the last sync; requests made while one runs fold into the next.
    val onlineSync = remember(container, gameServices) {
        OnlineSync(
            gameServices,
            container.achievementsRepository,
            container.onlineSettingsRepository,
            container.gameRecordRepository::unpostedWin,
            container.cloudSave,
        )
    }
    LaunchedEffect(onlineSync) {
        // Only a sign-in's sync shows its progress on the Settings card; the ones after it are quiet. All of this runs
        // on the one main thread, so the flag needs no lock.
        var signInPending = false
        val signedIn = gameServices.state.map { it is GameServicesState.SignedIn }.distinctUntilChanged().filter { it }.map { signInPending = true }
        merge(signedIn, OnlineSyncRequests.requests).conflate().collect {
            val showProgress = signInPending
            signInPending = false
            // A sync that fails here (the records can't be written) costs that sync: thrown on, it would take the app down,
            // and every later sync with it.
            orNullOnFailure { onlineSync.reconcile(showProgress) }
        }
    }
    LaunchedEffect(onlineSync) {
        gameServices.state.collect { if (it !is GameServicesState.SignedIn) onlineSync.forgetSignInSync() }
    }
    // What the store says the player has is kept on the device, which the style locks and the ad banner read.
    LaunchedEffect(container, store) { container.purchasesRepository.follow(store) }
    LaunchedEffect(container) {
        container.settingsRepository.syncedChanges.debounce(SETTINGS_SYNC_DELAY).collect { OnlineSyncRequests.request() }
    }
    // Put one device down and pick another up: the first saves, the second reads. A check that fails asks for the sync.
    val appSwitches = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        appSwitches.launch { if (orNullOnFailure { onlineSync.needsSyncOnLeaving() } != false) OnlineSyncRequests.request() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        appSwitches.launch { if (orNullOnFailure { onlineSync.needsSyncOnReturning() } != false) OnlineSyncRequests.request() }
    }
    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalMotionSettings provides motion,
        LocalFramePaces provides framePaces,
        LocalFramePace provides framePaces.menus,
        LocalAppContainer provides container,
        LocalPlatformServices provides platform,
        LocalGameServices provides gameServices,
        LocalStore provides store,
        LocalAds provides ads,
        LocalNearby provides nearby,
        LocalOnlineSyncStatus provides onlineSync.status,
        LocalDriftState provides driftState,
        LocalScreenIdle provides screenIdle,
        LocalLeaveGameConfirmation provides leaveConfirmation,
    ) {
        // Inside the provider above, for the platform it creates its player from.
        CompositionLocalProvider(
            LocalSoundEffects provides rememberAppSoundEffects(container.settingsRepository.soundEnabled),
            LocalGameHaptics provides rememberAppHaptics(container.settingsRepository.vibrationEnabled),
        ) {
            LudoPlusTheme {
                Surface(modifier = Modifier.fillMaxSize().wakesScreen(screenIdle)) {
                    // Records that can't be opened stop the app here, saying why, rather than on the first page to read one.
                    val databaseFailure by container.databaseFailure.collectAsStateWithLifecycle()
                    databaseFailure?.let {
                        DatabaseFailurePage(details = it)
                        return@Surface
                    }
                    val navController = rememberNavController()
                    val pageCover = rememberPageCover()
                    // Each page opened, for a crash report to say where the player was. It wakes the screen too: a screen
                    // reader's double tap opens a page with no touch for wakesScreen to see.
                    LaunchedEffect(navController) {
                        navController.currentBackStackEntryFlow.map { it.destination.route }.distinctUntilChanged()
                            .collect {
                                Breadcrumbs.record("Opened $it") // i18n: not translated - a crash report's breadcrumb
                                screenIdle.wake()
                            }
                    }
                    // What can wait for the game to end does: leaving its screen, below, asks for the sync anyway.
                    LaunchedEffect(navController) {
                        OnlineSyncRequests.outsideGameRequests.awayFrom(Screen.PLAY_GAME) { navController.currentDestination?.route }
                            .collect { OnlineSyncRequests.request() }
                    }
                    // A game left part-way through is saved as it was left, for another device's Continue.
                    LaunchedEffect(navController) {
                        navController.currentBackStackEntryFlow.map { it.destination.route }.leaving(Screen.PLAY_GAME)
                            .collect { OnlineSyncRequests.request() }
                    }
                    // The ad banner, at the foot of every page but the game's and the tutorial's, under the page rather than
                    // over it, while the network is ready and ads aren't removed (unknown counts as removed, so none flashes
                    // up for a buyer). The page then leaves the navigation bar's inset to the banner, which sits above it.
                    val route = navController.currentBackStackEntryAsState().value?.destination?.route
                    val adsReady by ads.ready.collectAsStateWithLifecycle()
                    val noAds by remember(container) { container.purchasesRepository.has(Entitlement.NO_ADS) }.collectAsStateWithLifecycle(initialValue = true)
                    val showAd = adsReady && !noAds && route != null && route != Screen.PLAY_GAME && route != Screen.TUTORIAL
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Above the NavHost, not inside it: the burst of banners at the end of a game has to survive the move
                        // from the board to Game Over and on to the menu, which a per-page overlay wouldn't.
                        AchievementBannerHost(
                            modifier = Modifier.weight(1f).fillMaxWidth()
                                .then(if (showAd) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
                            isOnGameScreen = { navController.currentDestination?.route == Screen.PLAY_GAME },
                            onAchievementSelected = { achievement ->
                                val alreadyOnAchievements = navController.currentDestination?.route == Screen.ACHIEVEMENTS
                                // Requested before navigating: an Achievements page not yet open starts its collector only
                                // once it composes, and the request's replay is what lets it still catch this.
                                AchievementScrollRequests.request(AchievementScrollRequest(achievement.id, animate = alreadyOnAchievements))
                                if (!alreadyOnAchievements) {
                                    pageCover.change(reduceMotion) { navController.navigate(Screen.ACHIEVEMENTS) }
                                }
                            },
                            onStylesSelected = { styles ->
                                val target = Screen.stylesBannerTarget(styles)
                                val alreadyThere = navController.currentDestination?.route == target
                                // Before navigating, for the same reason as the achievement request above.
                                StyleScrollRequests.request(StyleScrollRequest(styles, animate = alreadyThere))
                                if (!alreadyThere) {
                                    pageCover.change(reduceMotion) {
                                        // Back to the Styles page if it's open under this page; otherwise a fresh one.
                                        if (!navController.popBackStack(Screen.STYLES, inclusive = false)) navController.navigate(Screen.STYLES)
                                        if (target != Screen.STYLES) navController.navigate(target)
                                    }
                                }
                            },
                        ) {
                            LudoPlusNavHost(navController = navController, cover = pageCover)
                        }
                        if (showAd) {
                            // Takes no touch while an achievement banner is over it.
                            ads.Banner(
                                clickable = !AchievementBannersShowing.state.value,
                                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
                            )
                        }
                    }
                    LeaveGameConfirmationDialog(leaveConfirmation)
                }
            }
        }
    }
}

/** Emits each time these routes move off [route] to another. */
internal fun Flow<String?>.leaving(route: String): Flow<Unit> = flow {
    var previous: String? = null
    distinctUntilChanged().collect { current ->
        if (previous == route) emit(Unit)
        previous = current
    }
}

/** Each of these made while [current] isn't [route]. */
internal fun Flow<Unit>.awayFrom(route: String, current: () -> String?): Flow<Unit> = filter { current() != route }

/** How long synced settings are left unchanged before they're saved to the cloud: a run of Styles picks saves once. */
private val SETTINGS_SYNC_DELAY = 5.seconds
