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
import androidx.compose.runtime.SideEffect
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.ludoplus.data.settings.MotionGroup
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.Portal
import net.zodac.ludoplus.model.portalAt
import net.zodac.ludoplus.model.portals
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.ui.common.BoundedArt
import net.zodac.ludoplus.ui.common.groupReduceMotion
import net.zodac.ludoplus.ui.theme.BoardEmptySeat
import net.zodac.ludoplus.ui.theme.BoardInk
import net.zodac.ludoplus.ui.theme.BoardIvory
import net.zodac.ludoplus.ui.style.LocalGameVisualTheme
import net.zodac.ludoplus.ui.style.BoardParts
import net.zodac.ludoplus.ui.style.BoardStyle
import net.zodac.ludoplus.ui.style.PawnStyle
import net.zodac.ludoplus.ui.style.PaintedSurface
import net.zodac.ludoplus.ui.theme.BoardSparkGold
import net.zodac.ludoplus.ui.theme.color

/** The ring round a pawn that can be moved: the brand violet, "press this" (`UI.md`, "Colour"). */

/** How much a pawn leaving its yard swells on its way to its start square. */
private const val YARD_EXIT_SWELL = 0.18f

/** How high a moving pawn rises at the top of each hop from square to square, in squares. */
internal const val HOP_HEIGHT = 0.42f

/** How much a hopping pawn swells at the top of its hop, as if nearer. */
internal const val HOP_SWELL = 0.08f

/** How long the pawns on a square take to make room for one landing on it, ending as it lands: half a hop, from the square's edge on. */
private const val MAKE_ROOM_MILLIS = PAWN_STEP_MILLIS / 2

