package net.zodac.ludoplus.ui.game

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
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.ludoplus.data.settings.MotionGroup
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.ui.common.BoundedArt
import net.zodac.ludoplus.ui.common.groupReduceMotion
import net.zodac.ludoplus.ui.theme.BoardEmptySeat
import net.zodac.ludoplus.ui.theme.BoardInk
import net.zodac.ludoplus.ui.theme.BoardIvory
import net.zodac.ludoplus.ui.style.LocalGameVisualTheme
import net.zodac.ludoplus.ui.style.BoardStyle
import net.zodac.ludoplus.ui.style.PawnStyle
import net.zodac.ludoplus.ui.style.drawCachedSurface
import net.zodac.ludoplus.ui.theme.BoardSparkGold
import net.zodac.ludoplus.ui.theme.color

/** The ring round a pawn that can be moved: the brand violet, "press this" (`UI.md`, "Colour"). */

/** How much a pawn leaving its yard swells on its way to its start square. */
private const val YARD_EXIT_SWELL = 0.18f

/** How high a moving pawn rises at the top of each hop from square to square, in squares. */
internal const val HOP_HEIGHT = 0.42f

/** How much a hopping pawn swells at the top of its hop, as if nearer. */
internal const val HOP_SWELL = 0.08f

/** How high a captured pawn is thrown on its way back to its yard: this, in squares, and [CAPTURE_ARC_PER_SQUARE] of the way. */
private const val CAPTURE_ARC = 0.8f
private const val CAPTURE_ARC_PER_SQUARE = 0.18f

/** How much of a capture's flight the burst on the square it was taken on lasts. */
private const val CAPTURE_BURST_SHARE = 0.55f

/** How much of a pawn home's flourish its pop (a swell and back) lasts, and how far it swells. */
private const val HOME_POP_SHARE = 0.4f
private const val HOME_POP = 0.5f

/** How far into the turn's yard its ring reaches, in squares: white, with an ink line on its inside edge. */
private const val TURN_RING_INNER = 0.2f
private const val TURN_RING_OUTER = 0.26f

/** How many streaks a capture's burst throws out, and sparks a pawn home's flourish. */
private const val BURST_STREAKS = 8
private const val HOME_SPARKS = 10

/**
 * The board for [state]'s players, drawn from its [BoardLayout], with [state]'s pawns on it. [seatColours] colours each
 * seat's yard, start square, home column, and pawns (null for a seat nobody sits at). Until the game is over, the yard of
 * the player whose turn it is has a ring round its inside edge. The pawns that [moves] can move
 * are ringed (a partner's too, when the player rolls for one), and a tap near one, or a screen reader's action on its
 * square ([BoardSemantics]), makes its move ([onMove]).
 *
 * A move hops square by square (out of the yard, it glides to the start square). A pawn it captures is knocked out
 * in a burst on its square and thrown back to its yard; a pawn that reaches home pops in a ring of sparks. None of it
 * plays while the Pawn moves motion is off ([MotionGroup.PAWN_MOVE]).
 */
