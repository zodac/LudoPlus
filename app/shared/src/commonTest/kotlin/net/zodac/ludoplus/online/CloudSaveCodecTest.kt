package net.zodac.ludoplus.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.data.Stamped
import net.zodac.ludoplus.data.achievements.CloudAchievements
import net.zodac.ludoplus.data.settings.SettingValue
import okio.Buffer
import okio.GzipSink
import okio.buffer
import okio.use

class CloudSaveCodecTest {

    private val save = CloudSave(
        achievements = CloudAchievements(
            unlockedAt = mapOf("the_journey_begins" to 1_790_000_000_000L),
            deviceCounters = mapOf("phone" to mapOf("GAMES_PLAYED" to 12), "tablet" to mapOf("PLAY_SECONDS" to 60)),
            latestCounters = mapOf("WIN_STREAK" to Stamped(3, 1_790_000_000_500L)),
        ),
        records = listOf(
            cloudRecord("aa", at = 1_790_000_000_000L, name = "Sam, the ; quick"),
            cloudRecord("bb", at = 1_790_000_000_000L, name = "Alex", won = false).copy(houseRules = true, playedUnmodified = false, isPrimaryPlayer = false),
            cloudRecord("cc", at = 1_790_000_100_000L, name = "Sam, the ; quick", turns = 61).copy(gameModeId = "two_dice", playerCount = 8),
            cloudRecord("dd", at = 1_790_000_200_000L, name = "Alex").copy(gameModeId = "survival", squaresMoved = 0),
        ),
        settings = mapOf(
            "dice_style_id" to Stamped(SettingValue.Text("ocean"), 7L),
            "player_count" to Stamped(SettingValue.Number(6), 8L),
            "sound_enabled" to Stamped(SettingValue.Flag(false), 9L),
            "house_rules" to Stamped(SettingValue.TextSet(setOf("b", "a")), 10L),
            "custom_mode" to Stamped(null, 11L),
        ),
        inProgressGame = Stamped("{\"players\":[]}", 11L),
    )

    @Test
    fun `a save round-trips whole - every record's every field among it`() {
        assertEquals(save, CloudSaveCodec.decode(CloudSaveCodec.encode(save)))
        assertEquals(CloudSave(), CloudSaveCodec.decode(CloudSaveCodec.encode(CloudSave())))
    }

    private fun gzip(json: String): ByteArray = Buffer().also { buffer -> GzipSink(buffer).buffer().use { it.writeUtf8(json) } }.readByteArray()

    @Test
    fun `a save that isn't one is corrupt - a newer one it can't read is kept as that version`() {
        assertEquals(DecodedSave.Corrupt, CloudSaveCodec.read("not gzip".encodeToByteArray()))
        assertEquals(DecodedSave.Corrupt, CloudSaveCodec.read(gzip("{}")))
        assertEquals(DecodedSave.Corrupt, CloudSaveCodec.read(gzip("""{"version":1,"records":{"names":[],"modes":[],"table":"x"}}""")))
        assertEquals(DecodedSave.Readable(CloudSave(version = 2)), CloudSaveCodec.read(gzip("""{"version":2,"records":{"table":"x"}}""")))
        assertTrue(CloudSave(version = 2).writableHere.not())
    }

    @Test
    fun `a record written before a field was added reads it as 0 - the squares moved as unknown`() {
        val json = """{"version":1,"records":{"names":["Sam"],"modes":["classic"],"table":"${"a".repeat(32)},0,0,5,11"}}"""
        val row = CloudSaveCodec.decode(gzip(json))!!.records.single()
        assertEquals(listOf("Sam", "sam", "classic", 5L, true, true, false, true, 0, 0), listOf(
            row.playerName, row.playerKey, row.gameModeId, row.endedAtEpochMillis, row.isPrimaryPlayer, row.playedUnmodified, row.houseRules,
            row.won, row.playerCount, row.turns,
        ))
        assertEquals(null, row.squaresMoved)
    }
}
