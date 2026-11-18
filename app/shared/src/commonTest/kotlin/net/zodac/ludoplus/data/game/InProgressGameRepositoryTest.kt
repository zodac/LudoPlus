package net.zodac.ludoplus.data.game

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
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.RollOff
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.ui.game.StartedGame

class InProgressGameRepositoryTest {

    private val game = StartedGame(
        mode = GameMode.CLASSIC,
        players = List(4) { PlayerConfig(it + 1, PlayerType.HUMAN, "Player ${it + 1}") },
        state = GameState.new(GameMode.CLASSIC, 4),
        rollOff = RollOff(listOf(mapOf(0 to 6, 1 to 5, 2 to 4, 3 to 3)), first = 0),
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
}
