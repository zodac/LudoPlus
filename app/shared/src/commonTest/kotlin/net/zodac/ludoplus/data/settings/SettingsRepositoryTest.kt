package net.zodac.ludoplus.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.achievements.AchievementsRepository
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCounter
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.TeamMode
import net.zodac.ludoplus.ui.game.StartedGame
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerType

/** Every value [flow] emits from now on, collected in the background. */
private fun <T> TestScope.emissions(flow: Flow<T>): List<T> =
    mutableListOf<T>().also { seen -> backgroundScope.launch { flow.collect { seen += it } } }

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {

    @Test
    fun `a retired setting an older version left is removed at launch - and the others are kept`() = runTest {
        val store = InMemoryPreferences()
        store.edit { it[intPreferencesKey("play_seconds")] = 90 }
        val repository = SettingsRepository(store)
        repository.setDiceStyleId("marble")
        repository.forgetRetired()
        assertEquals(null, store.data.first()[intPreferencesKey("play_seconds")])
        assertEquals("marble", repository.diceStyleId.first())
        // Nothing left to remove: run again, nothing changes.
        val before = store.data.first()
        repository.forgetRetired()
        assertEquals(before, store.data.first())
    }

    @Test
    fun `a snapshot reads back what the setup form saved - the defaults with nothing saved`() = runTest {
        val repository = SettingsRepository(InMemoryPreferences())
        val empty = repository.snapshot()
        assertEquals(null, empty.playerCount)
        assertEquals(null, empty.playerNameFor(1))
        assertEquals(GameMode.DEFAULT, empty.gameMode)
        assertEquals(TeamMode.OFF, empty.teamMode)
        assertFalse(empty.partnerCaptures)
        assertTrue(empty.rollForPartners)
        assertEquals(emptySet(), empty.houseRules)
        assertEquals(emptyList(), empty.customModes)

        repository.batch {
            setPlayerCount(3)
            setPlayerName(2, "Bea")
            setPlayerType(2, PlayerType.AI)
            setPlayerDifficulty(2, Difficulty.HARD)
            setPlayerColour(2, PlayerColour.entries.last())
            setGameMode(GameMode.PORTALS)
        }
        val saved = repository.snapshot()
        assertEquals(3, saved.playerCount)
        assertEquals("Bea", saved.playerNameFor(2))
        assertEquals(PlayerType.AI, saved.playerTypeFor(2))
        assertEquals(Difficulty.HARD, saved.playerDifficultyFor(2))
        assertEquals(PlayerColour.entries.last(), saved.playerColourFor(2))
        assertEquals(GameMode.PORTALS, saved.gameMode)
    }

    @Test
    fun `a setting's flow emits when that setting changes - not on a write to another`() = runTest {
        val repository = SettingsRepository(InMemoryPreferences())
        val sound = emissions(repository.soundEnabled)
        val diceStyle = emissions(repository.diceStyleId)
        runCurrent()

        repository.batch { setPlayerName(1, "Alice") }
        repository.setVibrationEnabled(false)
        runCurrent()
        assertEquals(listOf(true), sound)
        assertEquals(listOf("classic_ivory"), diceStyle)

        repository.setSoundEnabled(false)
        repository.setDiceStyleId("marble")
        runCurrent()
        assertEquals(listOf(true, false), sound)
        assertEquals(listOf("classic_ivory", "marble"), diceStyle)
    }

    @Test
    fun `the achievements emit when an unlock or a counter changes - not on any other write to their file`() = runTest {
        val store = InMemoryPreferences()
        val repository = AchievementsRepository(store)
        val states = emissions(repository.state)
        runCurrent()

        store.edit { it[stringPreferencesKey("unrelated")] = "x" }
        runCurrent()
        assertEquals(1, states.size)

        val achievement = Achievement.entries.first()
        val counter = AchievementCounter.entries.first()
        repository.record(mapOf(achievement to 5L), mapOf(counter to 2))
        runCurrent()
        assertEquals(2, states.size)
        assertEquals(5L, states.last().unlockedAt[achievement])
        assertEquals(2, states.last().counters[counter])
    }

    @Test
    fun `whether a game is saved emits when that changes - not on every move saved`() = runTest {
        val repository = InProgressGameRepository(InMemoryPreferences(), backgroundScope)
        val saved = emissions(repository.hasInProgressGame)
        runCurrent()

        var state = GameState.new(GameMode.CLASSIC, 2)
        val players = List(2) { PlayerConfig(it + 1, PlayerType.HUMAN, "P${it + 1}") }
        repeat(3) {
            repository.save(StartedGame(GameMode.CLASSIC, players, state))
            runCurrent()
            state = LudoEngine.applyRoll(state, 2)
        }
        repository.clear()
        runCurrent()
        assertEquals(listOf(false, true, false), saved)
    }
}
