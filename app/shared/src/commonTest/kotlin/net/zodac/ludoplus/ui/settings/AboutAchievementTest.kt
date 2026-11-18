package net.zodac.ludoplus.ui.settings

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.zodac.ludoplus.data.achievements.InMemoryAchievementStore
import net.zodac.ludoplus.model.Achievement

@OptIn(ExperimentalCoroutinesApi::class)
class AboutAchievementTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opening About earns Who Made This - once`() = runTest {
        val store = InMemoryAchievementStore()
        val viewModel = settingsViewModel(achievements = store)
        viewModel.onAboutViewed()
        advanceUntilIdle()
        val first = store.current().unlockedAt
        assertEquals(setOf(Achievement.WHO_MADE_THIS), first.keys)
        viewModel.onAboutViewed()
        advanceUntilIdle()
        assertEquals(first, store.current().unlockedAt)
    }
}
