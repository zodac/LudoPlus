package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.TestScreen
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.ui.style.MovableRing
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

private const val BOARD = "board"
private const val SIDE = 600
private const val UNIT = SIDE / 15f
private const val Y = -1

/** What a mode's own tiles say when tapped: a portal's pair while it's pressed, and a power-up's name, and what it does. */
class BoardTilesTest : UiTest() {

    private val made = mutableListOf<Move>()
    private var state by mutableStateOf(GameState.new(GameMode.CLASSIC, 4))
    private var swapping by mutableStateOf(false)

    private fun show(shown: GameState) {
        state = shown
        compose.setContent {
            LudoPlusTheme {
                val moves = (state.phase as? TurnPhase.AwaitingMove)?.moves.orEmpty().filter { (it.swap != null) == swapping }
                LudoBoard(state, List(4) { PlayerColour.defaultFor(it) }, moves, { made += it }, Modifier.size(SIDE.dp).testTag(BOARD), swapping)
            }
        }
        compose.waitForIdle()
    }

    private fun game(mode: GameMode, vararg red: Int, powerUps: Map<Int, PowerUp> = emptyMap()): GameState {
        val new = GameState.new(mode, 4)
        return new.copy(players = new.players.mapIndexed { i, it -> if (i == 0) it.copy(pawns = List(4) { pawn -> red.getOrElse(pawn) { Y } }) else it }, powerUps = powerUps)
    }

    private fun square(square: Int): Offset = ClassicBoardLayout.trackSquare(square).centre * UNIT

    private fun board() = compose.onNodeWithTag(BOARD)

    /** The colour a little in from the edge of the portal on [square], clear of its symbol. */
    private fun edge(square: Int): Color = square(square).let { board().captureToImage().toPixelMap()[(it.x + UNIT * 0.43f).toInt(), it.y.toInt()] }

