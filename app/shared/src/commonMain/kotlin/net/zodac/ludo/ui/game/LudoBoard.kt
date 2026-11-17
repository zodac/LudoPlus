package net.zodac.ludo.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.PI
import kotlin.math.sin
import net.zodac.ludo.data.settings.MotionGroup
import net.zodac.ludo.game.GameEvent
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.Move
import net.zodac.ludo.game.PawnRef
import net.zodac.ludo.model.PawnPosition
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.ui.common.groupReduceMotion
import net.zodac.ludo.ui.theme.BoardEmptySeat
import net.zodac.ludo.ui.theme.BoardInk
import net.zodac.ludo.ui.theme.BoardIvory
import net.zodac.ludo.ui.style.LocalGameVisualTheme
import net.zodac.ludo.ui.theme.color

/** The ring round a pawn that can be moved: the brand violet, "press this" (`UI.md`, "Colour"). */

/** How much a moving pawn swells at the top of each hop from square to square. */
private const val HOP_SWELL = 0.18f

/**
 * The board for [state]'s players, drawn from its [BoardLayout], with [state]'s pawns on it. [seatColours] colours each
 * seat's yard, start square, home column, and pawns (null for a seat nobody sits at). The pawns that [moves] can move
 * are ringed, and a tap near one, or a screen reader's action on its square ([BoardSemantics]), moves it ([onPawn]).
 *
 * A move arrives square by square, and any pawn it captures then slides back to its yard, unless the Pawn moves motion
 * is off ([MotionGroup.PAWN_MOVE]).
 */
