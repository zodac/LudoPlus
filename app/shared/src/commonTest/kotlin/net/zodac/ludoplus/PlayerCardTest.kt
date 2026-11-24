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

/** The player cards over the board: each player's rank as the game stands, and a name too long for its card. */
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

    @Test
    fun `a tap on a name too long for its card shows the whole of it - and a name that fits shows nothing`() = uiTest(TestScreen(360, 800)) {
        show(game(listOf(6, 6, 6, 6), listOf("Alex", "WWWWWWWW", "Ace", "Bo")))
        val long = compose.onAllNodesWithText("WWWWWWWW")
        val before = long.fetchSemanticsNodes().size
        // The whole name is read with the card, so TalkBack is handed no button for it.
        assertEquals(false, SemanticsActions.OnClick in card("WWWWWWWW").fetchSemanticsNode().config)
        card("WWWWWWWW").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(before + 1, long.fetchSemanticsNodes().size)

        show(game(listOf(6, 6, 6, 6), listOf("Alex", "WWWWWWWW", "Ace", "Bo")))
        val short = compose.onAllNodesWithText("Bo").fetchSemanticsNodes().size
        card("Bo").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(short, compose.onAllNodesWithText("Bo").fetchSemanticsNodes().size)
    }
}
