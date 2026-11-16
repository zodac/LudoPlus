package net.zodac.ludo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.app.Breadcrumbs
import net.zodac.ludo.app.LocalAppContainer
import net.zodac.ludo.data.settings.AnimationLevel
import net.zodac.ludo.navigation.LudoPlusNavHost
import net.zodac.ludo.platform.LocalPlatformServices
import net.zodac.ludo.platform.PlatformServices
import net.zodac.ludo.ui.common.DriftState
import net.zodac.ludo.ui.common.FramePaces
import net.zodac.ludo.ui.common.LastMotionSettings
import net.zodac.ludo.ui.common.LocalDriftState
import net.zodac.ludo.ui.common.LocalFramePace
import net.zodac.ludo.ui.common.LocalFramePaces
import net.zodac.ludo.ui.common.LocalMotionSettings
import net.zodac.ludo.ui.common.LocalReduceMotion
import net.zodac.ludo.ui.common.LocalScreenIdle
import net.zodac.ludo.ui.common.ScreenIdle
import net.zodac.ludo.ui.common.delayWhileResumed
import net.zodac.ludo.ui.common.rememberPageCover
import net.zodac.ludo.ui.common.wakesScreen
import net.zodac.ludo.ui.theme.LudoPlusTheme

/**
 * The whole app, on whatever platform hosts it: each platform's entry point builds its [AppContainer] and
 * [PlatformServices] and hands them in here, and everything below is shared.
 *
 * DiceFive's online sync, purchases, play-time count and achievement banners hung off this root too; they come back in
 * their phases of `.claude/IMPLEMENTATION.md`.
 */
@Composable
fun LudoPlusApp(
    container: AppContainer,
    platform: PlatformServices,
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
    LifecycleResumeEffect(screenIdle) {
        screenIdle.wake()
        onPauseOrDispose { }
    }
    CompositionLocalProvider(
        LocalReduceMotion provides reduceMotion,
        LocalMotionSettings provides motion,
        LocalFramePaces provides framePaces,
        LocalFramePace provides framePaces.menus,
        LocalAppContainer provides container,
        LocalPlatformServices provides platform,
        LocalDriftState provides driftState,
        LocalScreenIdle provides screenIdle,
    ) {
        LudoPlusTheme {
            Surface(modifier = Modifier.fillMaxSize().wakesScreen(screenIdle)) {
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
                LudoPlusNavHost(navController = navController, cover = pageCover)
            }
        }
    }
}
