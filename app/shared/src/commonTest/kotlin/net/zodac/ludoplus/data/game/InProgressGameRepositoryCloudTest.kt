package net.zodac.ludoplus.data.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.Stamped
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.RollOff
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.ui.game.StartedGame

/** The saved game's side of a merge: the newest game kept, but never a copy of one set back, nor one finished here. */
class InProgressGameRepositoryCloudTest {

    private var clock = 1_000L

    private val game = StartedGame(
        mode = GameMode.CLASSIC,
        players = List(4) { PlayerConfig(it + 1, PlayerType.HUMAN, "Player ${it + 1}") },
        state = GameState.new(GameMode.CLASSIC, 4),
        rollOff = RollOff(listOf(mapOf(0 to 6, 1 to 5, 2 to 4, 3 to 3)), first = 0),
        id = "a".repeat(32),
    )

    /** [game] [steps] on: a roll further, by its count. */
    private fun further(steps: Int) = game.copy(state = LudoEngine.applyRoll(game.state, 3), steps = steps)

    private fun TestScope.repository() = InProgressGameRepository(InMemoryPreferences(), backgroundScope, now = { clock })

    @Test
    fun `each save is stamped - and never earlier than the last - with the clock set back`() = runTest {
        val repository = repository()
        repository.save(game)
        assertEquals(Stamped(SavedGameJson.encode(game), 1_000L), repository.cloudGame())
        clock = 500L
        repository.clear()
        assertEquals(Stamped(null, 1_001L), repository.cloudGame())
    }

    @Test
    fun `a game saved later elsewhere is taken - an older one isn't`() = runTest {
        val repository = repository()
        repository.save(game)
        val other = game.copy(id = "b".repeat(32))
        repository.mergeCloud(Stamped(SavedGameJson.encode(other), 500L))
        assertEquals(game, repository.load())
        repository.mergeCloud(Stamped(SavedGameJson.encode(other), 2_000L))
        assertEquals(other, repository.load())
    }

    @Test
    fun `of two copies of one game - the one further on wins whatever their stamps`() = runTest {
        val repository = repository()
        repository.save(further(steps = 5))
        repository.mergeCloud(Stamped(SavedGameJson.encode(game), 9_000L))
        assertEquals(further(steps = 5), repository.load())
        repository.mergeCloud(Stamped(SavedGameJson.encode(further(steps = 6)), 1L))
        assertEquals(further(steps = 6), repository.load())
    }

    @Test
    fun `a game finished here never comes back - nor does one this app can't read`() = runTest {
        val repository = repository()
        repository.save(game)
        repository.clear(finished = game)
        repository.mergeCloud(Stamped(SavedGameJson.encode(further(steps = 9)), 9_000L))
        assertNull(repository.load())
        repository.mergeCloud(Stamped("{\"version\":99}", 9_000L))
        assertNull(repository.load())
    }
}
