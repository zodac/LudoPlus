package net.zodac.ludo.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import net.zodac.ludo.model.BoardGeometry
import net.zodac.ludo.model.PawnPosition

/**
 * Where everything on a board sits, in board units: the board is [size] units square, one unit a square's side, (0, 0)
 * its top-left corner. Seat 0's arm points left and play runs clockwise. Pure, so the drawing and the taps agree by test.
 *
 * The classic 15x15 cross is [ClassicBoardLayout]; every other board ([BoardGeometry.forPlayers]) is a
 * [StarBoardLayout] of arms round a centre.
 */
sealed interface BoardLayout {
    /** The board this layout draws. */
    val geometry: BoardGeometry

    /** The board's width and height, in squares. */
    val size: Float

    /** Whether the board is a disc [size] across, rather than filling its square. */
    val round: Boolean

    /** The square of absolute track square [square]. */
    fun trackSquare(square: Int): BoardSquare

    /** The square of [seat]'s home column square [index] (0 nearest the track). */
    fun homeColumnSquare(seat: Int, index: Int): BoardSquare

    /** The outline of [seat]'s yard. */
    fun yard(seat: Int): List<Offset>

    /** The outline of [seat]'s yard's court, the plain inside round its pawns' spots. */
    fun yardCourt(seat: Int): List<Offset>

    /** The centre of the yard spot [slot] (0-3) of [seat]. */
    fun yardSpot(seat: Int, slot: Int): Offset

    /**
     * Where [seat]'s pawn [pawn] waits in its yard, of a game of [pawns] each: its own spot, or with two pawns, opposite
     * corners (the first spot and the last), so the pair sits balanced in the yard's four.
     */
    fun yardSpotOf(seat: Int, pawn: Int, pawns: Int): Offset = yardSpot(seat, if (pawns == 2) pawn * 3 else pawn)

    /** How big a yard spot is drawn, across from its centre. */
    val yardSpotRadius: Float

    /** How big a pawn in its yard is drawn. */
    val yardPawnRadius: Float

    /** The corners of [seat]'s part of home, in the centre: its side is the one its home column joins. */
    fun homeArea(seat: Int): List<Offset>

    /** Where [seat]'s pawn [slot] (0-3) stands once home: each on a spot of its own in its part of home. */
    fun homeSpot(seat: Int, slot: Int): Offset

    /** The box round home, the whole centre. */
    val homeBounds: Rect

    /** The centre of where a pawn of [seat] at [position] stands ([slot] places a yard or home pawn). */
    fun centreOf(seat: Int, position: PawnPosition, slot: Int): Offset = when (position) {
        PawnPosition.Yard -> yardSpot(seat, slot)
        is PawnPosition.Track -> trackSquare(geometry.trackSquare(seat, position.steps)).centre
        is PawnPosition.HomeColumn -> homeColumnSquare(seat, position.index).centre
        PawnPosition.Home -> homeSpot(seat, slot)
    }

    companion object {
        private val stars = mutableMapOf<BoardGeometry, StarBoardLayout>()

        /** The layout that draws [geometry]. */
        fun of(geometry: BoardGeometry): BoardLayout =
            if (geometry == ClassicBoardLayout.geometry) ClassicBoardLayout else stars.getOrPut(geometry) { StarBoardLayout(geometry) }
    }
}

/**
 * One square of a board, its [corners] in order round it. A track or home-column square is a unit square turned to its
 * arm, but for the yard-side squares of the three-seat board, which are stretched to meet the arms.
 */
data class BoardSquare(val corners: List<Offset>) {
    val centre: Offset = corners.reduce { a, b -> a + b } / corners.size.toFloat()

    /** The direction of its first side, in degrees clockwise from rightwards: how far its art is turned. */
    val angle: Float = (corners[1] - corners[0]).let { atan2(it.y, it.x) * 180f / PI.toFloat() }

    /** Whether it's a whole unit square, so its art is drawn turned, not stretched to fit. */
    val isUnit: Boolean = corners.size == 4 &&
        corners.indices.all { abs((corners[(it + 1) % 4] - corners[it]).getDistance() - 1f) < 1e-3f } &&
        abs((corners[2] - corners[0]).getDistance() - sqrt(2f)) < 1e-3f

    /** The box round it. */
    val bounds: Rect = Rect(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })
}

/** One square of the classic grid: [column] from the left and [row] from the top, from 0. */
data class Cell(val column: Int, val row: Int) {
    /** Its centre, in board units. */
    val centre: Offset get() = Offset(column + 0.5f, row + 0.5f)

    /** It as a [BoardSquare], corners clockwise from its top-left. */
    val square: BoardSquare
        get() = BoardSquare(
            listOf(Offset(column.toFloat(), row.toFloat()), Offset(column + 1f, row.toFloat()), Offset(column + 1f, row + 1f), Offset(column.toFloat(), row + 1f)),
        )
}

