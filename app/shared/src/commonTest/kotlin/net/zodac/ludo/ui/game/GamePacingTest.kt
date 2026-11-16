package net.zodac.ludo.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludo.game.GameEvent
import net.zodac.ludo.game.Move
import net.zodac.ludo.game.PawnRef

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
}
