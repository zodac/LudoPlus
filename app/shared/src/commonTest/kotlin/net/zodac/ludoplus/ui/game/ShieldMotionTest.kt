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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.model.withHouseRules
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import net.zodac.ludoplus.ui.theme.ShieldBlue

private const val BOARD = "board"
private const val Y = -1
private const val FRAME = 16

/** A pawn's shield while a move plays out. */
class ShieldMotionTest : UiTest() {

    private fun game(vararg pawns: List<Int>, shielded: PawnRef? = null, powerUps: Map<Int, PowerUp> = emptyMap()): GameState {
        val new = GameState.new(GameMode.CHAOS, 4)
        return new.copy(
            current = 0,
            powerUps = powerUps,
            // None to come while a test plays.
            turnsToPowerUp = 5,
            players = new.players.mapIndexed { i, side ->
                side.copy(pawns = pawns.getOrElse(i) { listOf(Y, Y, Y, Y) }, shielded = if (shielded?.player == i) setOf(shielded.pawn) else emptySet())
            },
        )
    }

    @Test
    fun `a shield goes with its pawn, hop by hop, and is on it where it lands`() = uiTest {
        val rolled = LudoEngine.applyRoll(game(listOf(5, Y, Y, Y), shielded = PawnRef(0, 0)), 4)
        val state = show(rolled)
        val start = assertNotNull(shieldAt(), "Before")
        state.value = LudoEngine.move(rolled, 0)
        val along = frames(4 * PAWN_STEP_MILLIS + 100)
        assertTrue(along.all { it != null }, "Gone on a frame: $along")
        // Seen on the way, not only where it starts and ends.
        val end = along.last()!!
        assertTrue(along.any { (it!! - start).getDistance() > 0.02f && (it - end).getDistance() > 0.02f }, "Never between $start and $end: $along")
        assertTrue((end - start).getDistance() > 0.1f, "Landed: $end, from $start")
    }

    @Test
    fun `a shield goes as its pawn comes off the track`() = uiTest {
        // Progress 49 is two short of the first square of the home column.
        val rolled = LudoEngine.applyRoll(game(listOf(49, Y, Y, Y), shielded = PawnRef(0, 0)), 4)
        val state = show(rolled)
        state.value = LudoEngine.move(rolled, 0)
        val along = frames(4 * PAWN_STEP_MILLIS + 100)
        val last = along.indexOfLast { it != null } * FRAME
        assertTrue(last in PAWN_STEP_MILLIS..(2 * PAWN_STEP_MILLIS + 2 * FRAME), "Gone at ${last}ms")
    }

    @Test
    fun `a shield a pawn takes shows as it lands, not before`() = uiTest {
        val rolled = LudoEngine.applyRoll(game(listOf(1, Y, Y, Y), powerUps = mapOf(3 to PowerUp.SHIELD)), 2)
        val state = show(rolled)
        assertNull(shieldAt(), "Before")
        state.value = LudoEngine.move(rolled, 0)
        val along = frames(2 * PAWN_STEP_MILLIS + 100)
        assertTrue(along.indexOfFirst { it != null } * FRAME >= 2 * PAWN_STEP_MILLIS - FRAME, "Too soon: $along")
        assertNotNull(shieldAt(), "Landed")
    }

    @Test
    fun `a shield goes with its pawn as it's swapped`() = uiTest {
        // Red on square 1, two short of a Swap; blue's shielded pawn on square 45.
        val start = game(listOf(1, Y, Y, Y), listOf(Y, Y, Y, Y), listOf(Y, Y, Y, Y), listOf(6, Y, Y, Y), shielded = PawnRef(3, 0), powerUps = mapOf(3 to PowerUp.SWAP))
        val rolled = LudoEngine.applyRoll(start, 2)
        val state = show(rolled)
        val from = assertNotNull(shieldAt(), "Before")
        val landed = LudoEngine.move(rolled, 0)
        state.value = landed
        frames(2 * PAWN_STEP_MILLIS + 100)
        // It waits where it is while the swap is picked.
        assertEquals(from, shieldAt())
        state.value = LudoEngine.move(landed, 0, swap = PawnRef(3, 0))
        val along = frames(PORTAL_WARP_MILLIS + CAPTURE_FLIGHT_MILLIS + 100)
        assertTrue(along.all { it != null }, "Gone on a frame: $along")
        assertTrue((along.last()!! - from).getDistance() > 0.1f, "Swapped: ${along.last()}, from $from")
    }

    @Test
    fun `a pawn that keeps its place on a Swap stays there on every frame`() = uiTest {
        val start = game(listOf(1, Y, Y, Y), listOf(9, Y, Y, Y), shielded = PawnRef(0, 0), powerUps = mapOf(3 to PowerUp.SWAP))
        val landed = LudoEngine.move(LudoEngine.applyRoll(start, 2), 0)
        val state = show(landed)
        val at = assertNotNull(shieldAt(), "Landed")
        state.value = LudoEngine.move(landed, 0, swap = PawnRef(0, 0))
        assertEquals(setOf<Offset?>(at), frames(200).toSet())
    }

    @Test
    fun `a shielded pawn taken from the yard loses its shield as it's knocked out`() = uiTest {
        val new = game(listOf(Y, Y, Y, Y), listOf(39, Y, Y, Y), shielded = PawnRef(1, 0))
        // Green's progress 39 is red's start square, which Guarded Starts clears.
        val rolled = LudoEngine.applyRoll(new.copy(rules = new.rules.withHouseRules(setOf(HouseRule.GUARDED_STARTS))), 6)
        val state = show(rolled)
        assertNotNull(shieldAt(), "Before")
        val moved = LudoEngine.move(rolled, 0)
        assertEquals(emptySet(), moved.players[1].shielded)
        state.value = moved
        val along = frames(PAWN_STEP_MILLIS + CAPTURE_FLIGHT_MILLIS + 100)
        val last = along.indexOfLast { it != null } * FRAME
        assertTrue(last in PAWN_STEP_MILLIS / 2..(PAWN_STEP_MILLIS + 2 * FRAME), "Gone at ${last}ms")
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
        shieldAt()
        return state
    }

    /** Where the shield is on each frame of the next [millis]. */
    private fun ComposeUiTest.frames(millis: Int): List<Offset?> = List(millis / FRAME) {
        // Drawn each frame, as a screen is: the move's clock starts on its first drawn frame.
        shieldAt().also { mainClock.advanceTimeByFrame() }
    }

    /** The middle of what's drawn in a shield's colour, a share of the board's width and height; null with nothing. */
    private fun ComposeUiTest.shieldAt(): Offset? {
        val pixels = onNodeWithTag(BOARD).captureToImage().toPixelMap()
        val found = (0..<pixels.height).flatMap { y -> (0..<pixels.width).filter { x -> pixels[x, y] == ShieldBlue }.map { x -> Offset(x.toFloat(), y.toFloat()) } }
        if (found.isEmpty()) return null
        return Offset(found.map { it.x }.average().toFloat() / pixels.width, found.map { it.y }.average().toFloat() / pixels.height)
    }
}
