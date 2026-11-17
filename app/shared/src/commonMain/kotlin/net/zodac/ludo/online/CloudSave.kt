package net.zodac.ludo.online

import net.zodac.ludo.data.Stamped
import net.zodac.ludo.data.achievements.CloudAchievements
import net.zodac.ludo.data.game.furtherOn
import net.zodac.ludo.data.newerOf
import net.zodac.ludo.data.records.GameResult
import net.zodac.ludo.data.settings.SettingValue

/**
 * Everything one player's progress holds, as a progress file carries it (and, from Play Games' cloud save, the cloud):
 * the whole of a device's synced progress, or several devices' merged ([CloudSaveMerge]). The Graphics page's settings
 * and the two list filters are each device's own and never in it. Stored as [CloudSaveCodec] writes it.
 */
data class CloudSave(
    /** The format it was written in: an app reads a newer one's fields that it knows, but never writes over it. */
    val version: Int = CloudSaveCodec.VERSION,
    val achievements: CloudAchievements = CloudAchievements(),
    /** Every game record, oldest first. Each row's [GameResult.id] is the device's own, and means nothing here. */
    val records: List<GameResult> = emptyList(),
    /** The synced settings ([net.zodac.ludo.data.settings.SettingsRepository.cloudSettings]), by preference key name. */
    val settings: Map<String, Stamped<SettingValue?>> = emptyMap(),
    /** The game in progress (`SavedGameJson`), or null once it finished or was abandoned. */
    val inProgressGame: Stamped<String?> = Stamped(null, 0),
) {
    /** Whether this app may write over it: not over a newer app's format, whose fields it would drop. */
    val writableHere: Boolean get() = version <= CloudSaveCodec.VERSION
}

/**
 * Combines two [CloudSave]s (two devices', or a device's and a file's) without asking which to keep: the data are facts,
 * so nothing needs throwing away. Either order gives the same result, and merging one in twice changes nothing.
 *
 * - Achievement unlocks are a union, each at its earliest time.
 * - A [net.zodac.ludo.model.CounterMerge.SUM] counter keeps each device's share and adds them up; a share only grows,
 *   so the larger of two copies is the newer. A streak ([net.zodac.ludo.model.CounterMerge.LATEST]) is the one written
 *   last.
 * - Records are a union by [GameResult.uuid].
 * - Each setting, and the game in progress, is whichever was set last; an unstamped one beats one never set. Of two
 *   copies of the same game, though, the one further on wins ([furtherOn]).
 */
object CloudSaveMerge {

    fun merge(a: CloudSave, b: CloudSave): CloudSave = CloudSave(
        // The newer format's: a merge with a newer app's save is still one this app mustn't write.
        version = maxOf(a.version, b.version),
        achievements = merge(a.achievements, b.achievements),
        records = (a.records + b.records).distinctBy { it.uuid }.sortedWith(compareBy({ it.endedAtEpochMillis }, { it.uuid })),
        settings = unionBy(a.settings, b.settings) { x, y -> newerOf(x, y) { it.toString() } },
        inProgressGame = when {
            furtherOn(a.inProgressGame, b.inProgressGame) -> a.inProgressGame
            furtherOn(b.inProgressGame, a.inProgressGame) -> b.inProgressGame
            else -> newerOf(a.inProgressGame, b.inProgressGame) { it.orEmpty() }
        },
    )

    fun merge(a: CloudAchievements, b: CloudAchievements): CloudAchievements = CloudAchievements(
        unlockedAt = unionBy(a.unlockedAt, b.unlockedAt, ::minOf),
        deviceCounters = unionBy(a.deviceCounters, b.deviceCounters) { x, y -> unionBy(x, y, ::maxOf) },
        latestCounters = unionBy(a.latestCounters, b.latestCounters) { x, y -> newerOf(x, y) { it.toString().padStart(11, '0') } },
    )

    /** Every key of [a] and [b], with [combine] of the two values where both have one. */
    private fun <K, V> unionBy(a: Map<K, V>, b: Map<K, V>, combine: (V, V) -> V): Map<K, V> {
        val result = LinkedHashMap(a)
        for ((key, value) in b) result[key] = result[key]?.let { combine(it, value) } ?: value
        return result
    }
}
