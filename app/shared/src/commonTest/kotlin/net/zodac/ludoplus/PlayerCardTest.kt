package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import net.zodac.ludoplus.ui.theme.color
import kotlin.test.assertTrue
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
    fun `a card doesn't say its player's captures - its popup does`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(6, 6, 6, 6), names, ::withCaptures))
        assertEquals("6 pawns left", spoken("Lucky"))
    }

    @Test
    fun `a tap on a name too long for its card shows the whole of it - and one that fits is not repeated`() = uiTest(TestScreen(360, 800)) {
        // The name is read with the totals, so it's told by the room it takes over them in the popup.
        fun above(name: String): Float {
            show(game(listOf(6, 6, 6, 6), listOf("Alex", "WWWWWWWW", "Ace", "Bo")))
            tap(name)
            return compose.onNodeWithContentDescription("$name, 0 captures, captured by 0").fetchSemanticsNode().positionInRoot.y
        }
        assertTrue(above("WWWWWWWW") > above("Bo") + 10)
    }

    /** Lucky has captured Alex's pawns twice and Ace's once, and been captured by Ace once. */
    private fun withOpponents(game: GameState): GameState = game.copy(
        players = game.players.mapIndexed { i, it ->
            when (i) {
                0 -> it.copy(timesCaptured = 2)
                1 -> it.copy(captures = 3, timesCaptured = 1, capturesOf = mapOf(0 to 2, 2 to 1))
                2 -> it.copy(captures = 1, timesCaptured = 1, capturesOf = mapOf(1 to 1))
                else -> it
            }
        },
    )

    private fun says(text: String): Int = compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().size

    private fun tap(name: String) {
        card(name).performTouchInput { click() }
        compose.waitForIdle()
    }

    @Test
    fun `a tap on a card shows its player's captures and times captured by each opponent - until it's closed`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(6, 6, 6, 6), names, ::withOpponents))
        val popup = listOf("Lucky, 3 captures, captured by 1", "Alex, 2 captures, captured by 0", "Ace, 1 capture, captured by 1", "Fluke, 0 captures, captured by 0")
        assertEquals(listOf(0, 0, 0, 0), popup.map(::says))
        tap("Lucky")
        assertEquals(listOf(1, 1, 1, 1), popup.map(::says))
        assertEquals(0, says("Lucky, 0 captures, captured by 0"))
        assertEquals(listOf(1, 1), listOf("Captures", "Captured by").map(::drawn))
        // It outlasts a plain tooltip's time.
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertEquals(listOf(1, 1, 1, 1), popup.map(::says))
        // Each says its place among them, as a large font can leave some out of view.
        val row = compose.onNodeWithContentDescription("Ace, 1 capture, captured by 1").fetchSemanticsNode().config
        assertEquals(1, row[SemanticsProperties.CollectionItemInfo].rowIndex)
    }

    @Test
    fun `TalkBack's activation shows a card's captures by opponent - and there's no long press or other action`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(6, 6, 6, 6), names, ::withOpponents))
        val config = card("Ace").fetchSemanticsNode().config
        assertEquals(Role.Button, config[SemanticsProperties.Role])
        assertEquals("Show captures and rolls", config[SemanticsActions.OnClick].label)
        assertTrue(SemanticsActions.OnLongClick !in config && SemanticsActions.CustomActions !in config)
        card("Ace").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(listOf(1, 1, 1), listOf("Ace, 1 capture, captured by 1", "Alex, 0 captures, captured by 0", "Lucky, 1 capture, captured by 1").map(::says))
    }

    @Test
    fun `captures by opponent leave out a partner - unless partners capture each other`() = uiTest(TestScreen(411, 891)) {
        fun team(partnerCaptures: Boolean) =
            game(listOf(6, 6, 6, 6), names) { it.copy(rules = it.rules.copy(teams = listOf(0, 1, 0, 1), partnerCaptures = partnerCaptures)) }
        show(team(partnerCaptures = false))
        tap("Alex")
        assertEquals(listOf(1, 0, 1), listOf("Lucky", "Ace", "Fluke").map { says("$it, 0 captures, captured by 0") })
        show(team(partnerCaptures = true))
        tap("Alex")
        assertEquals(listOf(1, 1, 1), listOf("Lucky", "Ace", "Fluke").map { says("$it, 0 captures, captured by 0") })
    }

    @Test
    fun `a game saved before captures were kept by opponent shows its totals all the same`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(6, 6, 6, 6), names, ::withCaptures))
        tap("Lucky")
        assertEquals(listOf(1, 1), listOf("Lucky, 3 captures, captured by 1", "Alex, 0 captures, captured by 0").map(::says))
    }

    /** Lucky has rolled every face but a 4. */
    private fun withRolls(game: GameState): GameState = withOpponents(game).let { state ->
        state.copy(players = state.players.mapIndexed { i, it -> if (i == 1) it.copy(rolls = mapOf(1 to 7, 2 to 1, 3 to 9, 5 to 5, 6 to 11)) else it })
    }

    private val luckysRolls = listOf(
        "Rolled a 1, 7 times", "Rolled a 2, 1 time", "Rolled a 3, 9 times", "Rolled a 4, 0 times", "Rolled a 5, 5 times", "Rolled a 6, 11 times",
    )

    @Test
    fun `a tap on a card shows how many times its player has rolled each face - and in all`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(6, 6, 6, 6), names, ::withRolls))
        assertEquals(0, says("33 rolls"))
        tap("Lucky")
        assertEquals(1, says("33 rolls"))
        assertEquals(1, drawn("Rolls"))
        luckysRolls.forEachIndexed { index, spoken ->
            assertEquals(index, compose.onNodeWithContentDescription(spoken).fetchSemanticsNode().config[SemanticsProperties.CollectionItemInfo].rowIndex, spoken)
        }
    }

    @Test
    fun `rolls are in two columns - each a group read down before the next`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(6, 6, 6, 6), names, ::withRolls))
        tap("Lucky")
        val at = luckysRolls.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode() }
        val groups = at.map { node -> generateSequence(node.parent) { it.parent }.first { it.config.getOrNull(SemanticsProperties.IsTraversalGroup) == true }.id }
        assertEquals(listOf(3, 3), groups.groupBy { it }.values.map { it.size })
        assertEquals(listOf(1, 1), listOf(groups.take(3), groups.drop(3)).map { it.distinct().size })
        assertTrue(at[0].positionInRoot.y == at[3].positionInRoot.y && at[0].positionInRoot.x < at[3].positionInRoot.x)
    }

    @Test
    fun `a player who hasn't rolled has none of each face`() = uiTest(TestScreen(411, 891)) {
        show(game(listOf(6, 6, 6, 6), names, ::withRolls))
        tap("Ace")
        assertEquals(List(7) { 1 }, (listOf("0 rolls") + (1..6).map { "Rolled a $it, 0 times" }).map(::says))
    }

    @Test
    fun `captures by opponent are whole on the smallest phone - and under a name its card cuts`() = uiTest(TestScreen(320, 640)) {
        show(game(listOf(6, 6, 6, 6), listOf("Alex", "WWWWWWWW", "Ace", "Bo"), ::withRolls))
        tap("WWWWWWWW")
        // The name over them is read with the totals.
        assertEquals(1, says("WWWWWWWW, 3 captures, captured by 1"))
        compose.onNodeWithContentDescription("Bo, 0 captures, captured by 0").assertIsDisplayed()
        // And so are the rolls under them.
        for (spoken in luckysRolls.takeLast(1) + "33 rolls") compose.onNodeWithContentDescription(spoken).assertIsDisplayed()
    }

    @Test
    fun `an opened card is outlined in dashes - unlike the current player's solid outline`() = uiTest(TestScreen(411, 891, density = 2f)) {
        // How many of a card's pixels are in its player's colour.
        fun coloured(name: String, colour: Color): Int = card(name).captureToImage().toPixelMap().let { pixels ->
            (0..<pixels.width).sumOf { x -> (0..<pixels.height).count { y -> pixels[x, y] == colour } }
        }
        val game = game(listOf(6, 6, 6, 6), names)
        val lucky = game.players[1].colour.color
        show(game)
        val closed = coloured("Lucky", lucky)
        val current = coloured("Alex", game.players[0].colour.color)
        // Not by touch: the press's ripple fades on a clock of its own, over the pixels counted.
        card("Lucky").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        val opened = coloured("Lucky", lucky)
        assertTrue(opened > closed + 100, "$closed closed, $opened opened")
        // Dashes: far less of an outline than a whole one.
        assertTrue(opened - closed < current, "$opened opened, $current current")
        // The current player's card has dashes in place of its solid outline, until it's closed.
        card("Alex").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(closed, coloured("Lucky", lucky))
        val dashed = coloured("Alex", game.players[0].colour.color)
        assertTrue(dashed < current - 100, "$current solid, $dashed opened")
        card("Lucky").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals(current, coloured("Alex", game.players[0].colour.color))
    }
}