    private fun difference(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    @Test
    fun `a portal pressed is picked out with its pair - everything else on the board fades - until it's let go`() = uiTest(TestScreen(700, 900)) {
        show(game(GameMode.PORTALS, 20))
        fun at(square: Offset): Color = board().captureToImage().toPixelMap()[square.x.toInt(), square.y.toInt()]
        // Red's short pair is 3 and 10. The rest: another portal, a pawn, a plain square, a yard, and home.
        val pair = listOf(3, 10)
        val rest = listOf(square(16) + Offset(UNIT * 0.43f, 0f), square(20), square(4), Offset(UNIT * 0.5f, UNIT * 0.5f), Offset(UNIT * 7.5f, UNIT * 6.7f))
        val before = rest.map(::at) to pair.map(::edge)
        board().performTouchInput { down(square(3)) }
        compose.waitForIdle()
        // At a fifth, over the page behind (the test's is white).
        rest.forEachIndexed { i, point -> assertTrue(difference(at(point), Color.White) < difference(before.first[i], Color.White) * 0.3f + 0.02f, "Point $i didn't fade: ${at(point)}") }
        // Each end bright, in a ring where there was none.
        for (end in pair) {
            assertTrue(difference(edge(end), Color.White) > 0.3f, "The portal on $end faded")
            assertTrue(difference(at(square(end) + Offset(UNIT * 0.68f, 0f)), Color.White) > 0.3f, "No ring round $end")
        }
        board().performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(before, rest.map(::at) to pair.map(::edge))
        assertEquals(emptyList(), made)
    }

    @Test
    fun `while a Swap is picked, only the pawn that moves and those it can be with stay bright - until it's made or declined`() = uiTest(TestScreen(700, 900)) {
        // Red's pawn on 1 rolls a 2, onto the Swap on 3. Green has a pawn on the track's 22 and yellow one on 35; green's two on 23 can't be swapped with.
        val new = game(GameMode.CHAOS, 1, 30, powerUps = mapOf(3 to PowerUp.SWAP, 40 to PowerUp.SHIELD))
        val others = mapOf(1 to listOf(9, 10, 10, Y), 2 to listOf(9, Y, Y, Y))
        val rolled = LudoEngine.applyRoll(new.copy(players = new.players.mapIndexed { i, it -> others[i]?.let { pawns -> it.copy(pawns = pawns) } ?: it }), 2)
        show(rolled)
        fun at(square: Offset): Color = board().captureToImage().toPixelMap()[square.x.toInt(), square.y.toInt()]
        val bright = listOf(1, 22, 35).map(::square)
        // Another pawn of the mover's, two it can't swap with, the two power-ups, a yard, home, and a pawn in its yard.
        val rest = listOf(square(30), square(23), square(3), square(40), Offset(UNIT * 0.5f, UNIT * 0.5f), Offset(UNIT * 7.5f, UNIT * 6.7f)) +
            ClassicBoardLayout.yardSpotOf(1, 3, 4) * UNIT
        val before = rest.map(::at) to bright.map(::at)
        swapping = true
        compose.waitForIdle()
        rest.forEachIndexed { i, point -> assertTrue(difference(at(point), Color.White) < difference(before.first[i], Color.White) * 0.3f + 0.02f, "Point $i didn't fade: ${at(point)}") }
        bright.forEachIndexed { i, point -> assertTrue(difference(at(point), before.second[i]) < 0.05f, "Pawn $i isn't as bright: ${at(point)}, not ${before.second[i]}") }
        // Those to swap with are ringed, as movable pawns are; the one that moves, no longer.
        fun ring(square: Int): Color = at(square(square) + Offset(UNIT * 0.49f, 0f))
        val rings = listOf(1, 22, 35).map(::ring)
        assertTrue(rings.drop(1).all { difference(it, MovableRing) < 0.1f }, "Not ringed: $rings")
        assertTrue(difference(rings[0], MovableRing) > 0.2f, "The pawn that moves is ringed: $rings")
        // Nor can it be picked; one to swap with can.
        board().performTouchInput { click(square(1)) }
        assertEquals(emptyList(), made)
        board().performTouchInput { click(square(22)) }
        assertEquals(listOf(Move(0, 1, 3, swap = PawnRef(1, 0))), made)
        swapping = false
        compose.waitForIdle()
        assertEquals(before, rest.map(::at) to bright.map(::at))
    }

    @Test
    fun `a tap on a portal isn't a move - even held, or with a movable pawn beside it - but on the pawn is`() = uiTest(TestScreen(700, 900)) {
        show(LudoEngine.applyRoll(game(GameMode.PORTALS, 2, 30), 1))
        board().performTouchInput { click(square(3)) }
        board().performTouchInput { longClick(square(3)) }
        assertEquals(emptyList(), made)
        board().performTouchInput { click(square(2)) }
        assertEquals(listOf(0), made.map { it.pawn })
        // A pawn held is a pawn tapped.
        board().performTouchInput { longClick(square(30)) }
        assertEquals(listOf(0, 1), made.map { it.pawn })
    }

    @Test
    fun `a tap on a power-up shows its name - until another tap - however long`() = uiTest(TestScreen(700, 900)) {
        show(game(GameMode.CHAOS, powerUps = mapOf(3 to PowerUp.SHIELD, 20 to PowerUp.SWAP)))
        board().performTouchInput { click(square(3)) }
        compose.waitForIdle()
        compose.onAllNodesWithText("Shield").assertCountEquals(1)
        compose.onAllNodesWithText("The next pawn to land on yours", substring = true).assertCountEquals(0)
        compose.mainClock.advanceTimeBy(60_000)
        compose.onAllNodesWithText("Shield").assertCountEquals(1)
        board().performTouchInput { click(square(3)) }
        compose.waitForIdle()
        compose.onAllNodesWithText("Shield").assertCountEquals(0)
    }

    @Test
    fun `a long press on a power-up says what it does too - and it goes once the power-up is taken`() = uiTest(TestScreen(700, 900)) {
        show(game(GameMode.CHAOS, powerUps = mapOf(3 to PowerUp.SHIELD)))
        board().performTouchInput { longClick(square(3)) }
        compose.waitForIdle()
        compose.onAllNodesWithText("Shield").assertCountEquals(1)
        compose.onAllNodesWithText("The next pawn to land on yours bounces off to the square before it, and the shield breaks.").assertCountEquals(1)
        state = state.copy(powerUps = emptyMap())
        compose.waitForIdle()
        compose.onAllNodesWithText("Shield").assertCountEquals(0)
        // Another there later isn't shown unasked.
        state = state.copy(powerUps = mapOf(3 to PowerUp.SHIELD))
        compose.waitForIdle()
        compose.onAllNodesWithText("Shield").assertCountEquals(0)
    }

    @Test
    fun `a movable pawn on a power-up takes its tap - and beside one doesn't`() = uiTest(TestScreen(700, 900)) {
        show(LudoEngine.applyRoll(game(GameMode.CHAOS, 3, 30, powerUps = mapOf(3 to PowerUp.SHIELD, 31 to PowerUp.DOUBLE)), 2))
        board().performTouchInput { click(square(3)) }
        assertEquals(listOf(0), made.map { it.pawn })
        board().performTouchInput { click(square(31)) }
        compose.waitForIdle()
        assertEquals(1, made.size)
        compose.onAllNodesWithText("Double").assertCountEquals(1)
    }

    @Test
    fun `TalkBack's action on a power-up's square shows what it does`() = uiTest(TestScreen(700, 900)) {
        show(game(GameMode.CHAOS, powerUps = mapOf(3 to PowerUp.SHIELD)))
        val action = compose.onNodeWithContentDescription("Square 4, Shield power-up, empty").fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertEquals("What Shield does", action.label)
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
        compose.onAllNodesWithText("The next pawn to land on yours", substring = true).assertCountEquals(1)
        // Announced as it comes: the popup is a live region.
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertCountEquals(1)
    }
}
