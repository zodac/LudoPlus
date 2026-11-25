package net.zodac.ludoplus.ui.game

import kotlin.math.abs
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.GhostMove
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PowerUpEffect

/** How long a moving pawn takes to hop from one square to the next. */
const val PAWN_STEP_MILLIS = 140

/** How long a captured pawn takes to fly back to its yard, once the pawn that took it has landed. */
const val CAPTURE_FLIGHT_MILLIS = 480

/** How long a captured pawn takes to come back out of its yard, once it has landed there, where a banked Call Up brings it. */
const val RECALL_MILLIS = PAWN_STEP_MILLIS * 2

/** How long the flourish round a pawn that reaches home lasts, once it has landed. */
const val HOME_FLOURISH_MILLIS = 720

/** How long a pawn takes to go in at a portal and come out at its pair, once it has landed on it. */
const val PORTAL_WARP_MILLIS = 520

/** How long a ghost takes to drift from one square to the next: half a pawn's speed. */
const val GHOST_STEP_MILLIS = PAWN_STEP_MILLIS * 2

/** How long squares take to vanish in the Cataclysm, once the turn they go after has played out. */
const val VANISH_MILLIS = 700

/** How long the die tumbles before it settles on a roll. */
const val DIE_TUMBLE_MILLIS = 420

/** How many hops [move]'s pawn makes: one a square, and one out of the yard. */
private fun hops(move: Move): Int = if (move.from < 0) 1 else move.squares

/** How many hops back [move]'s pawn makes, off a shield. */
private fun hopsBack(move: Move): Int = move.bouncedFrom?.let { it - move.to } ?: 0

/**
 * How long [move]'s pawn travels: [PAWN_STEP_MILLIS] a hop, on and back off a shield, and [PORTAL_WARP_MILLIS] through a
 * portal, or where a power-up it ends on takes it, as [effect] says.
 */
fun travelMillis(move: Move, effect: PowerUpEffect? = null): Int =
    (hops(move) + hopsBack(move)) * PAWN_STEP_MILLIS + if (move.via != null || effect?.movedTo != null) PORTAL_WARP_MILLIS else 0

/** How long [event]'s move plays for, on a board whose home is [homeProgress]: its pawn's travel, and what follows its landing. */
fun moveMillis(event: GameEvent.Moved, homeProgress: Int): Int =
    travelMillis(event.move, event.effect) + afterLandingMillis(event.move, homeProgress, event.effect, recalled = event.recalled.isNotEmpty())

/** How long [move] plays for: the ghost's drift, then the flight of any pawn it caught. */
fun ghostMillis(move: GhostMove): Int = abs(move.steps) * GHOST_STEP_MILLIS + if (move.caught.isEmpty()) 0 else CAPTURE_FLIGHT_MILLIS

/**
 * What follows [move]'s landing, on a board whose home is [homeProgress]: a capture's flight back to its yard (or another
 * pawn's, where a power-up moves it, as [effect] says), and back out where it's [recalled], or a pawn home's flourish.
 */
fun afterLandingMillis(move: Move, homeProgress: Int, effect: PowerUpEffect? = null, recalled: Boolean = false): Int = when {
    (effect?.movedTo ?: move.to) == homeProgress -> HOME_FLOURISH_MILLIS
    move.captures.isNotEmpty() || effect?.captures.orEmpty().isNotEmpty() -> CAPTURE_FLIGHT_MILLIS + if (recalled) RECALL_MILLIS else 0
    effect?.swapped != null || effect?.calledUp != null -> CAPTURE_FLIGHT_MILLIS
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
     * its animation's length (a move's travel, then its capture's flight or its home flourish), and that of the [ghostMove]
     * or of squares [vanishing] after it.
     */
    fun waitAfter(event: GameEvent?, homeProgress: Int, ghostMove: GhostMove? = null, vanishing: Boolean = false): Long =
        thinkMillis + playOutMillis(event, homeProgress, ghostMove, vanishing)

    /** [waitAfter] the last action of [state], and what came after it. */
    fun waitAfter(state: GameState): Long = thinkMillis + playOutMillis(state)

    /**
     * How long the screen animates [event] for, on a board whose home is [homeProgress]: the die's tumble, or a move's
     * travel and what follows its landing, then the [ghostMove] that came after it, or squares [vanishing].
     */
    fun playOutMillis(event: GameEvent?, homeProgress: Int, ghostMove: GhostMove? = null, vanishing: Boolean = false): Long = afterTurnMillis(ghostMove, vanishing) + when (event) {
        null -> 0L
        is GameEvent.Moved -> if (pawnsAnimated) moveMillis(event, homeProgress).toLong() else 0L
        is GameEvent.Rolled, is GameEvent.NoMove, is GameEvent.RollAgainLimit -> if (dieAnimated) DIE_TUMBLE_MILLIS.toLong() else 0L
    }

    /** [playOutMillis] for the last action of [state], and what came after it. */
    fun playOutMillis(state: GameState): Long = playOutMillis(state.lastEvent, state.board.homeProgress, state.ghostMove, state.justLost.isNotEmpty())

    private fun afterTurnMillis(ghostMove: GhostMove?, vanishing: Boolean): Long =
        if (!pawnsAnimated) 0 else (ghostMove?.let(::ghostMillis) ?: 0) + if (vanishing) VANISH_MILLIS.toLong() else 0

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
 * pawn lands back in its yard, and another as any [recalled] comes back out. [effect] is what a power-up it ended on did.
 */
fun moveSoundCues(move: Move, homeProgress: Int, animated: Boolean, effect: PowerUpEffect? = null, recalled: Boolean = false): List<Pair<Long, MoveSound>> {
    // A pawn that keeps its place (GameEvent.Moved.keptPlace) goes nowhere.
    if (move.squares == 0 && move.bouncedFrom == null && effect == null) return emptyList()
    // Coming out of a portal is a landing of its own, after its hops.
    val hopped = (1..hops(move) + hopsBack(move)).map { it * PAWN_STEP_MILLIS.toLong() }
    val warps = move.via != null || effect?.movedTo != null
    val captures = move.captures + effect?.captures.orEmpty()
    val steps = if (!animated) listOf(0L) else if (!warps) hopped else hopped + travelMillis(move, effect).toLong()
    val landing = when {
        captures.isNotEmpty() -> MoveSound.CAPTURE
        (effect?.movedTo ?: move.to) == homeProgress -> MoveSound.HOME
        else -> MoveSound.STEP
    }
    val backInYard = if (animated && captures.isNotEmpty()) listOf(steps.last() + CAPTURE_FLIGHT_MILLIS) else emptyList()
    val backOut = if (recalled) backInYard.map { it + RECALL_MILLIS } else emptyList()
    return steps.dropLast(1).map { it to MoveSound.STEP } + (steps.last() to landing) + (backInYard + backOut).map { it to MoveSound.STEP }
}
