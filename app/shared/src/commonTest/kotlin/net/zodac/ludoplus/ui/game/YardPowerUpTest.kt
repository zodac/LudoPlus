package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import net.zodac.ludoplus.ui.theme.color

private const val BOARD = "board"
private const val Y = -1
private const val FRAME = 16

/** The power-ups in force for a player, on their yard: Double while their rolls count double, and Call Up while they have any banked. */
class YardPowerUpTest : UiTest() {

    private fun game(vararg doubledRolls: Int, powerUps: Map<Int, PowerUp> = emptyMap()): GameState {
        val new = GameState.new(GameMode.CHAOS, 4)
        return new.copy(
            current = 0,
            powerUps = powerUps,
            // None to come while a test plays.
            turnsToPowerUp = 5,
            players = new.players.mapIndexed { i, side -> side.copy(pawns = listOf(1, Y, Y, Y), doubledRolls = doubledRolls.getOrElse(i) { 0 }) },
        )
    }

    private fun GameState.banking(vararg callUps: Int) = copy(players = players.mapIndexed { i, side -> side.copy(callUps = callUps[i]) })

    @Test
    fun `Double is on the yards of those with rolls left to count double - and only theirs`() = uiTest {
        val state = show(game(0, 2, 0, 1))
        assertEquals(listOf(1, 3), yardsShown())
        state.value = game(3, 0, 0, 1)
        assertEquals(listOf(0, 3), frames(5 * FRAME).last())
    }

    @Test
    fun `Double comes as the pawn lands on the Double - not before`() = uiTest {
        val rolled = LudoEngine.applyRoll(game(powerUps = mapOf(3 to PowerUp.DOUBLE)), 2)
        val state = show(rolled)
        state.value = LudoEngine.move(rolled, 0)
        val along = frames(2 * PAWN_STEP_MILLIS + 100)
        assertTrue(along.indexOfFirst { it.isNotEmpty() } * FRAME >= 2 * PAWN_STEP_MILLIS - FRAME, "Too soon: $along")
        assertEquals(listOf(0), along.last())
    }

    @Test
    fun `Double stays while the pawn its last doubled roll moves is on its way`() = uiTest {
        val rolled = LudoEngine.applyRoll(game(1), 2)
        val state = show(rolled)
        state.value = LudoEngine.move(rolled, 0)
        val along = frames(4 * PAWN_STEP_MILLIS + 100)
        assertTrue(along.indexOfFirst { it.isEmpty() } * FRAME >= 4 * PAWN_STEP_MILLIS - FRAME, "Too soon: $along")
        assertEquals(emptyList(), along.last())
    }

    @Test
    fun `Call Up is on the yards of those with any banked - in a place of its own`() = uiTest {
        show(game(0, 2, 0, 1).banking(0, 1, 5, 0))
        assertEquals(listOf(1, 2), yardsShown(PowerUp.CALL_UP))
        assertEquals(listOf(1, 3), yardsShown())
    }

    @Test
    fun `Call Up stays till the pawn it brings back out is out`() = uiTest {
        // Red's pawn takes green's on square 3 (42 from its start).
        val start = game().banking(0, 1, 0, 0)
        val rolled = LudoEngine.applyRoll(start.copy(players = start.players.mapIndexed { i, side -> if (i == 1) side.copy(pawns = listOf(42, Y, Y, Y)) else side }), 2)
        val state = show(rolled)
        state.value = LudoEngine.move(rolled, 0)
        assertEquals(0, state.value.players[1].callUps)
        val length = 2 * PAWN_STEP_MILLIS + CAPTURE_FLIGHT_MILLIS + RECALL_MILLIS
        val along = frames(length + 100, PowerUp.CALL_UP)
        assertTrue(along.indexOfFirst { it.isEmpty() } * FRAME >= length - FRAME, "Too soon: $along")
        assertEquals(emptyList(), along.last())
    }

    @Test
    fun `the power-up that replaces one taken shows as the pawn that took it lands - not before`() = uiTest {
        // One of each other kind, so only Shield's is replaced.
        val others = (PowerUp.entries - PowerUp.SHIELD).withIndex().associate { (i, kind) -> 40 + i to kind }
        val rolled = LudoEngine.applyRoll(game(powerUps = others + (3 to PowerUp.SHIELD)), 2)
        val state = show(rolled)
        state.value = LudoEngine.move(rolled, 0)
        val square = (state.value.powerUps.keys - others.keys).single()
        val along = List((2 * PAWN_STEP_MILLIS + 100) / FRAME) { isDrawn(PowerUp.SHIELD, square).also { mainClock.advanceTimeByFrame() } }
        assertTrue(along.indexOf(true) * FRAME >= 2 * PAWN_STEP_MILLIS - FRAME, "Too soon: $along")
        assertTrue(along.last(), "Never shown")
    }

    /** Whether [powerUp] is drawn on track [square]: its colour under its symbol. */
    private fun ComposeUiTest.isDrawn(powerUp: PowerUp, square: Int): Boolean {
        val pixels = onNodeWithTag(BOARD).captureToImage().toPixelMap()
        val at = (ClassicBoardLayout.trackSquare(square).centre + Offset(0f, POWER_UP_RADIUS * 0.72f)) * (pixels.width / ClassicBoardLayout.size)
        return pixels[at.x.toInt(), at.y.toInt()] == powerUp.color
    }

    private fun ComposeUiTest.show(start: GameState): MutableState<GameState> {
        val state = mutableStateOf(start)
        mainClock.autoAdvance = false
        setContent {
            LudoPlusTheme {
                LudoBoard(state.value, List(4) { PlayerColour.defaultFor(it) }, emptyList(), {}, Modifier.size(330.dp).testTag(BOARD))
            }
        }
        mainClock.advanceTimeByFrame()
        yardsShown()
        return state
    }

    /** The yards [powerUp] is on, on each frame of the next [millis]. */
    private fun ComposeUiTest.frames(millis: Int, powerUp: PowerUp = PowerUp.DOUBLE): List<List<Int>> = List(millis / FRAME) {
        // Drawn each frame, as a screen is: the move's clock starts on its first drawn frame.
        yardsShown(powerUp).also { mainClock.advanceTimeByFrame() }
    }

    /** The seats whose yards [powerUp] is drawn on: its colour under its symbol. */
    private fun ComposeUiTest.yardsShown(powerUp: PowerUp = PowerUp.DOUBLE): List<Int> {
        val pixels = onNodeWithTag(BOARD).captureToImage().toPixelMap()
        val unit = pixels.width / ClassicBoardLayout.size
        return (0..3).filter { seat ->
            val at = (ClassicBoardLayout.yardBadges(seat, 4, YARD_BADGE_ROOM)[if (powerUp == PowerUp.DOUBLE) 0 else 1].centre + Offset(0f, YARD_BADGE_RADIUS * 0.72f)) * unit
            pixels[at.x.toInt(), at.y.toInt()] == powerUp.color
        }
    }
}