/** How small a pawn going through a portal gets, of its size. */
private const val MIN_WARP_SIZE = 0.08f

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
 * the player whose turn it is has a ring round its inside edge, and that of one with a power-up in force has it, and how
 * many are left ([InForce]). The pawns that [moves] can move
 * are ringed (a partner's too, when the player rolls for one), and a tap near one, or a screen reader's action on its
 * square ([BoardSemantics]), makes its move ([onMove]). While [swapping], the pawns ringed and tapped are those the move
 * ([Move.swap]) can swap with, once the pawn has landed on the Swap.
 *
 * A move hops square by square (out of the yard, it glides to the start square). The pawns it comes to share a square with
 * make room as it comes onto the square, not before. A shield goes with its pawn. A pawn it captures is knocked out
 * in a burst on its square and thrown back to its yard; a pawn that reaches home pops in a ring of sparks. None of it
 * plays while the Pawn moves motion is off ([MotionGroup.PAWN_MOVE]).
 *
 * While a portal is pressed, it and its pair are picked out, and everything else on the board fades; as all but the pawn
 * that moves and those it can swap with does while [swapping]. A tap on a power-up shows its name, and a
 * long press what it does too, until a tap puts it away.
 */
@Composable
fun LudoBoard(
    state: GameState,
    seatColours: List<PlayerColour?>,
    moves: List<Move>,
    onMove: (Move) -> Unit,
    modifier: Modifier = Modifier,
    swapping: Boolean = false,
) {
    val colours = remember(seatColours) { seatColours.map { it?.color } }
    val movable = remember(moves, state, swapping) { moves.mapNotNull { it.picked(state, swapping) }.toSet() }
    val marks = remember(state.players) { pawnMarks(state) }
    val textMeasurer = rememberTextMeasurer()
    val currentMarks by rememberUpdatedState(marks)
    val currentMovable by rememberUpdatedState(movable)
    val currentMoves by rememberUpdatedState(moves)
    val currentState by rememberUpdatedState(state)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentSwapping by rememberUpdatedState(swapping)
    // The squares claimed as they're shown: as they were, while a move plays out.
    val shownClaims = remember { arrayOf(state.claimed) }
    // The power-ups on the track, as they're shown: likewise, but for one taken, which goes with its move.
    val shownPowerUps = remember { arrayOf(state.powerUps) }
    // What's in force for each player, as it's shown: likewise.
    val shownInForce = remember { arrayOf(state.players.map(::InForce)) }
    // The portal being pressed, and the power-up whose popup is up.
    var pressedPortal by remember { mutableStateOf<Portal?>(null) }
    var tip by remember { mutableStateOf<PowerUpTip?>(null) }
    val motion = rememberMoveMotion(state)
    val ghostMotion = rememberGhostMotion(state)
    val vanish = rememberVanishMotion(state)
    val theme = LocalGameVisualTheme.current
    val layout = BoardLayout.of(state.board)
    // Read here, so the board's layer below holds a number rather than the state, which changes every move.
    val pawns = state.rules.pawnsPerPlayer
    val pawnColours = remember(state.players, colours) { state.players.map { colours.getOrNull(it.seat) ?: BoardEmptySeat } }
    Box(modifier = modifier.aspectRatio(1f)) {
        fun DrawScope.drawMark(mark: PawnMark, unit: Float, ringed: Boolean) {
            val shielded = mark.pawns.any { state.isShielded(mark.player, it) || PawnRef(mark.player, it) in motion.moving?.brokenShields.orEmpty() }
            if (shielded) drawShieldBubble(mark.centre * unit, mark.radius * unit)
            if (mark.home) with(theme.board) { drawHomeSpot(mark.centre * unit, mark.radius * unit) }
            with(theme.pawns) { drawPawn(mark.centre * unit, mark.radius * unit, pawnColours[mark.player], ringed) }
            // At the top right, for pawns sharing a square.
            if (mark.pawns.size > 1) drawCount(textMeasurer, mark.pawns.size, (mark.centre + Offset(mark.radius * 0.8f, -mark.radius * 0.8f)) * unit, unit * PAWN_COUNT_RADIUS)
        }
        // A Swap's pawn, landed on it, and those it can be with, once no move is playing out: drawn over the rest, not with it.
        val pickedOut = if (swapping && motion.moving == null) {
            val swappers = moves.mapNotNull { it.picked(state, swapping = false) }
            marks.filter { mark -> mark.pawns.any { pawn -> PawnRef(mark.player, pawn).let { it in movable || it in swappers } } }
        } else {
            emptyList()
        }
        // All that's drawn but what's picked out, faded together: a press is read by the layer alone, so it redraws none of it.
        Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = if (pressedPortal == null && pickedOut.isEmpty()) 1f else BOARD_FADE }) {
            // The board, painted once (off the main thread) into an image for its style, seats' colours, pawn count, squares claimed, and size,
            // on a layer of its own: a frame of a move or a turn's ring never draws it again.
            val lastStanding = state.rules.lastStanding
            val stage = state.suddenDeathStage
            // The squares claimed: painted again as one changes hands, once the pawn that claims it has landed.
            if (motion.moving == null) shownClaims[0] = state.claimed
            val claims = shownClaims[0].mapValues { (_, player) -> pawnColours[player] }
            val parts = if (lastStanding) BoardParts(starts = stage < 1, stars = stage < 2, homes = false) else BoardParts(claims = claims)
            // A last-standing yard's spots are its tally, drawn with the pawns.
            val spots = if (lastStanding) 0 else pawns
            val surface = BoardSurface(theme.board, layout, colours, spots, parts)
            PaintedSurface(surface, modifier = Modifier.fillMaxSize().graphicsLayer { }) {
                with(theme.board) { drawBoard(layout, size.minDimension / layout.size, colours, spots, parts) }
            }
            // The arena, drawn again only as sudden death comes on, or takes squares.
            if (lastStanding) {
                val lost = state.lostSquares - vanish.squares
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { }) { drawArena(layout, size.minDimension / layout.size, stage, lost) }
                if (vanish.squares.isNotEmpty()) BoundedArt(modifier = Modifier.fillMaxSize()) { with(vanish) { drawVanishing(layout, size.minDimension / layout.size, stage) } }
            }
            // The portals, under the pawns: drawn once.
            if (state.rules.portals) Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { }) { drawPortals(layout, size.minDimension / layout.size) }
            // The power-ups, under the pawns: drawn again as one comes or goes.
            if (state.rules.powerUps) {
                // One that comes in place of one taken shows once the pawn that took it has landed.
                shownPowerUps[0] = if (motion.moving == null) state.powerUps else shownPowerUps[0].filter { (square, powerUp) -> state.powerUps[square] == powerUp }
                val powerUps = shownPowerUps[0]
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { }) { drawPowerUps(layout, size.minDimension / layout.size, powerUps) }
            }
            // On a layer of its own too, drawn again only as the turn passes.
            val turnSeat = if (state.isFinished) null else state.currentPlayer.seat
            if (turnSeat != null) {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { }) { drawTurnYard(layout, size.minDimension / layout.size, turnSeat) }
            }
            // What's in force for each player, on their yards: as it was while a move plays out, so a Double shows as the pawn lands on it.
            if (motion.moving == null) shownInForce[0] = state.players.map(::InForce)
            val inForce = shownInForce[0]
            if (state.rules.powerUps) {
                val seats = state.players.map { it.seat }
                val places = remember(layout, seats, pawns) { seats.map { layout.yardBadges(it, pawns, YARD_BADGE_ROOM) } }
                if (inForce.any { it.any }) {
                    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { }) { drawInForce(places, size.minDimension / layout.size, inForce, textMeasurer) }
                }
            }
            // The pawns standing still, on a layer of their own: drawn again as a move starts and ends, not on its frames.
            val animating = motion.moving
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { }
                    .pointerInput(layout) {
                        fun point(at: Offset) = at / (size.width.toFloat() / layout.size)
                        // A tile's tap is its own, not a move, unless it's for a pawn.
                        fun tap(point: Offset) {
                            val now = currentState
                            val tile = tileAt(point, layout, specialSquares(now))?.takeUnless { pawnTakesTap(point, layout, it, currentMarks, currentMovable) }
                            if (tile != null) {
                                tip = if (tip == null && tile in now.powerUps) PowerUpTip(tile, detailed = false) else null
                                return
                            }
                            val picked = pawnAt(point, currentMarks, currentMovable)
                            currentMoves.firstOrNull { picked != null && it.picked(now, currentSwapping) == picked }?.let(currentOnMove)
                        }
                        detectTapGestures(
                            onPress = { at ->
                                if (currentState.rules.portals) {
                                    pressedPortal = tileAt(point(at), layout, specialSquares(currentState))?.let(layout.geometry::portalAt)
                                    try {
                                        awaitRelease()
                                    } finally {
                                        pressedPortal = null
                                    }
                                }
                            },
                            onLongPress = { at ->
                                val tile = tileAt(point(at), layout, specialSquares(currentState))
                                when {
                                    tile == null -> tap(point(at))
                                    tile in currentState.powerUps -> tip = PowerUpTip(tile, detailed = true)
                                    // A portal held to be looked at: a pawn on it isn't moved.
                                    else -> Unit
                                }
                            },
                            onTap = { tap(point(it)) },
                        )
                    },
            ) {
                val unit = size.minDimension / layout.size
                if (lastStanding) drawFallen(state, layout, unit, { pawnColours[it] }, theme.pawns) { player, pawn -> animating != null && motion.isAnimated(player, pawn) }
                for (mark in marks - pickedOut.toSet()) {
                    // The moving pawn and any it captured are drawn on their way, below; the rest of their mark stays.
                    // So are those making room for it.
                    val pawns = mark.pawns.filterNot { motion.isAnimated(mark.player, it) || motion.isShifting(mark.player, it) || ghostMotion.isCaught(PawnRef(mark.player, it)) }
                    if (pawns.isEmpty()) continue
                    drawMark(mark.copy(pawns = pawns), unit, ringed = animating == null && pawns.any { PawnRef(mark.player, it) in movable })
                }
            }
            // The ghosts over the pawns, the drifting one and any it's catching on a layer of their own.
            if (state.rules.ghosts) {
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { }) { with(ghostMotion) { drawStill(state, layout, size.minDimension / layout.size) } }
                if (ghostMotion.drifting != null) {
                    BoundedArt(modifier = Modifier.fillMaxSize()) {
                        with(ghostMotion) { drawDrift(size.minDimension / layout.size, theme.pawns, { pawnColours[it] }, moving = animating?.let { PawnRef(it.player, it.pawn) }) }
                    }
                }
            }
            // The move under way: only this is drawn again on its frames, reading the move's clock as it draws, and only
            // over the board.
            if (animating != null) {
                BoundedArt(modifier = Modifier.fillMaxSize()) {
                    val unit = size.minDimension / layout.size
                    with(motion) {
                        drawShifting { drawMark(it, unit, ringed = false) }
                        drawMotion(unit, theme.pawns) { pawnColours[it] }
                    }
                }
            }
        }
        // The pawns of a Swap, bright over the rest: ringed, but for the one that moves.
        if (pickedOut.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (mark in pickedOut) drawMark(mark, size.minDimension / layout.size, ringed = mark.pawns.any { PawnRef(mark.player, it) in movable })
            }
        }
        // The pair pressed, bright over the rest.
        pressedPortal?.let { pressed -> Canvas(modifier = Modifier.fillMaxSize()) { drawPressedPortal(layout, size.minDimension / layout.size, pressed) } }
        // Gone once its power-up is taken, for good.
        LaunchedEffect(state.powerUps) { if (tip?.square !in state.powerUps) tip = null }
        tip?.let { shown ->
            state.powerUps[shown.square]?.let { PowerUpTooltip(layout, shown, it, onDismiss = { tip = null }, Modifier.matchParentSize()) }
        }
        BoardSemantics(state, seatColours, moves, onMove, Modifier.fillMaxSize(), swapping, onDescribe = { tip = PowerUpTip(it, detailed = true) })
    }
}