@Composable
fun LudoBoard(
    state: GameState,
    seatColours: List<PlayerColour?>,
    moves: List<Move>,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colours = remember(seatColours) { seatColours.map { it?.color } }
    val movable = remember(moves, state) { moves.map { PawnRef(it.ownerIn(state), it.pawn) }.toSet() }
    val marks = remember(state.players) { pawnMarks(state) }
    val textMeasurer = rememberTextMeasurer()
    val currentMarks by rememberUpdatedState(marks)
    val currentMovable by rememberUpdatedState(movable)
    val currentMoves by rememberUpdatedState(moves)
    val currentState by rememberUpdatedState(state)
    val currentOnMove by rememberUpdatedState(onMove)
    val motion = rememberMoveMotion(state)
    val theme = LocalGameVisualTheme.current
    val layout = BoardLayout.of(state.board)
    // Read here, so the board's layer below holds a number rather than the state, which changes every move.
    val pawns = state.rules.pawnsPerPlayer
    val pawnColours = remember(state.players, colours) { state.players.map { colours.getOrNull(it.seat) ?: BoardEmptySeat } }
    Box(modifier = modifier.aspectRatio(1f)) {
        // The board, painted once into an image for its style, seats' colours, pawn count, and size, on a layer of its own:
        // a frame of a move or a turn's ring never draws it again.
        val surface = BoardSurface(theme.board, layout, colours, pawns)
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { }) {
            drawCachedSurface(surface.copy(density = density)) {
                with(theme.board) { drawBoard(layout, size.minDimension / layout.size, colours, pawns) }
            }
        }
        // On a layer of its own too, drawn again only as the turn passes.
        val turnSeat = if (state.isFinished) null else state.currentPlayer.seat
        if (turnSeat != null) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { }) { drawTurnYard(layout, size.minDimension / layout.size, turnSeat) }
        }
        // The pawns standing still, on a layer of their own: drawn again as a move starts and ends, not on its frames.
        val animating = motion.moving
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { }
                .pointerInput(layout) {
                    detectTapGestures { tap ->
                        val unit = size.width.toFloat() / layout.size
                        val picked = pawnAt(tap / unit, currentMarks, currentMovable)
                        currentMoves.firstOrNull { picked != null && it.pawn == picked.pawn && it.ownerIn(currentState) == picked.player }?.let(currentOnMove)
                    }
                },
        ) {
            val unit = size.minDimension / layout.size
            for (mark in marks) {
                // The moving pawn and any it captured are drawn on their way, below; the rest of their mark stays.
                val pawns = if (animating == null) mark.pawns else mark.pawns.filterNot { motion.isAnimated(mark.player, it) }
                if (pawns.isEmpty()) continue
                val colour = pawnColours[mark.player]
                val ringed = animating == null && pawns.any { PawnRef(mark.player, it) in movable }
                if (mark.home) with(theme.board) { drawHomeSpot(mark.centre * unit, mark.radius * unit) }
                with(theme.pawns) { drawPawn(mark.centre * unit, mark.radius * unit, colour, ringed) }
                if (pawns.size > 1) drawCount(textMeasurer, pawns.size, mark, unit)
            }
        }
        // The move under way: only this is drawn again on its frames, reading the move's clock as it draws, and only
        // over the board.
        if (animating != null) {
            BoundedArt(modifier = Modifier.fillMaxSize()) {
                with(motion) { drawMotion(size.minDimension / layout.size, theme.pawns) { pawnColours[it] } }
            }
        }
        BoardSemantics(state, seatColours, moves, onMove, Modifier.fillMaxSize())
    }
}

/**
 * A move being shown: [player]'s pawn (its owner's, a partner's if one was rolled for) on [path] (board-unit centres,
 * square by square), hopping along it unless it's leaving its yard, and [landingRadius] across where it lands (home's
 * smaller spot when it reaches [home]), capturing [captured].
 */
private class MovingPawn(
    val player: Int,
    val pawn: Int,
    val path: List<Offset>,
    val hops: Boolean,
    val landingRadius: Float,
    val home: Boolean,
    val captured: List<CapturedPawn>,
    val totalMillis: Int,
) {
    val landing: Offset get() = path.last()
    val travelMillis: Int get() = (path.size - 1) * PAWN_STEP_MILLIS
}

/** A pawn [ref] taken by a move, on its way back to its [yard] spot, where it is [yardRadius] across. */
private class CapturedPawn(val ref: PawnRef, val yard: Offset, val yardRadius: Float)

/**
 * The pawn move under way on the board, if any, and its [clock] (milliseconds from its start), read only while drawing,
 * so its frames redraw the move without recomposing the board.
 */
private class MoveMotion(val moving: MovingPawn?, private val clock: State<Float>) {
    private val millis: Float get() = clock.value

    fun isAnimated(player: Int, pawn: Int): Boolean =
        moving != null && ((moving.player == player && moving.pawn == pawn) || moving.captured.any { it.ref == PawnRef(player, pawn) })

    /**
     * The move at this moment, on a board of [unit] per square, in [pawns]' style and each player's colour ([colourOf]):
     * a capture's burst under the mover, a home flourish's ring round it and its sparks over it, and the captured pawns
     * in flight over all.
     */
    fun DrawScope.drawMotion(unit: Float, pawns: PawnStyle, colourOf: (Int) -> Color) {
        val moving = moving!!
        val millis = millis
        // How far through what follows the landing, from 0 as it lands.
        val after = ((millis - moving.travelMillis) / (moving.totalMillis - moving.travelMillis).coerceAtLeast(1)).coerceIn(0f, 1f)
        val landed = millis >= moving.travelMillis
        val mover = colourOf(moving.player)
        if (landed && moving.captured.isNotEmpty()) {
            drawCaptureBurst(moving.landing * unit, unit, (after / CAPTURE_BURST_SHARE).coerceAtMost(1f), colourOf(moving.captured.first().ref.player))
        }
        if (landed && moving.home) drawHomeRings(moving.landing * unit, unit, after, mover)
        val (centre, lift, radius) = moverPose(moving, if (landed && moving.home) after else 0f)
        if (lift > 0f) drawGroundShadow(centre * unit, radius * unit, lift / HOP_HEIGHT)
        with(pawns) { drawPawn((centre - Offset(0f, lift)) * unit, radius * unit, mover, ringed = false) }
        if (landed && moving.home) drawHomeSparks(moving.landing * unit, unit, after, mover)
        for (captured in moving.captured) {
            val from = moving.landing
            val height = CAPTURE_ARC + CAPTURE_ARC_PER_SQUARE * (captured.yard - from).getDistance()
            val flight = if (landed) after else 0f
            val ground = lerp(from, captured.yard, flight)
            val up = height * 4f * flight * (1f - flight)
            val size = PAWN_RADIUS + (captured.yardRadius - PAWN_RADIUS) * flight
            if (up > 0f) drawGroundShadow(ground * unit, size * unit, up / height)
            with(pawns) { drawPawn((ground - Offset(0f, up)) * unit, size * unit, colourOf(captured.ref.player), ringed = false) }
        }
    }

