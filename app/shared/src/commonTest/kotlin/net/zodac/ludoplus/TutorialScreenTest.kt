package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.TutorialScript
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.game.GamePacing
import net.zodac.ludoplus.ui.game.GameScreen
import net.zodac.ludoplus.ui.game.GameViewModel
import net.zodac.ludoplus.ui.game.LeaveGameConfirmation
import net.zodac.ludoplus.ui.game.LeaveGameConfirmationDialog
import net.zodac.ludoplus.ui.game.LocalLeaveGameConfirmation
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** The tutorial as a screen reader meets it: the coach card's words, only the step's own action, and leaving it. */
class TutorialScreenTest : UiTest() {

    private val showcase = Showcase { compose }

    /** A move action's label, for the squares that offer one. */
    private val movable = SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick) and
        SemanticsMatcher("moves a pawn") { node -> node.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label?.startsWith("Move ") == true }

    /** The tutorial on screen, with no pause between steps: nothing on the board animates, and the coach doesn't wait. */
    private fun showTutorial(onLeft: () -> Unit = {}): GameViewModel {
        val viewModel = GameViewModel(
            SettingsRepository(InMemoryPreferences()),
            InProgressGameRepository(InMemoryPreferences()),
            GameRecordRepository(InMemoryGameResultDao()),
            tutorial = TutorialScript.CLASSIC,
        )
        viewModel.setPacing(GamePacing(thinkMillis = 0, pawnsAnimated = false, dieAnimated = false))
        compose.waitUntil { viewModel.setupForm.setupRestored.value }
        runBlocking { viewModel.startTutorial("Alex", "Green") }
        // Each screen its own confirmation: the shared default would carry a pending dialog into the next test.
        val confirmation = LeaveGameConfirmation()
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices, LocalLeaveGameConfirmation provides confirmation) {
                LudoPlusTheme {
                    GameScreen(viewModel, onBackToMenu = onLeft)
                    LeaveGameConfirmationDialog(confirmation)
                }
            }
        }
        // The screen sets its own pacing from the motion settings: put back the test's.
        compose.waitForIdle()
        viewModel.setPacing(GamePacing(thinkMillis = 0, pawnsAnimated = false, dieAnimated = false))
        return viewModel
    }

    @Test
    fun `the coach card is read out as each step arrives - its part a node of its own`() = uiTest(TestScreen(411, 891)) {
        showTutorial()
        val card = compose.onNode(hasText("Coach", substring = true) and hasText("Welcome to LudoPlus!", substring = true))
        assertEquals(
            listOf("Coach", "Welcome to LudoPlus! You're Red, racing Green. First, getting out of your yard. Tap the die to roll."),
            card.fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text },
        )
        assertEquals(LiveRegionMode.Polite, card.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        compose.onNodeWithText("Part 1 of 4").assertExists()
        // There's no part before the first; the next one is a tap away.
        compose.onNodeWithContentDescription("Next part").performClick()
        compose.onNodeWithText("Part 2 of 4").assertExists()
        compose.onNodeWithText("Next, captures. Green's pawn is 4 tiles ahead of yours. Tap the die.").assertExists()
        compose.onNodeWithContentDescription("Previous part").performClick()
        compose.onNodeWithText("Part 1 of 4").assertExists()
    }

    @Test
    fun `only the step's own action is offered - the die at a roll step, the step's pawn at a move step`() = uiTest(TestScreen(411, 891)) {
        val viewModel = showTutorial()
        viewModel.jumpToTutorialPart(1)
        compose.waitForIdle()
        // A roll step: the die rolls, and no pawn moves.
        compose.onAllNodes(movable).assertCountEquals(0)
        compose.onNodeWithContentDescription("Die").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("A 4! Tap your pawn to land on Green's and send it back to its yard.").assertExists()
        // A move step: only the step's pawn, and the die no longer rolls.
        val moves = compose.onAllNodes(movable).fetchSemanticsNodes().map { it.config[SemanticsActions.OnClick].label }
        assertEquals(listOf("Move Red pawn 1 4 tiles, capturing Green pawn 1"), moves)
        compose.onNodeWithContentDescription("Die showing 4").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onAllNodes(movable)[0].performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Captured! A capture earns another roll. Tap the die.").assertExists()
    }

    @Test
    fun `Green waits for Next - its words read out first and each step its own`() = uiTest(TestScreen(411, 891)) {
        showTutorial()
        compose.onAllNodes(hasText("Next")).assertCountEquals(0)
        compose.onNodeWithContentDescription("Die").performSemanticsAction(SemanticsActions.OnClick)
        // Each step's words, the line beside Next, and Next itself.
        listOf(
            "Only a 6 brings a pawn out of the yard, so your turn passed. Tap Next to watch Green." to "Green's turn.",
            "Green rolled a 6, so it can bring a pawn out. Tap Next." to "Green rolled 6.",
            "Green's pawn is out. A 6 also earns another roll. Tap Next." to "Green rolls again.",
            "Green moves a pawn by the number it rolled. Tap Next." to "Green rolled 2.",
        ).forEach { (words, line) ->
            compose.waitForIdle()
            compose.onNodeWithText(words).assertExists()
            compose.onNodeWithContentDescription(line).assertExists()
            compose.onNodeWithText("Next").assertIsEnabled().performClick()
        }
        compose.waitForIdle()
        compose.onNodeWithText("Your turn. Tap the die.").assertExists()
        compose.onAllNodes(hasText("Next")).assertCountEquals(0)
    }

    @Test
    fun `a part ends on its own words - and Next sets out the one after`() = uiTest(TestScreen(411, 891)) {
        val viewModel = showTutorial()
        viewModel.jumpToTutorialPart(1)
        viewModel.jumpToTutorialPart(1)
        repeat(2) {
            viewModel.roll()
            compose.waitForIdle()
            viewModel.move(it)
            compose.waitForIdle()
        }
        compose.onNodeWithText("Part 3 of 4").assertExists()
        compose.onNodeWithText("Well done! Tap Next to carry on.").assertExists()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Part 4 of 4").assertExists()
    }

    @Test
    fun `back always asks before leaving the tutorial - in its own words`() = uiTest(TestScreen(411, 891)) {
        var left = 0
        showTutorial { left++ }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Leave the tutorial?").assertExists()
        compose.onNodeWithText("Nothing in it is kept. You can play it again from the Rules.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, left)
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Leave").performClick()
        assertEquals(1, left)
    }
}
