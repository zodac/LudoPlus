package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LogEntry
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.game.PowerUpEffect
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.model.TeamMode
import net.zodac.ludoplus.model.withHouseRules
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.game.GameContent
import net.zodac.ludoplus.ui.game.StartedGame
import net.zodac.ludoplus.ui.game.withNamesInColour
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import net.zodac.ludoplus.ui.theme.color

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

    private fun show(state: GameState, log: List<LogEntry> = emptyList(), actionShown: Boolean = true, reviewing: Boolean = false) {
        val game = StartedGame(state.mode, players, state, first = 0, log = log)
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { GameContent(game, onRoll = { rolls++ }, onMove = { moved += it.pawn }, onBack = { backs++ }, onResults = {}, actionShown = actionShown, reviewing = reviewing) }
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
            "Tile 1, Red start, safe, Red pawn 3, 56 tiles from home",
            "Tile 2, empty",
            "Tile 35, safe, Yellow pawn 1, 48 tiles from home",
            "Tile 14, Green start, safe, empty",
            "Red home column, tile 2, Red pawn 4, in the home column, 4 tiles from home",
            "Green home column, tile 5, empty",
            "Home, Green pawn 1, home",
        ).forEach { compose.onNodeWithContentDescription(it).assertExists() }
        // 4 yards, 52 track squares, 20 home-column squares, and home.
        compose.onAllNodes(SemanticsMatcher("a board tile") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()?.let {
                it.startsWith("Tile ") || it.contains(" yard,") || it.contains(" home column, tile ") || it.startsWith("Home,")
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
            "Tile 1, Red start, safe for Red, empty",
            "Tile 14, Green start, safe for Green, empty",
            "Tile 9, safe, empty",
            "Tile 5, Red blockade, Red pawn 1, 52 tiles from home, Red pawn 2, 52 tiles from home",
            "Tile 15, Green pawn 1, 55 tiles from home",
        ).forEach { compose.onNodeWithContentDescription(it).assertExists() }
    }

    @Test
    fun `portals - each says where it leads - and a move through one says so`() = uiTest(TestScreen(411, 891)) {
        val new = GameState.new(GameMode.PORTALS, 4)
        val pawns = listOf(listOf(1, 12, Y, Y), listOf(49, Y, Y, Y))
        show(LudoEngine.applyRoll(new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }) }), 2))
        listOf(
            "Tile 4, portal to tile 11, empty",
            "Tile 11, portal to tile 4, Green pawn 1, 7 tiles from home",
            "Tile 7, portal to tile 23, empty",
            "Tile 23, portal to tile 7, empty",
        ).forEach { compose.onNodeWithContentDescription(it).assertExists() }
        compose.onNodeWithContentDescription("Tile 2, Red pawn 1, 55 tiles from home")
            .assert(onClickLabel("Move Red pawn 1 2 tiles, through a portal to tile 11, capturing Green pawn 1"))
    }

    @Test
    fun `the log says a move went through a portal`() = uiTest(TestScreen(411, 891)) {
        val state = GameState.new(GameMode.PORTALS, 4)
        show(state, log = listOf(LogEntry(GameEvent.Moved(0, 2, Move(0, 8, 3, via = 10), finished = false))))
        compose.onNodeWithContentDescription("Alex's Pawn 1 moved 2 tiles, then through a portal.").assertExists()
    }

    @Test
    fun `Survival - the yards count the pawns lost - the cards those left - there's no home - and safety goes with sudden death`() = uiTest(TestScreen(411, 891)) {
        val new = GameState.new(GameMode.SURVIVAL, 4)
        val x = -2
        val pawns = listOf(listOf(0, 0, 9, x, x, x), listOf(x, x, x, x, x, x))
        val state = new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = pawns.getOrElse(i) { List(6) { 0 } }) }, knockedOut = listOf(1))
        show(state)
        listOf(
            "Red yard, 3 of 6 pawns lost",
            "Green yard, 6 of 6 pawns lost",
            "Yellow yard, 0 of 6 pawns lost",
            "Tile 1, Red start, safe, Red pawn 1, Red pawn 2",
            "Tile 10, Red pawn 3",
            "Tile 9, safe, empty",
        ).forEach { compose.onNodeWithContentDescription(it).assertExists() }
        compose.onNode(hasContentDescription("home", substring = true, ignoreCase = true)).assertDoesNotExist()
        compose.onNode(hasText("Alex") and stateSays("3 pawns left, playing now, currently 3rd")).assertExists()
        compose.onNode(hasText("Gambit") and stateSays("0 pawns left, 4th place")).assertExists()
        compose.onNodeWithText("Overtime in 50 rounds: start tiles unsafe.").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))

        // The last three rounds are counted down aloud, and the stage said as it comes.
        show(state.copy(roundsPlayed = 49))
        compose.onNodeWithText("Overtime in 1 round: start tiles unsafe.").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        show(state.copy(roundsPlayed = 50))
        compose.onNodeWithText("Overtime! Only stars are safe.").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithContentDescription("Tile 1, Red start, Red pawn 1, Red pawn 2").assertExists()
        compose.onNodeWithContentDescription("Tile 9, safe, empty").assertExists()
        show(state.copy(roundsPlayed = 60))
        compose.onNodeWithText("Sudden Death in 15 rounds: stars unsafe.").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        show(state.copy(roundsPlayed = 75))
        compose.onNodeWithText("Sudden Death! Nowhere is safe.").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithContentDescription("Tile 9, empty").assertExists()
        show(state.copy(roundsPlayed = 76, suddenDeathTurns = 6))
        compose.onNodeWithText("Cataclysm in 4 turns: tiles vanish.").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        show(state.copy(roundsPlayed = 76, suddenDeathTurns = 9))
        compose.onNodeWithText("Cataclysm in 1 turn: tiles vanish.").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun `the Cataclysm is counted down in turns, heard as it comes - and the squares it takes are no longer there`() = uiTest(TestScreen(411, 891)) {
        val new = GameState.new(GameMode.SURVIVAL, 4)
        val x = -2
        val pawns = listOf(listOf(8, 20, x, x, x, x), listOf(0, x, x, x, x, x))
        val state = new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = pawns.getOrElse(i) { listOf(5 + i, x, x, x, x, x) }) }, roundsPlayed = 100, suddenDeathTurns = 10)
        show(state)
        compose.onNodeWithText("Cataclysm! Tiles vanish in 5 turns.").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithContentDescription("Tile 11, empty").assertExists()
        show(state.copy(suddenDeathTurns = 14))
        compose.onNodeWithText("Cataclysm! Tiles vanish in 1 turn.").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))

        // Over squares 10 and 11, a roll of 4 reaches square 15.
        show(LudoEngine.applyRoll(state.copy(lostSquares = setOf(9, 10)), 4))
        compose.onNodeWithContentDescription("Tile 11, empty").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tile 9, Red pawn 1").assert(onClickLabel("Move Red pawn 1 4 tiles"))
        val log = listOf(LogEntry(GameEvent.Moved(0, 4, Move(0, 8, 14, skipped = listOf(9, 10)), finished = false), vanished = 2))
        show(state, log)
        compose.onNodeWithContentDescription("Alex's Pawn 1 moved 4 tiles. 2 tiles vanished.", useUnmergedTree = true).assertExists()

        show(state.copy(lostSquares = (0..<52).filter { it !in setOf(8, 20, 13, 33, 47, 1, 2) }.toSet()))
        compose.onNodeWithText("Cataclysm! No more tiles will vanish.").assertExists()
    }

    private fun stateSays(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun `Chaos - a square says its power-up - a pawn its shield - a move what it takes or bounces off - and a yard its doubled rolls and banked Call Ups`() = uiTest(TestScreen(411, 891)) {
        val new = GameState.new(GameMode.CHAOS, 4)
        val pawns = listOf(listOf(1, 20, 49, Y), listOf(9, Y, Y, Y))
        val state = new.copy(
            players = new.players.mapIndexed { i, p ->
                p.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }, shielded = if (i == 1) setOf(0) else emptySet(), doubledRolls = if (i == 2) 1 else 0, callUps = if (i > 1) i - 1 else 0)
            },
            powerUps = mapOf(3 to PowerUp.ROLL_AGAIN, 40 to PowerUp.SHIELD),
        )
        show(LudoEngine.applyRoll(state, 2))
        listOf(
            "Tile 4, Roll Again power-up, empty",
            "Tile 41, Shield power-up, empty",
            "Tile 23, Green pawn 1, 47 tiles from home, shielded",
            "Yellow yard, 4 pawns waiting, 1 doubled roll left, 1 Call Up banked",
            "Blue yard, 4 pawns waiting, 2 Call Ups banked",
            "Green yard, 3 pawns waiting",
        ).forEach { compose.onNodeWithContentDescription(it).assertExists() }
        compose.onNodeWithContentDescription("Tile 2, Red pawn 1, 55 tiles from home").assert(onClickLabel("Move Red pawn 1 2 tiles, taking Roll Again"))
        compose.onNodeWithContentDescription("Tile 21, Red pawn 2, 36 tiles from home").assert(onClickLabel("Move Red pawn 2 2 tiles, bouncing off Green pawn 1's shield"))
        compose.onNode(hasText("Sam") and stateSays("0 pawns home, currently joint 3rd")).assertExists()
    }

    @Test
    fun `Colour Rush - a square says whose it is - a move that it claims one - and a card how many`() = uiTest(TestScreen(411, 891)) {
        val new = GameState.new(GameMode.COLOUR_RUSH, 4)
        val pawns = listOf(listOf(2, 20, 3, 31), listOf(44, Y, Y, Y))
        val state = new.copy(
            players = new.players.mapIndexed { i, p -> p.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }) },
            claimed = mapOf(5 to 1, 23 to 2, 7 to 0, 8 to 3, 9 to 0),
        )
        show(LudoEngine.applyRoll(state, 3))
        listOf(
            "Tile 1, Red start, safe, empty",
            "Tile 8, Red's tile, empty",
            "Tile 9, safe, Blue's tile, empty",
            "Tile 24, Yellow's tile, empty",
        ).forEach { compose.onNodeWithContentDescription(it).assertExists() }
        // A capture takes green's; yellow's, with nobody on it, stays; and nobody's is claimed.
        compose.onNode(onClickLabel("Move Red pawn 1 3 tiles, capturing Green pawn 1, claiming the tile")).assertExists()
        compose.onNode(onClickLabel("Move Red pawn 2 3 tiles")).assertExists()
        compose.onNode(onClickLabel("Move Red pawn 3 3 tiles, claiming the tile")).assertExists()
        // A star nobody has (34).
        compose.onNode(onClickLabel("Move Red pawn 4 3 tiles, claiming the tile")).assertExists()
        compose.onNode(hasText("Alex") and stateSays("0 pawns home, 2 tiles, playing now, currently 1st")).assertExists()
        compose.onNode(hasText("Gambit") and stateSays("0 pawns home, 1 tile, currently joint 2nd")).assertExists()
    }

    @Test
    fun `a Swap is made once its pawn has landed - with the pawn picked - or nobody's`() = uiTest(TestScreen(411, 891)) {
        val new = GameState.new(GameMode.CHAOS, 4)
        val pawns = listOf(listOf(1, 40, Y, Y), listOf(9, Y, Y, Y), listOf(Y, 35, Y, Y))
        val state = new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }) }, powerUps = mapOf(3 to PowerUp.SWAP))
        val made = mutableListOf<Move>()
        fun show(shown: GameState, settled: Boolean = true) = showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { GameContent(StartedGame(GameMode.CHAOS, players, shown, first = 0), onRoll = {}, onMove = { made += it }, onBack = {}, onResults = {}, settled = settled) }
            }
        }
        val rolled = LudoEngine.applyRoll(state, 2)
        show(rolled)
        // A pawn that doesn't land on it moves at once.
        compose.onNode(onClickLabel("Move Red pawn 2 2 tiles")).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(Move(1, 40, 42)), made)
        made.clear()
        // Nor is one that does asked about first: it lands.
        compose.onNode(onClickLabel("Move Red pawn 1 2 tiles, taking Swap")).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(Move(0, 1, 3)), made)
        made.clear()
        val landed = LudoEngine.move(rolled, 0)
        // Nothing is asked till it has.
        show(landed, settled = false)
        compose.onNodeWithContentDescription("Alex rolled 2.").assertExists()
        compose.onNodeWithText("Don't swap").assertDoesNotExist()
        compose.onNode(onClickLabel("Swap places with Green pawn 1")).assertDoesNotExist()
        show(landed)
        compose.onNodeWithContentDescription("Tile 4, Red pawn 1, 53 tiles from home").assertExists()
        val prompt = compose.onNodeWithContentDescription("Alex, pick a pawn to swap with.").fetchSemanticsNode()
        assertEquals(listOf("Swap places with Green pawn 1", "Swap places with Yellow pawn 2"), prompt.config[SemanticsActions.CustomActions].map { it.label })
        assertEquals(LiveRegionMode.Polite, prompt.config[SemanticsProperties.LiveRegion])
        compose.onNodeWithContentDescription("Tile 23, Green pawn 1, 47 tiles from home").assert(onClickLabel("Swap places with Green pawn 1")).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(Move(0, 3, 3, swap = PawnRef(1, 0))), made)
        made.clear()
        compose.onNodeWithText("Don't swap").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(Move(0, 3, 3, swap = PawnRef(0, 0))), made)
    }

    @Test
    fun `the log says what a power-up did - after the move that took it`() = uiTest(TestScreen(411, 891)) {
        fun moved(effect: PowerUpEffect?, move: Move = Move(0, 1, 3)) = LogEntry(GameEvent.Moved(0, 2, move, finished = false, effect = effect))
        fun recalled(vararg pawns: PawnRef) = LogEntry(GameEvent.Moved(0, 2, Move(0, 1, 3, pawns.toList()), finished = false, recalled = pawns.toList()))
        val state = GameState.new(GameMode.CHAOS, 4)
        for ((entry, line) in listOf(
            moved(PowerUpEffect(PowerUp.DOUBLE)) to "Alex's Pawn 1 moved 2 tiles. Alex's next 3 rolls count double.",
            moved(PowerUpEffect(PowerUp.SHIELD)) to "Alex's Pawn 1 moved 2 tiles. Alex took Shield.",
            moved(PowerUpEffect(PowerUp.CALL_UP, calledUp = 2)) to "Alex's Pawn 1 moved 2 tiles. Alex called up Pawn 3.",
            moved(PowerUpEffect(PowerUp.CALL_UP, banked = true)) to "Alex's Pawn 1 moved 2 tiles. Alex banked a Call Up.",
            recalled(PawnRef(1, 0)) to "Alex captured Gambit's Pawn 1 with Pawn 1. Gambit's Call Up brought Pawn 1 straight back out.",
            recalled(PawnRef(1, 0), PawnRef(1, 3)) to "Alex captured Gambit's Pawn 1 and Pawn 4 with Pawn 1. Gambit's Call Ups brought Pawn 1 and Pawn 4 straight back out.",
            moved(PowerUpEffect(PowerUp.CALL_UP)) to "Alex's Pawn 1 moved 2 tiles. Alex took Call Up.",
            moved(PowerUpEffect(PowerUp.SWAP)) to "Alex's Pawn 1 moved 2 tiles. Alex took Swap.",
            moved(PowerUpEffect(PowerUp.SWAP, movedTo = 14, swapped = PawnRef(2, 0)), Move(0, 3, 3, swap = PawnRef(2, 0))) to "Alex swapped Pawn 1 with Sam's Pawn 1.",
            // As 1.1.0's games logged it: the landing and its swap in one.
            moved(PowerUpEffect(PowerUp.SWAP, movedTo = 14, swapped = PawnRef(2, 0)), Move(0, 1, 3, swap = PawnRef(2, 0))) to "Alex's Pawn 1 moved 2 tiles. Alex swapped Pawn 1 with Sam's Pawn 1.",
            moved(PowerUpEffect(PowerUp.ROLL_AGAIN), Move(0, 1, 3, listOf(PawnRef(1, 0)))) to "Alex captured Gambit's Pawn 1 with Pawn 1. Alex took Roll Again.",
            moved(null, Move(0, 1, 2, bouncedFrom = 3, brokenShields = listOf(PawnRef(1, 0)))) to "Alex's Pawn 1 moved 2 tiles and bounced off Gambit's shield.",
        )) {
            show(state, log = listOf(entry))
            compose.onNodeWithContentDescription(line).assertExists()
        }
    }

    @Test
    fun `Haunted House - a square says its ghost - and the log who one caught as a turn ended`() = uiTest(TestScreen(411, 891)) {
        val new = GameState.new(GameMode.HAUNTED_HOUSE, 4).copy(ghosts = listOf(1, 40))
        val state = new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = if (i == 0) listOf(1, Y, Y, Y) else listOf(Y, Y, Y, Y)) })
        show(
            state,
            log = listOf(
                LogEntry(GameEvent.NoMove(0, 3), haunted = listOf(PawnRef(1, 0), PawnRef(1, 1))),
                LogEntry(GameEvent.Moved(0, 2, Move(0, 1, 3), finished = false), haunted = listOf(PawnRef(0, 0), PawnRef(2, 1))),
            ),
        )
        compose.onNodeWithContentDescription("Tile 2, ghost, Red pawn 1, 55 tiles from home").assertExists()
        compose.onNodeWithContentDescription("Tile 41, ghost, empty").assertExists()
        compose.onNodeWithContentDescription("Alex rolled 3 and had no move. A ghost caught Gambit's Pawn 1 and Pawn 2!").assertExists()
        compose.onNodeWithContentDescription("Alex's Pawn 1 moved 2 tiles. A ghost caught Alex's Pawn 1 and Sam's Pawn 2!").assertExists()
    }

    @Test
    fun `the log names every pawn by its number - a partner's and those of several players by their names too`() = uiTest(TestScreen(411, 891)) {
        val rules = GameMode.CLASSIC.rules.copy(teams = listOf(0, 1, 0, 1))
        val state = GameState.new(GameMode.CLASSIC, 4, rules = rules)
        for ((move, line) in listOf(
            Move(2, 0, 4, owner = 2) to "Sam's Pawn 3 moved 4 tiles.",
            Move(3, Y, 0, owner = 2) to "Alex brought Sam's Pawn 4 out.",
            Move(1, H - 4, H, owner = 2) to "Alex brought Sam's Pawn 2 home.",
            Move(2, 0, 4, listOf(PawnRef(1, 0), PawnRef(1, 3), PawnRef(3, 1)), owner = 2) to
                "Alex captured Gambit's Pawn 1, Pawn 4, and Payout's Pawn 2 with Sam's Pawn 3.",
        )) {
            show(state, log = listOf(LogEntry(GameEvent.Moved(0, 4, move, finished = false))))
            val node = compose.onNodeWithContentDescription(line).fetchSemanticsNode()
            // The partner's name is in their colour, as the mover's is where it's there.
            val text = node.config[SemanticsProperties.Text][1]
            assertTrue(listOf("Alex" to 0, "Sam" to 2).filter { it.first in line }.all { (name, seat) -> text.spanStyles.any { text.text.substring(it.start, it.end) == name && it.item.color == PlayerColour.defaultFor(seat).color } }, line)
        }
    }

    @Test
    fun `the log says who was knocked out`() = uiTest(TestScreen(411, 891)) {
        val state = GameState.new(GameMode.SURVIVAL, 4)
        val move = Move(0, 1, 3, listOf(PawnRef(1, 0), PawnRef(2, 0)))
        show(
            state,
            log = listOf(
                LogEntry(GameEvent.Moved(0, 2, move.copy(captures = listOf(PawnRef(1, 0))), finished = false, knockedOut = listOf(1))),
                LogEntry(GameEvent.Moved(0, 2, move, finished = false, knockedOut = listOf(1, 2))),
            ),
        )
        compose.onNodeWithContentDescription("Alex knocked Gambit out of the game with Pawn 1.").assertExists()
        compose.onNodeWithContentDescription("Alex knocked Gambit and Sam out of the game with Pawn 1.").assertExists()
    }

    @Test
    fun `two dice - each named with its face - one picked for the next move - the prompt naming it - and the die left after a move`() = uiTest(TestScreen(411, 891)) {
        val new = GameState.new(GameMode.TWO_DICE, 4)
        val rolled = LudoEngine.applyDice(new.copy(players = new.players.mapIndexed { i, p -> p.copy(pawns = if (i == 0) listOf(10, Y, Y, Y) else listOf(Y, Y, Y, Y)) }), listOf(3, 5))
        var state = rolled
        val picks = mutableListOf<Move>()
        val game = { StartedGame(GameMode.TWO_DICE, players, state, first = 0) }
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
            listOf("Move Red pawn 1 3 tiles", "Move Red pawn 1 5 tiles"),
            prompt.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label },
        )
        compose.onNodeWithContentDescription("Tile 11, Red pawn 1, 46 tiles from home").assert(onClickLabel("Move Red pawn 1 5 tiles"))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(Move(0, 10, 15, roll = 5)), picks)

        state = LudoEngine.move(rolled, picks.single())
        show(state)
        // One pawn and one die left: the move makes itself, so none is asked for.
        compose.onNodeWithContentDescription("Alex has 3 left.").assertExists()
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
    fun `a move with no choice makes itself - no pick is asked for - and no pawn offers it - but one out of the yard is the player's`() = uiTest(TestScreen(411, 891)) {
        show(LudoEngine.applyRoll(state(listOf(10, Y, Y, Y)), 3))
        val prompt = compose.onNodeWithContentDescription("Alex rolled 3.")
        assertEquals(null, prompt.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions))
        compose.onNodeWithContentDescription("Tile 11, Red pawn 1, 46 tiles from home").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))

        // Every pawn in the yard: the 6 that brings one out is still the player's to move.
        show(LudoEngine.applyRoll(state(), 6))
        compose.onNodeWithContentDescription("Alex rolled 6. Pick a pawn to move.").assertExists()
        compose.onNodeWithContentDescription("Red yard, 4 pawns waiting").assert(onClickLabel("Bring Red pawn 1 out of the yard"))
    }

    @Test
    fun `a pawn out free - the prompt says why - it offers no pick - and the log says it came out free`() = uiTest(TestScreen(411, 891)) {
        // Gambit (green) has started 5 turns since Alex's pawn, the first, came out, and has none out.
        val start = state(listOf(10, Y, Y, Y))
        val due = start.copy(current = 1, pityFrom = listOf(0, 0, 0, 0), players = start.players.mapIndexed { i, p -> if (i == 1) p.copy(turns = 5) else p })
        val waiting = due.copy(phase = TurnPhase.AwaitingMove(roll = 0, moves = LudoEngine.pityMoves(due), dice = emptyList(), rolled = emptyList()))
        show(waiting)
        val prompt = compose.onNodeWithContentDescription("Gambit still has no pawn out after 5 turns, so one comes out free.")
        assertEquals(null, prompt.fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions))

        val free = Move(0, Y, 0, free = true)
        show(
            waiting,
            log = listOf(
                LogEntry(GameEvent.Moved(1, 0, free, finished = false)),
                LogEntry(GameEvent.Moved(1, 0, free.copy(pawn = 1, captures = listOf(PawnRef(0, 0))), finished = false)),
            ),
        )
        compose.onNodeWithContentDescription("Gambit brought Pawn 1 out free, with none out after 5 turns.").assertExists()
        compose.onNodeWithContentDescription("Gambit brought Pawn 2 out free, with none out after 5 turns. Gambit captured Alex's Pawn 1 with Pawn 2.").assertExists()
    }

    @Test
    fun `a movable pawn's square is a button naming the move - and the prompt offers every move too`() = uiTest(TestScreen(411, 891)) {
        // Red rolls a 6: pawn 1 can leave the yard; pawn 3 on its 21st step can capture Gambit's pawn on its 14th, both
        // landing on track square 27 (the 28th from red's start).
        val rolled = LudoEngine.applyRoll(state(listOf(Y, 50, 21, H), listOf(14, Y, Y, Y)), 6)
        show(rolled)
        compose.onNodeWithContentDescription("Red yard, 1 pawn waiting").assert(onClickLabel("Bring Red pawn 1 out of the yard"))
        compose.onNodeWithContentDescription("Tile 22, safe, Red pawn 3, 35 tiles from home")
            .assert(onClickLabel("Move Red pawn 3 6 tiles, capturing Green pawn 1"))
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(2), moved)
        compose.onNodeWithContentDescription("Red home column, tile 1, empty").assertExists()
        compose.onNodeWithContentDescription("Tile 51, Red pawn 2, 6 tiles from home")
            .assert(onClickLabel("Move Red pawn 2 6 tiles, reaching home"))
        val prompt = compose.onNodeWithContentDescription("Alex rolled 6. Pick a pawn to move.")
        val actions = prompt.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(
            listOf("Bring Red pawn 1 out of the yard", "Move Red pawn 2 6 tiles, reaching home", "Move Red pawn 3 6 tiles, capturing Green pawn 1"),
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
        assertEquals(listOf("Move Yellow pawn 4 1 tile, reaching home"), prompt.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label })
        compose.onNode(hasText("Alex").and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Team 1, 4 pawns home, playing now, currently 1st"))).assertExists()
        compose.onNode(hasText("Sam").and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Team 1, 3 pawns home, currently 1st"))).assertExists()
        compose.onNode(hasText("Gambit").and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Team 2, 0 pawns home, currently 2nd"))).assertExists()
        // The pawn's own square offers the move too.
        compose.onNode(onClickLabel("Move Yellow pawn 4 1 tile, reaching home")).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(3), moved)
    }

    @Test
    fun `the die says what it shows and rolls - the cards say each player's pawns home and whose turn it is`() = uiTest(TestScreen(411, 891)) {
        show(state(listOf(H, Y, Y, Y)))
        compose.onNodeWithContentDescription("Die").assert(onClickLabel("Roll")).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(1, rolls)
        compose.onNode(hasText("Alex").and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "1 pawn home, playing now, currently 1st")))
            .assertExists()
        // A computer player's card names it as one: "Gambit, CPU, 0 pawns home, currently joint 2nd".
        compose.onNode(
            hasText("Gambit").and(hasContentDescription("CPU")).and(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 pawns home, currently joint 2nd")),
        ).assertExists()

        // After a roll the die names it; on a CPU's turn it can't be rolled.
        show(state().copy(current = 1, lastEvent = GameEvent.NoMove(0, 4)))
        compose.onNodeWithContentDescription("Die showing 4").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        compose.onNodeWithContentDescription("Alex rolled 4 and had no move.", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the winner is said once the game is over and reviewed from the results - not while its last move plays out`() = uiTest(TestScreen(411, 891)) {
        val last = GameEvent.Moved(1, 3, Move(0, 53, 56), finished = true)
        val over = state().copy(phase = TurnPhase.Finished, finishingOrder = listOf(1, 0, 3, 2), lastEvent = last)
        show(over)
        compose.onNodeWithText("Gambit wins!").assertDoesNotExist()
        compose.onNodeWithText("Results").assertDoesNotExist()
        compose.onNodeWithContentDescription("Gambit is moving.").assertExists()
        show(over, reviewing = true)
        compose.onNodeWithText("Gambit wins!").assertExists()
        compose.onNodeWithText("Results").assertExists()
    }

    @Test
    fun `the prompt by the die shows the current player's name in their colour - and is said as before`() = uiTest(TestScreen(411, 891)) {
        show(state().copy(current = 2))
        val prompt = compose.onNodeWithText("Sam, tap the die to roll.").fetchSemanticsNode()
        val text = prompt.config[SemanticsProperties.Text].single()
        val span = text.spanStyles.single()
        assertEquals("Sam", text.text.substring(span.start, span.end))
        assertEquals(PlayerColour.defaultFor(2).color, span.item.color)
        assertEquals(FontWeight.Bold, span.item.fontWeight)
        assertEquals("Sam, tap the die to roll.", prompt.config[SemanticsProperties.ContentDescription].single())
    }

    /** Which of Alex and Sam the cards say is playing now. */
    private fun ComposeUiTest.playing() = listOf("Alex", "Sam").filter { name ->
        onAllNodes(hasText(name) and SemanticsMatcher("playing now") { "playing now" in it.config.getOrNull(SemanticsProperties.StateDescription).orEmpty() })
            .fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `a move keeps the turn its player's until it has been shown, though the log tells of it at once - and one that rolls again keeps its prompt`() =
        uiTest(TestScreen(411, 891)) {
            val event = GameEvent.Moved(0, 4, Move(0, 0, 4), finished = false)
            val passed = state(listOf(4, Y, Y, Y)).copy(current = 2, lastEvent = event)
            show(passed, listOf(LogEntry(event)), actionShown = false)
            val waiting = compose.onNodeWithContentDescription("Alex is moving.").fetchSemanticsNode()
            assertEquals(PlayerColour.defaultFor(0).color, waiting.config[SemanticsProperties.Text].single().spanStyles.single().item.color)
            // The log's line is what's heard of the move.
            assertNull(waiting.config.getOrNull(SemanticsProperties.LiveRegion))
            val line = compose.onNode(hasContentDescription("Alex's Pawn 1 moved 4 tiles.")).fetchSemanticsNode()
            assertEquals(LiveRegionMode.Polite, line.config[SemanticsProperties.LiveRegion])
            compose.onNodeWithContentDescription("Sam, tap the die to roll.").assertDoesNotExist()
            compose.onNode(onClickLabel("Roll")).assertDoesNotExist()
            assertEquals(listOf("Alex"), compose.playing())

            show(passed, listOf(LogEntry(event)))
            compose.onNodeWithContentDescription("Alex is moving.").assertDoesNotExist()
            val prompt = compose.onNodeWithContentDescription("Sam, tap the die to roll.").fetchSemanticsNode()
            assertEquals(LiveRegionMode.Polite, prompt.config[SemanticsProperties.LiveRegion])
            compose.onNode(onClickLabel("Roll")).assertExists()
            assertEquals(listOf("Sam"), compose.playing())

            show(passed.copy(current = 0, rollingAgain = true), listOf(LogEntry(event)), actionShown = false)
            compose.onNodeWithContentDescription("Alex, roll again: tap the die.").assertExists()
            assertEquals(listOf("Alex"), compose.playing())
        }

    @Test
    fun `a roll with no move keeps the turn its roller's and out of the log until it has been shown - whoever rolls next`() = uiTest(TestScreen(411, 891)) {
        val first = LogEntry(GameEvent.NoMove(3, 2))
        // The turn passes to Sam by a roll with no move, or a third 6; or Alex has another try.
        val rolls = listOf(
            Triple(GameEvent.NoMove(0, 3), 2, "Sam, tap the die to roll.") to "Alex rolled 3 and had no move.",
            Triple(GameEvent.RollAgainLimit(0, 6), 2, "Sam, tap the die to roll.") to "Alex rolled a third 6 in a row, so the turn passed.",
            Triple(GameEvent.NoMove(0, 3, triesLeft = 1), 0, "Alex, roll again: tap the die.") to "Alex rolled 3 and had no move: 1 more try.",
        )
        for ((roll, line) in rolls) {
            val (event, next, prompt) = roll
            val state = state().copy(current = next, rollingAgain = next == 0, lastEvent = event)
            show(state, listOf(first, LogEntry(event)), actionShown = false)
            val waiting = compose.onNodeWithContentDescription("Alex is rolling.").fetchSemanticsNode()
            assertEquals(PlayerColour.defaultFor(0).color, waiting.config[SemanticsProperties.Text].single().spanStyles.single().item.color)
            compose.onNodeWithContentDescription(prompt).assertDoesNotExist()
            compose.onNode(hasContentDescription(line)).assertDoesNotExist()
            compose.onNode(hasContentDescription("Payout rolled 2 and had no move.")).assertExists()
            compose.onNode(onClickLabel("Roll")).assertDoesNotExist()
            assertEquals(listOf("Alex"), compose.playing())

            show(state, listOf(first, LogEntry(event)))
            compose.onNodeWithContentDescription("Alex is rolling.").assertDoesNotExist()
            compose.onNodeWithContentDescription(prompt).assertExists()
            compose.onNode(hasContentDescription(line)).assertExists()
            compose.onNode(onClickLabel("Roll")).assertExists()
            assertEquals(listOf(if (next == 0) "Alex" else "Sam"), compose.playing())
        }
    }

    /** A log of every kind of line, oldest first, with what each says. */
    private val everyLine = listOf(
        LogEntry(GameEvent.Moved(0, 6, Move(0, Y, 0), finished = false)) to "Alex brought Pawn 1 out.",
        LogEntry(GameEvent.Moved(0, 4, Move(0, 0, 4), finished = false)) to "Alex's Pawn 1 moved 4 tiles.",
        LogEntry(GameEvent.Moved(0, 1, Move(0, 4, 5), finished = false)) to "Alex's Pawn 1 moved 1 tile.",
        LogEntry(GameEvent.NoMove(1, 3)) to "Gambit rolled 3 and had no move.",
        LogEntry(GameEvent.RollAgainLimit(2, 6)) to "Sam rolled a third 6 in a row, so the turn passed.",
        LogEntry(GameEvent.Moved(3, 2, Move(1, H - 2, H), finished = false)) to "Payout brought Pawn 2 home.",
        LogEntry(GameEvent.Moved(3, 2, Move(2, H - 2, H), finished = true), place = 1) to "Payout has every pawn home, in 1st place.",
        LogEntry(GameEvent.Moved(1, 5, Move(0, 15, 20, listOf(PawnRef(0, 1))), finished = false)) to "Gambit captured Alex's Pawn 2 with Pawn 1.",
    )

    @Test
    fun `the log under the die says every move - newest first - back to who went first - each with its time - word for word`() =
        uiTest(TestScreen(411, 891)) {
            // A minute apart, the first a minute after the game started.
            val log = everyLine.mapIndexed { i, (entry, _) -> entry.copy(atEpochMillis = (i + 1) * 60_000L) }
            show(state().copy(current = 2, lastEvent = everyLine.last().first.event), log)
            compose.onNodeWithText("Moves").assertDoesNotExist()
            compose.onNodeWithText("Sam, tap the die to roll.").assertExists()
            val lines = (listOf("Alex goes first.") + everyLine.map { it.second }).reversed()
            val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
            assertEquals(lines.size, list.fetchSemanticsNode().config[SemanticsProperties.CollectionInfo].rowCount)
            val minutes = mutableListOf<Int>()
            lines.forEachIndexed { index, line ->
                list.performScrollToIndex(index)
                val node = compose.onNode(hasContentDescription(line)).fetchSemanticsNode()
                assertEquals(index, node.config[SemanticsProperties.CollectionItemInfo].rowIndex)
                assertEquals(index == 0, node.config.getOrNull(SemanticsProperties.LiveRegion) == LiveRegionMode.Polite, line)
                // Shown after its time, "[hh:mm:ss] ", in the phone's time zone; said without it.
                val shown = node.config[SemanticsProperties.Text].joinToString("") { it.text }
                val time = Regex("""^\[(\d\d):(\d\d):00] (.*)$""").find(shown) ?: error("No time on \"$shown\"")
                assertEquals(line, time.groupValues[3])
                minutes += time.groupValues[2].toInt()
            }
            // A minute between each, newest first, whatever the time zone.
            assertEquals(List(lines.size - 1) { 1 }, minutes.zipWithNext { newer, older -> (newer - older + 60) % 60 })
        }

    @Test
    fun `on a screen with no room for the log its newest line is shown alone - and heard as it comes`() = uiTest(TestScreen(320, 470)) {
        show(state().copy(current = 2, lastEvent = everyLine.last().first.event), everyLine.map { it.first })
        compose.onNode(hasContentDescription("Gambit captured Alex's Pawn 2 with Pawn 1."))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNode(hasContentDescription("Alex brought Pawn 1 out.")).assertDoesNotExist()
    }

    @Test
    fun `a line in the log shows each player it names in their colour - not bold - and is said as written`() = uiTest(TestScreen(411, 891)) {
        show(state().copy(current = 2, lastEvent = GameEvent.Moved(1, 5, Move(0, 15, 20, listOf(PawnRef(0, 1))), finished = false)), listOf(LogEntry(GameEvent.Moved(1, 5, Move(0, 15, 20, listOf(PawnRef(0, 1))), finished = false))))
        val node = compose.onNode(hasContentDescription("Gambit captured Alex's Pawn 2 with Pawn 1.")).fetchSemanticsNode()
        // The time, then the line: two texts in one row.
        val (time, line) = node.config[SemanticsProperties.Text]
        assertEquals(
            listOf("Gambit" to PlayerColour.defaultFor(1).color, "Alex" to PlayerColour.defaultFor(0).color),
            line.spanStyles.sortedBy { it.start }.map { line.text.substring(it.start, it.end) to it.item.color },
        )
        // Coloured, not bold: bold is the prompt's.
        assertEquals(listOf(null, null), line.spanStyles.map { it.item.fontWeight })
        assertTrue(Regex("""^\[\d\d:\d\d:\d\d] $""").matches(time.text), time.text)
        assertEquals("Gambit captured Alex's Pawn 2 with Pawn 1.", line.text)
        assertEquals("Gambit captured Alex's Pawn 2 with Pawn 1.", node.config[SemanticsProperties.ContentDescription].single())
    }

    @Test
    fun `each name is coloured once - a short one never inside a longer one - and one not there is left`() {
        val text = withNamesInColour("Jo captured Joe's Pawn 1 with Pawn 2.", listOf("Jo" to Color.Red, "Joe" to Color.Blue, "Sam" to Color.Green, "" to Color.Cyan))
        assertEquals(
            listOf(Triple(0, 2, Color.Red), Triple(12, 15, Color.Blue)),
            text.spanStyles.sortedBy { it.start }.map { Triple(it.start, it.end, it.item.color) },
        )
    }
}
