package net.zodac.ludo.ui.game

import net.zodac.ludo.game.GameEvent
import net.zodac.ludo.game.Move

/** How long a moving pawn takes over each square, and a captured pawn to slide back to its yard. */
const val PAWN_STEP_MILLIS = 110

/** How long a captured pawn takes to slide back to its yard, once the pawn that took it has landed. */
const val CAPTURE_SLIDE_MILLIS = 280

/** How long the die tumbles before it settles on a roll. */
const val DIE_TUMBLE_MILLIS = 420

/**
 * What a CPU's turn waits on so the player can follow it: [thinkMillis] before each roll and move, on top of whatever
 * the screen is still animating from the action before. [pawnsAnimated] and [dieAnimated] say whether the screen
 * animates a move and a roll at all (their motion groups on), so a CPU has nothing to wait for when they're off.
 */
data class GamePacing(
    val thinkMillis: Long = THINK_MILLIS,
    val pawnsAnimated: Boolean = true,
    val dieAnimated: Boolean = true,
) {
    /** How long to wait after [event] before the next action: the pause, plus its animation's length. */
    fun waitAfter(event: GameEvent?): Long = thinkMillis + when (event) {
        null -> 0L
        is GameEvent.Moved -> if (pawnsAnimated) moveMillis(event) else 0L
        is GameEvent.Rolled, is GameEvent.NoMove, is GameEvent.RollAgainLimit -> if (dieAnimated) DIE_TUMBLE_MILLIS.toLong() else 0L
    }

    private fun moveMillis(event: GameEvent.Moved): Long {
        val move = event.move
        // Out of the yard is one hop onto the start square.
        val squares = if (move.from < 0) 1 else move.to - move.from
        return squares * PAWN_STEP_MILLIS.toLong() + if (move.captures.isEmpty()) 0L else CAPTURE_SLIDE_MILLIS.toLong()
    }

    companion object {
        /** How long a CPU pauses before each roll and move, so each can be followed. */
        const val THINK_MILLIS = 500L
    }
}

/** A sound a move makes: a step onto a square, or what its landing does. */
enum class MoveSound { STEP, CAPTURE, HOME }

/**
 * When [move]'s sounds play, in milliseconds from its start, for a board whose home is [homeProgress]: a step as the pawn
 * reaches each square (one hop out of the yard), in time with its move (all at once with [animated] off), the landing's
 * step taken by a capture's sound or a pawn home's chime when it does either.
 */
fun moveSoundCues(move: Move, homeProgress: Int, animated: Boolean): List<Pair<Long, MoveSound>> {
    val hops = if (move.from < 0) 1 else move.to - move.from
    val steps = if (animated) (1..hops).map { it * PAWN_STEP_MILLIS.toLong() } else listOf(0L)
    val landing = when {
        move.captures.isNotEmpty() -> MoveSound.CAPTURE
        move.to == homeProgress -> MoveSound.HOME
        else -> MoveSound.STEP
    }
    return steps.dropLast(1).map { it to MoveSound.STEP } + (steps.last() to landing)
}
