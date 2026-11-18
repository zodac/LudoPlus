package net.zodac.ludoplus.online

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludoplus.data.Stamped
import net.zodac.ludoplus.data.achievements.CloudAchievements
import net.zodac.ludoplus.data.settings.SettingValue

class CloudSaveMergeTest {

    private val phone = CloudSave(
        achievements = CloudAchievements(
            unlockedAt = mapOf("a" to 5L, "b" to 9L),
            deviceCounters = mapOf("phone" to mapOf("GAMES_PLAYED" to 4), "tablet" to mapOf("GAMES_PLAYED" to 1)),
            latestCounters = mapOf("WIN_STREAK" to Stamped(2, 100L)),
        ),
        records = listOf(cloudRecord("aa", at = 10L), cloudRecord("bb", at = 30L)),
        settings = mapOf("sound_enabled" to Stamped(SettingValue.Flag(false), 50L), "player_count" to Stamped(SettingValue.Number(3), 10L)),
        inProgressGame = Stamped("phone's", 40L),
    )

    private val tablet = CloudSave(
        achievements = CloudAchievements(
            unlockedAt = mapOf("a" to 3L, "c" to 1L),
            deviceCounters = mapOf("tablet" to mapOf("GAMES_PLAYED" to 2)),
            latestCounters = mapOf("WIN_STREAK" to Stamped(0, 200L)),
        ),
        records = listOf(cloudRecord("bb", at = 30L), cloudRecord("cc", at = 20L)),
        settings = mapOf("sound_enabled" to Stamped(SettingValue.Flag(true), 40L), "player_count" to Stamped(SettingValue.Number(6), 20L)),
        inProgressGame = Stamped(null, 60L),
    )

    @Test
    fun `nothing is thrown away - unlocks at their earliest - shares at their largest - the later streak - every record once`() {
        val merged = CloudSaveMerge.merge(phone, tablet)
        assertEquals(mapOf("a" to 3L, "b" to 9L, "c" to 1L), merged.achievements.unlockedAt)
        assertEquals(mapOf("phone" to mapOf("GAMES_PLAYED" to 4), "tablet" to mapOf("GAMES_PLAYED" to 2)), merged.achievements.deviceCounters)
        assertEquals(mapOf("WIN_STREAK" to Stamped(0, 200L)), merged.achievements.latestCounters)
        assertEquals(listOf("aa", "cc", "bb"), merged.records.map { it.uuidLabel })
        assertEquals(Stamped(SettingValue.Flag(false), 50L), merged.settings["sound_enabled"])
        assertEquals(Stamped(SettingValue.Number(6), 20L), merged.settings["player_count"])
        // The game was finished on the tablet after the phone's last save.
        assertEquals(Stamped(null, 60L), merged.inProgressGame)
    }

    @Test
    fun `either order gives the same - and merging one in twice changes nothing`() {
        val merged = CloudSaveMerge.merge(phone, tablet)
        assertEquals(merged, CloudSaveMerge.merge(tablet, phone))
        assertEquals(merged, CloudSaveMerge.merge(merged, tablet))
        assertEquals(merged, CloudSaveMerge.merge(merged, phone))
    }
}
