package net.zodac.ludo.ui.game

import androidx.compose.ui.geometry.Offset
import net.zodac.ludo.model.BoardGeometry
import net.zodac.ludo.model.PawnPosition

/**
 * Where everything sits on the classic 15x15 cross, in board units: the board is [SIZE] units square, one unit a
 * square, (0, 0) its top-left corner. Seat 0's yard is the top-left corner and play runs clockwise; each other seat's
 * part of the board is seat 0's turned a quarter clockwise per seat. Pure, so the drawing and the taps agree by test.
 *
 * Only the classic board ([BoardGeometry.CLASSIC]) has a layout; 2-8 players bring others (Phase 8).
 */
object BoardLayout {
    /** The board's width and height, in squares. */
    const val SIZE = 15

    /** How many squares wide a yard is. */
    const val YARD_SIZE = 6

    private const val CENTRE = SIZE / 2f

    /** Seat 0's quarter of the track, from its start square: its arm's top row, then up the next arm and across. */
    private val SEAT_0_QUARTER: List<Cell> =
        (1..5).map { Cell(it, 6) } + (5 downTo 0).map { Cell(6, it) } + Cell(7, 0) + Cell(8, 0)

    /** Seat 0's yard pawns' spots, centres in board units. */
    private val SEAT_0_YARD_SPOTS = listOf(Offset(2f, 2f), Offset(4f, 2f), Offset(2f, 4f), Offset(4f, 4f))

    /** The board this layout draws. */
    val geometry: BoardGeometry = BoardGeometry.CLASSIC

    /** The square of absolute track square [square]. */
    fun trackCell(square: Int): Cell {
        val quarter = geometry.seatSpacing
        return SEAT_0_QUARTER[square % quarter].turned(square / quarter)
    }

    /** The square of [seat]'s home column square [index] (0 nearest the track). */
    fun homeColumnCell(seat: Int, index: Int): Cell = Cell(1 + index, 7).turned(seat)

    /** The top-left square of [seat]'s yard. */
    fun yardOrigin(seat: Int): Cell = Cell(0, 0).turnedArea(seat, YARD_SIZE)

    /** The centre of the yard spot [slot] (0-3) of [seat], in board units. */
    fun yardSpot(seat: Int, slot: Int): Offset = SEAT_0_YARD_SPOTS[slot].turned(seat)

    /** The three corners of [seat]'s triangle of home, in the centre: its base on the side its home column joins. */
    fun homeTriangle(seat: Int): List<Offset> =
        listOf(Offset(6f, 6f), Offset(6f, 9f), Offset(CENTRE, CENTRE)).map { it.turned(seat) }

    /**
     * Seat 0's spots for pawns that are home: in a line along its triangle's base, close together. In a line, not a
     * block, so the upright pawns never stand in front of one another whichever way a seat's triangle points.
     */
    private val SEAT_0_HOME_SPOTS = listOf(Offset(6.4f, 6.75f), Offset(6.4f, 7.25f), Offset(6.4f, 7.75f), Offset(6.4f, 8.25f))

    /** Where [seat]'s pawn [slot] (0-3) stands once home: each on a spot of its own inside its triangle. */
    fun homeSpot(seat: Int, slot: Int): Offset = SEAT_0_HOME_SPOTS[slot].turned(seat)

    /** The centre of where a pawn of [seat] at [position] stands, in board units ([slot] places a yard or home pawn). */
    fun centreOf(seat: Int, position: PawnPosition, slot: Int): Offset = when (position) {
        PawnPosition.Yard -> yardSpot(seat, slot)
        is PawnPosition.Track -> trackCell(geometry.trackSquare(seat, position.steps)).centre
        is PawnPosition.HomeColumn -> homeColumnCell(seat, position.index).centre
        PawnPosition.Home -> homeSpot(seat, slot)
    }

    /** A point turned [quarters] quarter turns clockwise about the board's centre. */
    private fun Offset.turned(quarters: Int): Offset {
        var point = this
        repeat(quarters % 4) { point = Offset(SIZE - point.y, point.x) }
        return point
    }

    private fun Cell.turned(quarters: Int): Cell = turnedArea(quarters, 1)

    /** The top-left square of a [size]-square block whose top-left is this, turned [quarters] clockwise. */
    private fun Cell.turnedArea(quarters: Int, size: Int): Cell {
        var cell = this
        repeat(quarters % 4) { cell = Cell(SIZE - size - cell.row, cell.column) }
        return cell
    }
}

/** One square of the board: [column] from the left and [row] from the top, from 0. */
data class Cell(val column: Int, val row: Int) {
    /** Its centre, in board units. */
    val centre: Offset get() = Offset(column + 0.5f, row + 0.5f)
}
