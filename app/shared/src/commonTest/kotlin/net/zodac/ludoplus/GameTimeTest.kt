package net.zodac.ludoplus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.TutorialMessage
import net.zodac.ludoplus.game.TutorialStep
import net.zodac.ludoplus.model.CustomMode
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.common.ScreenScaffold
import net.zodac.ludoplus.ui.game.GameContent
import net.zodac.ludoplus.ui.game.GameTime
import net.zodac.ludoplus.ui.game.StartedGame
import net.zodac.ludoplus.ui.game.TutorialView
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** The time played under the game's title: what it shows and says, that it counts, and that it moves nothing. */
class GameTimeTest : UiTest() {

    private val showcase = Showcase { compose }

    private val time = hasContentDescription("Time played", substring = true)

    private fun assertTime(seconds: Long, shown: String, spoken: String) {
        showcase.show { LudoPlusTheme { GameTime(gameKey = 1, running = false, playMillis = { seconds * 1_000 + 999 }) } }
        compose.onNodeWithContentDescription(spoken).assertIsDisplayed()
        compose.onNodeWithText(shown, useUnmergedTree = true).assertExists()
        // Not read out as it counts.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertDoesNotExist()
    }

    @Test
    fun `it's shown and said as Statistics' is - seconds for the first minute then hours and minutes`() = uiTest {
        assertTime(0, "Time played: 0s", "Time played: 0 seconds")
        assertTime(1, "Time played: 1s", "Time played: 1 second")
        assertTime(59, "Time played: 59s", "Time played: 59 seconds")
        assertTime(12 * 60 + 4, "Time played: 12m", "Time played: 12 minutes")
        assertTime(3_600 + 5 * 60, "Time played: 1h 5m", "Time played: 1 hour and 5 minutes")
    }

    @Test
    fun `it counts each second while the game runs - stops once it's over - and starts afresh with another game`() = uiTest {
        var millis = 4_300L
        var game by mutableStateOf(1)
        var running by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { LudoPlusTheme { GameTime(gameKey = game, running = running, playMillis = { millis }) } }
        fun assertShows(seconds: Int) = compose.onNodeWithContentDescription("Time played: $seconds seconds").assertExists()
        compose.mainClock.advanceTimeByFrame()
        assertShows(4)
        millis = 5_010
        compose.mainClock.advanceTimeBy(800)
        assertShows(5)
        millis = 6_010
        compose.mainClock.advanceTimeBy(1_100)
        assertShows(6)
        compose.runOnIdle {
            millis = 7_000
            running = false
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(100)
        assertShows(7)
        millis = 9_000
        compose.mainClock.advanceTimeBy(5_000)
        assertShows(7)
        compose.runOnIdle {
            millis = 0
            game = 2
            running = true
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(100)
        assertShows(0)
    }

    @Test
    fun `past the first minute it changes on each whole minute of the game`() = uiTest {
        var millis = 119_400L
        compose.mainClock.autoAdvance = false
        compose.setContent { LudoPlusTheme { GameTime(gameKey = 1, running = true, playMillis = { millis }) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Time played: 1 minute").assertExists()
        millis = 120_010
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithContentDescription("Time played: 2 minutes").assertExists()
    }

    private fun showPage(subtitled: Boolean, fontScale: Float, title: String) = showcase.show {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            LudoPlusTheme {
                ScreenScaffold(
                    title = title,
                    onBack = {},
                    wide = true,
                    subtitle = if (subtitled) ({ GameTime(gameKey = 1, running = false, playMillis = { 724_000 }) }) else null,
                ) { Box(Modifier.fillMaxSize().testTag("content")) }
            }
        }
    }

    /** The time fits between the title and the bar's foot, [bar] dp down, and the page hasn't moved. Returns how far the title did. */
    private fun assertSlotsIn(fontScale: Float, bar: Float, title: String = "Classic"): Float {
        // A one-line title's page: one that wrapped could make its bar taller.
        showPage(subtitled = false, fontScale, "Classic")
        val contentBefore = compose.onNodeWithTag("content").getBoundsInRoot()
        val titleBefore = compose.onNodeWithText("Classic").getBoundsInRoot()
        showPage(subtitled = true, fontScale, title)
        assertEquals(contentBefore, compose.onNodeWithTag("content").getBoundsInRoot(), "at $fontScale it moved the page")
        val titleNow = compose.onNodeWithText(title).getBoundsInRoot()
        val shown = compose.onNode(time).getBoundsInRoot()
        val root = compose.onRoot().getBoundsInRoot().right
        assertEquals(root.value / 2, (shown.left + shown.right).value / 2, 1.1f, "centred on the bar")
        assertTrue(shown.bottom.value <= bar, "at $fontScale it ends at ${shown.bottom}, past the bar's foot at $bar")
        assertTrue(shown.top.value >= titleNow.bottom.value - 1.1f, "at $fontScale it starts at ${shown.top}, over the title, which ends at ${titleNow.bottom}")
        assertTrue(titleNow.top.value >= -1f, "at $fontScale the title is cut off at the top: $titleNow")
        assertTrue((shown.bottom - shown.top).value >= 15.9f, "at $fontScale it's smaller than at 100%: $shown")
        return (titleBefore.top - titleNow.top).value
    }

    @Test
    fun `in portrait it slots in between the title and the page - which stays where it is - at any font`() = uiTest(TestScreen(374, 795)) {
        assertEquals(0f, assertSlotsIn(1f, bar = 64f), "the title moved")
        listOf(1.3f, 1.5f, 2f).forEach { assertSlotsIn(it, bar = 64f) }
    }

    @Test
    fun `in landscape the title moves up a little for it - and the page stays where it is`() = uiTest(TestScreen(827, 342)) {
        assertEquals(6f, assertSlotsIn(1f, bar = 48f), 0.6f)
        listOf(1.3f, 2f).forEach { assertSlotsIn(it, bar = 48f) }
    }

    @Test
    fun `the longest custom name is on one line over it`() = uiTest(TestScreen(374, 795)) {
        listOf(1f, 2f).forEach { assertSlotsIn(it, bar = 64f, title = "W".repeat(CustomMode.MAX_NAME_LENGTH)) }
    }

    private val players = listOf(PlayerConfig(1, PlayerType.HUMAN, "Alex"), PlayerConfig(2, PlayerType.HUMAN, "Sam"))

    private fun showGame(tutorial: TutorialView?) = showcase.show {
        CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
            LudoPlusTheme {
                GameContent(
                    StartedGame(GameMode.CLASSIC, players, GameState.new(GameMode.CLASSIC, 2, first = 0), first = 0, playMillis = 724_000),
                    onRoll = {}, onMove = {}, onBack = {}, onResults = {}, tutorial = tutorial,
                )
            }
        }
    }

    @Test
    fun `the game screen has it - but not the tutorial`() = uiTest(TestScreen(374, 795)) {
        showGame(tutorial = null)
        compose.onNodeWithContentDescription("Time played: 12 minutes").assertIsDisplayed()
        showGame(TutorialView(step = null, part = 0, parts = 1, steps = listOf(TutorialStep.Next(TutorialMessage.entries.first())), onJump = {}))
        compose.onNode(time).assertDoesNotExist()
    }
}