/**
 * A move being shown: [player]'s pawn (its owner's, a partner's if one was rolled for) on [path] (board-unit centres,
 * square by square), hopping along it unless it's leaving its yard, and [landingRadius] across where it lands (smaller on
 * its spot when it reaches [home], or on a square it comes to share), sending [sent], and the pawns in [shifts] making
 * room. Through a portal, [path] ends where it goes in, and it
 * comes out at [warpTo].
 */
private class MovingPawn(
    val player: Int,
    val pawn: Int,
    val path: List<Offset>,
    val hops: Boolean,
    val landingRadius: Float,
    val home: Boolean,
    val sent: List<SentPawn>,
    val shifts: List<PawnShift>,
    val totalMillis: Int,
    /** How long the pawns it captures that come straight back out take to, at its end. */
    val recallMillis: Int,
    val warpTo: Offset? = null,
    /** The power-up it takes, there till it lands. */
    val powerUp: PowerUp? = null,
    /** The pawns whose shields it breaks, each there till the move has played out. */
    val brokenShields: List<PawnRef> = emptyList(),
    /** Whether it has a shield as it sets off. */
    val shielded: Boolean = false,
    /** How long till it's off the track, where its shield goes: null if it stays on it. */
    val offTrackMillis: Int? = null,
) {
    val landing: Offset get() = warpTo ?: path.last()

    /** How long it hops for, before any portal. */
    val hopMillis: Int get() = (path.size - 1) * PAWN_STEP_MILLIS
    val travelMillis: Int get() = hopMillis + if (warpTo != null) PORTAL_WARP_MILLIS else 0

    /** Whether it has a shield, [millis] from the move's start: one it takes, from when it lands. */
    fun isShielded(millis: Float): Boolean =
        (shielded && (offTrackMillis == null || millis < offTrackMillis)) || (powerUp == PowerUp.SHIELD && millis >= travelMillis)

    /** How far [shift]'s pawns have made room (0 to 1), [millis] from the move's start: all of it as the pawn they make it for lands. */
    fun shifted(shift: PawnShift, millis: Float): Float {
        val isMover = shift.arriving == PawnRef(player, pawn)
        val end = if (isMover) travelMillis else totalMillis
        // From when it starts to come out of a portal, if it goes through one.
        val length = if (isMover && warpTo != null) PORTAL_WARP_MILLIS / 2 else MAKE_ROOM_MILLIS
        return ((millis - (end - length)) / length).coerceIn(0f, 1f)
    }
}