@Composable
fun LudoBoard(
    state: GameState,
    seatColours: List<PlayerColour?>,
    moves: List<Move>,
    onPawn: (pawn: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = remember(seatColours) { seatColours.map { it?.color } }
    val movable = moves.map { it.pawn }.toSet()
    val marks = remember(state.players) { pawnMarks(state) }
    val textMeasurer = rememberTextMeasurer()
    val currentMarks by rememberUpdatedState(marks)
    val currentMovable by rememberUpdatedState(movable)
    val currentPlayer by rememberUpdatedState(state.current)
    val currentOnPawn by rememberUpdatedState(onPawn)
    val motion = rememberMoveMotion(state)
    val theme = LocalGameVisualTheme.current
    val layout = BoardLayout.of(state.board)
    Box(modifier = modifier.aspectRatio(1f)) {
        // The board on a layer of its own, which reads only the style and the seats' colours: it's drawn again when they
        // change, not on every frame of a move.
        Canvas(modifier = Modifier.fillMaxSize()) {
            with(theme.board) { drawBoard(layout, size.minDimension / layout.size, colours) }
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(layout) {
                    detectTapGestures { tap ->
                        val unit = size.width.toFloat() / layout.size
                        pawnAt(tap / unit, currentMarks, currentPlayer, currentMovable)?.let(currentOnPawn)
                    }
                },
        ) {
            val unit = size.minDimension / layout.size
            val animating = motion.moving
            for (mark in marks) {
                // The moving pawn and any it captured are drawn on their way, below; the rest of their mark stays.
                val pawns = mark.pawns.filterNot { animating != null && motion.isAnimated(mark.player, it) }
                if (pawns.isEmpty()) continue
                val colour = colours.getOrNull(state.players[mark.player].seat) ?: BoardEmptySeat
                val ringed = animating == null && mark.player == state.current && pawns.any { it in movable }
                if (mark.home) with(theme.board) { drawHomeSpot(mark.centre * unit, mark.radius * unit) }
                with(theme.pawns) { drawPawn(mark.centre * unit, mark.radius * unit, colour, ringed) }
                if (pawns.size > 1) drawCount(textMeasurer, pawns.size, mark, unit)
            }
            if (animating != null) {
                for ((player, centre) in motion.capturedCentres()) {
                    with(theme.pawns) { drawPawn(centre * unit, 0.38f * unit, colours.getOrNull(state.players[player].seat) ?: BoardEmptySeat, ringed = false) }
                }
                val (centre, swell) = motion.moverCentre()
                with(theme.pawns) {
                    drawPawn(centre * unit, 0.38f * unit * (1f + swell), colours.getOrNull(state.players[animating.player].seat) ?: BoardEmptySeat, ringed = false)
                }
            }
        }
        BoardSemantics(state, seatColours, moves, onPawn, Modifier.fillMaxSize())
    }
}

/** A move being shown: [player]'s pawn on [path] (board-unit centres, square by square), capturing [captured]. */
private class MovingPawn(val player: Int, val pawn: Int, val path: List<Offset>, val captured: List<Pair<PawnRef, Offset>>, val landing: Offset)

/** The pawn move under way on the board, if any, and how far along it is. */
private class MoveMotion(val moving: MovingPawn?, private val steps: Float, private val slide: Float) {
    fun isAnimated(player: Int, pawn: Int): Boolean =
        moving != null && ((moving.player == player && moving.pawn == pawn) || moving.captured.any { it.first == PawnRef(player, pawn) })

    /** Where the mover is, and how much it swells mid-hop. */
    fun moverCentre(): Pair<Offset, Float> {
        val path = moving!!.path
        val step = steps.coerceIn(0f, (path.size - 1).toFloat())
        val index = step.toInt().coerceAtMost(path.size - 2)
        val fraction = step - index
        return lerp(path[index], path[index + 1], fraction) to HOP_SWELL * sin(PI * fraction).toFloat()
    }

    /** Where each captured pawn is: on the square it was taken on until the mover lands, then sliding to its yard. */
    fun capturedCentres(): List<Pair<Int, Offset>> = moving!!.captured.map { (ref, yard) -> ref.player to lerp(moving.landing, yard, slide) }
}

/** Follows [state]'s last move, animating it from where it started; the first state seen is shown as it is. */
@Composable
private fun rememberMoveMotion(state: GameState): MoveMotion {
    val event = state.lastEvent as? GameEvent.Moved
    val firstSeen = remember { event }
    val reduce = groupReduceMotion(MotionGroup.PAWN_MOVE)
    val moving = remember(event) { if (event == null || event === firstSeen || reduce) null else movingPawn(state, event) }
    val steps = remember(event) { Animatable(0f) }
    val slide = remember(event) { Animatable(0f) }
    var done by remember(event) { mutableStateOf(moving == null) }
    LaunchedEffect(event) {
        if (moving == null) return@LaunchedEffect
        val count = moving.path.size - 1
        steps.animateTo(count.toFloat(), tween(count * PAWN_STEP_MILLIS, easing = LinearEasing))
        if (moving.captured.isNotEmpty()) slide.animateTo(1f, tween(CAPTURE_SLIDE_MILLIS))
        done = true
    }
    return MoveMotion(if (done) null else moving, steps.value, slide.value)
}

private fun movingPawn(state: GameState, event: GameEvent.Moved): MovingPawn {
    val layout = BoardLayout.of(state.board)
    val seat = state.players[event.player].seat
    val move = event.move
    val path = if (move.from == PawnPosition.YARD_PROGRESS) {
        listOf(layout.yardSpot(seat, move.pawn), layout.centreOf(seat, PawnPosition.of(0, state.board), 0))
    } else {
        (move.from..move.to).map { layout.centreOf(seat, PawnPosition.of(it, state.board), move.pawn) }
    }
    val captured = move.captures.map { it to layout.yardSpot(state.players[it.player].seat, it.pawn) }
    return MovingPawn(event.player, move.pawn, path, captured, path.last())
}

/** A count badge at the top right of [mark], for pawns sharing a square. */
private fun DrawScope.drawCount(textMeasurer: TextMeasurer, count: Int, mark: PawnMark, unit: Float) {
    val badgeCentre = (mark.centre + Offset(mark.radius * 0.8f, -mark.radius * 0.8f)) * unit
    val badgeRadius = unit * 0.24f
    drawCircle(BoardInk, badgeRadius, badgeCentre)
    val text = textMeasurer.measure(
        count.toString(),
        TextStyle(color = BoardIvory, fontSize = (badgeRadius * 1.3f).toSp(), fontWeight = FontWeight.Bold),
    )
    drawText(text, topLeft = badgeCentre - Offset(text.size.width / 2f, text.size.height / 2f))
}

private operator fun Offset.div(by: Float) = Offset(x / by, y / by)

