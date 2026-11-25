package net.zodac.ludoplus.ui.game

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GhostMove
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.game.PowerUpEffect
import net.zodac.ludoplus.model.PowerUp

class GamePacingTest {

    private val pacing = GamePacing(thinkMillis = 500)

    @Test
    fun `a CPU waits its think plus whatever the screen is still showing`() {
        assertEquals(500, pacing.waitAfter(null, HOME))
        assertEquals(500L + DIE_TUMBLE_MILLIS, pacing.waitAfter(GameEvent.Rolled(1, 4), HOME))
        // A roll that passes the turn on is shown a moment once the die has settled; one with another try isn't.
        assertEquals(500L + DIE_TUMBLE_MILLIS + TURN_PASSED_MILLIS, pacing.waitAfter(GameEvent.NoMove(1, 4), HOME))
        assertEquals(500L + DIE_TUMBLE_MILLIS + TURN_PASSED_MILLIS, pacing.waitAfter(GameEvent.RollAgainLimit(1, 6), HOME))
        assertEquals(500L + DIE_TUMBLE_MILLIS, pacing.waitAfter(GameEvent.NoMove(1, 4, triesLeft = 1), HOME))
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
        // Off a shield: on to it and back, and a power-up's jump after the hops to it.
        assertEquals(4 * PAWN_STEP_MILLIS, travelMillis(Move(0, 10, 12, bouncedFrom = 13)))
        assertEquals(2 * PAWN_STEP_MILLIS + PORTAL_WARP_MILLIS, travelMillis(Move(0, 10, 12), PowerUpEffect(PowerUp.SWAP, movedTo = 15)))
        assertEquals(2 * PAWN_STEP_MILLIS, travelMillis(Move(0, 10, 12), PowerUpEffect(PowerUp.SHIELD)))
        assertEquals(CAPTURE_FLIGHT_MILLIS, afterLandingMillis(Move(0, 10, 12), HOME, PowerUpEffect(PowerUp.CALL_UP, calledUp = 1)))
        // A banked Call Up: back out once it's back in its yard.
        val capture = Move(0, 10, 12, listOf(PawnRef(1, 2)))
        val recalled = GameEvent.Moved(0, 2, capture, finished = false, recalled = listOf(PawnRef(1, 2)))
        assertEquals(2 * PAWN_STEP_MILLIS + CAPTURE_FLIGHT_MILLIS + RECALL_MILLIS, moveMillis(recalled, HOME))
        assertEquals(2L * PAWN_STEP_MILLIS + CAPTURE_FLIGHT_MILLIS + RECALL_MILLIS, GamePacing().playOutMillis(recalled, HOME))
        val inYard = 2L * PAWN_STEP_MILLIS + CAPTURE_FLIGHT_MILLIS
        assertEquals(listOf(inYard to MoveSound.STEP, inYard + RECALL_MILLIS to MoveSound.STEP), moveSoundCues(capture, HOME, animated = true, recalled = true).takeLast(2))
        // Through a portal: its hops to it, then in and out.
        assertEquals(2 * PAWN_STEP_MILLIS + PORTAL_WARP_MILLIS, travelMillis(Move(0, 1, 10, via = 3)))
        assertEquals(2 * PAWN_STEP_MILLIS + PORTAL_WARP_MILLIS, travelMillis(Move(0, 8, 3, via = 10)))
        assertEquals(0, afterLandingMillis(Move(0, 10, 14), HOME))
        assertEquals(CAPTURE_FLIGHT_MILLIS, afterLandingMillis(Move(0, 10, 14, listOf(PawnRef(1, 2))), HOME))
        assertEquals(HOME_FLOURISH_MILLIS, afterLandingMillis(Move(0, HOME - 3, HOME), HOME))
    }

    @Test
    fun `a ghost drifts at half a pawn's speed - after the turn before it - and a catch's flight follows`() {
        assertEquals(2 * PAWN_STEP_MILLIS, GHOST_STEP_MILLIS)
        assertEquals(3 * GHOST_STEP_MILLIS, ghostMillis(GhostMove(0, 20, -3)))
        assertEquals(3 * GHOST_STEP_MILLIS + CAPTURE_FLIGHT_MILLIS, ghostMillis(GhostMove(0, 20, 3, listOf(PawnRef(1, 0)))))
        // Over two lost squares, 3 hops; and the squares that vanish after it.
        val skipping = GameEvent.Moved(1, 3, Move(0, 10, 15, skipped = listOf(11, 12)), finished = false)
        assertEquals(3L * PAWN_STEP_MILLIS, pacing.playOutMillis(skipping, HOME))
        assertEquals(3L * PAWN_STEP_MILLIS + VANISH_MILLIS, pacing.playOutMillis(skipping, HOME, vanishing = true))
        assertEquals(DIE_TUMBLE_MILLIS + TURN_PASSED_MILLIS + 3L * GHOST_STEP_MILLIS, pacing.playOutMillis(GameEvent.NoMove(1, 4), HOME, GhostMove(0, 20, -3)))
        assertEquals(500L + DIE_TUMBLE_MILLIS + TURN_PASSED_MILLIS + 3 * GHOST_STEP_MILLIS, pacing.waitAfter(GameEvent.NoMove(1, 4), HOME, GhostMove(0, 20, -3)))
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

    @Test
    fun `a Swap's landing is played out on its own - then the swap - and a pawn that keeps its place takes no time and makes no sound`() {
        val step = PAWN_STEP_MILLIS.toLong()
        val landing = GameEvent.Moved(1, 2, Move(0, 1, 3), finished = false, effect = PowerUpEffect(PowerUp.SWAP))
        assertEquals(2 * step, pacing.playOutMillis(landing, HOME))
        assertEquals(listOf(step to MoveSound.STEP, 2 * step to MoveSound.STEP), moveSoundCues(landing.move, HOME, animated = true, landing.effect))
        val swap = GameEvent.Moved(1, 2, Move(0, 3, 3, swap = PawnRef(2, 0)), finished = false, effect = PowerUpEffect(PowerUp.SWAP, movedTo = 14, swapped = PawnRef(2, 0)))
        assertEquals(0L + PORTAL_WARP_MILLIS + CAPTURE_FLIGHT_MILLIS, pacing.playOutMillis(swap, HOME))
        assertEquals(listOf(PORTAL_WARP_MILLIS.toLong() to MoveSound.STEP), moveSoundCues(swap.move, HOME, animated = true, swap.effect))
        val kept = GameEvent.Moved(1, 2, Move(0, 3, 3, swap = PawnRef(1, 0)), finished = false)
        assertEquals(0L, pacing.playOutMillis(kept, HOME))
        assertEquals(emptyList(), moveSoundCues(kept.move, HOME, animated = true))
    }

    private companion object {
        const val HOME = 56
    }
}
