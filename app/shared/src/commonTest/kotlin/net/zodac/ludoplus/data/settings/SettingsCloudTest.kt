package net.zodac.ludoplus.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.Stamped
import net.zodac.ludoplus.model.HouseRule

/** The settings' side of a merge: each synced setting stamped as it changes, and the later one kept. */
class SettingsCloudTest {

    private var clock = 1_000L
    private val repository = SettingsRepository(InMemoryPreferences(), now = { clock })

    @Test
    fun `a synced setting is stamped when it changes - a device's own is never in the save`() = runTest {
        repository.setSoundEnabled(false)
        clock = 2_000L
        repository.batch { setHouseRules(setOf(HouseRule.entries.first())) }
        repository.setAnimationLevel(AnimationLevel.entries.first())
        repository.setLockedOnlyAchievements(true)
        assertEquals(
            mapOf(
                "sound_enabled" to Stamped(SettingValue.Flag(false), 1_000L),
                "house_rules" to Stamped(SettingValue.TextSet(setOf(HouseRule.entries.first().id)), 2_000L),
            ),
            repository.cloudSettings(),
        )
        // Set again to the same value, it keeps its stamp.
        clock = 3_000L
        repository.setSoundEnabled(false)
        assertEquals(1_000L, repository.cloudSettings().getValue("sound_enabled").atEpochMillis)
    }

    @Test
    fun `a later setting from elsewhere is taken - an earlier one isn't - nor a key this app doesn't sync`() = runTest {
        repository.setSoundEnabled(false)
        repository.mergeCloud(
            mapOf(
                "sound_enabled" to Stamped(SettingValue.Flag(true), 500L),
                "vibration_enabled" to Stamped(SettingValue.Flag(false), 600L),
                "animation_level" to Stamped(SettingValue.Text("off"), 9_000L),
                "player_count" to Stamped(SettingValue.Text("not a number"), 9_000L),
            ),
        )
        assertEquals(false, repository.soundEnabled.first())
        assertEquals(false, repository.vibrationEnabled.first())
        assertEquals(Stamped(SettingValue.Flag(false), 600L), repository.cloudSettings()["vibration_enabled"])
        assertNull(repository.cloudSettings()["animation_level"])
        assertNull(repository.cloudSettings()["player_count"])
        // A later one is, a removal included.
        repository.batch { setCustomModeChoice("mine") }
        repository.mergeCloud(mapOf("sound_enabled" to Stamped(SettingValue.Flag(true), 5_000L), "custom_mode" to Stamped(null, 5_000L)))
        assertEquals(true, repository.soundEnabled.first())
        assertNull(repository.snapshot().customModeId)
    }
}
