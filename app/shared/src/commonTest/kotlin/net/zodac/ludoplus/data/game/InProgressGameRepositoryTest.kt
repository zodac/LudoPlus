package net.zodac.ludoplus.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.Stamped
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.ui.game.StartedGame

class InProgressGameRepositoryTest {

    private val game = StartedGame(
        mode = GameMode.CLASSIC,
        players = List(4) { PlayerConfig(it + 1, PlayerType.HUMAN, "Player ${it + 1}") },
        state = GameState.new(GameMode.CLASSIC, 4),
        first = 0,
    )

    @Test
    fun `load waits for the last save asked for - and a clear after it`() = runTest {
        val repository = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        assertNull(repository.load())
        assertFalse(repository.hasInProgressGame.first())
        val later = game.copy(state = LudoEngine.applyRoll(game.state, 3))
        repository.save(game)
        repository.save(later)
        assertEquals(later, repository.load())
        assertTrue(repository.hasInProgressGame.first())
        repository.clear()
        assertNull(repository.load())
        assertFalse(repository.hasInProgressGame.first())
    }

    @Test
    fun `a save that can't be read is removed - Continue isn't offered for it again`() = runTest {
        val preferences = InMemoryPreferences()
        preferences.edit { it[stringPreferencesKey("game")] = "{\"version\":99}" }
        val repository = InProgressGameRepository(preferences, backgroundScope)
        assertTrue(repository.hasInProgressGame.first())
        assertNull(repository.load())
        assertFalse(repository.hasInProgressGame.first())
    }

    @Test
    fun `a removal the crash handler asked for comes before the menu sees the game - and no copy of it comes back from a merge`() = runTest {
        val saved = game.copy(id = "a".repeat(32))
        val store = InMemoryPreferences()
        InProgressGameRepository(store, backgroundScope).run {
            save(saved)
            load()
        }
        val discard = RecordingDiscard(requested = true)

        val repository = InProgressGameRepository(store, backgroundScope, discard = discard)
        assertFalse(repository.hasInProgressGame.first())
        assertNull(repository.load())
        assertFalse(discard.requested, "the request is over once the game has gone")
        repository.mergeCloud(Stamped(SavedGameJson.encode(saved), Long.MAX_VALUE))
        assertNull(repository.load())
    }

    @Test
    fun `with no removal asked for the saved game stays - and a removal that can't be written stays asked for`() = runTest {
        val store = InMemoryPreferences()
        InProgressGameRepository(store, backgroundScope).run {
            save(game)
            load()
        }
        assertEquals(game, InProgressGameRepository(store, backgroundScope, discard = RecordingDiscard(requested = false)).load())

        val unwritable = object : DataStore<Preferences> by store {
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw IOException("Full")
        }
        val discard = RecordingDiscard(requested = true)
        assertEquals(game, InProgressGameRepository(unwritable, backgroundScope, discard = discard).load())
        assertTrue(discard.requested)
    }
}

private class RecordingDiscard(var requested: Boolean) : SavedGameDiscard {
    override fun isRequested(): Boolean = requested

    override fun done() {
        requested = false
    }
}
