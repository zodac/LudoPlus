package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import net.zodac.ludoplus.app.LocalAppContainer
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.achievements.InMemoryAchievementStore
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementVisibility
import net.zodac.ludoplus.navigation.AchievementScrollRequest
import net.zodac.ludoplus.navigation.AchievementScrollRequests
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.achievements.AchievementsScreen
import net.zodac.ludoplus.ui.achievements.resetAchievementsScrollMemory
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import net.zodac.ludoplus.ui.theme.GoldAccent
import org.jetbrains.compose.resources.getString

/** A banner's long press flashes its row gold, also when the screen was not open yet. */
class AchievementFlashTest : UiTest() {

    private fun goldPixels(title: String): Int {
        val bitmap = compose.onNodeWithText(title).captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        return pixels.count { it == GoldAccent.toArgb() }
    }

    @Test
    fun `a fresh arrival flashes its row gold`() = uiTest {
        resetAchievementsScrollMemory()
        val target = Achievement.entries.first { it.visibility == AchievementVisibility.NORMAL }
        val title = getString(target.title)
        val viewModel = achievementsViewModel()
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalAppContainer provides inMemoryAppContainer(), LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
            }
        }
        AchievementScrollRequests.request(AchievementScrollRequest(target.id, animate = false))
        var flashed = false
        repeat(150) {
            mainClock.advanceTimeBy(16)
            if (!flashed && onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty()) flashed = goldPixels(title) > 0
        }
        assertTrue(flashed, "the row never flashed")
    }

    /**
     * The vertical middle of [achievement]'s row (the row is one node), and its height, the screen opened by a request for
     * it with every other achievement earned.
     */
    private fun rowMiddle(achievement: Achievement): Pair<Float, Float> {
        resetAchievementsScrollMemory()
        var result = 0f to 0f
        uiTest {
            val settings = SettingsRepository(InMemoryPreferences())
            settings.setLockedOnlyAchievements(true)
            val normal = Achievement.entries.filter { it.visibility == AchievementVisibility.NORMAL }
            val target = achievement
            val index = normal.indexOf(target)
            val store = InMemoryAchievementStore()
            store.record(normal.filterIndexed { i, _ -> i % 2 == 0 && i != index }.associateWith { 1L }, emptyMap())
            val viewModel = achievementsViewModel(store = store, settings = settings)
            AchievementScrollRequests.request(AchievementScrollRequest(target.id, animate = false))
            setContent {
                CompositionLocalProvider(LocalAppContainer provides inMemoryAppContainer(), LocalPlatformServices provides SilentPlatformServices) {
                    LudoPlusTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
                }
            }
            waitForIdle()
            val row = onNodeWithText(getString(target.title)).getUnclippedBoundsInRoot()
            result = (row.top + row.bottom).value / 2 to (row.bottom - row.top).value
        }
        return result
    }

    @Test
    fun `a row lands in the middle of the list whatever its height, with only the locked shown`() {
        // A one-line description, and one of three lines.
        val (shortMiddle, shortHeight) = rowMiddle(Achievement.FIRST_VICTORY)
        val (tallMiddle, tallHeight) = rowMiddle(Achievement.LAP_OF_HONOUR)
        assertTrue(tallHeight > shortHeight + 12, "the two rows are not of different heights: $shortHeight and $tallHeight")
        assertTrue(abs(shortMiddle - tallMiddle) < 2f, "a $shortHeight row has its middle at $shortMiddle, a $tallHeight one at $tallMiddle")
    }
}