/**
 * Another pawn, [ref], that a move sends from [from] to [to], where it is [toRadius] across: one taken, on its way back to
 * its yard spot, or one a power-up moves ([taken] false). A shield on it ([shielded]) goes with it, but is lost as it's taken.
 * One taken that a banked Call Up brings straight back out then goes on to where it stands in [recall].
 */
private class SentPawn(
    val ref: PawnRef,
    val from: Offset,
    val to: Offset,
    val fromRadius: Float,
    val toRadius: Float,
    val taken: Boolean,
    val shielded: Boolean,
    val recall: PawnMark? = null,
)

/** The squares of [state]'s board with something of the mode's own on them to tap: portals, or power-ups. */
private fun specialSquares(state: GameState): Collection<Int> =
    if (state.rules.portals) state.board.portals.flatMap { listOf(it.first, it.second) } else state.powerUps.keys

/**
 * The pawn move under way on the board, if any, and its [clock] (milliseconds from its start), read only while drawing,
 * so its frames redraw the move without recomposing the board.
 */
private class MoveMotion(val moving: MovingPawn?, private val clock: State<Float>) {
    private val millis: Float get() = clock.value

    fun isAnimated(player: Int, pawn: Int): Boolean =
        moving != null && ((moving.player == player && moving.pawn == pawn) || moving.sent.any { it.ref == PawnRef(player, pawn) })