    /** Where the mover stands on the ground, how high it is off it, and how big it is, [home] of the way through its flourish. */
    private fun moverPose(moving: MovingPawn, home: Float): Triple<Offset, Float, Float> {
        val millis = millis
        val path = moving.path
        val step = (millis / PAWN_STEP_MILLIS).coerceIn(0f, (path.size - 1).toFloat())
        val index = step.toInt().coerceAtMost(path.size - 2)
        val fraction = step - index
        val last = index == path.size - 2
        val radius = if (last) PAWN_RADIUS + (moving.landingRadius - PAWN_RADIUS) * fraction else PAWN_RADIUS
        if (!moving.hops) {
            return Triple(lerp(path[index], path[index + 1], fraction), 0f, radius * (1f + YARD_EXIT_SWELL * sin(PI * fraction).toFloat()))
        }
        // Off one square and onto the next, gathering speed and slowing to land, so each square is a hop of its own.
        val across = fraction * fraction * (3f - 2f * fraction)
        val arc = 4f * fraction * (1f - fraction)
        val pop = if (home < HOME_POP_SHARE) HOME_POP * sin(PI * home / HOME_POP_SHARE).toFloat() else 0f
        return Triple(lerp(path[index], path[index + 1], across), HOP_HEIGHT * arc, radius * (1f + HOP_SWELL * arc + pop))
    }
}

/** A ring round the inside edge of [seat]'s yard, on a board of [unit] per square: whose turn it is. */
private fun DrawScope.drawTurnYard(layout: BoardLayout, unit: Float, seat: Int) {
    val outline = layout.yard(seat)
    val path = Path().apply {
        outline.forEachIndexed { i, corner -> if (i == 0) moveTo(corner.x * unit, corner.y * unit) else lineTo(corner.x * unit, corner.y * unit) }
        close()
    }
    // Stroked across the edge and clipped to the yard, so the ring lies wholly inside it.
    clipPath(path) {
        drawPath(path, BoardInk, style = Stroke(unit * TURN_RING_OUTER * 2, join = StrokeJoin.Round))
        drawPath(path, Color.White, style = Stroke(unit * TURN_RING_INNER * 2, join = StrokeJoin.Round))
    }
}

/** The shadow a pawn [radius] across casts on the ground at [centre] while it's off it, smaller and fainter the [height] (0 to 1) it is up. */
internal fun DrawScope.drawGroundShadow(centre: Offset, radius: Float, height: Float) {
    val width = 1.4f * radius * (1f - 0.35f * height)
    drawOval(
        Color.Black.copy(alpha = 0.22f * (1f - 0.5f * height)),
        Offset(centre.x - width / 2, centre.y + 0.55f * radius),
        Size(width, 0.34f * radius * (1f - 0.35f * height)),
    )
}

/** A pawn knocked out at [centre]: a ring of the captured pawn's [colour] spreading and fading, and streaks flung out, [progress] (0 to 1) through. */
private fun DrawScope.drawCaptureBurst(centre: Offset, unit: Float, progress: Float, colour: Color) {
    if (progress >= 1f) return
    val fade = 1f - progress
    val spread = 1f - fade * fade
    drawCircle(colour.copy(alpha = fade), unit * (0.35f + 0.75f * spread), centre, style = Stroke(unit * 0.14f * fade))
    for (i in 0 until BURST_STREAKS) {
        val angle = 2 * PI * (i + 0.5) / BURST_STREAKS
        val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        val inner = centre + direction * (unit * (0.4f + 0.8f * spread))
        val outer = inner + direction * (unit * 0.32f * fade)
        drawLine(BoardInk.copy(alpha = fade), inner, outer, strokeWidth = unit * 0.13f, cap = StrokeCap.Round)
        drawLine(BoardIvory.copy(alpha = fade), inner, outer, strokeWidth = unit * 0.07f, cap = StrokeCap.Round)
    }
}

/** Behind a pawn home at [centre]: a gold ring, then one of its [colour], spreading and fading, [progress] (0 to 1) through its flourish. */
private fun DrawScope.drawHomeRings(centre: Offset, unit: Float, progress: Float, colour: Color) {
    for ((delay, ring) in listOf(0f to BoardSparkGold, 0.18f to colour)) {
        val t = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (t <= 0f || t >= 1f) continue
        val fade = 1f - t
        drawCircle(ring.copy(alpha = fade), unit * (0.25f + 1.25f * (1f - fade * fade)), centre, style = Stroke(unit * 0.12f * fade))
    }
}

