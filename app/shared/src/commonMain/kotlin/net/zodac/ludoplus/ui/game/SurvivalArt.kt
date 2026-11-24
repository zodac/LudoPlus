package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.min
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.ui.style.PawnStyle
import net.zodac.ludoplus.ui.theme.ArenaFloors
import net.zodac.ludoplus.ui.theme.ArenaHeats
import net.zodac.ludoplus.ui.theme.BoardInk
import net.zodac.ludoplus.ui.theme.BoardSparkGold
import net.zodac.ludoplus.ui.theme.FallenCross
import net.zodac.ludoplus.ui.theme.FallenPawn
import net.zodac.ludoplus.ui.theme.SwordHilt
import net.zodac.ludoplus.ui.theme.SwordSteel
import net.zodac.ludoplus.ui.theme.UnsafeCross

/** The emblem's radius, in squares: in the middle of the arena, which home's whole centre makes. */
private const val EMBLEM_RADIUS = 1.02f

/**
 * The last-standing board's middle at [stage] of sudden death (0 to 2), over a board drawn with no home: the home columns
 * and home as one dark arena, crossed swords in its middle, in flames from stage 1, and a skull at stage 2; and each start
 * square (from 1) and star square (from 2) that is no longer safe, marked with a red cross.
 */
internal fun DrawScope.drawArena(layout: BoardLayout, unit: Float, stage: Int) {
    val board = layout.geometry
    val floor = ArenaFloors[stage]
    val heat = ArenaHeats[stage]
    for (seat in 0..<board.seats) {
        for (index in 0..<board.homeColumnLength) drawFloor(layout.homeColumnSquare(seat, index).corners, unit, floor)
        drawFloor(layout.homeArea(seat), unit, floor)
        if (stage >= 1) drawCross(layout.trackSquare(board.startSquare(seat)).centre * unit, unit * UNSAFE_REACH, UnsafeCross)
        if (stage >= 2) drawCross(layout.trackSquare(board.startSquare(seat) + board.starOffset).centre * unit, unit * UNSAFE_REACH, UnsafeCross)
    }
    val middle = layout.homeBounds.center * unit
    val radius = min(EMBLEM_RADIUS * unit, layout.homeBounds.width / 2 * unit)
    if (stage > 0) {
        drawCircle(Brush.radialGradient(listOf(heat.copy(alpha = 0.75f), Color.Transparent), middle, radius * 1.45f), radius * 1.45f, middle)
        val flames = if (stage == 1) 10 else 16
        for (i in 0..<flames) rotate(360f * i / flames, middle) {
            val base = middle + Offset(0f, -radius * 0.98f)
            val height = radius * (if (stage == 1) 0.36f else 0.5f) * (if (i % 2 == 0) 1f else 0.75f)
            drawFlame(base, height, BoardInk)
            drawFlame(base, height * 0.9f, heat)
            drawFlame(base, height * 0.55f, BoardSparkGold)
        }
        drawCircle(floor, radius, middle)
    }
    drawCircle(heat, radius, middle, style = Stroke(radius * 0.1f))
    if (stage < 2) drawSwords(middle, radius, heat) else drawSkull(middle, radius)
}

private fun DrawScope.drawFloor(corners: List<Offset>, unit: Float, floor: Color) {
    val path = Path().apply {
        corners.forEachIndexed { i, corner -> if (i == 0) moveTo(corner.x * unit, corner.y * unit) else lineTo(corner.x * unit, corner.y * unit) }
        close()
    }
    drawPath(path, floor)
    // Over the seams between them.
    drawPath(path, floor, style = Stroke(unit * 0.04f))
}

/** How far a no longer safe square's cross reaches from its middle, across and down, in squares. */
private const val UNSAFE_REACH = 0.28f

/** A cross of [red] edged in ink, [reach] across and down from [centre]: a pawn out of the game, and a square no longer safe. */
private fun DrawScope.drawCross(centre: Offset, reach: Float, red: Color) {
    for ((colour, width) in listOf(BoardInk to 0.45f, red to 0.27f)) {
        for (lean in listOf(1f, -1f)) drawLine(colour, centre + Offset(-reach, -reach * lean), centre + Offset(reach, reach * lean), reach * width, StrokeCap.Round)
    }
}

