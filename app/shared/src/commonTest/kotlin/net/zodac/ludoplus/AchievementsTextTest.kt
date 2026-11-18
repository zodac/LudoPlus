package net.zodac.ludoplus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.abs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.app.AchievementEvent
import net.zodac.ludoplus.app.AchievementEvents
import net.zodac.ludoplus.app.LocalAppContainer
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.achievements.AchievementsState
import net.zodac.ludoplus.data.achievements.InMemoryAchievementStore
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCategory
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.achievements.AchievementBannerHost
import net.zodac.ludoplus.ui.achievements.AchievementsScreen
import net.zodac.ludoplus.ui.achievements.AchievementsViewModel
import net.zodac.ludoplus.ui.achievements.resetAchievementsScrollMemory
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import org.jetbrains.compose.resources.getString

/** The Achievements page's and its banners' words: the count, categories, a hidden row, and what each banner says and is called. */
class AchievementsTextTest : UiTest() {

    private val showcase = Showcase { compose }

    /** The page remembers where it was scrolled to, and the Locked only switch, for the whole process: every test starts afresh. */
    @BeforeTest
    fun openAtTheTop() = resetAchievementsScrollMemory()

    private fun showAchievements(viewModel: AchievementsViewModel) = showcase.show {
        CompositionLocalProvider(
            LocalAppContainer provides inMemoryAppContainer(),
            LocalPlatformServices provides SilentPlatformServices,
        ) {
            LudoPlusTheme { AchievementsScreen(viewModel = viewModel, onBack = {}) }
        }
    }

    @Test
    fun `the page is titled - counts what is unlocked - hides a description as three question marks - and each banner says what it is`() = uiTest {
        val viewModel = achievementsViewModel()
        showAchievements(viewModel)
        compose.onNodeWithText("Achievements").assertExists()
        val total = Achievement.entries.count { it.category != AchievementCategory.EASTER_EGGS }
        compose.onNodeWithText("0 of $total earned").assertExists()
        // A category header is read with how many of its achievements are earned (its drawn count is hidden from TalkBack so it is not said twice).
        val milestones = Achievement.entries.count { it.category == AchievementCategory.MILESTONES }
        compose.onNodeWithContentDescription("Milestones, 0 of $milestones earned").assertExists()
        // A hidden achievement's description is three question marks until it is earned - all in Miscellaneous, further
        // down the list: scroll to the first.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("???"))
        compose.onAllNodesWithText("???").onFirst().assertExists()

        // Each banner on a host of its own: a host shows its banners one after another.
        fun showBannerHost() = showcase.show {
            CompositionLocalProvider(LocalAppContainer provides inMemoryAppContainer()) {
                LudoPlusTheme {
                    AchievementBannerHost(isOnGameScreen = { false }, onAchievementSelected = {}, onStylesSelected = {}) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        showBannerHost()
        // An unlock banner is read as the achievement unlocked and its description.
        AchievementEvents.emit(AchievementEvent.Unlocked(Achievement.THE_JOURNEY_BEGINS))
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Achievement earned: The Journey Begins. Start your first game")).assertExists()
        // A progress banner is read as its title and how far along.
        showBannerHost()
        AchievementEvents.emit(AchievementEvent.Progressed(Achievement.GETTING_COMFORTABLE, 4, 5))
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Getting Comfortable: 5 of 10")).assertExists()
        val config = compose.onAllNodesWithContentDescription("Getting Comfortable: 5 of 10").onFirst().fetchSemanticsNode().config
        assertEquals(true, SemanticsActions.OnClick in config)
    }

    @Test
    fun `The Unearned switch is one switch - it hides what is unlocked - and is still on when the page is opened again`() = uiTest {
        val store = InMemoryAchievementStore(AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 0L)))
        val settings = SettingsRepository(InMemoryPreferences())
        showAchievements(achievementsViewModel(store = store, settings = settings))
        // Its label and switch are one target, read as "All, switch" or "Unearned, switch" and whether it's on.
        val lockedOnly = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        lockedOnly.assertIsOff()
        compose.onNodeWithText("The Journey Begins").assertExists()

        lockedOnly.performClick()
        compose.waitForIdle()
        lockedOnly.assertIsOn()
        compose.onNodeWithText("The Journey Begins").assertDoesNotExist()
        compose.onNodeWithText("Let's Finish This").assertExists()

        // Back to the menu and in again: a new page, as navigation makes one, still showing the locked ones only.
        showAchievements(achievementsViewModel(store = store, settings = settings))
        lockedOnly.assertIsOn()
        compose.onNodeWithText("The Journey Begins").assertDoesNotExist()

        lockedOnly.performClick()
        compose.waitForIdle()
        lockedOnly.assertIsOff()
        compose.onNodeWithText("The Journey Begins").assertExists()
    }

    @Test
    fun `a row's title stays on one line at its own size when it fits - never below the 12sp floor nor over two lines`() = uiTest(TestScreen(345, 800)) {
        showAchievements(achievementsViewModel())
        val short = getString(Achievement.THE_JOURNEY_BEGINS.title)
        val short14 = titleLayout(short).layoutInput
        assertEquals(1, short14.maxLines)
        assertEquals(14f, short14.style.fontSize.value, 0.01f)

        // The longest title, stepped down if it must be - never below the app-wide 12sp floor - and two lines at most.
        val longest = Achievement.entries.map { getString(it.title) }.maxBy { it.length }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(longest))
        val layout = titleLayout(longest)
        assertTrue(layout.layoutInput.style.fontSize.value >= 12f, "never below the 12sp floor")
        assertTrue(layout.lineCount <= 2, "at most two lines")
    }

