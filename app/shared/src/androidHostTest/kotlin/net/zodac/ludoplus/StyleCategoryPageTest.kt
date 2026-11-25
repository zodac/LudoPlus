package net.zodac.ludoplus

import net.zodac.ludoplus.ui.style.PawnStyles
import net.zodac.ludoplus.ui.style.BoardStyles
import androidx.compose.runtime.CompositionLocalProvider
import net.zodac.ludoplus.platform.PlatformServices
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.ludoplus.data.achievements.AchievementsState
import net.zodac.ludoplus.data.settings.SavedStyles
import net.zodac.ludoplus.game.style.StyleAccess
import net.zodac.ludoplus.game.style.StyleCategory
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.style.DiceStyles
import net.zodac.ludoplus.ui.style.TableBackgrounds
import net.zodac.ludoplus.ui.styles.StyleCategoryScreen
import net.zodac.ludoplus.ui.styles.StylesViewModel
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import org.jetbrains.compose.resources.getString

/**
 * A Styles category's own page once it's open: every tile at once, spaced to the phone, and the Unlocked switch.
 *
 * Android only: it steps the clock through seconds of a whole page, which the desktop test harness draws frame by frame.
 */
class StyleCategoryPageTest : UiTest() {

    private val tiles = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private val showcase = Showcase { compose }

    // Everything earned, so every page holds more tiles than fit across.
    private val everything = AchievementsState(unlockedAt = Achievement.entries.associateWith { 0L })

    private fun picks(achievements: AchievementsState = everything, diceId: String = DiceStyles.default.id) =
        MutableStateFlow<SavedStyles?>(SavedStyles(BoardStyles.default.id, PawnStyles.default.id, diceId, TableBackgrounds.default.id, achievements))

