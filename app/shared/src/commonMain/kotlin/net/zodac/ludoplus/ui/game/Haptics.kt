package net.zodac.ludoplus.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.ludoplus.platform.HapticEffect
import net.zodac.ludoplus.platform.HapticsPlayer
import kotlinx.coroutines.flow.Flow
import net.zodac.ludoplus.platform.LocalPlatformServices

/** Short vibrations fired alongside [SoundEffects] in [GameScreen]: a tick for a pawn moved, and a longer buzz for a
 * capture. There's no release step to manage the way [SoundEffects] needs one. */
class GameHaptics(createPlayer: () -> HapticsPlayer) {

    /** Built on the first vibration actually fired, so a session with vibration off never looks up the vibrator. */
    private val player by lazy(createPlayer)
    private var playerCreated = false

    /** Follows the Settings screen's "Vibration" switch, set once by [rememberAppHaptics] - see [SoundEffects.enabled]
     * for why this is a plain var rather than gating at the call site. */
    var enabled: Boolean = true

    fun playMoveTick() {
        if (enabled) play(HapticEffect.SELECT_TICK)
    }

    fun playCaptureBuzz() {
        if (enabled) play(HapticEffect.CAPTURE_BUZZ)
    }

    /** A Nearby guest's turn is nearly up: the capture's longer buzz. */
    fun playTurnWarning() {
        if (enabled) play(HapticEffect.CAPTURE_BUZZ)
    }

    private fun play(effect: HapticEffect) {
        playerCreated = true
        player.play(effect)
    }

    /** Stops a buzz under way - the app has gone to the background. */
    fun cancel() {
        if (playerCreated) player.cancel()
    }
}

/** The app's one [GameHaptics] - see [rememberAppHaptics] - or null outside the app (a lone screen in a test). */
val LocalGameHaptics = staticCompositionLocalOf<GameHaptics?> { null }

/** The [GameHaptics] every screen vibrates through: the app's one ([LocalGameHaptics]), or else one of its own. */
@Composable
fun rememberGameHaptics(): GameHaptics = LocalGameHaptics.current ?: rememberScopedGameHaptics()

/** The app's one [GameHaptics], for [LocalGameHaptics]. It follows [vibrationEnabled] from then on, so no screen sets it. */
@Composable
fun rememberAppHaptics(vibrationEnabled: Flow<Boolean>): GameHaptics {
    val haptics = rememberScopedGameHaptics()
    // Still until the setting has been read.
    // Read here, not in the effect: a read inside it wouldn't run it again when the setting changes.
    val enabled = vibrationEnabled.collectAsStateWithLifecycle(initialValue = false).value
    SideEffect { haptics.enabled = enabled }
    return haptics
}

/** A [GameHaptics] scoped to the current composition. */
@Composable
private fun rememberScopedGameHaptics(): GameHaptics {
    val platform = LocalPlatformServices.current
    val haptics = remember { GameHaptics(platform::createHapticsPlayer) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { haptics.cancel() }
    return haptics
}
