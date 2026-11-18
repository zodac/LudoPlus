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
        assertEquals(500, pacing.waitAfter(null))
        assertEquals(500L + DIE_TUMBLE_MILLIS, pacing.waitAfter(GameEvent.Rolled(1, 4)))
        assertEquals(500L + DIE_TUMBLE_MILLIS, pacing.waitAfter(GameEvent.NoMove(1, 4)))
        // Three squares, then a capture's slide.
        assertEquals(500L + 3 * PAWN_STEP_MILLIS, pacing.waitAfter(GameEvent.Moved(1, 3, Move(0, 10, 13), finished = false)))
        assertEquals(
            500L + 3 * PAWN_STEP_MILLIS + CAPTURE_SLIDE_MILLIS,
            pacing.waitAfter(GameEvent.Moved(1, 3, Move(0, 10, 13, listOf(PawnRef(0, 0))), finished = false)),
        )
        // Out of the yard is one hop.
        assertEquals(500L + PAWN_STEP_MILLIS, pacing.waitAfter(GameEvent.Moved(1, 6, Move(0, -1, 0), finished = false)))
    }

    @Test
    fun `with the motion off there is nothing to wait for but the think`() {
        val still = GamePacing(thinkMillis = 500, pawnsAnimated = false, dieAnimated = false)
        assertEquals(500, still.waitAfter(GameEvent.Rolled(1, 4)))
        assertEquals(500, still.waitAfter(GameEvent.Moved(1, 3, Move(0, 10, 13, listOf(PawnRef(0, 0))), finished = false)))
    }

    @Test
    fun `a move steps once a square in time with the pawn - its landing a capture's sound or the home chime when it does either`() {
        val step = PAWN_STEP_MILLIS.toLong()
        assertEquals(
            listOf(step to MoveSound.STEP, 2 * step to MoveSound.STEP, 3 * step to MoveSound.STEP),
            moveSoundCues(Move(0, 10, 13), homeProgress = 56, animated = true),
        )
        // Out of the yard is one hop onto the start square.
        assertEquals(listOf(step to MoveSound.STEP), moveSoundCues(Move(0, -1, 0), homeProgress = 56, animated = true))
        assertEquals(
            listOf(step to MoveSound.STEP, 2 * step to MoveSound.CAPTURE),
            moveSoundCues(Move(0, 10, 12, listOf(PawnRef(1, 0))), homeProgress = 56, animated = true),
        )
        assertEquals(listOf(step to MoveSound.STEP, 2 * step to MoveSound.HOME), moveSoundCues(Move(0, 54, 56), homeProgress = 56, animated = true))
        // With pawn moves still, the landing alone, at once.
        assertEquals(listOf(0L to MoveSound.HOME), moveSoundCues(Move(0, 54, 56), homeProgress = 56, animated = false))
        assertEquals(listOf(0L to MoveSound.STEP), moveSoundCues(Move(0, 10, 13), homeProgress = 56, animated = false))
    }
}
