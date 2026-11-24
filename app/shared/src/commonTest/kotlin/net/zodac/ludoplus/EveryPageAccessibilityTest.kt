package net.zodac.ludoplus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.app.LocalAppContainer
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.game.style.StyleCategory
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.achievements.AchievementsScreen
import net.zodac.ludoplus.ui.common.LocalReduceMotion
import net.zodac.ludoplus.ui.game.GameScreen
import net.zodac.ludoplus.ui.menu.MenuScreen
import net.zodac.ludoplus.ui.records.LeaderboardScreen
import net.zodac.ludoplus.ui.records.LeaderboardViewModel
import net.zodac.ludoplus.ui.records.StatisticsScreen
import net.zodac.ludoplus.ui.records.StatisticsViewModel
import net.zodac.ludoplus.ui.rules.RulesScreen
import net.zodac.ludoplus.ui.rules.resetRulesPageMemory
import net.zodac.ludoplus.ui.setup.GameSetupScreen
import net.zodac.ludoplus.ui.styles.StyleCategoryScreen
import net.zodac.ludoplus.ui.styles.StylesScreen
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/**
 * What TalkBack needs of every page (`.claude/UI.md`, the accessibility checklist): a name for all that can be tapped, and
 * the page's title as a heading; and at a large font, that title whole.
 */
class EveryPageAccessibilityTest : UiTest() {

    private val showcase = Showcase { compose }

    private val records get() = GameRecordRepository(InMemoryGameResultDao())

    /** Every page, by the title it's headed with. */
    private fun pages(): Map<String, @Composable () -> Unit> {
        val game = gameViewModel().also { viewModel ->
            viewModel.setupForm.setPlayerCount(8)
            viewModel.startGame { "Player $it" }
        }
        val setup = gameViewModel()
        val achievements = achievementsViewModel()
        val leaderboard = LeaderboardViewModel(records)
        val statistics = StatisticsViewModel(records)
        val styles = stylesViewModel()
        return mapOf(
            "LudoPlus" to { MenuScreen(hasInProgressGame = true, {}, {}, {}, {}, {}, {}, {}, {}) },
            "New Game" to { GameSetupScreen(setup, onStartGame = {}, onBack = {}) },
            "Classic" to { GameScreen(game, onBackToMenu = {}) },
            "Achievements" to { AchievementsScreen(achievements, onBack = {}) },
            "Leaderboard" to { LeaderboardScreen(leaderboard, onBack = {}) },
            "Statistics" to { StatisticsScreen(statistics, onBack = {}) },
            "Styles" to { StylesScreen(styles, onBack = {}, onOpenCategory = {}) },
            "Die" to { StyleCategoryScreen(styles, StyleCategory.DICE, onBack = {}) },
            "Rules" to { RulesScreen(onBack = {}) },
        )
    }

    private fun show(scale: Float, page: @Composable () -> Unit) {
        resetRulesPageMemory()
        showcase.show {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides SilentPlatformServices,
                LocalReduceMotion provides true,
            ) {
                LudoPlusTheme {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) { Box(Modifier.fillMaxSize()) { page() } }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun SemanticsNode.spokenName(): String =
        config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString() +
            config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text } +
            config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()

    @Test
    fun `everything that can be tapped has a name - on every page`() = uiTest(TestScreen(411, 891)) {
        for ((title, page) in pages()) {
            show(scale = 1f, page)
            val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            assertTrue(nodes.isNotEmpty(), "$title: nothing to tap")
            nodes.forEach { assertTrue(it.spokenName().isNotBlank(), "$title: a tap with no name at ${it.boundsInRoot}") }
        }
    }

    @Test
    fun `every page's title is a heading - whole on its line at 200 percent`() = uiTest(TestScreen(411, 891)) {
        for ((title, page) in pages()) {
            for (scale in listOf(1f, 2f)) {
                show(scale, page)
                val headings = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).fetchSemanticsNodes()
                val heading = headings.firstOrNull { title in it.spokenName() }
                assertTrue(heading != null, "$title at $scale: not a heading, only ${headings.map { it.spokenName() }}")
                // The menu's is the logo's wordmark: a picture, the same size at any font.
                if (title == "LudoPlus") continue
                val layouts = mutableListOf<TextLayoutResult>()
                heading.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
                layouts.forEach { assertEquals(false, it.hasVisualOverflow, "$title at $scale: cut off") }
            }
        }
    }

    @Test
    fun `the die can be reached and tapped at 200 percent with eight players`() = uiTest(TestScreen(411, 891)) {
        show(2f, pages().getValue("Classic"))
        val die = compose.onNode(hasContentDescription("Die") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(die.height >= 48f && die.width >= 48f && die.bottom <= 891f, "$die")
        compose.onNodeWithContentDescription("Back").assertExists()
    }
}
