package net.zodac.ludoplus.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef

class GamePacingTest {

    private val pacing = GamePacing(thinkMillis = 500)

    @Test
    fun `a CPU waits its think plus whatever the screen is still showing`() {
        assertEquals(500, pacing.waitAfter(null, HOME))
        assertEquals(500L + DIE_TUMBLE_MILLIS, pacing.waitAfter(GameEvent.Rolled(1, 4), HOME))
        assertEquals(500L + DIE_TUMBLE_MILLIS, pacing.waitAfter(GameEvent.NoMove(1, 4), HOME))
        // Three hops, then a capture's flight back to its yard.
        assertEquals(500L + 3 * PAWN_STEP_MILLIS, pacing.waitAfter(GameEvent.Moved(1, 3, Move(0, 10, 13), finished = false), HOME))
        assertEquals(
            500L + 3 * PAWN_STEP_MILLIS + CAPTURE_FLIGHT_MILLIS,
            pacing.waitAfter(GameEvent.Moved(1, 3, Move(0, 10, 13, listOf(PawnRef(0, 0))), finished = false), HOME),
        )
        // Two hops home, then its flourish.
        assertEquals(
            500L + 2 * PAWN_STEP_MILLIS + HOME_FLOURISH_MILLIS,
            pacing.waitAfter(GameEvent.Moved(1, 2, Move(0, HOME - 2, HOME), finished = false), HOME),
        )
        // Out of the yard is one hop.
        assertEquals(500L + PAWN_STEP_MILLIS, pacing.waitAfter(GameEvent.Moved(1, 6, Move(0, -1, 0), finished = false), HOME))
    }

    @Test
    fun `a move's timeline is its hops then a capture's flight or a home flourish`() {
        assertEquals(4 * PAWN_STEP_MILLIS, travelMillis(Move(0, 10, 14)))
        assertEquals(PAWN_STEP_MILLIS, travelMillis(Move(0, -1, 0)))
        assertEquals(0, afterLandingMillis(Move(0, 10, 14), HOME))
        assertEquals(CAPTURE_FLIGHT_MILLIS, afterLandingMillis(Move(0, 10, 14, listOf(PawnRef(1, 2))), HOME))
        assertEquals(HOME_FLOURISH_MILLIS, afterLandingMillis(Move(0, HOME - 3, HOME), HOME))
    }

    @Test
    fun `with the motion off there is nothing to wait for but the think`() {
        val still = GamePacing(thinkMillis = 500, pawnsAnimated = false, dieAnimated = false)
        assertEquals(500, still.waitAfter(GameEvent.Rolled(1, 4), HOME))
        assertEquals(500, still.waitAfter(GameEvent.Moved(1, 3, Move(0, 10, 13, listOf(PawnRef(0, 0))), finished = false), HOME))
        assertEquals(500, still.waitAfter(GameEvent.Moved(1, 2, Move(0, HOME - 2, HOME), finished = false), HOME))
    }

    @Test
    fun `a move steps once a square in time with the pawn - its landing a capture's sound or the home chime when it does either`() {
        val step = PAWN_STEP_MILLIS.toLong()
        assertEquals(
            listOf(step to MoveSound.STEP, 2 * step to MoveSound.STEP, 3 * step to MoveSound.STEP),
            moveSoundCues(Move(0, 10, 13), homeProgress = HOME, animated = true),
        )
        // Out of the yard is one hop onto the start square.
        assertEquals(listOf(step to MoveSound.STEP), moveSoundCues(Move(0, -1, 0), homeProgress = HOME, animated = true))
        // A capture, then a step as the captured pawn lands back in its yard.
        assertEquals(
            listOf(step to MoveSound.STEP, 2 * step to MoveSound.CAPTURE, 2 * step + CAPTURE_FLIGHT_MILLIS to MoveSound.STEP),
            moveSoundCues(Move(0, 10, 12, listOf(PawnRef(1, 0))), homeProgress = HOME, animated = true),
        )
        assertEquals(listOf(step to MoveSound.STEP, 2 * step to MoveSound.HOME), moveSoundCues(Move(0, 54, 56), homeProgress = HOME, animated = true))
        // With pawn moves still, the landing alone, at once.
        assertEquals(listOf(0L to MoveSound.HOME), moveSoundCues(Move(0, 54, 56), homeProgress = HOME, animated = false))
        assertEquals(listOf(0L to MoveSound.STEP), moveSoundCues(Move(0, 10, 13), homeProgress = HOME, animated = false))
        assertEquals(listOf(0L to MoveSound.CAPTURE), moveSoundCues(Move(0, 10, 12, listOf(PawnRef(1, 0))), homeProgress = HOME, animated = false))
    }

    private companion object {
        const val HOME = 56
    }
}
