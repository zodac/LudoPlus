package net.zodac.ludo.app

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.zodac.ludo.game.AchievementUpdate
import net.zodac.ludo.model.Achievement

/** Something worth putting on screen for a moment - see `ui/achievements/AchievementBannerHost`. */
sealed interface AchievementEvent {

    /** Earned just now. */
    data class Unlocked(val achievement: Achievement) : AchievementEvent

    /** Moved closer to being earned, from [previous] to [current] out of the target, so the banner can climb between them. */
    data class Progressed(val achievement: Achievement, val previous: Int, val current: Int) : AchievementEvent
}

/**
 * Where banners are announced, and where the banner host listens.
 *
 * A process-wide object rather than state on a ViewModel, because the two ends have no scope in common: achievements
 * are raised by the play graph's `GameViewModel` (and Settings), while the host sits above the whole `NavHost` in
 * `LudoPlusApp`, so a burst keeps showing as the player moves from the board to Game Over and on to the menu.
 *
 * The buffer holds a whole burst, and drops the *oldest* if it somehow overflows, since the newest banner is the one the
 * player is waiting to see. Emission never suspends, so a slow collector can't stall the game.
 */
object AchievementEvents {

    private const val BUFFER_CAPACITY = 64

    private val _events = MutableSharedFlow<AchievementEvent>(
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<AchievementEvent> = _events.asSharedFlow()

    fun emit(event: AchievementEvent) {
        _events.tryEmit(event)
    }
}

/**
 * Puts everything a recorded [update] earned up as banners: each unlock, then each progress nudge. Call it only once
 * [update] is stored, so a banner can never outlive what it announces.
 */
fun announce(update: AchievementUpdate) {
    update.newlyUnlocked.forEach { AchievementEvents.emit(AchievementEvent.Unlocked(it)) }
    update.progressed.forEach { AchievementEvents.emit(AchievementEvent.Progressed(it.achievement, it.previous, it.current)) }
}
