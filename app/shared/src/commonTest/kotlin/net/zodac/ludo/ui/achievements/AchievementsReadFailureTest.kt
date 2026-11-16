package net.zodac.ludo.ui.achievements

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.achievements.InMemoryAchievementStore
import net.zodac.ludo.data.scores.InMemoryScoreDao
import net.zodac.ludo.data.scores.ScoreDao
import net.zodac.ludo.data.scores.ScoreRepository
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.LeaderboardTotals
import net.zodac.ludo.online.OnlineSettingsRepository

/** Scores that can't be read cost the Achievements page its score progress, not the app. */
@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsReadFailureTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the page still loads when the leaderboard figures can't be read`() = runTest {
        val dao = object : ScoreDao by InMemoryScoreDao() {
            override suspend fun leaderboardTotals(): LeaderboardTotals = throw IllegalStateException("The disk is unwell")
        }
        val viewModel = AchievementsViewModel(
            InMemoryAchievementStore(),
            ScoreRepository(dao),
            isDebugBuild = false,
            onlineSettingsRepository = OnlineSettingsRepository(InMemoryPreferences()),
            settingsRepository = SettingsRepository(InMemoryPreferences()),
        )
        val state = viewModel.uiState.first { it.isLoaded }
        assertTrue(state.groups.isNotEmpty())
        assertEquals(0, state.unlockedCount)
    }
}