/**
 * The classic 15x15 cross. Seat 0's yard is the top-left corner; each other seat's part of the board is seat 0's turned
 * a quarter clockwise per seat.
 */
object ClassicBoardLayout : BoardLayout {
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

    override val geometry: BoardGeometry = BoardGeometry.CLASSIC
    override val size: Float = SIZE.toFloat()
    override val round: Boolean = false
    override val yardSpotRadius: Float = 0.62f
    override val yardPawnRadius: Float = YARD_PAWN_RADIUS
    override val homeBounds: Rect = Rect(6f, 6f, 9f, 9f)

    /** The grid square of absolute track square [square]. */
    fun trackCell(square: Int): Cell {
        val quarter = geometry.seatSpacing
        return SEAT_0_QUARTER[square % quarter].turned(square / quarter)
    }

    /** The grid square of [seat]'s home column square [index] (0 nearest the track). */
    fun homeColumnCell(seat: Int, index: Int): Cell = Cell(1 + index, 7).turned(seat)

    override fun trackSquare(square: Int): BoardSquare = trackCell(square).square

    override fun homeColumnSquare(seat: Int, index: Int): BoardSquare = homeColumnCell(seat, index).square

    /** The top-left square of [seat]'s yard. */
    fun yardOrigin(seat: Int): Cell = Cell(0, 0).turnedArea(seat, YARD_SIZE)

    override fun yard(seat: Int): List<Offset> = yardOrigin(seat).let { square(it.column.toFloat(), it.row.toFloat(), YARD_SIZE.toFloat()) }

    override fun yardCourt(seat: Int): List<Offset> = yardOrigin(seat).let { square(it.column + 1f, it.row + 1f, YARD_SIZE - 2f) }

    override fun yardSpot(seat: Int, slot: Int): Offset = SEAT_0_YARD_SPOTS[slot].turned(seat)

    /** The three corners of [seat]'s triangle of home, in the centre: its base on the side its home column joins. */
    fun homeTriangle(seat: Int): List<Offset> =
        listOf(Offset(6f, 6f), Offset(6f, 9f), Offset(CENTRE, CENTRE)).map { it.turned(seat) }

    override fun homeArea(seat: Int): List<Offset> = homeTriangle(seat)

    /**
     * Seat 0's spots for pawns that are home: in a line along its triangle's base, close together. In a line, not a
     * block, so the upright pawns never stand in front of one another whichever way a seat's triangle points.
     */
    private val SEAT_0_HOME_SPOTS = listOf(Offset(6.4f, 6.75f), Offset(6.4f, 7.25f), Offset(6.4f, 7.75f), Offset(6.4f, 8.25f))

    override fun homeSpot(seat: Int, slot: Int): Offset = SEAT_0_HOME_SPOTS[slot].turned(seat)

    private fun square(left: Float, top: Float, side: Float) =
        listOf(Offset(left, top), Offset(left + side, top), Offset(left + side, top + side), Offset(left, top + side))

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

/**
 * A board of one arm a seat round a centre, as approved from renders (2026-11-18): each arm the classic board's, 3 squares
 * wide and 6 long, and between two arms a yard reaching out to the board's round edge. The centre is a polygon of a side
 * an arm, but for three seats ([BoardGeometry.THREE_SEAT]), whose centre is a hexagon with the arms on every other side,
 * so each home has room; its track runs 3 squares along each yard's side of it.
 *
 * Seat `k`'s arm points [ARM_START_DEGREES] plus `k` turns of the seats clockwise. On its arm, a seat has its start
 * square (second from the end, on the side play leaves by) and its home column (the middle lane).
 */
class StarBoardLayout(override val geometry: BoardGeometry) : BoardLayout {
    private val seats = geometry.seats
    private val hexagon = geometry == BoardGeometry.THREE_SEAT

    init {
        require(hexagon || (geometry.seatSpacing == 13 && geometry.starOffset == 8 && geometry.homeColumnLength == 5)) {
            "No star layout for $geometry"
        }
    }

    /** How far from the middle an arm's inner end is: the centre's apothem. */
    private val armStart: Double = if (hexagon) HALF_ARM * sqrt(3.0) else HALF_ARM / tan(PI / seats)

    /** How far from the middle the board's furthest corners are, the arms' outer ones. */
    private val reach: Double = hypot(armStart + ARM_LENGTH, HALF_ARM)

    override val size: Float = (2 * (reach + RIM)).toFloat()
    override val round: Boolean = true

    private val middle = Offset(size / 2, size / 2)

    private fun angle(seat: Int): Double = PI * ARM_START_DEGREES / 180 + seat.mod(seats) * 2 * PI / seats

