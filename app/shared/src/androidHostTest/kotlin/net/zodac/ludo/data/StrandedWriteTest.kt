package net.zodac.ludo.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * A write asked from a dispatcher that never runs again doesn't hold up the writes after it. Needs a real DataStore file
 * (DataStore's own write queue is what would hang), so it's here rather than in commonTest.
 */
class StrandedWriteTest {

    private val key = intPreferencesKey("count")

    /** Runs what it's given in place until [dead], then drops it: a finished test's main thread. */
    private class DyingDispatcher : CoroutineDispatcher() {
        @Volatile
        var dead = false

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = dead

        override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
    }

    @OptIn(ExperimentalCoroutinesApi::class) // CoroutineStart.UNDISPATCHED
    @Test
    fun `a write asked from a dispatcher that dies doesn't strand the writes after it`() = runBlocking {
        val file = Files.createTempDirectory("stranded").resolve("settings.preferences_pb").toString()
        val store = createPreferencesDataStore(file)
        val main = DyingDispatcher()
        // Asked from "main", which then stops running anything.
        CoroutineScope(main).launch(start = CoroutineStart.UNDISPATCHED) { store.edit { it[key] = 1 } }
        main.dead = true
        withTimeout(10_000) {
            launch(Dispatchers.Default) { store.edit { it[key] = 2 } }.join()
            assertEquals(2, store.data.first()[key])
        }
    }
}
