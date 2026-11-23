package net.zodac.ludoplus.app

import net.zodac.ludoplus.game.style.styleRewards
import net.zodac.ludoplus.game.style.stylesUnlockedByCount
import org.jetbrains.compose.resources.StringResource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.zodac.ludoplus.game.AchievementUpdate
import net.zodac.ludoplus.model.Achievement

/** Something worth putting on screen for a moment - see `ui/achievements/AchievementBannerHost`. */
sealed interface AchievementEvent {

    /** Earned just now. */
    data class Unlocked(val achievement: Achievement) : AchievementEvent

    /** Moved closer to being earned, from [previous] to [current] out of the target, so the banner can climb between them. */
    data class Progressed(val achievement: Achievement, val previous: Int, val current: Int) : AchievementEvent

    /**
     * [earnedBy] unlocked [styles], which share one banner: every style one update's count unlocked, or every style
     * (or hidden colour) behind one particular achievement.
     */
    data class StylesUnlocked(val styles: List<UnlockedStyle>, val earnedBy: StylesEarnedBy) : AchievementEvent {
        init {
            require(styles.isNotEmpty()) { "A styles banner needs a style" }
        }
    }
}

/** What unlocked the styles on a [AchievementEvent.StylesUnlocked] banner. */
sealed interface StylesEarnedBy {

    /** Earning [achievementCount] achievements met their count lock. */
    data class Count(val achievementCount: Int) : StylesEarnedBy

    /** Earning [achievement], which they're locked behind. */
    data class OneAchievement(val achievement: Achievement) : StylesEarnedBy
}

/**
 * A style a [AchievementEvent.StylesUnlocked] names: its [name] and what one of its category is called - "'Frosted'
 * dice". With [hiddenColours], it's that many hidden colours of the style that were unlocked, not the style itself.
 */
data class UnlockedStyle(val name: StringResource, val categoryNoun: StringResource, val hiddenColours: Int = 0)

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
 * Puts everything a recorded [update] earned up as banners: each unlock, followed by a styles banner for whatever
 * styles are locked behind that achievement ([styleRewards]); then one banner for any styles its unlocks brought
 * within reach by count (see [stylesUnlockedByCount]) - however many, and from however many categories - then each
 * progress nudge. A player who [ownsAllStyles] gets no styles banners: every style is already theirs. Call it only once
 * [update] is stored, so a banner can never outlive what it announces.
 */
fun announce(update: AchievementUpdate, ownsAllStyles: Boolean) {
    update.newlyUnlocked.forEach { achievement ->
        AchievementEvents.emit(AchievementEvent.Unlocked(achievement))
        val rewards = achievement.styleRewards
        if (rewards.isNotEmpty() && !ownsAllStyles) {
            AchievementEvents.emit(
                AchievementEvent.StylesUnlocked(
                    rewards.map { UnlockedStyle(it.styleName, it.categoryNoun, it.hiddenColours) },
                    StylesEarnedBy.OneAchievement(achievement),
                ),
            )
        }
    }
    val styles = if (ownsAllStyles) emptyList() else stylesUnlockedByCount(update.unlockedCountBefore, update.unlockedCountAfter)
    if (styles.isNotEmpty()) {
        AchievementEvents.emit(
            AchievementEvent.StylesUnlocked(
                styles.map { UnlockedStyle(it.styleName, it.categoryNoun) },
                StylesEarnedBy.Count(update.unlockedCountAfter),
            ),
        )
    }
    update.progressed.forEach { AchievementEvents.emit(AchievementEvent.Progressed(it.achievement, it.previous, it.current)) }
}