    /** [category]'s page on [viewModel], on a device whose own density is [defaultDensity] (null: as the screen's), at [fontScale]. */
    private fun open(
        category: StyleCategory,
        viewModel: StylesViewModel = stylesViewModel(picks()),
        defaultDensity: Float? = null,
        fontScale: Float = 1f,
    ) {
        val platform = object : PlatformServices by SilentPlatformServices {
            override val defaultDensity: Float? = defaultDensity
        }
        showcase.show {
            LudoPlusTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalPlatformServices provides platform, LocalDensity provides Density(density.density, fontScale)) {
                    StyleCategoryScreen(viewModel = viewModel, category = category, onBack = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    private fun settle() = repeat(30) { compose.mainClock.advanceTimeByFrame() }

    @Test
    fun `a narrow phone's pages fit four dice and backgrounds to a line - and three boards and pawns`() = uiTest(TestScreen(360, 780)) {
        val perLine = mapOf(StyleCategory.BOARD to 3, StyleCategory.PAWNS to 3, StyleCategory.DICE to 4, StyleCategory.BACKGROUND to 4)
        for ((category, expected) in perLine) {
            open(category)
            // Frame by frame: the backgrounds' animated art never lets the page go idle once it's on screen.
            compose.mainClock.autoAdvance = false
            settle()
            val lines = compose.onAllNodes(tiles).fetchSemanticsNodes().groupBy { it.positionInRoot.y }
            assertEquals(expected, lines.values.first().size, "$category: ${lines.values.map { it.size }}")
            // Every tile left to right within the screen.
            val width = compose.onRoot().fetchSemanticsNode().boundsInRoot.width
            assertTrue(lines.values.flatten().all { it.positionInRoot.x >= 0f && it.positionInRoot.x + it.size.width <= width }, "$category")
            compose.mainClock.autoAdvance = true
        }
    }

    /** How far the page scrolls, in dp, and how many tiles are on its first line. */
    private fun scrollAndFirstLine(): Pair<Float, Int> {
        val scroll = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes()
            .single().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() / compose.density.density
        val firstLine = compose.onAllNodes(tiles).fetchSemanticsNodes().groupBy { it.positionInRoot.y }.minBy { it.key }.value.size
        return scroll to firstLine
    }

    @Test
    fun `at the device's own sizes a long category scrolls at full size - at a larger display size or font its tiles shrink to fit`() = uiTest(TestScreen(360, 768)) {
        // The device's own density and font: the dice scroll, four to a line.
        open(StyleCategory.DICE)
        val (fullScroll, fullLine) = scrollAndFirstLine()
        assertTrue(fullScroll > 100f, "scrolls $fullScroll")
        assertEquals(4, fullLine)

        // A larger display size (the screen's density above the device's own): smaller tiles, five to a line, no scrolling.
        open(StyleCategory.DICE, defaultDensity = compose.density.density / 1.1f)
        val (zoomedScroll, zoomedLine) = scrollAndFirstLine()
        assertEquals(0f, zoomedScroll, "zoomed dice")
        assertEquals(5, zoomedLine)

        // A larger font: the backgrounds shrink just enough to fit too.
        open(StyleCategory.BACKGROUND, fontScale = 1.3f)
        assertEquals(0f, scrollAndFirstLine().first, "backgrounds at 130%")
    }

    @Test
    fun `boards and pawns all fit a 374 x 827dp phone at full size without scrolling - their lines closed up a little`() = uiTest(TestScreen(374, 795)) {
        // 827dp less the phone's 32dp status bar, which isn't drawn here. With some styles locked, as with none.
        for (achievements in listOf(AchievementsState(), everything)) {
            for (category in listOf(StyleCategory.BOARD, StyleCategory.PAWNS)) {
                open(category, stylesViewModel(picks(achievements)))
                assertEquals(0f, scrollAndFirstLine().first, "$category")
                assertTrue(compose.onAllNodes(tiles).fetchSemanticsNodes().all { it.size.width == 97 && it.size.height == 76 }, "$category")
            }
        }
    }

    /** How far down the page is scrolled, in px. */
    private fun scrolled(): Float = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).fetchSemanticsNodes()
        .single().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun `a page opens with its pick in view - centred at once if it's off screen, not scrolled if it's already there`() = uiTest(TestScreen(360, 560)) {
        val unlockedDice = DiceStyles.families.filter { it.unlock.isMet(StyleAccess(everything)) }
        val farDown = unlockedDice[unlockedDice.size * 3 / 5]

        // Three fifths down a page this short: off screen at the top, so put in the middle within two frames.
        showcase.show {
            LudoPlusTheme {
                CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                    StyleCategoryScreen(stylesViewModel(picks(diceId = farDown.colours.first().style.id)), StyleCategory.DICE, onBack = {})
                }
            }
        }
        compose.mainClock.autoAdvance = false
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val name = getString(farDown.name)
        val tile = compose.onNode(tiles and hasContentDescription(name, substring = true)).fetchSemanticsNode().boundsInRoot
        assertTrue(tile.center.y in screen.height * 0.45f..screen.height * 0.8f, "$name at $tile on $screen")
        compose.mainClock.autoAdvance = true

        // On the second line, wholly in view already: the page opens at its top.
        open(StyleCategory.DICE, stylesViewModel(picks(diceId = unlockedDice[5].colours.first().style.id)))
        assertEquals(0f, scrolled(), "scrolled to a pick already in view")
    }

    @Test
    fun `the page's right-hand margin drags it as a scrollbar - further than the finger`() = uiTest(TestScreen(411, 891)) {
        open(StyleCategory.DICE)
        compose.mainClock.autoAdvance = false
        settle()
        fun firstTileY() = compose.onAllNodes(tiles).fetchSemanticsNodes().minOf { it.positionInRoot.y }
        val before = firstTileY()
        val drag = with(compose.density) { 100.dp.toPx() }
        compose.onRoot().performTouchInput {
            val x = right - with(compose.density) { 10.dp.toPx() }
            swipe(start = Offset(x, centerY), end = Offset(x, centerY + drag), durationMillis = 300)
        }
        settle()
        val moved = before - firstTileY()
        assertTrue(moved > drag, "moved $moved for a drag of $drag")
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun `Unlocked only hides the locked tiles without rebuilding the others`() = uiTest {
        val viewModel = stylesViewModel()
        open(StyleCategory.DICE, viewModel)
        val before = compose.onAllNodes(tiles).fetchSemanticsNodes().filter { it.config.getOrNull(SemanticsProperties.StateDescription) != "Locked" }.map { it.id }.toSet()
        // Frame by frame, so tiles torn down and built again would show as tiles gone partway.
        compose.mainClock.autoAdvance = false
        viewModel.setUnlockedOnly(true)
        compose.mainClock.advanceTimeByFrame()
        val seen = mutableListOf<Set<Int>>()
        repeat(10) {
            seen += compose.onAllNodes(tiles).fetchSemanticsNodes().map { it.id }.toSet()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.autoAdvance = true
        // The same unlocked tiles throughout: rebuilt ones would be new.
        assertTrue(seen.all { it == before }, "before $before, then $seen")
    }

    @Test
    fun `Unlocked only takes the locked tiles away - unlocked ones stay put, nothing locked is reachable - and turning it off brings them back`() = uiTest {
        val viewModel = stylesViewModel()
        open(StyleCategory.DICE, viewModel)
        fun unlockedPositions() = compose.onAllNodes(tiles).fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsProperties.StateDescription) != "Locked" }
            .map { it.positionInRoot }
        fun reachable() = compose.onRoot().fetchSemanticsNode().let { root ->
            generateSequence(listOf(root)) { level -> level.flatMap { it.children }.takeIf { it.isNotEmpty() } }.sumOf { it.size }
        }
        val positionsOff = unlockedPositions()
        val nodesOff = reachable()
        viewModel.setUnlockedOnly(true)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(positionsOff, unlockedPositions(), "the unlocked tiles moved")
        assertEquals(0, compose.onAllNodes(hasContentDescription("Frosted")).fetchSemanticsNodes().size)
        assertTrue(reachable() < nodesOff, "the locked tiles are still reachable")
        viewModel.setUnlockedOnly(false)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(positionsOff, unlockedPositions())
        assertEquals(nodesOff, reachable(), "the locked tiles didn't all come back")
    }

    @Test
    fun `picking a style keeps the page's other tiles built - and picking each die rolls it through without breaking the page`() = uiTest {
        val unlockedDice = DiceStyles.families.filter { it.unlock.isMet(StyleAccess(everything)) }
        assertTrue(unlockedDice.size >= 2, "needs two unlocked dice styles to pick between")
        val saved = picks(diceId = unlockedDice[0].colours.first().style.id)
        open(StyleCategory.DICE, stylesViewModel(saved))
        val built = compose.onAllNodes(tiles).fetchSemanticsNodes().size
        // Frame by frame from here, so a page that dropped its tiles and built them again would be caught partway.
        compose.mainClock.autoAdvance = false
        saved.value = saved.value!!.copy(diceStyleId = unlockedDice[1].colours.first().style.id)
        compose.mainClock.advanceTimeByFrame()
        val after = compose.onAllNodes(tiles).fetchSemanticsNodes()
        assertEquals(built, after.size)
        val selected = after.filter { it.config.getOrNull(SemanticsProperties.Selected) == true }.map { it.config[SemanticsProperties.ContentDescription].single() }
        assertTrue(selected.any { it.startsWith(getString(unlockedDice[1].name)) }, selected.toString())
        compose.mainClock.autoAdvance = true

        // Every die style, each drawn as it rolls: the pick's roll on its tile.
        open(StyleCategory.DICE)
        val allBuilt = compose.onAllNodes(tiles).fetchSemanticsNodes().size
        for (die in DiceStyles.families.filter { it.unlock.isMet(StyleAccess(everything)) }) {
            val name = getString(die.name)
            val tile = compose.onAllNodes(tiles and SemanticsMatcher("starts with $name") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull()?.startsWith(name) == true
            }).onFirst()
            tile.performSemanticsAction(SemanticsActions.OnClick)
            // Partway through the roll, then past it.
            compose.mainClock.advanceTimeBy(300)
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        assertEquals(allBuilt, compose.onAllNodes(tiles).fetchSemanticsNodes().size)
    }
}
