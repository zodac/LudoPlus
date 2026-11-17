package net.zodac.ludo.data.achievements

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.zodac.ludo.data.JsonObject
import net.zodac.ludo.data.Stamped
import net.zodac.ludo.data.ints
import net.zodac.ludo.data.jsonInts
import net.zodac.ludo.data.parseJson
import net.zodac.ludo.data.toJson
import net.zodac.ludo.game.nowEpochMillis
import net.zodac.ludo.model.Achievement
import net.zodac.ludo.model.AchievementCounter
import net.zodac.ludo.model.CounterMerge
import net.zodac.ludo.online.CloudSaveMerge

/**
 * Unlock timestamps and progress counters, on their own DataStore file ([net.zodac.ludo.data.PreferencesFile.ACHIEVEMENTS]),
 * apart from the settings.
 *
 * Keys are built from [Achievement.id] and [AchievementCounter.id], so storage survives the enums being reordered. A key
 * that no longer matches an entry (one removed in a later version) is ignored on read.
 *
 * For the cloud save and progress files, a [CounterMerge.SUM] counter's stored value is the total across shares: the
 * other shares, as of the last merge, are kept beside it ([SHARES_KEY]), and this file's own is the rest. The own share's
 * id ([SHARE_ID_KEY]) is in this file too, made on first need. A [CounterMerge.LATEST] counter is stamped with when it
 * was last written ([latestAtKey]).
 */
class AchievementsRepository(
    private val dataStore: DataStore<Preferences>,
    private val now: () -> Long = ::nowEpochMillis,
) : AchievementStore {

    /** Emits only when an unlock or a counter changes, not on every write to the file. */
    override val state: Flow<AchievementsState> = dataStore.data.map { prefs ->
        AchievementsState(
            unlockedAt = UNLOCKED_KEYS.mapNotNull { (achievement, key) -> prefs[key]?.let { achievement to it } }.toMap(),
            counters = COUNTER_KEYS.filter { (counter, _) -> counter != AchievementCounter.PLAY_SECONDS }
                .mapNotNull { (counter, key) -> prefs[key]?.let { counter to it } }.toMap(),
        )
    }.distinctUntilChanged()

    override val playSeconds: Flow<Int> = dataStore.data.map { it[counterKey(AchievementCounter.PLAY_SECONDS)] ?: 0 }.distinctUntilChanged()

    /** In an edit of its own, so it can't lose or undo a game's write of the other counters. */
    override suspend fun addPlaySeconds(seconds: Int) {
        val key = counterKey(AchievementCounter.PLAY_SECONDS)
        dataStore.edit { it[key] = ((it[key] ?: 0).toLong() + seconds).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
    }

    override suspend fun current(): AchievementsState = state.first()

    /** Writes newly unlocked achievements and updated counters in one edit, so a burst can't be half-saved. */
    override suspend fun record(unlockedAt: Map<Achievement, Long>, counters: Map<AchievementCounter, Int>) {
        if (unlockedAt.isEmpty() && counters.isEmpty()) return
        dataStore.edit { prefs ->
            for ((achievement, timestamp) in unlockedAt) {
                // Never overwrite an unlock time: it's the first time it happened.
                if (prefs[unlockedKey(achievement)] == null) prefs[unlockedKey(achievement)] = timestamp
            }
            for ((counter, value) in counters) {
                prefs[counterKey(counter)] = value
                if (counter.cloudMerge == CounterMerge.LATEST) prefs[latestAtKey(counter)] = now()
            }
        }
    }

    /** This device's achievements as the cloud save holds them, with its own counter shares under its share id. */
    suspend fun cloudAchievements(): CloudAchievements {
        dataStore.data.first().let { prefs -> prefs[SHARE_ID_KEY]?.let { return cloudAchievements(prefs, it) } }
        var saved = CloudAchievements()
        dataStore.edit { prefs -> saved = cloudAchievements(prefs, shareId(prefs)) }
        return saved
    }

    /** This file's own share id, made on first need. */
    private fun shareId(prefs: MutablePreferences): String =
        prefs[SHARE_ID_KEY] ?: Uuid.random().toHexString().also { prefs[SHARE_ID_KEY] = it }

    private fun cloudAchievements(prefs: Preferences, shareId: String): CloudAchievements {
        val others = readShares(prefs)
        val own = COUNTER_KEYS.filter { (counter, _) -> counter.cloudMerge == CounterMerge.SUM }.mapNotNull { (counter, key) ->
            val total = prefs[key] ?: return@mapNotNull null
            counter.id to (total - others.values.sumOf { it[counter.id] ?: 0 }).coerceAtLeast(0)
        }.toMap()
        return CloudAchievements(
            unlockedAt = UNLOCKED_KEYS.mapNotNull { (achievement, key) -> prefs[key]?.let { achievement.id to it } }.toMap(),
            deviceCounters = others + (shareId to own),
            latestCounters = COUNTER_KEYS.filter { it.first.cloudMerge == CounterMerge.LATEST }
                .mapNotNull { (counter, key) -> prefs[key]?.let { counter.id to Stamped(it, prefs[latestAtKey(counter)] ?: 0) } }.toMap(),
        )
    }

    /**
     * Merges the cloud save's [remote] achievements into this device's, in one edit ([CloudSaveMerge]): unlocks it
     * lacks, earlier unlock times, the other devices' newer shares, and a newer streak. Silent: nothing is announced.
     * Run inside [locked], so it can't land between a game's read of the counters and its write.
     */
    suspend fun mergeCloud(remote: CloudAchievements) {
        dataStore.edit { prefs ->
            val ownId = shareId(prefs)
            val merged = CloudSaveMerge.merge(cloudAchievements(prefs, ownId), remote)
            for ((achievement, key) in UNLOCKED_KEYS) merged.unlockedAt[achievement.id]?.let { prefs[key] = it }
            val others = merged.deviceCounters - ownId
            for ((counter, key) in COUNTER_KEYS) {
                val value = when (counter.cloudMerge) {
                    CounterMerge.SUM -> merged.deviceCounters.values.sumOf { it[counter.id] ?: 0 }.takeIf { it > 0 || prefs[key] != null }
                    CounterMerge.LATEST -> merged.latestCounters[counter.id]?.also { prefs[latestAtKey(counter)] = it.atEpochMillis }?.value
                }
                value?.let { prefs[key] = it }
            }
            prefs[SHARES_KEY] = JsonObject(others.mapValues { (_, counters) -> jsonInts(counters) }).toJson()
        }
    }

    /** The other devices' counter shares, as of the last merge; none if the stored copy can't be read. */
    private fun readShares(prefs: Preferences): Map<String, Map<String, Int>> = runCatching {
        (parseJson(prefs[SHARES_KEY] ?: return emptyMap()) as JsonObject).fields.mapValues { (_, counters) -> (counters as JsonObject).ints() }
    }.getOrDefault(emptyMap())

    /** Every unlock, counter and share, and this file's share id, so the next save starts a new share. */
    override suspend fun resetAll() {
        dataStore.edit { it.clear() }
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
        fun latestAtKey(counter: AchievementCounter) = longPreferencesKey("counter_at_${counter.id}")

        /** The other shares of each [CounterMerge.SUM] counter, as JSON: `{"<share id>": {"<counter id>": n}}`. */
        val SHARES_KEY = stringPreferencesKey("counter_shares")

        /** This file's own share's id in the cloud save: random, made once. */
        val SHARE_ID_KEY = stringPreferencesKey("counter_share_id")
    }
}
