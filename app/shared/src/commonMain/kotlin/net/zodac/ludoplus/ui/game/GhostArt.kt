package net.zodac.ludoplus.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sin
import net.zodac.ludoplus.data.settings.MotionGroup
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.GhostMove
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.ui.common.groupReduceMotion
import net.zodac.ludoplus.ui.style.PawnStyle
import net.zodac.ludoplus.ui.theme.BoardInk
import net.zodac.ludoplus.ui.theme.GhostGlow
import net.zodac.ludoplus.ui.theme.GhostWhite

/** How big a ghost is, in squares, and how high it floats as it drifts. */
private const val GHOST_SIZE = 1.1f
private const val GHOST_FLOAT = 0.12f

/** How high a caught pawn is thrown on its way back to its yard, in squares, and how much of that more a square of the way. */
private const val CAUGHT_ARC = 0.8f
private const val CAUGHT_ARC_PER_SQUARE = 0.18f

/** A ghost's move being shown: [path] is its squares' centres, in board units, and [caught] each pawn it takes, with where its yard spot is. */
internal class DriftingGhost(val move: GhostMove, val path: List<Offset>, val caught: Map<PawnRef, Offset>, val yardRadius: Float) {
    val driftMillis: Int = (path.size - 1) * GHOST_STEP_MILLIS
}

/**
 * The ghost's move under way on the board, if any, and its [clock], in milliseconds from its start, below zero while the
 * turn it comes after is still playing out; read only while drawing.
 */
internal class GhostMotion(val drifting: DriftingGhost?, private val clock: State<Float>) {
    /** Whether [pawn] is one it has yet to send home, or is sending: drawn here, not in its yard. */
    fun isCaught(pawn: PawnRef): Boolean = drifting != null && pawn in drifting.caught

    /** The ghosts of [state], on a board of [unit] per square, but for the one that's drifting. */
    fun DrawScope.drawStill(state: GameState, layout: BoardLayout, unit: Float) {
        state.ghosts.forEachIndexed { ghost, square ->
            if (ghost != drifting?.move?.ghost) drawGhost(layout.trackSquare(square).centre * unit, unit)
        }
    }

    /** Each pawn being caught at this moment (but for [moving], the pawn whose move is still playing out), drawn by [draw] at its centre and as big as it is, in squares. */
    fun eachCaught(moving: PawnRef?, draw: (PawnRef, Offset, Float) -> Unit) {
        val drifting = drifting ?: return
        val landing = drifting.path.last()
        val flight = ((clock.value - drifting.driftMillis) / CAPTURE_FLIGHT_MILLIS).coerceIn(0f, 1f)
        for ((pawn, yard) in drifting.caught) {
            if (pawn == moving) continue
            val ground = lerp(landing, yard, flight)
            val up = (CAUGHT_ARC + CAUGHT_ARC_PER_SQUARE * (yard - landing).getDistance()) * 4f * flight * (1f - flight)
            draw(pawn, ground - Offset(0f, up), PAWN_RADIUS + (drifting.yardRadius - PAWN_RADIUS) * flight)
        }
    }

    /** The pawns being caught ([eachCaught]), and the ghost drifting onto them. */
    fun DrawScope.drawDrift(unit: Float, pawns: PawnStyle, colourOf: (Int) -> Color, moving: PawnRef?) {
        val drifting = drifting ?: return
        val millis = clock.value
        val step = (millis / GHOST_STEP_MILLIS).coerceIn(0f, (drifting.path.size - 1).toFloat())
        val index = step.toInt().coerceAtMost(drifting.path.size - 2)
        val fraction = step - index
        eachCaught(moving) { pawn, centre, size -> with(pawns) { drawPawn(centre * unit, size * unit, colourOf(pawn.player), ringed = false) } }
        val at = lerp(drifting.path[index], drifting.path[index + 1], fraction) - Offset(0f, GHOST_FLOAT * sin(PI * fraction).toFloat())
        drawGhost(at * unit, unit)
    }
}