    /** Whether [player]'s [pawn] makes room for the move: drawn by [drawShifting], not with the pawns standing still. */
    fun isShifting(player: Int, pawn: Int): Boolean = moving != null && moving.shifts.any { it.to.player == player && pawn in it.to.pawns }

    /** The pawns making room at this moment, each drawn by [draw]. */
    fun drawShifting(draw: (PawnMark) -> Unit) {
        val moving = moving!!
        for (shift in moving.shifts) {
            val shifted = moving.shifted(shift, clock.value)
            draw(shift.to.copy(centre = lerp(shift.from.centre, shift.to.centre, shifted), radius = shift.from.radius + (shift.to.radius - shift.from.radius) * shifted))
        }
    }

    /**
     * The move at this moment, on a board of [unit] per square, in [pawns]' style and each player's colour ([colourOf]):
     * a capture's burst under the mover, a home flourish's ring round it and its sparks over it, and the captured pawns
     * in flight over all.
     */
    fun DrawScope.drawMotion(unit: Float, pawns: PawnStyle, colourOf: (Int) -> Color) {
        val moving = moving!!
        val millis = millis
        // How far through what follows the landing, from 0 as it lands.
        val flightEnd = moving.totalMillis - moving.recallMillis
        val after = ((millis - moving.travelMillis) / (flightEnd - moving.travelMillis).coerceAtLeast(1)).coerceIn(0f, 1f)
        val recalled = if (moving.recallMillis == 0) 0f else ((millis - flightEnd) / moving.recallMillis).coerceIn(0f, 1f)
        val landed = millis >= moving.travelMillis
        val mover = colourOf(moving.player)
        if (landed) {
            for (taken in moving.sent.filter { it.taken }.distinctBy { it.from }) {
                drawCaptureBurst(taken.from * unit, unit, (after / CAPTURE_BURST_SHARE).coerceAtMost(1f), colourOf(taken.ref.player))
            }
        }
        if (landed && moving.home) drawHomeRings(moving.landing * unit, unit, after, mover)
        if (moving.powerUp != null && millis < moving.hopMillis) drawPowerUp(moving.powerUp, moving.path.last() * unit, unit * POWER_UP_RADIUS)
        val warp = ((millis - moving.hopMillis) / PORTAL_WARP_MILLIS).coerceIn(0f, 1f)
        if (moving.warpTo != null && warp > 0f && warp < 1f) drawWarpTrail(moving.path.last() * unit, moving.warpTo * unit, unit, warp, mover)
        val (centre, lift, radius) = moverPose(moving, if (landed && moving.home) after else 0f)
        if (lift > 0f) drawGroundShadow(centre * unit, radius * unit, lift / HOP_HEIGHT)
        if (moving.isShielded(millis)) drawShieldBubble((centre - Offset(0f, lift)) * unit, radius * unit)
        with(pawns) { drawPawn((centre - Offset(0f, lift)) * unit, radius * unit, mover, ringed = false) }
        if (landed && moving.home) drawHomeSparks(moving.landing * unit, unit, after, mover)
        for (sent in moving.sent) {
            val height = CAPTURE_ARC + CAPTURE_ARC_PER_SQUARE * (sent.to - sent.from).getDistance()
            val flight = if (landed) after else 0f
            if (sent.recall != null && recalled > 0f) {
                val swell = 1f + YARD_EXIT_SWELL * sin(PI * recalled).toFloat()
                val size = (sent.toRadius + (sent.recall.radius - sent.toRadius) * recalled) * swell
                with(pawns) { drawPawn(lerp(sent.to, sent.recall.centre, recalled) * unit, size * unit, colourOf(sent.ref.player), ringed = false) }
                continue
            }
            val ground = lerp(sent.from, sent.to, flight)
            val up = height * 4f * flight * (1f - flight)
            val size = sent.fromRadius + (sent.toRadius - sent.fromRadius) * flight
            if (up > 0f) drawGroundShadow(ground * unit, size * unit, up / height)
            if (sent.shielded && !(sent.taken && landed)) drawShieldBubble((ground - Offset(0f, up)) * unit, size * unit)
            with(pawns) { drawPawn((ground - Offset(0f, up)) * unit, size * unit, colourOf(sent.ref.player), ringed = false) }
        }
    }

