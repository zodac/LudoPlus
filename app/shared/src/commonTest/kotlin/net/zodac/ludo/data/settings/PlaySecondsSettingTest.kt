package net.zodac.ludo.data.settings

import androidx.datastore.preferences.core.intPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.ludo.data.InMemoryPreferences

/** The saved time played: Statistics' total, added to as the app counts it. */
class PlaySecondsSettingTest {

    // The saved name, written by hand: what's on a phone already must keep reading back.
    private val playSecondsKey = intPreferencesKey("play_seconds")

    @Test
    fun `it starts at none - each save adds to it - under its own name`() = runTest {
        val store = InMemoryPreferences()
        val repository = SettingsRepository(store)
        assertEquals(0, repository.playSeconds.first())
        repository.addPlaySeconds(300)
        repository.addPlaySeconds(42)
        assertEquals(342, repository.playSeconds.first())
        assertEquals(342, store.data.first()[playSecondsKey])
    }
}
