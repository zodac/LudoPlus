package net.zodac.ludoplus.app

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zodac.ludoplus.data.achievements.AchievementStore
import net.zodac.ludoplus.data.achievements.AchievementsState
import net.zodac.ludoplus.game.AchievementEngine
import net.zodac.ludoplus.game.AchievementUpdate
import net.zodac.ludoplus.game.GameFeats
import net.zodac.ludoplus.game.nowEpochMillis
import net.zodac.ludoplus.online.OnlineSyncRequests
import net.zodac.ludoplus.model.Achievement

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
        store.locked { decideAndStore(decide).also { announceStored(it) } }
    }

    /**
     * Records what [feats] earn, as [record] does, but leaves the announcing to the caller ([announceStored]): for one that
     * has something to show first. Returns what was recorded.
     */
    suspend fun recordQuietly(feats: GameFeats): AchievementUpdate = if (feats.isEmpty) AchievementUpdate() else lock.withLock {
        store.locked { decideAndStore { state -> AchievementEngine.evaluate(feats, state, now()) } }
    }

    /** Announces a recorded [update] ([announce]), here and to the online service. Nothing, for an empty one. */
    suspend fun announceStored(update: AchievementUpdate) {
        if (update.isEmpty) return
        announce(update, ownsAllStyles.first())
        // A new unlock reaches the online service at once, as it announces it; counters can wait for the game.
        if (update.newlyUnlocked.isEmpty()) OnlineSyncRequests.requestOutsideGame() else OnlineSyncRequests.request()
    }

    /** Records what [decide] makes of the stored achievements. Only under both locks. */
    private suspend fun decideAndStore(decide: (AchievementsState) -> AchievementUpdate): AchievementUpdate {
        val update = decide(store.current())
        if (!update.isEmpty) store.record(update.unlockedAt(), update.counters)
        return update
    }
}
