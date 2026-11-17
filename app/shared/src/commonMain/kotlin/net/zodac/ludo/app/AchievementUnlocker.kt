package net.zodac.ludo.app

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zodac.ludo.data.achievements.AchievementStore
import net.zodac.ludo.data.achievements.AchievementsState
import net.zodac.ludo.game.AchievementEngine
import net.zodac.ludo.game.AchievementUpdate
import net.zodac.ludo.game.GameFeats
import net.zodac.ludo.game.nowEpochMillis
import net.zodac.ludo.model.Achievement

/**
 * The one way an achievement is earned: read [store], decide with [AchievementEngine], record the result, and only
 * then announce it ([announce]), so a banner can never outlive its unlock.
 *
 * Every read-decide-write runs one at a time - this unlocker's own, and inside [AchievementStore.locked] every
 * screen's: two overlapping would each read "not unlocked yet" and both announce the same achievement.
 */
class AchievementUnlocker(
    private val store: AchievementStore,
    private val ownsAllStyles: Flow<Boolean> = flowOf(false),
    private val now: () -> Long = ::nowEpochMillis,
) {

    private val lock = Mutex()

    /** Unlocks whichever of [achievements] aren't yet, now. Returns what was recorded - empty if nothing was new. */
    suspend fun unlock(achievements: Set<Achievement>): AchievementUpdate = record(GameFeats(achievements))

    /** Records and announces what [feats] earn against the stored achievements. Returns what was recorded. */
    suspend fun record(feats: GameFeats): AchievementUpdate =
        if (feats.isEmpty) AchievementUpdate() else apply { state -> AchievementEngine.evaluate(feats, state, now()) }

    /** Records and announces what [decide] makes of the stored achievements. Returns what was recorded. */
    suspend fun apply(decide: (AchievementsState) -> AchievementUpdate): AchievementUpdate = lock.withLock {
        store.locked {
            val update = decide(store.current())
            if (!update.isEmpty) {
                store.record(update.unlockedAt(), update.counters)
                announce(update, ownsAllStyles.first())
            }
            update
        }
    }
}