/** Over a pawn home at [centre]: sparks, gold and its [colour] by turns, flying out and twinkling away, [progress] (0 to 1) through its flourish. */
private fun DrawScope.drawHomeSparks(centre: Offset, unit: Float, progress: Float, colour: Color) {
    if (progress >= 1f) return
    val fade = 1f - progress
    val spread = 1f - fade * fade * fade
    for (i in 0 until HOME_SPARKS) {
        val angle = 2 * PI * i / HOME_SPARKS - PI / 2
        val reach = unit * (0.3f + (if (i % 2 == 0) 1.3f else 0.95f) * spread)
        val at = centre + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * reach
        val size = unit * 0.2f * fade * (0.75f + 0.25f * sin(PI * (progress * 6 + i)).toFloat())
        drawSpark(at, size, if (i % 2 == 0) BoardSparkGold else lerp(colour, Color.White, 0.3f), fade)
    }
}

/** A four-pointed spark [size] from [centre] to each point, outlined in ink. */
private fun DrawScope.drawSpark(centre: Offset, size: Float, colour: Color, alpha: Float) {
    val waist = size * 0.28f
    val spark = Path().apply {
        moveTo(centre.x, centre.y - size)
        lineTo(centre.x + waist, centre.y - waist)
        lineTo(centre.x + size, centre.y)
        lineTo(centre.x + waist, centre.y + waist)
        lineTo(centre.x, centre.y + size)
        lineTo(centre.x - waist, centre.y + waist)
        lineTo(centre.x - size, centre.y)
        lineTo(centre.x - waist, centre.y - waist)
        close()
    }
    drawPath(spark, colour.copy(alpha = alpha))
    drawPath(spark, BoardInk.copy(alpha = alpha), style = Stroke(size * 0.12f, join = StrokeJoin.Round))
}

/**
 * Follows [state]'s last move, animating it from where it started; the first state seen is shown as it is. A roll made
 * while a move is still playing out doesn't cut it short.
 */
@Composable
private fun rememberMoveMotion(state: GameState): MoveMotion {
    val latest = remember { arrayOf(state.lastEvent as? GameEvent.Moved) }
    val firstSeen = remember { latest[0] }
    val event = (state.lastEvent as? GameEvent.Moved)?.also { latest[0] = it } ?: latest[0]
    val reduce = groupReduceMotion(MotionGroup.PAWN_MOVE)
    val moving = remember(event) { if (event == null || event === firstSeen || reduce) null else movingPawn(state, event) }
    val clock = remember(event) { Animatable(0f) }
    var done by remember(event) { mutableStateOf(moving == null) }
    LaunchedEffect(event) {
        if (moving == null) return@LaunchedEffect
        clock.animateTo(moving.totalMillis.toFloat(), tween(moving.totalMillis, easing = LinearEasing))
        done = true
    }
    return MoveMotion(if (done) null else moving, clock.asState())
}

private fun movingPawn(state: GameState, event: GameEvent.Moved): MovingPawn {
    val layout = BoardLayout.of(state.board)
    val move = event.move
    // The pawn's owner: the mover, or a partner they rolled for.
    val owner = move.owner ?: event.player
    val seat = state.players[owner].seat
    val fromYard = move.from == PawnPosition.YARD_PROGRESS
    val path = if (fromYard) {
        listOf(layout.yardSpotOf(seat, move.pawn, state.rules.pawnsPerPlayer), layout.centreOf(seat, PawnPosition.of(0, state.board), 0))
    } else {
        (move.from..move.to).map { layout.centreOf(seat, PawnPosition.of(it, state.board), move.pawn) }
    }
    val home = move.to == state.board.homeProgress
    val captured = move.captures.map {
        CapturedPawn(it, layout.yardSpotOf(state.players[it.player].seat, it.pawn, state.rules.pawnsPerPlayer), layout.yardPawnRadius)
    }
    return MovingPawn(
        player = owner,
        pawn = move.pawn,
        path = path,
        hops = !fromYard,
        landingRadius = if (home) HOME_PAWN_RADIUS else PAWN_RADIUS,
        home = home,
        captured = captured,
        totalMillis = travelMillis(move) + afterLandingMillis(move, state.board.homeProgress),
    )
}

/** What the board's image is painted from, besides its size: [density] is the drawing's. */
private data class BoardSurface(
    val style: BoardStyle,
    val layout: BoardLayout,
    val colours: List<Color?>,
    val pawns: Int,
    val density: Float = 0f,
)

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

