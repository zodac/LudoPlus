package net.zodac.ludoplus.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import kotlin.concurrent.Volatile
import net.zodac.ludoplus.data.settings.AnimationLevel
import net.zodac.ludoplus.data.settings.MotionGroup
import net.zodac.ludoplus.data.settings.MotionSettings

/**
 * Whether to show as little motion as possible: the system asks apps for less (Android's "Remove animations" -
 * [net.zodac.ludoplus.platform.PlatformServices.reduceMotion]) or the player has set the app's Menus & pages motion to
 * Off (the "Animations" level Off, or the Advanced settings). Provided once at the root, and again round the die and
 * the pawns for their own ([MotionGroup.DIE_ROLL], [MotionGroup.PAWN_MOVE]). When it's true, decoration stops moving
 * (looping glows, fireworks, page fades), the die lands straight on its result, and a pawn jumps to its square. What the
 * game does doesn't change.
 */
val LocalReduceMotion = compositionLocalOf { false }

/**
 * How the app moves ([MotionSettings]): the player's "Animations" level or Advanced settings, as
 * [AnimationLevel.OFF]'s while the system asks for reduced motion. Provided once at the root, beside
 * [LocalReduceMotion]. Read it through [ambientMotion] and [scorePulse], which also honour a [LocalHoldStill].
 */
val LocalMotionSettings = compositionLocalOf { AnimationLevel.default.motion }

/**
 * Whether everything here is held in its still pose whatever the settings - a Styles tile that isn't on screen is.
 * Provided beside a [LocalReduceMotion] of its own.
 */
val LocalHoldStill = compositionLocalOf { false }

/**
 * Whether reduced motion is asked for here other than by the Menus & pages group's own Off, which leaves the other
 * groups to their own settings: [LocalHoldStill], or a [LocalReduceMotion] while Menus & pages moves (provided further
 * down).
 */
private val stilledHere: Boolean
    @Composable @ReadOnlyComposable
    get() = LocalHoldStill.current || (LocalReduceMotion.current && LocalMotionSettings.current.menusMotion)

/**
 * The [LocalMotionSettings] the app last drew with, kept for the process: the crash page, drawn in a process of its
 * own where the settings aren't read, is handed it.
 */
object LastMotionSettings {
    @Volatile
    var value: MotionSettings = AnimationLevel.default.motion
}

/**
 * Whether [group]'s decoration that moves on its own while nothing is happening may move - one of
 * [MotionGroup.decoration]: drifting and floating dice, twinkles, sparkles, the Neon, RGB and Glitch art, the googly
 * dice's pupils. Only while its group moves, and nothing here asks for less (a [LocalHoldStill], or a
 * [LocalReduceMotion] but for the Menus & pages group's own Off). Otherwise each holds the still pose it has under
 * [LocalReduceMotion]. Run its frames at its [FramePace] (`LocalFramePaces.current.of(group)`).
 */
@Composable
@ReadOnlyComposable
fun ambientMotion(group: MotionGroup): Boolean = LocalMotionSettings.current.moves(group) && !stilledHere

/** Whether [group] shows as little motion as possible: it's off, or it's [stilledHere]. */
@Composable
@ReadOnlyComposable
fun groupReduceMotion(group: MotionGroup): Boolean = !LocalMotionSettings.current.moves(group) || stilledHere

/**
 * [content] as [group] moves it, not as Menus & pages does: under its own reduced motion ([groupReduceMotion]) as
 * [LocalReduceMotion], at its [FramePace]. Round the die ([MotionGroup.DIE_ROLL]), the pawns, the banners, and the
 * fireworks.
 */
@Composable
fun GroupMotion(group: MotionGroup, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalReduceMotion provides groupReduceMotion(group),
        LocalFramePace provides LocalFramePaces.current.of(group),
        content = content,
    )
}