    /** Outwards along seat [seat]'s arm. */
    private fun out(seat: Int): Offset = angle(seat).let { Offset(cos(it).toFloat(), sin(it).toFloat()) }

    /** Across seat [seat]'s arm, towards the next seat's. */
    private fun across(seat: Int): Offset = angle(seat).let { Offset(-sin(it).toFloat(), cos(it).toFloat()) }

    /** The point [distance] out from the middle along [seat]'s arm, and [side] across it. */
    private fun point(seat: Int, distance: Double, side: Double): Offset =
        middle + out(seat) * distance.toFloat() + across(seat) * side.toFloat()

    /** The square on [seat]'s arm in [row] (0 outermost) of the lane starting [lane] across. */
    private fun armSquare(seat: Int, row: Int, lane: Double): BoardSquare {
        val inner = armStart + ARM_LENGTH - 1 - row
        val outer = inner + 1
        return BoardSquare(listOf(point(seat, inner, lane), point(seat, outer, lane), point(seat, outer, lane + 1), point(seat, inner, lane + 1)))
    }

    /** Where [seat]'s arm meets the centre on the next seat's side, and where the next seat's arm does on this one's. */
    private fun innerCorner(seat: Int): Offset = point(seat, armStart, HALF_ARM)
    private fun nextInnerCorner(seat: Int): Offset = point(seat + 1, armStart, -HALF_ARM)

    /** Outwards from the middle of the centre's side between [seat]'s arm and the next (the three-seat board). */
    private fun yardSideOut(seat: Int): Offset = ((innerCorner(seat) + nextInnerCorner(seat)) / 2f - middle).let { it / it.getDistance() }

    /** Square [index] (0-2) of the track along the three-seat board's yard side after [seat]'s arm. */
    private fun yardSideSquare(seat: Int, index: Int): BoardSquare {
        val from = innerCorner(seat)
        val to = nextInnerCorner(seat)
        val step = (to - from) / 3f
        val out = yardSideOut(seat)
        return BoardSquare(
            when (index) {
                // The end squares reach the arms, which lean away from the side.
                0 -> listOf(from, from + step, from + step + out, from + out(seat) * 2f)
                1 -> listOf(from + step, from + step * 2f, from + step * 2f + out, from + step + out)
                else -> listOf(from + step * 2f, to, to + out(seat + 1) * 2f, from + step * 2f + out)
            },
        )
    }

    override fun trackSquare(square: Int): BoardSquare {
        require(square in 0..<geometry.trackLength) { "No track square $square" }
        val seat = square / geometry.seatSpacing
        // Up this arm's leaving side, (the yard's side of the centre,) then out the next arm, across its end, and in.
        val along = square % geometry.seatSpacing - if (hexagon) YARD_SIDE_SQUARES else 0
        return when {
            square % geometry.seatSpacing < ARM_LENGTH - 1 -> armSquare(seat, square % geometry.seatSpacing + 1, LEAVING_LANE)
            along < ARM_LENGTH - 1 -> yardSideSquare(seat, along - (ARM_LENGTH - 1) + YARD_SIDE_SQUARES)
            along < 2 * ARM_LENGTH - 1 -> armSquare(seat + 1, 2 * ARM_LENGTH - 2 - along, ARRIVING_LANE)
            along == 2 * ARM_LENGTH - 1 -> armSquare(seat + 1, 0, MIDDLE_LANE)
            else -> armSquare(seat + 1, 0, LEAVING_LANE)
        }
    }

    override fun homeColumnSquare(seat: Int, index: Int): BoardSquare = armSquare(seat, index + 1, MIDDLE_LANE)

    override fun yard(seat: Int): List<Offset> {
        val rim = middle + (if (hexagon) yardSideOut(seat) else (innerCorner(seat) - middle).let { it / it.getDistance() }) * reach.toFloat()
        val armEnd = point(seat, armStart + ARM_LENGTH, HALF_ARM)
        val nextArmEnd = point(seat + 1, armStart + ARM_LENGTH, -HALF_ARM)
        return if (hexagon) {
            listOf(innerCorner(seat) + out(seat) * 2f, armEnd, rim, nextArmEnd, nextInnerCorner(seat) + out(seat + 1) * 2f)
        } else {
            listOf(innerCorner(seat), armEnd, rim, nextArmEnd)
        }
    }

    /** Each yard's widest circle, along the line through its middle: its court and spots are set round it. */
    private val yardCircles: List<Pair<Offset, Float>> = List(seats) { seat ->
        val outline = yard(seat)
        val start = if (hexagon) (outline.first() + outline.last()) / 2f else outline.first()
        val direction = (outline[2] - start).let { it / it.getDistance() }
        val length = (outline[2] - start).getDistance()
        (1..<SEARCH_STEPS).map { start + direction * (length * it / SEARCH_STEPS) }
            .map { it to edgeDistance(outline, it) }
            .maxBy { it.second }
    }

