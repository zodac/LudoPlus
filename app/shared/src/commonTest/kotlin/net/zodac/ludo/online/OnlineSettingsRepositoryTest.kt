package net.zodac.ludo.online

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.ludo.data.InMemoryPreferences

class OnlineSettingsRepositoryTest {

    @Test
    fun noAccountSyncedAndMirroringAllowedUntilSet() = runTest {
        val repository = OnlineSettingsRepository(InMemoryPreferences())
        assertNull(repository.syncedAccount.first())
        assertFalse(repository.mirroringBlocked.first())
        repository.setSyncedAccount("ann")
        repository.blockMirroring()
        assertEquals("ann", repository.syncedAccount.first())
        assertTrue(repository.mirroringBlocked.first())
    }
}
