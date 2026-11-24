package net.zodac.ludoplus.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.ludoplus.data.achievements.AchievementsRepository
import net.zodac.ludoplus.game.AchievementUpdate
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCounter
import net.zodac.ludoplus.online.OnlineSyncRequests

/** In-memory preferences whose writes wait for [gate] while it's set - so a write can be caught half-way. */
private class GatedPreferences : DataStore<Preferences> {

    private val _data = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = _data
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        gate?.await()
        return transform(_data.value).also { _data.value = it }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AchievementUnlockerTest {

    @Test
    fun `two screens unlocking the same achievement at once record and announce it once`() = runTest {
        val preferences = GatedPreferences()
        val repository = AchievementsRepository(preferences)
        // The menu's and the game's: each screen has its own unlocker, over the one store.
        val first = AchievementUnlocker(repository, flowOf(false))
        val second = AchievementUnlocker(repository, flowOf(false))

        val gate = CompletableDeferred<Unit>()
        preferences.gate = gate
        val firstUpdate = async { first.unlock(setOf(Achievement.LAP_OF_HONOUR)) }
        val secondUpdate = async { second.unlock(setOf(Achievement.LAP_OF_HONOUR)) }
        // The first is now mid-write; without the store's lock the second would read "not unlocked yet" here too.
        runCurrent()
        gate.complete(Unit)

        val updates = listOf(firstUpdate.await(), secondUpdate.await())
        assertEquals(1, updates.count { !it.isEmpty }, "recorded once, got $updates")
        assertTrue(repository.current().isUnlocked(Achievement.LAP_OF_HONOUR))
    }

    @Test
    fun `a new unlock asks for a sync now - as the service announces it - and counters alone can wait for the game to end`() = runTest {
        val now = mutableListOf<Unit>()
        val outsideGame = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { OnlineSyncRequests.requests.toList(now) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { OnlineSyncRequests.outsideGameRequests.toList(outsideGame) }
        val unlocker = AchievementUnlocker(AchievementsRepository(GatedPreferences()), flowOf(false))

        unlocker.apply { AchievementUpdate(counters = mapOf(AchievementCounter.DIE_ROLLS to 1)) }
        runCurrent()
        assertEquals(0 to 1, now.size to outsideGame.size)

        unlocker.unlock(setOf(Achievement.WHO_MADE_THIS))
        runCurrent()
        assertEquals(1 to 1, now.size to outsideGame.size)
    }

    @Test
    fun `unlocking what is already unlocked records nothing`() = runTest {
        val repository = AchievementsRepository(GatedPreferences())
        val unlocker = AchievementUnlocker(repository, flowOf(false))
        assertTrue(!unlocker.unlock(setOf(Achievement.WHO_MADE_THIS)).isEmpty)
        assertTrue(unlocker.unlock(setOf(Achievement.WHO_MADE_THIS)).isEmpty)
    }
}
