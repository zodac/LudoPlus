package net.zodac.ludo.data.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.Stamped
import net.zodac.ludo.model.Achievement
import net.zodac.ludo.model.AchievementCounter

/** The achievements' side of a merge: this device's shares out, other devices' merged in, nothing announced. */
class AchievementsRepositoryCloudTest {

    private var clock = 1_000L
    private val repository = AchievementsRepository(InMemoryPreferences(), now = { clock })

    /** This file's own share id, as its save names it: the one share not in [others]. */
    private suspend fun ownShare(others: Set<String> = emptySet()): String = (repository.cloudAchievements().deviceCounters.keys - others).single()

    @Test
    fun `this device's save holds its unlocks - its counters as its own share - and a stamped streak`() = runTest {
        repository.record(
            mapOf(Achievement.THE_JOURNEY_BEGINS to 500L),
            mapOf(AchievementCounter.GAMES_PLAYED to 4, AchievementCounter.WIN_STREAK to 2),
        )
        val own = ownShare()
        assertEquals(
            CloudAchievements(
                unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS.id to 500L),
                deviceCounters = mapOf(own to mapOf(AchievementCounter.GAMES_PLAYED.id to 4)),
                latestCounters = mapOf(AchievementCounter.WIN_STREAK.id to Stamped(2, 1_000L)),
            ),
            repository.cloudAchievements(),
        )
        // The same id every time.
        assertEquals(own, ownShare())
    }

    @Test
    fun `another device's games add to this one's - and this one's share is what's left`() = runTest {
        repository.record(emptyMap(), mapOf(AchievementCounter.GAMES_PLAYED to 4))
        val own = ownShare()
        repository.mergeCloud(
            CloudAchievements(deviceCounters = mapOf("tablet" to mapOf(AchievementCounter.GAMES_PLAYED.id to 3), own to mapOf(AchievementCounter.GAMES_PLAYED.id to 2))),
        )
        assertEquals(7, repository.current().counter(AchievementCounter.GAMES_PLAYED))
        // A game here then counts 8, all but the tablet's 3 being this phone's.
        repository.record(emptyMap(), mapOf(AchievementCounter.GAMES_PLAYED to 8))
        assertEquals(
            mapOf("tablet" to mapOf(AchievementCounter.GAMES_PLAYED.id to 3), own to mapOf(AchievementCounter.GAMES_PLAYED.id to 5)),
            repository.cloudAchievements().deviceCounters,
        )
        // The same save merged in again changes nothing.
        repository.mergeCloud(CloudAchievements(deviceCounters = mapOf("tablet" to mapOf(AchievementCounter.GAMES_PLAYED.id to 3))))
        assertEquals(8, repository.current().counter(AchievementCounter.GAMES_PLAYED))
    }

    @Test
    fun `the time played adds up the same way - and stays out of the counters`() = runTest {
        repository.addPlaySeconds(60)
        repository.mergeCloud(CloudAchievements(deviceCounters = mapOf("tablet" to mapOf(AchievementCounter.PLAY_SECONDS.id to 40))))
        assertEquals(100, repository.playSeconds.first())
        assertEquals(emptyMap(), repository.current().counters)
    }

    @Test
    fun `merged unlocks are quiet - at their earliest time - and a newer streak replaces this one`() = runTest {
        repository.record(mapOf(Achievement.THE_JOURNEY_BEGINS to 500L), mapOf(AchievementCounter.WIN_STREAK to 4))
        repository.mergeCloud(
            CloudAchievements(
                unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS.id to 300L, Achievement.CENTURION.id to 900L, "not_an_achievement" to 1L),
                latestCounters = mapOf(AchievementCounter.WIN_STREAK.id to Stamped(0, 2_000L)),
            ),
        )
        val state = repository.current()
        assertEquals(mapOf(Achievement.THE_JOURNEY_BEGINS to 300L, Achievement.CENTURION to 900L), state.unlockedAt)
        assertEquals(0, state.counter(AchievementCounter.WIN_STREAK))
        // An older streak doesn't.
        repository.mergeCloud(CloudAchievements(latestCounters = mapOf(AchievementCounter.WIN_STREAK.id to Stamped(9, 1_500L))))
        assertEquals(0, repository.current().counter(AchievementCounter.WIN_STREAK))
    }
}