private fun DrawScope.drawFlame(base: Offset, height: Float, colour: Color) {
    val flame = Path().apply {
        moveTo(base.x - height * 0.32f, base.y)
        cubicTo(base.x - height * 0.5f, base.y - height * 0.45f, base.x - height * 0.05f, base.y - height * 0.5f, base.x, base.y - height)
        cubicTo(base.x + height * 0.12f, base.y - height * 0.55f, base.x + height * 0.5f, base.y - height * 0.4f, base.x + height * 0.32f, base.y)
        close()
    }
    drawPath(flame, colour)
}

private fun DrawScope.drawSwords(middle: Offset, radius: Float, guard: Color) {
    fun up(y: Float) = middle + Offset(0f, radius * y)
    for (turn in listOf(-45f, 45f)) rotate(turn, middle) {
        drawLine(BoardInk, up(0.72f), up(-0.78f), radius * 0.24f, StrokeCap.Round)
        drawLine(SwordSteel, up(0.4f), up(-0.76f), radius * 0.14f, StrokeCap.Round)
        drawLine(SwordHilt, up(0.42f), up(0.72f), radius * 0.14f, StrokeCap.Round)
        drawLine(guard, up(0.4f) - Offset(radius * 0.24f, 0f), up(0.4f) + Offset(radius * 0.24f, 0f), radius * 0.12f, StrokeCap.Round)
    }
}

private fun DrawScope.drawSkull(middle: Offset, radius: Float) {
    fun at(x: Float, y: Float) = middle + Offset(radius * x, radius * y)
    for ((colour, grow) in listOf(BoardInk to 0.06f, SwordSteel to 0f)) {
        drawCircle(colour, radius * (0.54f + grow), at(0f, -0.1f))
        drawRoundRect(colour, at(-0.31f - grow, 0.2f), Size(radius * (0.62f + 2 * grow), radius * (0.41f + grow)), CornerRadius(radius * 0.1f))
    }
    for (x in listOf(-0.22f, 0.22f)) drawCircle(BoardInk, radius * 0.15f, at(x, -0.08f))
    val nose = Path().apply {
        at(0f, 0.1f).let { moveTo(it.x, it.y) }
        at(-0.08f, 0.24f).let { lineTo(it.x, it.y) }
        at(0.08f, 0.24f).let { lineTo(it.x, it.y) }
        close()
    }
    drawPath(nose, BoardInk)
    for (x in listOf(-0.12f, 0f, 0.12f)) drawLine(BoardInk, at(x, 0.38f), at(x, 0.6f), radius * 0.04f)
}

/**
 * Where the last standing wins, each player's yard as a tally of their pawns: a spot a pawn, in their colour while it's
 * still in the game, and grey once it's out, with the pawn crossed through on it (but for one still [inFlight] there).
 */
internal fun DrawScope.drawFallen(state: GameState, layout: BoardLayout, unit: Float, colourOf: (Int) -> Color, pawns: PawnStyle, inFlight: (player: Int, pawn: Int) -> Boolean) {
    state.players.forEachIndexed { player, side ->
        val radius = layout.yardSpotRadiusOf(side.pawns.size) * unit
        side.pawns.forEachIndexed { pawn, progress ->
            val centre = layout.yardSpotOf(side.seat, pawn, side.pawns.size) * unit
            val fallen = progress == PawnPosition.OUT_PROGRESS
            drawCircle(if (fallen) FallenPawn.copy(alpha = 0.45f) else colourOf(player).copy(alpha = 0.45f), radius, centre)
            drawCircle(BoardInk.copy(alpha = 0.45f), radius, centre, style = Stroke(unit * 0.04f))
            if (fallen && !inFlight(player, pawn)) {
                with(pawns) { drawPawn(centre, radius * 0.72f, FallenPawn, ringed = false) }
                drawCross(centre, radius * 0.62f, FallenCross)
            }
        }
    }
}
