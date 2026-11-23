package net.zodac.ludoplus.data.achievements

import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCounter

/**
 * Everything the app knows about this device's achievements: when each one was unlocked, and the running totals the
 * incremental ones are measured against.
 */
data class AchievementsState(
    val unlockedAt: Map<Achievement, Long> = emptyMap(),
    val counters: Map<AchievementCounter, Int> = emptyMap(),
) {

    fun isUnlocked(achievement: Achievement): Boolean = achievement in unlockedAt

    /**
     * How many achievements have been earned, Easter Eggs included: the number a style's achievement-count lock is
     * measured against.
     */
    val unlockedCount: Int
        get() = unlockedAt.size

    fun counter(counter: AchievementCounter): Int = counters[counter] ?: 0

    /** How far along [achievement] is, capped at its target (a counter can run past it). */
    fun progress(achievement: Achievement): Int = achievement.counter?.let { counter(it).coerceAtMost(achievement.target) } ?: 0
}
