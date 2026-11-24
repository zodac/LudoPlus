package net.zodac.ludoplus

import net.zodac.ludoplus.model.CustomMode
import net.zodac.ludoplus.model.GameMode
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.TeamMode
import net.zodac.ludoplus.netplay.LocalNearby
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.game.FakeNearby
import net.zodac.ludoplus.ui.game.GameViewModel
import net.zodac.ludoplus.ui.setup.GameSetupScreen
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** The screen this class's tests are drawn on. */
private val SCREEN = TestScreen(411, 891)

/**
 * The New Game form's words - and that an empty name field shows its seat's default as a hint, not as text that was typed.
 * On a wide phone: at 360dp a computer player's difficulties are their initials (see DifficultySelector).
 */
class SetupTextTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun show(viewModel: GameViewModel = gameViewModel()) = showcase.show {
        CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
            LudoPlusTheme { GameSetupScreen(viewModel = viewModel, onStartGame = {}, onBack = {}) }
        }
    }

    @Test
    fun `the form's words - its title and section - a seat's default as a hint - a clash with it - and the seat chips and difficulties`() = uiTest(SCREEN) {
        val viewModel = gameViewModel()
        show(viewModel)
        listOf("New Game", "Game Mode", "Start Game").forEach { compose.onAllNodesWithText(it).assertCountEquals(1) }

        // An empty name field shows its seat's default as a hint - and a name typed over it replaces it.
        compose.onNodeWithText("Player 1").assertExists()
        viewModel.setupForm.setPlayerName(1, "Wolfgang")
        compose.waitForIdle()
        compose.onNodeWithText("Wolfgang").assertExists()
        compose.onAllNodesWithText("Player 1").assertCountEquals(0)
        compose.onNodeWithContentDescription("Player 1 name").assertExists()

        // A seat's default clashes with another seat that typed the same words.
        val clash = gameViewModel()
        clash.setupForm.setPlayerType(2, PlayerType.HUMAN)
        clash.setupForm.setPlayerType(3, PlayerType.HUMAN)
        clash.setupForm.setPlayerName(2, "player 3")
        show(clash)
        compose.onNodeWithText("Names must be unique").assertExists()

        // The seat chips and a computer player's difficulties are named: seats 2 to 4 are computer players by default.
        show(gameViewModel())
        compose.onAllNodesWithText("CPU").assertCountEquals(3)
        listOf("Easy", "Medium", "Hard").forEach { compose.onAllNodesWithText(it).assertCountEquals(3) }
    }

    @Test
    fun `a narrower phone with Nearby - the difficulties still in full beside the wider Nearby chip`() = uiTest(TestScreen(380, 891)) {
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices, LocalNearby provides FakeNearby()) {
                LudoPlusTheme { GameSetupScreen(viewModel = gameViewModel(), onStartGame = {}, onBack = {}) }
            }
        }
        listOf("Easy", "Medium", "Hard").forEach { compose.onAllNodesWithText(it).assertCountEquals(3) }
    }

    @Test
    fun `the tutorial link shows until a first game is finished - and opens the tutorial`() = uiTest(SCREEN) {
        var opened = 0
        val viewModel = gameViewModel()
        var link by mutableStateOf(true)
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { GameSetupScreen(viewModel = viewModel, onStartGame = {}, onBack = {}, showTutorialLink = link, onTutorial = { opened++ }) }
            }
        }
        compose.onNodeWithText("New to the game? Play the tutorial").performClick()
        assertEquals(1, opened)
        link = false
        compose.waitForIdle()
        compose.onAllNodesWithText("New to the game? Play the tutorial").assertCountEquals(0)
    }

    @Test
    fun `the Players row - each number said as a count of players - picking one shows that many players`() = uiTest(SCREEN) {
        val viewModel = gameViewModel()
        show(viewModel)
        compose.onAllNodesWithText("Players").assertCountEquals(1)
        (2..8).forEach { compose.onNodeWithContentDescription("$it players").assertExists() }
        compose.onAllNodesWithContentDescription("Player 5 colour", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("8 players").performClick()
        compose.waitForIdle()
        assertEquals(8, viewModel.setupForm.setup.value.playerCount)
        compose.onAllNodesWithContentDescription("Player 8 colour, Purple").assertCountEquals(1)
    }

    @Test
    fun `the Teams card - three choices with 4, 6, or 8 players - what each makes said - and its two switches`() = uiTest(SCREEN) {
        val viewModel = gameViewModel()
        viewModel.setupForm.setPlayerCount(5)
        show(viewModel)
        compose.onNodeWithText("Teams need 4, 6, or 8 players").assertExists()
        viewModel.setupForm.setPlayerCount(6)
        compose.waitForIdle()
        listOf("Off", "Two Teams", "Pairs").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onAllNodesWithText("Partners can capture each other").assertCountEquals(0)
        compose.onNodeWithText("Two Teams").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Two teams of 3, seats alternating").assertExists()
        viewModel.setupForm.setTeamMode(TeamMode.PAIRS)
        compose.waitForIdle()
        compose.onNodeWithText("3 teams of two, partners opposite").assertExists()
        // Each switch is one toggle, named by its words.
        compose.onNode(hasText("Partners can capture each other") and isToggleable()).assertIsOff().performClick()
        compose.onNode(hasText("Roll for a partner once your pawns are home") and isToggleable()).assertIsOn()
        compose.waitForIdle()
        assertEquals(true, viewModel.setupForm.setup.value.partnerCaptures)
        assertEquals(listOf(0, 1, 2, 0, 1, 2), viewModel.setupForm.setup.value.teams)
    }

    @Test
    fun `the House Rules card - locked until a game is finished - then none on - a switch each - and what a game with any on doesn't count towards`() = uiTest(SCREEN) {
        // Locked: the card is one note, said as one.
        show(gameViewModel())
        compose.onNode(hasText("House Rules") and hasText("Finish a game to unlock house rules")).assertExists()
        compose.onAllNodesWithText("None").assertCountEquals(0)

        val viewModel = gameViewModel(records = GameRecordRepository(InMemoryGameResultDao(finishedGame = true)))
        show(viewModel)
        compose.onAllNodesWithText("Finish a game to unlock house rules").assertCountEquals(0)
        compose.onNodeWithText("House Rules").assertExists()
        compose.onNodeWithText("None").assertExists()
        compose.onNodeWithText("Common changes to the classic rules, in any mix").assertExists().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Blockades").assertExists()
        compose.onNodeWithText("Two of your pawns on one square make a blockade: no other player's pawn can pass it or land on it.").assertExists()
        compose.onNode(hasText("Must Capture") and isToggleable()).assertIsOff().performClick()
        compose.waitForIdle()
        assertEquals(setOf(HouseRule.MUST_CAPTURE), viewModel.setupForm.setup.value.houseRules)
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1 enabled").assertExists()
        compose.onNodeWithText("Off the Leaderboard and Statistics, and only its own achievements").assertExists()
    }

    @Test
    fun `the Pawns house rule - off by default - then one to six each but the mode's own number - kept for another mode`() = uiTest(SCREEN) {
        val viewModel = gameViewModel(records = GameRecordRepository(InMemoryGameResultDao(finishedGame = true)))
        show(viewModel)
        compose.onNodeWithText("House Rules").performScrollTo()
        compose.onNode(hasContentDescription("House Rules") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Pawns") and hasText("Each player has this many pawns.") and isToggleable()).assertIsOff().performClick()
        compose.waitForIdle()
        assertEquals(1, viewModel.setupForm.setup.value.playedPawns)
        // Classic's four aren't a choice.
        compose.onAllNodesWithContentDescription("4 pawns each").assertCountEquals(0)
        compose.onNodeWithContentDescription("1 pawn each").assertExists()
        compose.onNodeWithContentDescription("6 pawns each").performClick()
        compose.waitForIdle()
        assertEquals(6, viewModel.setupForm.setup.value.playedPawns)
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1 enabled").assertExists()
        compose.onNodeWithText("Off the Leaderboard and Statistics, and only its own achievements").assertExists()
        // Survival's own number: nothing to change there.
        viewModel.setupForm.setGameMode(GameMode.SURVIVAL)
        assertEquals(null, viewModel.setupForm.setup.value.playedPawns)
        viewModel.setupForm.setGameMode(GameMode.PORTALS)
        assertEquals(6, viewModel.setupForm.setup.value.playedPawns)
        assertTrue(viewModel.startGame { "Player $it" })
        val rules = checkNotNull(viewModel.game.value).state.rules
        assertEquals(6, rules.pawnsPerPlayer)
        assertTrue(rules.hasHouseRules)
    }

    @Test
    fun `a house rule that changes nothing in the mode picked is locked - saying so - and its pick kept for another mode`() = uiTest(SCREEN) {
        val viewModel = gameViewModel(records = GameRecordRepository(InMemoryGameResultDao(finishedGame = true)))
        viewModel.setupForm.setHouseRule(HouseRule.FREE_START, on = true)
        viewModel.setupForm.setHouseRule(HouseRule.BLOCKADES, on = true)
        viewModel.setupForm.setGameMode(GameMode.SURVIVAL)
        assertEquals(setOf(HouseRule.BLOCKADES), viewModel.setupForm.setup.value.playedHouseRules)
        show(viewModel)
        // The one locked isn't counted.
        compose.onNodeWithText("1 enabled").assertExists()
        compose.onNodeWithText("House Rules").performScrollTo()
        compose.onNode(hasContentDescription("House Rules") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Free Start") and hasText("Changes nothing in Survival") and isToggleable()).assertIsOff().assertIsNotEnabled()
        compose.onNode(hasText("Blockades") and isToggleable()).assertIsOn()
        viewModel.setupForm.setGameMode(GameMode.CLASSIC)
        assertEquals(setOf(HouseRule.FREE_START, HouseRule.BLOCKADES), viewModel.setupForm.setup.value.playedHouseRules)
    }

    @Test
    fun `no custom mode can be made - one made before is picked - edited with a name and a house rule - its rules said on the House Rules card - and deleted after asking`() = uiTest(SCREEN) {
        val viewModel = gameViewModel(records = GameRecordRepository(InMemoryGameResultDao(finishedGame = true)))
        show(viewModel)
        compose.onAllNodesWithText("New Custom Mode").assertCountEquals(0)
        compose.onAllNodesWithText("Edit").assertCountEquals(0)

        viewModel.saveCustomMode(CustomMode("custom_1", "House", GameMode.CLASSIC, setOf(HouseRule.FREE_START)))
        compose.waitForIdle()
        compose.onNodeWithText("Edit").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Edit Custom Mode").assertExists()
        // Nothing to save until it has a name and a house rule.
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextReplacement(" ")
        compose.onNodeWithText("Give it a name.").assertExists()
        compose.onNode(hasText("Save") and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextReplacement("Classic")
        compose.onNodeWithText("There's a mode by that name already.").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextReplacement("Cut-Throat")
        compose.onNode(hasText("Free Start") and isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Switch on a house rule: without one, it's just Classic.").assertExists()
        compose.onNode(hasText("Must Capture") and isToggleable()).performScrollTo().performClick()
        compose.onNode(hasText("Blockades") and isToggleable()).performScrollTo().performClick()
        compose.onNode(hasText("Save") and hasClickAction()).assertIsEnabled().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Cut-Throat").assertExists()
        compose.onNodeWithText("Classic, with Blockades and Must Capture").assertExists()
        compose.onNodeWithText("Set by Cut-Throat: Blockades and Must Capture.").assertExists()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete Cut-Throat?").assertExists()
        compose.onNodeWithText("It goes from New Game for good.").assertExists()
        compose.onAllNodes(hasText("Delete") and hasClickAction()).onLast().performClick()
        compose.waitForIdle()
        assertEquals(emptyList(), viewModel.setupForm.setup.value.customModes)
        compose.onAllNodesWithText("Cut-Throat").assertCountEquals(0)
    }
}
