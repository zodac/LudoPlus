package net.zodac.ludoplus.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.achievements.AchievementsRepository

/** The saved time played: Statistics' total, added to as the app counts it, and moved from where it was kept before. */
class PlaySecondsSettingTest {

    // The saved names, written by hand: what's on a phone already must keep reading back.
    private val legacyKey = intPreferencesKey("play_seconds")
    private val counterKey = intPreferencesKey("counter_PLAY_SECONDS")

    @Test
    fun `it starts at none - each save adds to it - under the counter's name`() = runTest {
        val store = InMemoryPreferences()
        val repository = AchievementsRepository(store)
        assertEquals(0, repository.playSeconds.first())
        repository.addPlaySeconds(300)
        repository.addPlaySeconds(42)
        assertEquals(342, repository.playSeconds.first())
        assertEquals(342, store.data.first()[counterKey])
    }

    @Test
    fun `the time kept with the settings is read once - then forgotten`() = runTest {
        val store = InMemoryPreferences()
        store.edit { it[legacyKey] = 1_234 }
        val settings = SettingsRepository(store)
        assertEquals(1_234, settings.legacyPlaySeconds())
        settings.forgetRetired()
        assertEquals(0, settings.legacyPlaySeconds())
        assertNull(store.data.first()[legacyKey])
    }

    @Test
    fun `the time played isn't one of the achievements' counters`() = runTest {
        val repository = AchievementsRepository(InMemoryPreferences())
        repository.addPlaySeconds(60)
        assertEquals(emptyMap(), repository.current().counters)
    }
}