    override fun yardCourt(seat: Int): List<Offset> = inset(yard(seat), min(1f, yardCircles[seat].second * COURT_INSET))

    override val yardSpotRadius: Float = max(MIN_SPOT_RADIUS, min(CLASSIC_SPOT_RADIUS, yardCircles[0].second * SPOT_SHARE))
    /** As the classic yard's pawn is to its spot, but never smaller than a pawn on the track. */
    override val yardPawnRadius: Float = max(PAWN_RADIUS, yardSpotRadius * YARD_PAWN_RADIUS / CLASSIC_SPOT_RADIUS)

    /** The four spots in a diamond about the yard's widest circle, as the classic yard's square of four is to its corner. */
    override fun yardSpot(seat: Int, slot: Int): Offset {
        val (centre, radius) = yardCircles[seat]
        val outward = (centre - middle).let { it / it.getDistance() }
        val side = Offset(-outward.y, outward.x)
        val spread = radius * SPOT_SPREAD
        return centre + when (slot) {
            0 -> outward * spread
            1 -> side * spread
            2 -> side * -spread
            else -> outward * -spread
        }
    }

    override fun homeArea(seat: Int): List<Offset> = if (hexagon) {
        listOf(
            (innerCorner(seat - 1) + nextInnerCorner(seat - 1)) / 2f,
            point(seat, armStart, -HALF_ARM),
            point(seat, armStart, HALF_ARM),
            (innerCorner(seat) + nextInnerCorner(seat)) / 2f,
            middle,
        )
    } else {
        listOf(point(seat, armStart, -HALF_ARM), point(seat, armStart, HALF_ARM), middle)
    }

    /** Along the side its home column joins, as on the classic board; the three-seat board's homes have room for a block. */
    override fun homeSpot(seat: Int, slot: Int): Offset = if (hexagon) {
        point(seat, armStart * HEXAGON_HOME_DEPTH + (slot / 2 * 2 - 1) * HEXAGON_HOME_SPREAD_OUT, (slot % 2 * 2 - 1) * HEXAGON_HOME_SPREAD_ACROSS)
    } else {
        point(seat, armStart - HOME_INSET, (slot - 1.5) * HOME_SPACING)
    }

    override val homeBounds: Rect = (0..<seats).flatMap { homeArea(it) }.let { points ->
        Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }

    private companion object {
        /** Seat 0's arm points left, as on the classic board. */
        const val ARM_START_DEGREES = 180.0
        const val ARM_LENGTH = 6
        const val HALF_ARM = 1.5
        const val LEAVING_LANE = 0.5
        const val MIDDLE_LANE = -0.5
        const val ARRIVING_LANE = -1.5
        const val YARD_SIDE_SQUARES = 3

        /** The plain margin round the board's furthest corners. */
        const val RIM = 0.4
        const val SEARCH_STEPS = 200
        const val COURT_INSET = 0.33f
        const val CLASSIC_SPOT_RADIUS = 0.62f
        const val MIN_SPOT_RADIUS = 0.45f
        const val SPOT_SHARE = 0.21f
        const val SPOT_SPREAD = 0.47f
        const val HOME_INSET = 0.4
        const val HOME_SPACING = 0.5
        const val HEXAGON_HOME_DEPTH = 0.6
        const val HEXAGON_HOME_SPREAD_OUT = 0.42
        const val HEXAGON_HOME_SPREAD_ACROSS = 0.48
    }
}

/** How far [point] is from the nearest side of the convex [outline]. */
private fun edgeDistance(outline: List<Offset>, point: Offset): Float = outline.indices.minOf { i ->
    val from = outline[i]
    val side = outline[(i + 1) % outline.size] - from
    abs(side.x * (point.y - from.y) - side.y * (point.x - from.x)) / side.getDistance()
}

/** The convex [outline] shrunk by [by] on every side (grown, for a negative [by]): a yard's court, or a frame's inside. */
internal fun inset(outline: List<Offset>, by: Float): List<Offset> {
    val inside = outline.reduce { a, b -> a + b } / outline.size.toFloat()
    val sides = outline.indices.map { i ->
        val from = outline[i]
        val direction = (outline[(i + 1) % outline.size] - from).let { it / it.getDistance() }
        var normal = Offset(-direction.y, direction.x)
        if ((inside - from).let { it.x * normal.x + it.y * normal.y } < 0) normal = -normal
        (from + normal * by) to direction
    }
    return sides.indices.map { i ->
        val (p1, d1) = sides[(i - 1 + sides.size) % sides.size]
        val (p2, d2) = sides[i]
        val t = ((p2.x - p1.x) * d2.y - (p2.y - p1.y) * d2.x) / (d1.x * d2.y - d1.y * d2.x)
        p1 + d1 * t
    }
}
