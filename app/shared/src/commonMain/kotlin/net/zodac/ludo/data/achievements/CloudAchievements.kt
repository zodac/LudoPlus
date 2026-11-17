package net.zodac.ludo.data.achievements

import net.zodac.ludo.data.Stamped

/**
 * The achievements part of the cloud save, by [net.zodac.ludo.model.Achievement.id] and
 * [net.zodac.ludo.model.AchievementCounter.id]: ids, so one this build doesn't know (a newer app's) is carried
 * through a merge rather than lost. Each counter is in the map its [net.zodac.ludo.model.CounterMerge] says.
 */
data class CloudAchievements(
    /** When each achievement was first unlocked, on any device. */
    val unlockedAt: Map<String, Long> = emptyMap(),
    /** [net.zodac.ludo.model.CounterMerge.SUM] counters: each share (a device, from its last reset on), by share id, then counter. */
    val deviceCounters: Map<String, Map<String, Int>> = emptyMap(),
    /** [net.zodac.ludo.model.CounterMerge.LATEST] counters, each as of the last time a device wrote it. */
    val latestCounters: Map<String, Stamped<Int>> = emptyMap(),
)
