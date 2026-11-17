package net.zodac.ludo.data.achievements

import kotlinx.coroutines.flow.Flow
import net.zodac.ludo.model.Achievement
import net.zodac.ludo.model.AchievementCounter

/**
 * Where achievement unlocks and progress live. [AchievementsRepository] is the real, DataStore backed implementation;
 * this interface lets the path from a game to a stored unlock be tested with no DataStore.
 */
interface AchievementStore {

    val state: Flow<AchievementsState>

    suspend fun current(): AchievementsState

    /**
     * The seconds the app has been in front of the player ([net.zodac.ludo.model.AchievementCounter.PLAY_SECONDS]), on
     * every device as of the last merge: apart from [state], which it would move every few minutes.
     */
    val playSeconds: Flow<Int>

    /** Adds [seconds] to [playSeconds]. */
    suspend fun addPlaySeconds(seconds: Int)

    /** Writes newly unlocked achievements and updated counters in one atomic edit. */
    suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>)

    /**
     * Wipes every unlock and counter, the time played included, as on a new device: for another online account signing
     * in, whose own come back from its cloud save and the service.
     */
    suspend fun resetAll()

    /**
     * Reverts a single achievement to locked, leaving every other unlock and every counter untouched. Debug builds only:
     * see `AchievementsViewModel`'s superuser mode.
     */
    suspend fun forceLock(achievement: Achievement)

    /**
     * Runs [block] - a read, a decision and a write - with no other [locked] block on this store running at once, so
     * two can't both read "not unlocked yet" and both announce it. A store used from one coroutine at a time needs no
     * lock, which is the default.
     */
    suspend fun <T> locked(block: suspend () -> T): T = block()
}
