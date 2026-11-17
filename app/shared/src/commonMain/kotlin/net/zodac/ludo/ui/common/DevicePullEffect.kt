package net.zodac.ludo.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.compose.LifecycleResumeEffect
import net.zodac.ludo.platform.LocalPlatformServices

// Standard gravity, for turning the accelerometer's m/s² into g; and how much of each new reading the
// pull takes on, smoothing out the sensor's own jitter without dulling a real shake.
private const val STANDARD_GRAVITY = 9.81f
// The pupils' sensor is read 20 times a second, not the default 50 - they're smoothed anyway, and every reading
// wakes the app. The smoothing is stronger per reading to match, so the pupils feel just as quick:
// 1 - 0.5^(50 / 20) is what 0.5 a reading at 50 a second comes to at 20.
private const val PULL_SAMPLES_PER_SECOND = 20
private const val PULL_SMOOTHING = 0.82f

/**
 * Reports the pull the device puts on anything loose on screen, in g (x right, y down): gravity down
 * whichever way the phone is tipped, and against however it's being moved - the reverse of what the
 * [Accelerometer][net.zodac.ludo.platform.Accelerometer] reads. Listening only while this screen
 * is resumed, as ShakeDetectorEffect does, so a backgrounded app's sensor isn't left running.
 */
@Composable
internal fun DevicePullEffect(onPull: (Offset) -> Unit) {
    val platform = LocalPlatformServices.current
    val currentOnPull = rememberUpdatedState(onPull)
    val accelerometer = remember { platform.createAccelerometer() } ?: return

    // Not on an idle screen either: the pupils are frozen with everything else.
    if (LocalScreenIdle.current?.idle == true) return
    LifecycleResumeEffect(accelerometer) {
        var pull: Offset? = null
        accelerometer.start(PULL_SAMPLES_PER_SECOND) { x, y, _ ->
            // Up the screen is -y on it, so the reading's y keeps its sign and x flips.
            val reading = Offset(-x, y) / STANDARD_GRAVITY
            val smoothed = pull?.let { it + (reading - it) * PULL_SMOOTHING } ?: reading
            pull = smoothed
            currentOnPull.value(smoothed)
        }
        onPauseOrDispose { accelerometer.stop() }
    }
}
