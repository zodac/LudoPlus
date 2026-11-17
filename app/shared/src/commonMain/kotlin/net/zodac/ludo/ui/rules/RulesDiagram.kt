package net.zodac.ludo.ui.rules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import net.zodac.ludo.model.PawnPosition
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.rules_board_diagram_cd
import net.zodac.ludo.resources.rules_capture_diagram_cd
import net.zodac.ludo.resources.rules_turn_diagram_cd
import net.zodac.ludo.resources.rules_winning_diagram_cd
import net.zodac.ludo.ui.common.stringResource
import net.zodac.ludo.ui.game.BoardLayout
import net.zodac.ludo.ui.game.GameDie
import net.zodac.ludo.ui.game.HOME_PAWN_RADIUS
import net.zodac.ludo.ui.style.ClassicBoardStyle
import net.zodac.ludo.ui.style.ClassicPawnStyle
import net.zodac.ludo.ui.theme.BoardInk
import net.zodac.ludo.ui.theme.color
import org.jetbrains.compose.resources.StringResource

/** A pawn in a diagram: [seat]'s, standing at [centre] (board units). A [ghost] is where a pawn was, drawn faint. */
internal class DiagramPawn(val seat: Int, val centre: Offset, val radius: Float = 0.38f, val ghost: Boolean = false, val home: Boolean = false)

/** An arrow through [points] (board units), dashed for a pawn sent back rather than moved. */
internal class DiagramArrow(val points: List<Offset>, val dashed: Boolean = false)

/**
 * One diagram: the part of the classic board in [view] (board units), with [pawns] and [arrows] on it, and the die
 * showing [die] (if any) at [dieAt], its top-left corner in board units. TalkBack hears it as one image, [description].
 */
internal class Diagram(
    val view: Rect,
    val description: StringResource,
    val pawns: List<DiagramPawn> = emptyList(),
    val arrows: List<DiagramArrow> = emptyList(),
    val die: Int? = null,
    val dieAt: Offset = Offset.Zero,
)

/** How big the die is in a diagram, in squares. */
private const val DIE_SQUARES = 1.6f

/** The widest a diagram draws, so a tablet's card doesn't blow it up past what a phone shows. */
private val MAX_DIAGRAM_WIDTH = 420.dp

private val board = BoardLayout.geometry

/** Where [seat]'s pawn stands [progress] steps on from its start square (`BoardGeometry`'s progress). */
private fun at(seat: Int, progress: Int): Offset = BoardLayout.centreOf(seat, PawnPosition.of(progress, board), 0)

/** The centre of [seat]'s home triangle. */
private fun homeCentre(seat: Int): Offset = BoardLayout.homeTriangle(seat).let { (a, b, c) -> (a + b + c) / 3f }

/** The board: Red's whole route, a lap of the track from its start square, then up its home column to home. */
private val BOARD_DIAGRAM = Diagram(
    view = Rect(0f, 0f, BoardLayout.SIZE.toFloat(), BoardLayout.SIZE.toFloat()),
    description = Res.string.rules_board_diagram_cd,
    pawns = listOf(DiagramPawn(0, at(0, 0))) + (1..3).map { DiagramPawn(0, BoardLayout.yardSpot(0, it), radius = 0.5f) } +
        (1..3).flatMap { seat -> (0..3).map { DiagramPawn(seat, BoardLayout.yardSpot(seat, it), radius = 0.5f) } },
    arrows = listOf(DiagramArrow((0..<board.homeProgress).map { at(0, it) } + homeCentre(0))),
)

/** Your turn: a 6 brings a red pawn out of its yard onto its start square. */
private val TURN_DIAGRAM = Diagram(
    view = Rect(0f, 0f, 8f, 8f),
    description = Res.string.rules_turn_diagram_cd,
    // The pawn leaving is the yard's lower left, nearest the start square, so its arrow crosses no other pawn.
    pawns = listOf(DiagramPawn(0, BoardLayout.yardSpot(0, 2), radius = 0.5f, ghost = true), DiagramPawn(0, at(0, 0))) +
        listOf(0, 1, 3).map { DiagramPawn(0, BoardLayout.yardSpot(0, it), radius = 0.5f) },
    arrows = listOf(DiagramArrow(listOf(BoardLayout.yardSpot(0, 2), at(0, 0)))),
    die = 6,
    dieAt = Offset(6.2f, 0.2f),
)

/** Where the capture diagram's red pawn starts and how far it moves: onto Green's pawn, on the square before a corner. */
private const val CAPTURE_FROM = 1
private const val CAPTURE_ROLL = 4

