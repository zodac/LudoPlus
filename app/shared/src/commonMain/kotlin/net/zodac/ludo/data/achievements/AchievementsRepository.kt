package net.zodac.ludo.data.achievements

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zodac.ludo.model.Achievement
import net.zodac.ludo.model.AchievementCounter

/**
 * Unlock timestamps and progress counters, on their own DataStore file ([net.zodac.ludo.data.PreferencesFile.ACHIEVEMENTS]),
 * apart from the settings.
 *
 * Keys are built from [Achievement.id] and [AchievementCounter.id], so storage survives the enums being reordered. A key
 * that no longer matches an entry (one removed in a later version) is ignored on read.
 */
class AchievementsRepository(private val dataStore: DataStore<Preferences>) : AchievementStore {

    /** Emits only when an unlock or a counter changes, not on every write to the file. */
    override val state: Flow<AchievementsState> = dataStore.data.map { prefs ->
        AchievementsState(
            unlockedAt = UNLOCKED_KEYS.mapNotNull { (achievement, key) -> prefs[key]?.let { achievement to it } }.toMap(),
            counters = COUNTER_KEYS.mapNotNull { (counter, key) -> prefs[key]?.let { counter to it } }.toMap(),
        )
    }.distinctUntilChanged()

    override suspend fun current(): AchievementsState = state.first()

    /** Writes newly unlocked achievements and updated counters in one edit, so a burst can't be half-saved. */
    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) {
        if (unlockedAt.isEmpty() && counters.isEmpty()) return
        dataStore.edit { prefs ->
            for ((achievement, timestamp) in unlockedAt) {
                // Never overwrite an unlock time: it's the first time it happened.
                if (prefs[unlockedKey(achievement)] == null) prefs[unlockedKey(achievement)] = timestamp
            }
            for ((counter, value) in counters) prefs[counterKey(counter)] = value
        }
    }

    override suspend fun forceLock(achievement: Achievement) {
        dataStore.edit { prefs -> prefs.remove(unlockedKey(achievement)) }
    }

    /** One per repository, and so per process: every screen that earns an achievement goes through it. */
    private val lock = Mutex()

    override suspend fun <T> locked(block: suspend () -> T): T = lock.withLock { block() }

    private companion object {
        /** Every achievement's and counter's key, built once rather than on every read of [state]. */
        val UNLOCKED_KEYS = Achievement.entries.map { it to longPreferencesKey("unlocked_${it.id}") }
        val COUNTER_KEYS = AchievementCounter.entries.map { it to intPreferencesKey("counter_${it.id}") }

        fun unlockedKey(achievement: Achievement) = UNLOCKED_KEYS[achievement.ordinal].second
        fun counterKey(counter: AchievementCounter) = COUNTER_KEYS[counter.ordinal].second
    }
}