    @Test
    fun `the earned count includes the Easter Eggs while the total leaves them out - so all earned reads more than the total`() = uiTest {
        val all = AchievementsState(unlockedAt = Achievement.entries.associateWith { 0L })
        showAchievements(achievementsViewModel(store = InMemoryAchievementStore(all)))
        val total = Achievement.entries.count { it.category != AchievementCategory.EASTER_EGGS }
        compose.onNodeWithText("${Achievement.entries.size} of $total earned").assertExists()
    }

    @Test
    fun `the Easter Eggs header is read with its earned count like every other category`() = uiTest {
        val eggs = Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS }
        val all = AchievementsState(unlockedAt = mapOf(eggs.first() to 0L))
        showAchievements(achievementsViewModel(store = InMemoryAchievementStore(all)))
        val header = hasContentDescription("Easter Eggs, 1 of ${eggs.size} earned")
        compose.onNode(hasScrollAction()).performScrollToNode(header)
        compose.onNode(header).assertExists()
    }

    private fun titleLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
        return results.first()
    }

    @Test
    fun `Flipping the switch keeps the list where it was`() = uiTest {
        val store = InMemoryAchievementStore(AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 0L)))
        showAchievements(achievementsViewModel(store = store, settings = SettingsRepository(InMemoryPreferences())))
        val switch = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Clean Sweep"))
        compose.waitForIdle()
        compose.onNodeWithText("Clean Sweep").assertIsDisplayed()

        switch.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Clean Sweep").assertIsDisplayed()

        switch.performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Clean Sweep").assertIsDisplayed()
    }

    @Test
    fun `Flipping the switch at the top keeps the top - no row is left behind the pinned header`() = uiTest {
        val store = InMemoryAchievementStore(AchievementsState(unlockedAt = mapOf(Achievement.THE_JOURNEY_BEGINS to 0L)))
        showAchievements(achievementsViewModel(store = store, settings = SettingsRepository(InMemoryPreferences())))
        val switch = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        val firstRowTop = compose.onNodeWithText("The Journey Begins").getUnclippedBoundsInRoot().top

        // The first locked row takes the first row's place, under its header: its title within a dp or two of the earned
        // row's, which sits a little differently in its card - behind the header, it was 55dp higher.
        switch.performClick()
        compose.waitForIdle()
        val lockedTop = compose.onNodeWithText("Let's Finish This").getUnclippedBoundsInRoot().top
        assertTrue(abs((lockedTop - firstRowTop).value) <= 2f, "The first locked row's title is at $lockedTop, not $firstRowTop")

        switch.performClick()
        compose.waitForIdle()
        assertEquals(firstRowTop, compose.onNodeWithText("The Journey Begins").getUnclippedBoundsInRoot().top)
    }

    @Test
    fun `a row whose achievement unlocks a style reads the style with it - and a tap shows it`() = uiTest {
        showAchievements(achievementsViewModel())
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Number Cruncher"))
        val reward = "Earn this to unlock the 'Maths' die style"
        val row = compose.onNode(hasContentDescription(reward) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
        assertEquals("Show which style this unlocks", row.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        // Heard, not seen, until the tap shows it as a tip.
        compose.onNodeWithText(reward).assertDoesNotExist()
        row.performClick()
        compose.waitForIdle()
        compose.onNodeWithText(reward).assertExists()
        // A row with no style behind it has no tap at all.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Who Made This?"))
        val plain = compose.onNode(hasText("Who Made This?")).fetchSemanticsNode()
        assertTrue(SemanticsActions.OnClick !in plain.config)
    }
}
