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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
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
import net.zodac.ludo.ui.theme.Primary
import net.zodac.ludo.ui.theme.color

/** The ring round a pawn that can be moved: the brand violet, "press this" (`UI.md`, "Colour"). */
private val MovableRing = Primary

/** How much a moving pawn swells at the top of each hop from square to square. */
private const val HOP_SWELL = 0.18f

/**
 * The classic board, drawn from [BoardLayout], with [state]'s pawns on it. [seatColours] colours each seat's yard, start
 * square, home column, and pawns (null for a seat nobody sits at). The pawns that [moves] can move are ringed, and a
 * tap near one, or a screen reader's action on its square ([BoardSemantics]), moves it ([onPawn]).
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
    val colours = seatColours.map { it?.color }
    val movable = moves.map { it.pawn }.toSet()
    val marks = remember(state.players) { pawnMarks(state) }
    val textMeasurer = rememberTextMeasurer()
    val currentMarks by rememberUpdatedState(marks)
    val currentMovable by rememberUpdatedState(movable)
    val currentPlayer by rememberUpdatedState(state.current)
    val currentOnPawn by rememberUpdatedState(onPawn)
    val motion = rememberMoveMotion(state)
    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val unit = size.width.toFloat() / BoardLayout.SIZE
                        pawnAt(tap / unit, currentMarks, currentPlayer, currentMovable)?.let(currentOnPawn)
                    }
                },
        ) {
            val unit = size.minDimension / BoardLayout.SIZE
            drawBoard(unit, colours)
            val animating = motion.moving
            for (mark in marks) {
                // The moving pawn and any it captured are drawn on their way, below; the rest of their mark stays.
                val pawns = mark.pawns.filterNot { animating != null && motion.isAnimated(mark.player, it) }
                if (pawns.isEmpty()) continue
                val colour = colours.getOrNull(state.players[mark.player].seat) ?: BoardEmptySeat
                val ringed = animating == null && mark.player == state.current && pawns.any { it in movable }
                if (mark.home) drawHomeSpot(mark.centre * unit, mark.radius * unit)
                drawPawn(mark.centre * unit, mark.radius * unit, colour, ringed)
                if (pawns.size > 1) drawCount(textMeasurer, pawns.size, mark, unit)
            }
            if (animating != null) {
                for ((player, centre) in motion.capturedCentres()) {
                    drawPawn(centre * unit, 0.38f * unit, colours.getOrNull(state.players[player].seat) ?: BoardEmptySeat, ringed = false)
                }
                val (centre, swell) = motion.moverCentre()
                drawPawn(centre * unit, 0.38f * unit * (1f + swell), colours.getOrNull(state.players[animating.player].seat) ?: BoardEmptySeat, ringed = false)
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
    val seat = state.players[event.player].seat
    val move = event.move
    val path = if (move.from == PawnPosition.YARD_PROGRESS) {
        listOf(BoardLayout.yardSpot(seat, move.pawn), BoardLayout.centreOf(seat, PawnPosition.of(0, state.board), 0))
    } else {
        (move.from..move.to).map { BoardLayout.centreOf(seat, PawnPosition.of(it, state.board), move.pawn) }
    }
    val captured = move.captures.map { it to BoardLayout.yardSpot(state.players[it.player].seat, it.pawn) }
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

/** The board itself, no pawns: squares, yards, start squares, stars, home columns, and the centre. */
internal fun DrawScope.drawBoard(unit: Float, seatColours: List<Color?>) {
    val board = BoardLayout.geometry
    val line = BoardInk.copy(alpha = 0.45f)
    val lineWidth = 1.dp.toPx()
    drawRect(BoardIvory, size = Size(unit * BoardLayout.SIZE, unit * BoardLayout.SIZE))
    fun square(cell: Cell, fill: Color) {
        val topLeft = Offset(cell.column * unit, cell.row * unit)
        drawRect(fill, topLeft, Size(unit, unit))
        drawRect(line, topLeft, Size(unit, unit), style = Stroke(lineWidth))
    }
    for (square in 0..<board.trackLength) {
        val cell = BoardLayout.trackCell(square)
        val startOf = if (square % board.seatSpacing == 0) square / board.seatSpacing else null
        square(cell, startOf?.let { seatColours.colourOf(it) } ?: BoardIvory)
        if (square % board.seatSpacing == board.starOffset) drawStar(cell.centre * unit, unit * 0.36f, BoardInk.copy(alpha = 0.55f))
    }
    for (seat in 0..<board.seats) {
        val colour = seatColours.colourOf(seat)
        for (index in 0..<board.homeColumnLength) square(BoardLayout.homeColumnCell(seat, index), colour)
        // The yard: a block of the seat's colour round an ivory court with a spot for each pawn.
        val origin = BoardLayout.yardOrigin(seat)
        val yardTopLeft = Offset(origin.column * unit, origin.row * unit)
        val yardSize = Size(BoardLayout.YARD_SIZE * unit, BoardLayout.YARD_SIZE * unit)
        drawRect(colour, yardTopLeft, yardSize)
        drawRect(line, yardTopLeft, yardSize, style = Stroke(lineWidth))
        drawRoundRect(
            BoardIvory,
            yardTopLeft + Offset(unit, unit),
            Size(4 * unit, 4 * unit),
            CornerRadius(unit * 0.5f),
        )
        for (slot in 0..3) {
            drawCircle(colour.copy(alpha = 0.45f), unit * 0.62f, BoardLayout.yardSpot(seat, slot) * unit)
        }
        // Home: the seat's triangle of the centre.
        val triangle = BoardLayout.homeTriangle(seat).map { it * unit }
        val path = Path().apply {
            moveTo(triangle[0].x, triangle[0].y)
            triangle.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, colour)
        drawPath(path, line, style = Stroke(lineWidth))
    }
}

