package net.zodac.ludoplus.ui.game

import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.Move

/** How long a moving pawn takes to hop from one square to the next. */
const val PAWN_STEP_MILLIS = 140

/** How long a captured pawn takes to fly back to its yard, once the pawn that took it has landed. */
const val CAPTURE_FLIGHT_MILLIS = 480

/** How long the flourish round a pawn that reaches home lasts, once it has landed. */
const val HOME_FLOURISH_MILLIS = 720

/** How long a pawn takes to go in at a portal and come out at its pair, once it has landed on it. */
const val PORTAL_WARP_MILLIS = 520

/** How long the die tumbles before it settles on a roll. */
const val DIE_TUMBLE_MILLIS = 420

/** How many hops [move]'s pawn makes: one a square, and one out of the yard. */
private fun hops(move: Move): Int = if (move.from < 0) 1 else move.rolledTo - move.from

/** How long [move]'s pawn travels: [PAWN_STEP_MILLIS] a hop, and [PORTAL_WARP_MILLIS] through a portal. */
fun travelMillis(move: Move): Int = hops(move) * PAWN_STEP_MILLIS + if (move.via != null) PORTAL_WARP_MILLIS else 0

/**
 * What follows [move]'s landing, on a board whose home is [homeProgress]: a capture's flight back to its yard, or a
 * pawn home's flourish.
 */
fun afterLandingMillis(move: Move, homeProgress: Int): Int = when {
    move.captures.isNotEmpty() -> CAPTURE_FLIGHT_MILLIS
    move.to == homeProgress -> HOME_FLOURISH_MILLIS
    else -> 0
}

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
    /**
     * How long to wait after [event], on a board whose home is [homeProgress], before the next action: the pause, plus
     * its animation's length (a move's travel, then its capture's flight or its home flourish).
     */
    fun waitAfter(event: GameEvent?, homeProgress: Int): Long = thinkMillis + when (event) {
        null -> 0L
        is GameEvent.Moved -> if (pawnsAnimated) (travelMillis(event.move) + afterLandingMillis(event.move, homeProgress)).toLong() else 0L
        is GameEvent.Rolled, is GameEvent.NoMove, is GameEvent.RollAgainLimit -> if (dieAnimated) DIE_TUMBLE_MILLIS.toLong() else 0L
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
 * lands on each square (one hop out of the yard), in time with its move (all at once with [animated] off), the landing's
 * step taken by a capture's sound or a pawn home's chime when it does either; and, [animated], a step as each captured
 * pawn lands back in its yard.
 */
fun moveSoundCues(move: Move, homeProgress: Int, animated: Boolean): List<Pair<Long, MoveSound>> {
    // Coming out of a portal is a landing of its own, after its hops.
    val hopped = (1..hops(move)).map { it * PAWN_STEP_MILLIS.toLong() }
    val steps = if (!animated) listOf(0L) else if (move.via == null) hopped else hopped + travelMillis(move).toLong()
    val landing = when {
        move.captures.isNotEmpty() -> MoveSound.CAPTURE
        move.to == homeProgress -> MoveSound.HOME
        else -> MoveSound.STEP
    }
    val backInYard = if (animated && move.captures.isNotEmpty()) listOf(steps.last() + CAPTURE_FLIGHT_MILLIS to MoveSound.STEP) else emptyList()
    return steps.dropLast(1).map { it to MoveSound.STEP } + (steps.last() to landing) + backInYard
}