/**
 * Follows [state]'s last ghost move ([GameState.ghostMove]), drifting it from where it started once the turn it came after
 * has played out; the first state seen is shown as it is.
 */
@Composable
internal fun rememberGhostMotion(state: GameState): GhostMotion {
    val latest = remember { arrayOf(state.ghostMove) }
    val firstSeen = remember { latest[0] }
    val move = state.ghostMove?.also { latest[0] = it } ?: latest[0]
    val reduce = groupReduceMotion(MotionGroup.PAWN_MOVE)
    val dieAnimated = !groupReduceMotion(MotionGroup.DIE_ROLL)
    val before = remember(move) { turnMillis(state, dieAnimated) }
    val drifting = remember(move) { if (move == null || move === firstSeen || reduce) null else driftingGhost(state, move) }
    val clock = remember(move) { Animatable(-before.toFloat()) }
    var done by remember(move) { mutableStateOf(drifting == null) }
    LaunchedEffect(move) {
        if (drifting == null) return@LaunchedEffect
        val end = ghostMillis(drifting.move)
        clock.animateTo(end.toFloat(), tween(before + end, easing = LinearEasing))
        done = true
    }
    return GhostMotion(if (done) null else drifting, clock.asState())
}

/** How long the turn that [state]'s ghost move came after takes to play out: its pawn's move, or its roll's showing, where the die is animated. */
internal fun turnMillis(state: GameState, dieAnimated: Boolean): Int = when (val event = state.lastEvent) {
    is GameEvent.Moved -> moveMillis(event, state.board.homeProgress)
    null -> 0
    else -> if (dieAnimated) rollMillis(event) else 0
}

private fun driftingGhost(state: GameState, move: GhostMove): DriftingGhost {
    val layout = BoardLayout.of(state.board)
    val path = (0..abs(move.steps)).map { layout.trackSquare((move.from + it * move.steps.sign).mod(state.board.trackLength)).centre }
    val pawns = state.rules.pawnsPerPlayer
    return DriftingGhost(move, path, move.caught.associateWith { layout.yardSpotOf(state.players[it.player].seat, it.pawn, pawns) }, layout.yardPawnRadiusOf(pawns))
}

/** A ghost, a little see-through so a pawn on its square shows, in a violet glow, centred on [centre]: the same on every board. */
internal fun DrawScope.drawGhost(centre: Offset, unit: Float) {
    val size = unit * GHOST_SIZE
    drawCircle(Brush.radialGradient(listOf(GhostGlow.copy(alpha = 0.75f), Color.Transparent), centre, size * 0.85f), size * 0.85f, centre)
    fun body(grow: Float) = Path().apply {
        val half = size * (0.36f + grow)
        val top = centre.y - size * (0.48f + grow)
        val bottom = centre.y + size * (0.42f + grow)
        moveTo(centre.x - half, bottom)
        lineTo(centre.x - half, centre.y - size * 0.1f)
        cubicTo(centre.x - half, top, centre.x + half, top, centre.x + half, centre.y - size * 0.1f)
        lineTo(centre.x + half, bottom)
        for (wave in 0..2) {
            quadraticTo(centre.x + half - 2 * half * (wave + 0.5f) / 3, bottom - size * 0.26f, centre.x + half - 2 * half * (wave + 1f) / 3, bottom)
        }
        close()
    }
    drawPath(body(0.05f), BoardInk.copy(alpha = 0.85f))
    drawPath(body(0f), GhostWhite.copy(alpha = 0.88f))
    for (side in listOf(-1f, 1f)) drawOval(BoardInk, centre + Offset(side * size * 0.14f - size * 0.06f, -size * 0.2f), Size(size * 0.12f, size * 0.18f))
    drawOval(BoardInk, centre + Offset(-size * 0.07f, size * 0.06f), Size(size * 0.14f, size * 0.12f))
}