private fun List<Color?>.colourOf(seat: Int): Color = getOrNull(seat) ?: BoardEmptySeat

/** A five-pointed star, point up, its points [radius] from [centre]: a safe square's mark. */
private fun DrawScope.drawStar(centre: Offset, radius: Float, colour: Color) {
    val path = Path()
    for (i in 0..9) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val angle = -PI / 2 + i * PI / 5
        val point = centre + Offset((r * cos(angle)).toFloat(), (r * sin(angle)).toFloat())
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path, colour)
}

/** The ivory spot a pawn that is home stands on, ringed in ink: it lifts the pawn off its own colour's triangle. */
internal fun DrawScope.drawHomeSpot(centre: Offset, radius: Float) {
    val spot = centre + Offset(0f, -0.05f * radius)
    drawCircle(BoardIvory, radius * 1.12f, spot)
    drawCircle(BoardInk.copy(alpha = 0.6f), radius * 1.12f, spot, style = Stroke(maxOf(0.75.dp.toPx(), radius * 0.07f)))
}

/**
 * A pawn standing on its spot, seen from the side: a round head on a collar, over a body flaring to a base, lit from
 * the left in [colour] and outlined in ink, with a shadow at its foot. It stands within [radius] across of [centre]
 * (a little taller than wide), and a violet ring round it when [ringed].
 */
internal fun DrawScope.drawPawn(centre: Offset, radius: Float, colour: Color, ringed: Boolean) {
    val r = radius
    fun at(x: Float, y: Float) = Offset(centre.x + x * r, centre.y + y * r)
    if (ringed) drawCircle(MovableRing, r * 1.3f, at(0f, -0.04f), style = Stroke(r * 0.2f))
    val ink = BoardInk
    val outline = Stroke(width = maxOf(1.dp.toPx(), r * 0.09f))
    val light = lerp(colour, Color.White, 0.35f)
    val shade = lerp(colour, Color.Black, 0.28f)
    val lit = Brush.horizontalGradient(listOf(light, colour, shade), startX = centre.x - 0.7f * r, endX = centre.x + 0.7f * r)
    // The shadow it casts, then the base, body, collar, and head, back to front.
    drawOval(Color.Black.copy(alpha = 0.25f), at(-0.8f, 0.72f), Size(1.6f * r, 0.36f * r))
    val body = Path().apply {
        at(-0.3f, -0.18f).let { moveTo(it.x, it.y) }
        at(-0.36f, 0.3f).let { c -> at(-0.66f, 0.62f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
        at(0.66f, 0.62f).let { lineTo(it.x, it.y) }
        at(0.36f, 0.3f).let { c -> at(0.3f, -0.18f).let { quadraticTo(c.x, c.y, it.x, it.y) } }
        close()
    }
    drawPath(body, lit)
    drawPath(body, ink, style = outline)
    val baseTopLeft = at(-0.82f, 0.56f)
    val baseSize = Size(1.64f * r, 0.36f * r)
    drawRoundRect(lit, baseTopLeft, baseSize, CornerRadius(0.16f * r))
    drawRoundRect(ink, baseTopLeft, baseSize, CornerRadius(0.16f * r), style = outline)
    val collarTopLeft = at(-0.46f, -0.3f)
    val collarSize = Size(0.92f * r, 0.2f * r)
    drawRoundRect(lit, collarTopLeft, collarSize, CornerRadius(0.1f * r))
    drawRoundRect(ink, collarTopLeft, collarSize, CornerRadius(0.1f * r), style = outline)
    val head = at(0f, -0.62f)
    drawCircle(lit, 0.38f * r, head)
    drawCircle(ink, 0.38f * r, head, style = outline)
    drawCircle(Color.White.copy(alpha = 0.55f), 0.11f * r, at(-0.13f, -0.75f))
}