    /** Where the mover stands on the ground, how high it is off it, and how big it is, [home] of the way through its flourish. */
    private fun moverPose(moving: MovingPawn, home: Float): Triple<Offset, Float, Float> {
        val millis = millis
        val path = moving.path
        if (moving.warpTo != null && millis >= moving.hopMillis) {
            // In at one portal, shrinking away, and out at the other, growing back.
            val warp = ((millis - moving.hopMillis) / PORTAL_WARP_MILLIS).coerceIn(0f, 1f)
            val radius = if (warp < 0.5f) PAWN_RADIUS else moving.landingRadius
            return Triple(if (warp < 0.5f) path.last() else moving.warpTo, 0f, radius * abs(1f - 2f * warp).coerceAtLeast(MIN_WARP_SIZE))
        }
        val step = (millis / PAWN_STEP_MILLIS).coerceIn(0f, (path.size - 1).toFloat())
        val index = step.toInt().coerceAtMost(path.size - 2)
        val fraction = step - index
        val last = index == path.size - 2 && moving.warpTo == null
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
 * while a move is still playing out doesn't cut it short. The shields pawns set off with are those of the state shown before it.
 */
@Composable
private fun rememberMoveMotion(state: GameState): MoveMotion {
    val latest = remember { arrayOf(state.lastEvent as? GameEvent.Moved) }
    val firstSeen = remember { latest[0] }
    val event = (state.lastEvent as? GameEvent.Moved)?.also { latest[0] = it } ?: latest[0]
    val reduce = groupReduceMotion(MotionGroup.PAWN_MOVE)
    val shown = remember { arrayOf(state) }
    val moving = remember(event) { if (event == null || event === firstSeen || reduce || event.keptPlace) null else movingPawn(state, event, before = shown[0]) }
    SideEffect { shown[0] = state }
    val clock = remember(event) { Animatable(0f) }
    var done by remember(event) { mutableStateOf(moving == null) }
    LaunchedEffect(event) {
        if (moving == null) return@LaunchedEffect
        clock.animateTo(moving.totalMillis.toFloat(), tween(moving.totalMillis, easing = LinearEasing))
        done = true
    }
    return MoveMotion(if (done) null else moving, clock.asState())
}

/** [event]'s move, [state] being the game after it, and [before] the one it was made in. */
private fun movingPawn(state: GameState, event: GameEvent.Moved, before: GameState): MovingPawn {
    val layout = BoardLayout.of(state.board)
    val move = event.move
    // The pawn's owner: the mover, or a partner they rolled for.
    val owner = move.owner ?: event.player
    val seat = state.players[owner].seat
    val fromYard = move.from == PawnPosition.YARD_PROGRESS
    val marks = moveMarks(state, event)
    val landing = marks.after(PawnRef(owner, move.pawn))
    // On to where the roll takes it, over any lost squares, and where that's onto a shield, back to where it settles.
    val steps = (move.from..move.rolledTo) - move.skipped.toSet() + (move.bouncedFrom?.let { it - 1 downTo move.to } ?: IntRange.EMPTY)
    val squares = if (fromYard) {
        listOf(layout.yardSpotOf(seat, move.pawn, state.rules.pawnsPerPlayer), layout.centreOf(seat, PawnPosition.of(0, state.board), 0))
    } else {
        steps.map { layout.centreOf(seat, PawnPosition.of(it, state.board), move.pawn, state.rules.pawnsPerPlayer) }
    }
    val effect = event.effect
    val warps = move.via != null || effect?.movedTo != null
    // Onto its own share of the square it lands on.
    val path = if (warps) squares else squares.dropLast(1) + landing.centre
    val home = event.endedAt == state.board.homeProgress
    fun sent(ref: PawnRef, taken: Boolean): SentPawn {
        val (from, to) = marks.before(ref) to marks.after(ref)
        val shielded = before.isShielded(ref.player, ref.pawn)
        if (ref !in event.recalled) return SentPawn(ref, from.centre, to.centre, from.radius, to.radius, taken, shielded)
        val side = state.players[ref.player]
        val spot = layout.yardSpotOf(side.seat, ref.pawn, side.pawns.size)
        return SentPawn(ref, from.centre, spot, from.radius, layout.yardPawnRadiusOf(side.pawns.size), taken, shielded, recall = to)
    }
    val sent = event.allCaptures.map { sent(it, taken = true) } +
        listOfNotNull(effect?.swapped, effect?.calledUp?.let { PawnRef(owner, it) }).map { sent(it, taken = false) }
    val travelling = sent.map { it.ref }.toSet() + PawnRef(owner, move.pawn)
    return MovingPawn(
        player = owner,
        pawn = move.pawn,
        path = path,
        hops = !fromYard,
        landingRadius = landing.radius,
        home = home,
        sent = sent,
        shifts = marks.shifts { it in travelling },
        totalMillis = moveMillis(event, state.board.homeProgress),
        recallMillis = if (event.recalled.isEmpty()) 0 else RECALL_MILLIS,
        powerUp = effect?.powerUp,
        brokenShields = move.brokenShields + effect?.brokenShields.orEmpty(),
        warpTo = if (warps) landing.centre else null,
        shielded = before.isShielded(owner, move.pawn),
        offTrackMillis = steps.indexOfFirst { it >= state.board.trackSteps }.takeIf { it >= 0 }?.let { it * PAWN_STEP_MILLIS },
    )
}

/** What the board's image is painted from, besides its size and density. */
private data class BoardSurface(
    val style: BoardStyle,
    val layout: BoardLayout,
    val colours: List<Color?>,
    val pawns: Int,
    val parts: BoardParts,
)

/** How big the count on a pawn is, across from its centre, in squares. */
internal const val PAWN_COUNT_RADIUS = 0.24f

/** [count] on a badge [badgeRadius] from [badgeCentre]. */
internal fun DrawScope.drawCount(textMeasurer: TextMeasurer, count: Int, badgeCentre: Offset, badgeRadius: Float) {
    drawCircle(BoardInk, badgeRadius, badgeCentre)
    val text = textMeasurer.measure(
        count.toString(),
        TextStyle(color = BoardIvory, fontSize = (badgeRadius * 1.3f).toSp(), fontWeight = FontWeight.Bold),
    )
    drawText(text, topLeft = badgeCentre - Offset(text.size.width / 2f, text.size.height / 2f))
}

private operator fun Offset.div(by: Float) = Offset(x / by, y / by)

