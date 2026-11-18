package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.game.RollOff
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.TeamMode
import net.zodac.ludoplus.model.withHouseRules
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.game.GameContent
import net.zodac.ludoplus.ui.game.StartedGame
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

private const val Y = -1
private const val H = 56

/** The board and the game screen as a screen reader meets them: every square named, every move an action, word for word. */
class BoardSemanticsTest : UiTest() {

    private val showcase = Showcase { compose }

    private val players = listOf(
        PlayerConfig(1, PlayerType.HUMAN, "Alex"),
        PlayerConfig(2, PlayerType.AI, "Gambit"),
        PlayerConfig(3, PlayerType.HUMAN, "Sam"),
        PlayerConfig(4, PlayerType.AI, "Payout"),
    )

    private fun state(vararg pawns: List<Int>): GameState {
        val new = GameState.new(GameMode.CLASSIC, 4)
        return new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }) })
    }

    private var moved = mutableListOf<Int>()
    private var rolls = 0
    private var backs = 0

    private fun show(state: GameState) {
        val game = StartedGame(state.mode, players, state, RollOff(listOf(mapOf(0 to 6, 1 to 1, 2 to 1, 3 to 1)), 0))
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { GameContent(game, onRoll = { rolls++ }, onMove = { moved += it.pawn }, onBack = { backs++ }, onResults = {}) }
            }
        }
    }

    private fun onClickLabel(label: String) = SemanticsMatcher("onClick labelled $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

    @Test
    fun `each yard - track square - home column square - and home says what is on it`() = uiTest(TestScreen(411, 891)) {
        show(state(listOf(Y, Y, 0, 52), listOf(H, Y, Y, Y), listOf(8, Y, Y, Y), listOf(Y, Y, Y, Y)))
        listOf(
            "Red yard, 2 pawns waiting",
            "Green yard, 3 pawns waiting",
            "Blue yard, 4 pawns waiting",
            "Square 1, Red start, safe, Red pawn 3, 56 squares from home",
            "Square 2, empty",
            "Square 35, safe, Yellow pawn 1, 48 squares from home",
            "Square 14, Green start, safe, empty",
            "Red home column, square 2, Red pawn 4, in the home column, 4 squares from home",
            "Green home column, square 5, empty",
            "Home, Green pawn 1, home",
        ).forEach { compose.onNodeWithContentDescription(it).assertExists() }
        // 4 yards, 52 track squares, 20 home-column squares, and home.
        compose.onAllNodes(SemanticsMatcher("a board square") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.let {
                it.startsWith("Square ") || it.contains(" yard,") || it.contains(" home column, square ") || it.startsWith("Home,")
            } ?: false
        }).assertCountEquals(4 + 52 + 20 + 1)
    }

    @Test
    fun `with house rules - a guarded start is safe for its colour alone - and a blockade is said as one`() = uiTest(TestScreen(411, 891)) {
        val rules = GameMode.CLASSIC.rules.withHouseRules(setOf(HouseRule.GUARDED_STARTS, HouseRule.BLOCKADES))
        val new = GameState.new(GameMode.CLASSIC, 4, rules = rules)
        // Red's two pawns 4 steps on make a blockade on square 5; green's 1 step on is alone on square 15.
        val pawns = listOf(listOf(4, 4, Y, Y), listOf(1, Y, Y, Y))
        show(new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }) }))
        listOf(
            "Square 1, Red start, safe for Red, empty",
            "Square 14, Green start, safe for Green, empty",
            "Square 9, safe, empty",
            "Square 5, Red blockade, Red pawn 1, 52 squares from home, Red pawn 2, 52 squares from home",
            "Square 15, Green pawn 1, 55 squares from home",
        ).forEach { compose.onNodeWithContentDescription(it).assertExists() }
    }

    @Test
    fun `two dice - each named with its face - one picked for the next move - the prompt naming it - and the die left after a move`() = uiTest(TestScreen(411, 891)) {
        val new = GameState.new(GameMode.TWO_DICE, 4)
        val rolled = LudoEngine.applyDice(new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = if (i == 0) listOf(10, Y, Y, Y) else listOf(Y, Y, Y, Y)) }), listOf(3, 5))
        var state = rolled
        val picks = mutableListOf<Move>()
        val game = { StartedGame(GameMode.TWO_DICE, players, state, RollOff(listOf(mapOf(0 to 6, 1 to 1, 2 to 1, 3 to 1)), 0)) }
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { GameContent(game(), onRoll = {}, onMove = { picks += it }, onBack = {}, onResults = {}) }
            }
        }
        compose.onNodeWithContentDescription("First die showing 3, selected").assertExists()
        compose.onNodeWithContentDescription("Second die showing 5").assert(onClickLabel("Use this die")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Second die showing 5, selected").assertExists()
        val prompt = compose.onNodeWithContentDescription("Alex rolled 3 and 5. Pick a pawn to move 5.")
        // TalkBack is offered each die's move; the board, the picked die's.
        assertEquals(
            listOf("Move Red pawn 1 3 squares", "Move Red pawn 1 5 squares"),
            prompt.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label },
        )
        compose.onNodeWithContentDescription("Square 11, Red pawn 1, 46 squares from home").assert(onClickLabel("Move Red pawn 1 5 squares"))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(Move(0, 10, 15, roll = 5)), picks)

        state = LudoEngine.move(rolled, picks.single())
        show(state)
        compose.onNodeWithContentDescription("Alex has 3 left. Pick a pawn to move.").assertExists()
        compose.onNodeWithContentDescription("Second die showing 5, moved").assertExists()
        compose.onNodeWithContentDescription("First die showing 3").assertExists()

        // Before a roll, either die rolls both.
        state = new
        show(state)
        compose.onNodeWithContentDescription("Alex, tap the dice to roll.").assertExists()
        compose.onNodeWithContentDescription("First die").assert(onClickLabel("Roll"))
        compose.onNodeWithContentDescription("Second die").assert(onClickLabel("Roll"))
    }

    @Test
    fun `a movable pawn's square is a button naming the move - and the prompt offers every move too`() = uiTest(TestScreen(411, 891)) {
        // Red rolls a 6: pawn 1 can leave the yard; pawn 3 on its 21st step can capture Gambit's pawn on its 14th, both
        // landing on track square 27 (the 28th from red's start).
        val rolled = LudoEngine.applyRoll(state(listOf(Y, 50, 21, H), listOf(14, Y, Y, Y)), 6)
        show(rolled)
        compose.onNodeWithContentDescription("Red yard, 1 pawn waiting").assert(onClickLabel("Bring Red pawn 1 out of the yard"))
        compose.onNodeWithContentDescription("Square 22, safe, Red pawn 3, 35 squares from home")
            .assert(onClickLabel("Move Red pawn 3 6 squares, capturing Green pawn 1"))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(2), moved)
        compose.onNodeWithContentDescription("Red home column, square 1, empty").assertExists()
        compose.onNodeWithContentDescription("Square 51, Red pawn 2, 6 squares from home")
            .assert(onClickLabel("Move Red pawn 2 6 squares, reaching home"))
        val prompt = compose.onNodeWithContentDescription("Alex rolled 6. Pick a pawn to move.")
        val actions = prompt.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(
            listOf("Bring Red pawn 1 out of the yard", "Move Red pawn 2 6 squares, reaching home", "Move Red pawn 3 6 squares, capturing Green pawn 1"),
            actions,
        )
    }

    @Test
    fun `in a team game each card says its team - and a player home is offered a partner's moves by its colour`() = uiTest(TestScreen(411, 891)) {
        // Alex and Sam (red and yellow) against Gambit and Payout: Alex is home, and rolls 1 for Sam's last pawn.
        val teams = GameMode.CLASSIC.rules.copy(teams = TeamMode.TWO_TEAMS.teamsFor(4))
        val new = GameState.new(GameMode.CLASSIC, 4, rules = teams)
        val pawns = listOf(listOf(H, H, H, H), listOf(Y, Y, Y, Y), listOf(H, H, H, H - 1), listOf(Y, Y, Y, Y))
        show(LudoEngine.applyRoll(new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = pawns[i]) }), 1))
        val prompt = compose.onNodeWithContentDescription("Alex rolled 1. Pick a partner's pawn to move.")
        assertEquals(listOf("Move Yellow pawn 4 1 square, reaching home"), prompt.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label })
        compose.onNode(hasText("Alex").and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Team 1, 4 pawns home, playing now"))).assertExists()
        compose.onNode(hasText("Sam").and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Team 1, 3 pawns home"))).assertExists()
        compose.onNode(hasText("Gambit").and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Team 2, 0 pawns home"))).assertExists()
        // The pawn's own square offers the move too.
        compose.onNode(onClickLabel("Move Yellow pawn 4 1 square, reaching home")).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(3), moved)
    }

    @Test
    fun `the die says what it shows and rolls - the cards say each player's pawns home and whose turn it is`() = uiTest(TestScreen(411, 891)) {
        show(state(listOf(H, Y, Y, Y)))
        compose.onNodeWithContentDescription("Die").assert(onClickLabel("Roll")).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, rolls)
        compose.onNode(hasText("Alex").and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 pawn home, playing now")))
            .assertExists()
        // A computer player's card names it as one: "Gambit, CPU, 0 pawns home".
        compose.onNode(
            hasText("Gambit").and(hasContentDescription("CPU")).and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 pawns home")),
        ).assertExists()

        // After a roll the die names it; on a CPU's turn it can't be rolled.
        show(state().copy(current = 1, lastEvent = GameEvent.NoMove(0, 4)))
        compose.onNodeWithContentDescription("Die showing 4").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithContentDescription("Alex rolled 4 and had no move.", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `what just happened is said - a capture - and the winner once the game is over`() = uiTest(TestScreen(411, 891)) {
        show(state().copy(current = 2, lastEvent = GameEvent.Moved(1, 5, Move(0, 15, 20, listOf(PawnRef(0, 1))), finished = false)))
        compose.onNodeWithText("Gambit captured 1 of Alex's pawns.").assertExists()
        compose.onNodeWithText("Sam, tap the die to roll.").assertExists()
        show(state().copy(phase = TurnPhase.Finished, finishingOrder = listOf(1, 0, 3, 2)))
        compose.onNodeWithText("Gambit wins!").assertExists()
        compose.onNodeWithText("Results").assertExists()
    }
}
