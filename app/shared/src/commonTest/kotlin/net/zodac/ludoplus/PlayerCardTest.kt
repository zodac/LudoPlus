package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.game.GameContent
import net.zodac.ludoplus.ui.game.StartedGame
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** The player cards over the board: each player's rank as the game stands, and what a tap on one shows. */
class PlayerCardTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun game(left: List<Int>, names: List<String>, change: (GameState) -> GameState = { it }): StartedGame {
        val new = GameState.new(GameMode.SURVIVAL, left.size, rules = GameMode.SURVIVAL.rules.forPlayers(left.size))
        val state = new.copy(players = new.players.mapIndexed { i, it -> it.copy(pawns = List(6) { pawn -> if (pawn < left[i]) 0 else PawnPosition.OUT_PROGRESS }) })
        val players = names.mapIndexed { i, name -> PlayerConfig(i + 1, if (i == 0) PlayerType.HUMAN else PlayerType.AI, name) }
        return StartedGame(GameMode.SURVIVAL, players, change(state))
    }

    private fun show(game: StartedGame) = showcase.show {
        CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
            LudoPlusTheme { GameContent(game, onRoll = {}, onMove = {}, onBack = {}, onResults = {}) }
        }
    }

    private fun card(name: String): SemanticsNodeInteraction = compose.onNode(hasText(name) and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))

    private fun spoken(name: String): String? = card(name).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private val names = listOf("Alex", "Lucky", "Ace", "Fluke")

    @Test
    fun `a card says its player's rank as the game stands - a tie as joint - and none while everyone is level`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(6, 6, 6, 6), names))
        assertEquals("6 pawns left, playing now", spoken("Alex"))
        assertEquals("6 pawns left", spoken("Lucky"))

        show(game(listOf(3, 1, 4, 3), names))
        assertEquals("3 pawns left, playing now, currently joint 2nd", spoken("Alex"))
        assertEquals("1 pawn left, currently 4th", spoken("Lucky"))
        assertEquals("4 pawns left, currently 1st", spoken("Ace"))
        assertEquals("3 pawns left, currently joint 2nd", spoken("Fluke"))
    }

    @Test
    fun `a tied rank is drawn with an equals sign - partners' shared one isn't`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(3, 1, 4, 3), names))
        assertEquals(listOf(2, 0, 1, 1), listOf("=2", "2", "1", "4").map(::drawn))
        // It isn't said: "=2" would be read as "equals 2".
        assertEquals(0, compose.onAllNodesWithText("=2").fetchSemanticsNodes().size)

        show(game(listOf(3, 1, 4, 3), names) { it.copy(rules = it.rules.copy(teams = listOf(0, 1, 0, 1))) })
        assertEquals(listOf(0, 0), listOf("=1", "=2").map(::drawn))
    }

    @Test
    fun `partners share a rank without being joint`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(3, 1, 4, 3), names) { it.copy(rules = it.rules.copy(teams = listOf(0, 1, 0, 1))) })
        assertEquals("Team 1, 3 pawns left, playing now, currently 1st", spoken("Alex"))
        assertEquals("Team 2, 1 pawn left, currently 2nd", spoken("Lucky"))
    }

    @Test
    fun `a player out of the game - or still in it at its end - has a place`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(3, 0, 4, 3), names) { it.copy(knockedOut = listOf(1)) })
        assertEquals("0 pawns left, 4th place", spoken("Lucky"))
        assertEquals("4 pawns left, currently 1st", spoken("Ace"))

        show(game(listOf(0, 0, 4, 0), names) { it.copy(knockedOut = listOf(1, 0, 3), finishingOrder = listOf(2, 3, 0, 1), phase = TurnPhase.Finished) })
        assertEquals("4 pawns left, 1st place", spoken("Ace"))
    }

    private fun withCaptures(game: GameState): GameState = game.copy(
        players = game.players.mapIndexed { i, it -> if (i == 1) it.copy(captures = 3, timesCaptured = 1) else if (i == 2) it.copy(timesCaptured = 2) else it },
    )

    private fun drawn(text: String): Int = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `a card says its player's captures and times captured last - neither while there are none of either`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(6, 6, 6, 6), names, ::withCaptures))
        assertEquals("6 pawns left, playing now", spoken("Alex"))
        assertEquals("6 pawns left, 3 captures, 1 captured", spoken("Lucky"))
        assertEquals("6 pawns left, 0 captures, 2 captured", spoken("Ace"))
    }

    @Test
    fun `a tap on a card shows its player's captures and times captured - until a tap on another shows theirs`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(6, 6, 6, 6), names, ::withCaptures))
        assertEquals(listOf(0, 0), listOf("3 captures", "1 captured").map(::drawn))
        card("Lucky").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf(1, 1), listOf("3 captures", "1 captured").map(::drawn))
        // It outlasts a plain tooltip's time.
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertEquals(listOf(1, 1), listOf("3 captures", "1 captured").map(::drawn))

        card("Alex").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(listOf(0, 0), listOf("3 captures", "1 captured").map(::drawn))
        assertEquals(listOf(1, 1), listOf("0 captures", "0 captured").map(::drawn))
    }

    @Test
    fun `a tap on a name too long for its card shows the whole of it - and one that fits is not repeated`() = uiTest(TestScreen(360, 800)) {
        show(game(listOf(6, 6, 6, 6), listOf("Alex", "WWWWWWWW", "Ace", "Bo")))
        val before = drawn("WWWWWWWW")
        // All the tooltip says is read with the card, so TalkBack is handed no button for it.
        assertEquals(false, SemanticsActions.OnClick in card("WWWWWWWW").fetchSemanticsNode().config)
        card("WWWWWWWW").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(before + 1, drawn("WWWWWWWW"))

        show(game(listOf(6, 6, 6, 6), listOf("Alex", "WWWWWWWW", "Ace", "Bo")))
        val short = drawn("Bo")
        card("Bo").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, drawn("0 captures"))
        assertEquals(short, drawn("Bo"))
    }
}