/** Captures: Red rolls 4 onto Green's pawn, which goes back to Green's yard; Blue sits safe on a star square. */
private val CAPTURE_DIAGRAM = Diagram(
    view = Rect(1f, 0f, 13f, 9f),
    description = Res.string.rules_capture_diagram_cd,
    pawns = listOf(
        DiagramPawn(0, at(0, CAPTURE_FROM), ghost = true),
        DiagramPawn(0, at(0, CAPTURE_FROM + CAPTURE_ROLL)),
        DiagramPawn(1, BoardLayout.yardSpot(1, 2), radius = 0.5f),
        DiagramPawn(3, at(0, board.starOffset)),
    ),
    arrows = listOf(
        DiagramArrow((CAPTURE_FROM..CAPTURE_FROM + CAPTURE_ROLL).map { at(0, it) }),
        DiagramArrow(listOf(at(0, CAPTURE_FROM + CAPTURE_ROLL), BoardLayout.yardSpot(1, 2)), dashed = true),
    ),
    die = CAPTURE_ROLL,
    dieAt = Offset(11.2f, 7.2f),
)

/** How far from home the winning diagram's pawn is: the roll it needs. */
private const val WINNING_ROLL = 3

/** Winning: a red pawn 3 squares from home needs exactly a 3; another is already home. */
private val WINNING_DIAGRAM = Diagram(
    view = Rect(0f, 5.5f, 9f, 9.5f),
    description = Res.string.rules_winning_diagram_cd,
    pawns = listOf(
        DiagramPawn(0, at(0, board.homeProgress - WINNING_ROLL)),
        DiagramPawn(0, BoardLayout.homeSpot(0, 0), radius = HOME_PAWN_RADIUS, home = true),
    ),
    arrows = listOf(DiagramArrow((board.homeProgress - WINNING_ROLL..<board.homeProgress).map { at(0, it) } + Offset(6.2f, 7.5f))),
    die = WINNING_ROLL,
    dieAt = Offset(7.3f, 5.6f),
)

/** The diagram each Rules card shows, by its heading. */
enum class RulesDiagramKind(internal val diagram: Diagram) {
    BOARD(BOARD_DIAGRAM),
    TURN(TURN_DIAGRAM),
    CAPTURE(CAPTURE_DIAGRAM),
    WINNING(WINNING_DIAGRAM),
}

/**
 * A Rules card's diagram ([kind]): a part of the classic board in the default seat colours, with its pawns, arrows, and
 * die. It draws only; TalkBack hears it as one image described in words.
 */
@Composable
fun RulesDiagram(kind: RulesDiagramKind, modifier: Modifier = Modifier) {
    val diagram = kind.diagram
    val view = diagram.view
    val colours = List(board.seats) { PlayerColour.defaultFor(it).color }
    val description = stringResource(diagram.description)
    BoxWithConstraints(
        modifier = modifier
            .widthIn(max = MAX_DIAGRAM_WIDTH)
            .fillMaxWidth()
            .aspectRatio(view.width / view.height)
            .clip(RoundedCornerShape(8.dp))
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Image
            },
    ) {
        val unitDp = maxWidth / view.width
        Canvas(modifier = Modifier.fillMaxSize()) {
            val unit = size.width / view.width
            translate(-view.left * unit, -view.top * unit) {
                with(ClassicBoardStyle) { drawBoard(unit, colours) }
                for (arrow in diagram.arrows) drawArrow(arrow, unit)
                for (pawn in diagram.pawns) {
                    val colour = colours[pawn.seat].let { if (pawn.ghost) it.copy(alpha = 0.35f) else it }
                    if (pawn.home) with(ClassicBoardStyle) { drawHomeSpot(pawn.centre * unit, pawn.radius * unit) }
                    with(ClassicPawnStyle) { drawPawn(pawn.centre * unit, pawn.radius * unit, colour, ringed = false) }
                }
            }
        }
        diagram.die?.let { value ->
            Box(
                modifier = Modifier
                    .offset(unitDp * (diagram.dieAt.x - view.left), unitDp * (diagram.dieAt.y - view.top))
                    .size(unitDp * DIE_SQUARES),
            ) {
                GameDie(value = value, roll = null, canRoll = false, onRoll = {}, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** [arrow] in ink, through its points' centres, with a head at its end. */
private fun DrawScope.drawArrow(arrow: DiagramArrow, unit: Float) {
    val points = arrow.points.map { it * unit }
    val width = unit * 0.14f
    val ink = BoardInk.copy(alpha = 0.85f)
    val end = points.last()
    val before = points[points.size - 2]
    val angle = atan2(end.y - before.y, end.x - before.x)
    val head = unit * 0.42f
    // The line stops short of the tip, so its square end doesn't poke out of the head.
    val shaftEnd = end - Offset(cos(angle), sin(angle)) * (head * 0.6f)
    val line = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).dropLast(1).forEach { lineTo(it.x, it.y) }
        lineTo(shaftEnd.x, shaftEnd.y)
    }
    val dashes = if (arrow.dashed) PathEffect.dashPathEffect(floatArrayOf(unit * 0.3f, unit * 0.22f)) else null
    drawPath(line, ink, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dashes))
    fun corner(turn: Double) = end - Offset(cos(angle + turn).toFloat(), sin(angle + turn).toFloat()) * head
    val tip = Path().apply {
        moveTo(end.x, end.y)
        corner(0.45).let { lineTo(it.x, it.y) }
        corner(-0.45).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(tip, ink)
}
