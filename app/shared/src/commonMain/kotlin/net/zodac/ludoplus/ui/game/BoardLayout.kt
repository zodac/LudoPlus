package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.ui.style.YARD_SPOTS

/**
 * Where everything on a board sits, in board units: the board is [size] units square, one unit a square's side, (0, 0)
 * its top-left corner. Seat 0's yard is at the bottom left, its arm pointing down, and play runs clockwise. Pure, so the drawing and the taps agree by test.
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

    /** The outline of [seat]'s yard, convex: a round side is many short ones. */
    fun yard(seat: Int): List<Offset>

    /** Whether [point] is in [seat]'s yard. */
    fun isInYard(seat: Int, point: Offset): Boolean = insideSides(yard(seat)).all { it.distanceTo(point) >= 0f }

    /** The outline of [seat]'s yard's court, the plain inside round its pawns' spots; round where the yard is. */
    fun yardCourt(seat: Int): List<Offset>

    /** The centre of the yard spot [slot] (0-3) of [seat]: where its pawns wait in a game of four each. */
    fun yardSpot(seat: Int, slot: Int): Offset = yardSpotOf(seat, slot, YARD_SPOTS)

    /** Where [seat]'s pawn [pawn] waits in its yard, of a game of [pawns] each: a spot of its own, alone the yard's middle. */
    fun yardSpotOf(seat: Int, pawn: Int, pawns: Int): Offset

    /** The middle of [seat]'s yard, where a lone pawn waits. */
    fun loneYardSpot(seat: Int): Offset

    /** How big a spot is drawn in a yard of [pawns], across from its centre. */
    fun yardSpotRadiusOf(pawns: Int): Float

    /** How big a pawn is drawn in a yard of [pawns]: as the classic yard's is to its spot, or as big as on the track if its spot takes that. */
    fun yardPawnRadiusOf(pawns: Int): Float = yardSpotRadiusOf(pawns).let { max(it * YARD_PAWN_RADIUS / YARD_SPOT_RADIUS, min(PAWN_RADIUS, it * SNUG_PAWN_SHARE)) }

    /**
     * The two places [seat]'s yard shows what's in force for its player, in a game of [pawns] each: on its court, clear of its
     * edge, its pawns, and each other. They're opposite each other about the yard's middle, or where there's clearly more
     * room, both on a line through it or one each side of one. Both have the same room, the most there is.
     */
    fun yardBadges(seat: Int, pawns: Int): List<YardBadge> {
        val sides = insideSides(yardCourt(seat))
        val spots = List(pawns) { yardSpotOf(seat, it, pawns) }
        // A pawn stands a little taller than its radius.
        val spotReach = max(yardSpotRadiusOf(pawns), yardPawnRadiusOf(pawns) * PAWN_REACH)

        /** How much room there is at [point], looked into no further once it's no more than [atLeast]. */
        fun room(point: Offset, atLeast: Float): Float {
            var room = Float.MAX_VALUE
            for (spot in spots) {
                room = min(room, (spot - point).getDistance() - spotReach)
                if (room <= atLeast) return room
            }
            for (edge in sides) {
                room = min(room, edge.distanceTo(point))
                if (room <= atLeast) return room
            }
            return room
        }

        val from = loneYardSpot(seat)
        val axis = badgeAxis(seat)
        val side = Offset(-axis.y, axis.x)
        val ways = listOf(axis, side, (axis + side) / sqrt(2f), (axis - side) / sqrt(2f))
        val steps = -BADGE_STEPS..BADGE_STEPS
        var most = 0f
        var best: Pair<Offset, Offset>? = null
        fun weigh(first: Offset, second: Offset) {
            // A tie goes to the same places in every yard.
            val wanted = most + BADGE_TIE
            val apart = (first - second).getDistance() / 2
            if (apart <= wanted) return
            val one = room(first, wanted)
            if (one <= wanted) return
            val found = minOf(apart, one, room(second, wanted))
            if (found <= wanted) return
            most = found
            best = first to second
        }
        // Opposite each other about the yard's middle.
        for (way in ways) for (out in 1..BADGE_STEPS) (way * (out * BADGE_STEP)).let { weigh(from + it, from - it) }
        val opposite = most
        val balanced = best
        // Anywhere on a line through it, or one each side of one, if there's enough more room.
        best = null
        most = opposite + BADGE_BALANCE
        for (way in ways) {
            val across = Offset(-way.y, way.x)
            val line = steps.map { from + way * (it * BADGE_STEP) }.filter { room(it, most) > most }
            for ((i, second) in line.withIndex()) for (first in line.drop(i + 1)) weigh(first, second)
            for (step in steps) for (out in 1..BADGE_STEPS) {
                val middle = from + way * (step * BADGE_STEP)
                (across * (out * BADGE_STEP)).let { weigh(middle + it, middle - it) }
            }
        }
        val found = best ?: checkNotNull(balanced) { "No room in a yard of $pawns" }
        val room = if (best == null) opposite else most
        fun along(point: Offset) = point.x * axis.x + point.y * axis.y
        val (first, second) = if (along(found.second) > along(found.first) + BADGE_TIE) found.second to found.first else found
        return listOf(YardBadge(first, room), YardBadge(second, room))
    }

    /** The way through [seat]'s yard its badges are placed about: the first is the one further along it. */
    fun badgeAxis(seat: Int): Offset

    /** The corners of [seat]'s part of home, in the centre: its side is the one its home column joins. */
    fun homeArea(seat: Int): List<Offset>

    /** Where [seat]'s pawn [slot] stands once home, of a game of [pawns] each: each on a spot of its own in its part of home. */
    fun homeSpot(seat: Int, slot: Int, pawns: Int = YARD_SPOTS): Offset

    /** How big a pawn home is drawn, of a game of [pawns] each. */
    fun homePawnRadiusOf(pawns: Int): Float = if (pawns > YARD_SPOTS) HOME_PAWN_RADIUS * CROWDED_HOME_SHARE else HOME_PAWN_RADIUS

    /** The box round home, the whole centre. */
    val homeBounds: Rect

    /** The centre of where a pawn of [seat] at [position] stands ([slot], of [pawns] each, places a yard or home pawn), of a style that takes [lift] on a turned square. */
    fun centreOf(seat: Int, position: PawnPosition, slot: Int, pawns: Int = YARD_SPOTS, lift: Float = 1f): Offset = when (position) {
        PawnPosition.Yard, PawnPosition.Out -> yardSpotOf(seat, slot, pawns)
        is PawnPosition.Track -> trackSquare((geometry.startSquare(seat) + position.steps) % geometry.trackLength).stand(lift)
        is PawnPosition.HomeColumn -> homeColumnSquare(seat, position.index).stand(lift)
        PawnPosition.Home -> homeSpot(seat, slot, pawns)
    }

    companion object {
        private val stars = mutableMapOf<BoardGeometry, StarBoardLayout>()

        /** How big the classic yard's spots are, and the biggest any yard's are. */
        internal const val YARD_SPOT_RADIUS = 0.62f

        /** The most of a small spot its pawn fills. */
        private const val SNUG_PAWN_SHARE = 0.9f

        /** How far apart the points [yardBadges] weighs up are, and how many it takes each way from a yard's middle. */
        private const val BADGE_STEP = 0.1f
        private const val BADGE_STEPS = 55

        /** How near two places' room is to count as as much, and how much more it takes for them not to be opposite each other. */
        private const val BADGE_TIE = 0.01f
        private const val BADGE_BALANCE = 0.03f

        /** How far a standing pawn reaches from its centre, of its radius. */
        private const val PAWN_REACH = 1.1f

        /** How much smaller five or six pawns home are than four. */
        private const val CROWDED_HOME_SHARE = 0.75f

        /** The layout that draws [geometry]: an endless board's squares lie where the same board's with a home do. */
        fun of(geometry: BoardGeometry): BoardLayout = geometry.copy(endless = false).let { board ->
            if (board == ClassicBoardLayout.geometry) ClassicBoardLayout else stars.getOrPut(board) { StarBoardLayout(board) }
        }
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

    /** How far it's turned from the nearest upright, in degrees clockwise: more than -45, to 45 for one on its corner. */
    val turn: Float = angle.mod(QUARTER_TURN).let { if (it > QUARTER_TURN / 2 + TURN_TOLERANCE) it - QUARTER_TURN else it }

    /** Whether it's turned at all. */
    val isTurned: Boolean = abs(turn) > TURN_TOLERANCE

    /** Whether it's a whole unit square, so its art is drawn turned, not stretched to fit. */
    val isUnit: Boolean = corners.size == 4 &&
        corners.indices.all { abs((corners[(it + 1) % 4] - corners[it]).getDistance() - 1f) < 1e-3f } &&
        abs((corners[2] - corners[0]).getDistance() - sqrt(2f)) < 1e-3f

    /**
     * Where a lone pawn's centre is on it: above its own centre where it's turned, so a pawn, upright whichever way its
     * square is and wider at its foot than its head, is as clear of the sides under it as of those over it. Never below.
     * [lift] is how much of the classic pawn's rise it takes ([net.zodac.ludoplus.ui.style.PawnStyle.lift]). Of pawns sharing
     * it, smaller by their [radius] and rising less by it, it's the point they stand about.
     */
    fun stand(lift: Float, radius: Float = PAWN_RADIUS): Offset = centre - Offset(0f, rise * lift * radius / PAWN_RADIUS)

    /** How far above its centre a lone classic pawn stands. */
    private val rise: Float by lazy {
        val sides = insideSides(corners)
        (0..STAND_STEPS).map { it * STAND_STEP }.maxBy { lift ->
            PAWN_OUTLINE.minOf { (at, reach) -> sides.minOf { it.distanceTo(centre + at * PAWN_RADIUS - Offset(0f, lift)) } - reach * PAWN_RADIUS }
        }
    }

    /** The box round it. */
    val bounds: Rect = Rect(corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y })

    private companion object {
        const val QUARTER_TURN = 90f
        const val TURN_TOLERANCE = 0.1f

        /** How far apart the rises tried are, in squares, and how many are: a tenth of a square at most. */
        const val STAND_STEP = 0.005f
        const val STAND_STEPS = 20

        /** The circles a standing classic pawn reaches as far as, outline and all: its foot's two corners, and its head, in its radii from its centre. */
        val PAWN_OUTLINE = listOf(Offset(-0.66f, 0.76f) to 0.205f, Offset(0.66f, 0.76f) to 0.205f, Offset(0f, -0.62f) to 0.425f)
    }
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
 * The classic 15x15 cross. Seat 0's yard is the bottom-left corner; each other seat's part of the board is seat 0's turned
 * a quarter clockwise per seat. Seat 0's part is set out below as if its yard were the top-left corner, then turned
 * [SEAT_0_TURN] quarters clockwise into place.
 */
object ClassicBoardLayout : BoardLayout {
    /** The board's width and height, in squares. */
    const val SIZE = 15

    /** How many squares wide a yard is. */
    const val YARD_SIZE = 6

    private const val CENTRE = SIZE / 2f

    /** How far apart a yard of six's spots are: across from the middle one, and its rows from the middle. */
    private const val CROWDED_ACROSS = 1.3f
    private const val CROWDED_DOWN = 1.3f

    /** How far into its triangle a line of pawns home stands, and how far apart they are: up to four, then five or six. */
    private const val HOME_IN = 6.4f
    private const val HOME_SPACING = 0.5f
    private const val CROWDED_HOME_IN = 6.32f
    private const val CROWDED_HOME_SPACING = 0.36f

    /** How many quarters clockwise seat 0's part is turned from the top-left corner it's set out in: to the bottom left. */
    private const val SEAT_0_TURN = 3

    /** Seat 0's quarter of the track as set out, from its start square: its arm's top row, then up the next arm and across. */
    private val SEAT_0_QUARTER: List<Cell> =
        (1..5).map { Cell(it, 6) } + (5 downTo 0).map { Cell(6, it) } + Cell(7, 0) + Cell(8, 0)

    /** Seat 0's yard pawns' spots as set out, centres in board units. */
    private val SEAT_0_YARD_SPOTS = listOf(Offset(1.8f, 1.8f), Offset(4.2f, 1.8f), Offset(1.8f, 4.2f), Offset(4.2f, 4.2f))

    override val geometry: BoardGeometry = BoardGeometry.CLASSIC
    override val size: Float = SIZE.toFloat()
    override val round: Boolean = false
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

    /**
     * Its own spot of the four; with two pawns, opposite corners (the first spot and the last), so the pair sits balanced in
     * them; three are one above two; and five or six are two rows, the second of five having two: both upright in every yard.
     */
    override fun yardSpotOf(seat: Int, pawn: Int, pawns: Int): Offset = when {
        pawns > YARD_SPOTS -> {
            val second = pawn / 3
            val across = pawn % 3 - if (second == 1) (pawns - 3 - 1) / 2f else 1f
            loneYardSpot(seat) + Offset(across * CROWDED_ACROSS, (second * 2 - 1) * CROWDED_DOWN)
        }
        pawns == 3 -> loneYardSpot(seat) + listOf(Offset(0f, -1.15f), Offset(-1.2f, 1.1f), Offset(1.2f, 1.1f))[pawn]
        pawns == 2 -> SEAT_0_YARD_SPOTS[pawn * 3].turned(seat)
        pawns == 1 -> loneYardSpot(seat)
        else -> SEAT_0_YARD_SPOTS[pawn].turned(seat)
    }

    override fun yardSpotRadiusOf(pawns: Int): Float = BoardLayout.YARD_SPOT_RADIUS

    override fun loneYardSpot(seat: Int): Offset = yardOrigin(seat).let { Offset(it.column + YARD_SIZE / 2f, it.row + YARD_SIZE / 2f) }

    /** Leftwards, so they're the same way round in every yard. */
    override fun badgeAxis(seat: Int): Offset = Offset(-1f, 0f)

    /** The three corners of [seat]'s triangle of home, in the centre: its base on the side its home column joins. */
    fun homeTriangle(seat: Int): List<Offset> =
        listOf(Offset(6f, 6f), Offset(6f, 9f), Offset(CENTRE, CENTRE)).map { it.turned(seat) }

    override fun homeArea(seat: Int): List<Offset> = homeTriangle(seat)

    /**
     * In a line along its triangle's base, close together, about its middle. In a line, not a
     * block, so the upright pawns never stand in front of one another whichever way a seat's triangle points.
     */
    override fun homeSpot(seat: Int, slot: Int, pawns: Int): Offset {
        val (into, apart) = if (pawns > YARD_SPOTS) CROWDED_HOME_IN to CROWDED_HOME_SPACING else HOME_IN to HOME_SPACING
        return Offset(into, CENTRE + (slot - (pawns - 1) / 2f) * apart).turned(seat)
    }

    private fun square(left: Float, top: Float, side: Float) =
        listOf(Offset(left, top), Offset(left + side, top), Offset(left + side, top + side), Offset(left, top + side))

    /** A point of seat 0's part, as set out, turned into seat [quarters]'s place: [quarters] more quarters clockwise. */
    private fun Offset.turned(quarters: Int): Offset {
        var point = this
        repeat((quarters + SEAT_0_TURN) % 4) { point = Offset(SIZE - point.y, point.x) }
        return point
    }

    private fun Cell.turned(quarters: Int): Cell = turnedArea(quarters, 1)

    /** The top-left square of a [size]-square block of seat 0's part, as set out, turned into seat [quarters]'s place. */
    private fun Cell.turnedArea(quarters: Int, size: Int): Cell {
        var cell = this
        repeat((quarters + SEAT_0_TURN) % 4) { cell = Cell(SIZE - size - cell.row, cell.column) }
        return cell
    }
}

/**
 * A board of one arm a seat round a centre, as approved from renders (2026-11-18): each arm the classic board's, 3 squares
 * wide and 6 long, and between two arms a yard reaching out to the board's round edge, its outer side an arc of a circle
 * about the board's middle. The centre is a polygon of a side
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

    /** The corners of [seat]'s yard, its arc's middle among them. */
    fun yardCorners(seat: Int): List<Offset> {
        val rim = middle + (if (hexagon) yardSideOut(seat) else (innerCorner(seat) - middle).let { it / it.getDistance() }) * reach.toFloat()
        val armEnd = point(seat, armStart + ARM_LENGTH, HALF_ARM)
        val nextArmEnd = point(seat + 1, armStart + ARM_LENGTH, -HALF_ARM)
        return if (hexagon) {
            listOf(innerCorner(seat) + out(seat) * 2f, armEnd, rim, nextArmEnd, nextInnerCorner(seat) + out(seat + 1) * 2f)
        } else {
            listOf(innerCorner(seat), armEnd, rim, nextArmEnd)
        }
    }

    override fun yard(seat: Int): List<Offset> = yardWithin(seat, 0f)

    /** The outline of [seat]'s yard, shrunk by [by] on every side: its straight sides, and the arc between its arms' ends. */
    private fun yardWithin(seat: Int, by: Float): List<Offset> {
        val radius = reach - by
        val side = HALF_ARM + by
        // Where the arc meets the sides along the two arms, in turn from the middle of its own arm and the next's.
        val fromArm = asin(side / radius)
        val start = angle(seat) + fromArm
        val sweep = 2 * PI / seats - 2 * fromArm
        val steps = ceil(sweep / MAX_ARC_STEP).toInt()
        val arc = (0..steps).map { step ->
            val turn = start + sweep * step / steps
            middle + Offset(cos(turn).toFloat(), sin(turn).toFloat()) * radius.toFloat()
        }
        // The other corners, all but the arc's two ends and middle.
        val corners = inset(yardCorners(seat), by)
        return listOf(corners.first()) + arc + corners.drop(ARC_CORNERS + 1)
    }

    /** Each yard's widest circle, along the line through its middle: its court and spots are set round it. */
    private val yardCircles: List<Pair<Offset, Float>> = List(seats) { seat ->
        val outline = yard(seat)
        val corners = yardCorners(seat)
        val start = if (hexagon) (corners.first() + corners.last()) / 2f else corners.first()
        val direction = (corners[2] - start).let { it / it.getDistance() }
        val length = (corners[2] - start).getDistance()
        (1..<SEARCH_STEPS).map { start + direction * (length * it / SEARCH_STEPS) }
            .map { it to edgeDistance(outline, it) }
            .maxBy { it.second }
    }

    /** How wide the yard's colour is round its court. */
    private val courtBorder: Float = min(1f, yardCircles[0].second * if (hexagon) HEXAGON_COURT_INSET else COURT_INSET)

    override fun yardCourt(seat: Int): List<Offset> = yardWithin(seat, courtBorder)

    /** As big as the classic yard's on the three-seat board; in a wedge, as big as its yard has room for, with its badges, and no bigger than that. */
    override fun yardSpotRadiusOf(pawns: Int): Float =
        if (hexagon) BoardLayout.YARD_SPOT_RADIUS else min(BoardLayout.YARD_SPOT_RADIUS, yardCircles[0].second * SPOT_SHARE.getValue(pawns))

    override fun loneYardSpot(seat: Int): Offset = yardCircles[seat].first

    /** Outwards, so they turn with the yard. */
    override fun badgeAxis(seat: Int): Offset = (loneYardSpot(seat) - middle).let { it / it.getDistance() }

    override fun yardSpotOf(seat: Int, pawn: Int, pawns: Int): Offset = when {
        pawns == 1 -> loneYardSpot(seat)
        hexagon -> longYardSpot(seat, pawn, pawns)
        else -> wedgeYardSpot(seat, pawn, pawns)
    }

    /** In a line along the three-seat board's long yard; five or six in two rows, the inner of three. */
    private fun longYardSpot(seat: Int, pawn: Int, pawns: Int): Offset {
        val centre = loneYardSpot(seat)
        val outward = badgeAxis(seat)
        val side = Offset(-outward.y, outward.x)
        if (pawns <= YARD_SPOTS) return centre + side * ((pawn - (pawns - 1) / 2f) * LINE_SPACING.getValue(pawns))
        val row = pawn / ROW
        val across = pawn % ROW - ((if (row == 0) ROW else pawns - ROW) - 1) / 2f
        return centre + outward * ((row * 2 - 1) * ROWS_OUT) + side * (across * ROW_SPACING)
    }

    /**
     * In pairs along a wedge's two straight sides, from its arc in, an odd one at its inner tip: so the middle is clear, and the whole
     * of each spot is on the court.
     */
    private fun wedgeYardSpot(seat: Int, pawn: Int, pawns: Int): Offset {
        val reach = yardWithin(seat, courtBorder + yardSpotRadiusOf(pawns) + SPOT_MARGIN)
        val tip = reach.first()
        if (pawn == pawns - 1 && pawns % 2 == 1) return tip
        val corner = if (pawn % 2 == 0) reach[1] else reach.last()
        return tip + (corner - tip) * PAIRS_OUT.getValue(pawns)[pawn / 2]
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
    override fun homeSpot(seat: Int, slot: Int, pawns: Int): Offset = when {
        hexagon && pawns > YARD_SPOTS ->
            point(seat, armStart * HEXAGON_HOME_DEPTH + (slot / 3 * 2 - 1) * HEXAGON_HOME_SPREAD_OUT, (slot % 3 - if (slot / 3 == 1) (pawns - 4) / 2.0 else 1.0) * HEXAGON_HOME_SPREAD_ACROSS)
        hexagon && pawns == 1 -> point(seat, armStart * HEXAGON_HOME_DEPTH, 0.0)
        // The last of three on its own in its row's middle.
        hexagon && pawns == 3 && slot == 2 -> point(seat, armStart * HEXAGON_HOME_DEPTH + HEXAGON_HOME_SPREAD_OUT, 0.0)
        hexagon -> point(seat, armStart * HEXAGON_HOME_DEPTH + (slot / 2 * 2 - 1) * HEXAGON_HOME_SPREAD_OUT, (slot % 2 * 2 - 1) * HEXAGON_HOME_SPREAD_ACROSS)
        pawns > YARD_SPOTS -> point(seat, armStart - CROWDED_HOME_INSET, (slot - (pawns - 1) / 2.0) * CROWDED_HOME_SPACING)
        else -> point(seat, armStart - HOME_INSET, (slot - (pawns - 1) / 2.0) * HOME_SPACING)
    }

    override val homeBounds: Rect = (0..<seats).flatMap { homeArea(it) }.let { points ->
        Rect(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }

    private companion object {
        /** Seat 0's arm points down, as on the classic board, so its yard is at the bottom left. */
        const val ARM_START_DEGREES = 90.0
        const val ARM_LENGTH = 6
        const val HALF_ARM = 1.5
        const val LEAVING_LANE = 0.5
        const val MIDDLE_LANE = -0.5
        const val ARRIVING_LANE = -1.5
        const val YARD_SIDE_SQUARES = 3

        /** The most a yard's arc turns in one of the straight steps it's drawn in, in radians: small enough to look round. */
        const val MAX_ARC_STEP = PI / 90

        /** How many of a yard's corners are its arc's: its two ends and its middle. */
        const val ARC_CORNERS = 3

        /** The plain margin round the board's furthest corners. */
        const val RIM = 0.4
        const val SEARCH_STEPS = 200
        const val COURT_INSET = 0.25f
        const val HEXAGON_COURT_INSET = 0.33f
        /** How big a wedge's spots are, of its yard's widest circle, by how many pawns there are. */
        val SPOT_SHARE = mapOf(1 to 0.345f, 2 to 0.345f, 3 to 0.295f, 4 to 0.29f, 5 to 0.25f, 6 to 0.235f)
        const val SPOT_MARGIN = 0.05f

        /** The three-seat board's yard spots: how far apart in a line of so many, and its two rows of [ROW], from the middle and along. */
        val LINE_SPACING = mapOf(2 to 2.4f, 3 to 2f, 4 to 1.6f)
        const val ROW = 3
        const val ROWS_OUT = 0.8f
        const val ROW_SPACING = 1.9f

        /** How far each pair of a wedge's pawns is from its tip to its arc, outermost first, by how many pawns there are. */
        val PAIRS_OUT = mapOf(2 to listOf(0.85f), 3 to listOf(1f), 4 to listOf(1f, 0.5f), 5 to listOf(1f, 0.65f), 6 to listOf(1f, 0.685f, 0.37f))
        const val HOME_INSET = 0.4
        const val HOME_SPACING = 0.5
        const val CROWDED_HOME_INSET = 0.32
        const val CROWDED_HOME_SPACING = 0.36
        const val HEXAGON_HOME_DEPTH = 0.6
        const val HEXAGON_HOME_SPREAD_OUT = 0.42
        const val HEXAGON_HOME_SPREAD_ACROSS = 0.48
    }
}

/** A place in a yard's court for a badge: its [centre], and how much [room] there is round it, in board units. */
class YardBadge(val centre: Offset, val room: Float)

/** One side of a convex outline: a point on it, and the way in from it. */
private class InsideSide(private val from: Offset, private val inward: Offset) {
    /** How far inside this side [point] is, negative outside it. */
    fun distanceTo(point: Offset): Float = (point.x - from.x) * inward.x + (point.y - from.y) * inward.y
}

/** The sides of the convex [outline]. */
private fun insideSides(outline: List<Offset>): List<InsideSide> {
    val inside = outline.reduce { a, b -> a + b } / outline.size.toFloat()
    return outline.indices.map { i ->
        val from = outline[i]
        val inward = (outline[(i + 1) % outline.size] - from).let { Offset(-it.y, it.x) / it.getDistance() }
        InsideSide(from, if ((inside - from).let { it.x * inward.x + it.y * inward.y } < 0) -inward else inward)
    }
}

/** How far [point] is from the nearest side of the convex [outline]. */
private fun edgeDistance(outline: List<Offset>, point: Offset): Float = outline.indices.minOf { i ->
    val from = outline[i]
    val side = outline[(i + 1) % outline.size] - from
    abs(side.x * (point.y - from.y) - side.y * (point.x - from.x)) / side.getDistance()
}

/**
 * The convex [outline] shrunk by [by] on every side (grown, for a negative [by]), a corner for each of its own: a frame's
 * inside. No side may be so short that it shrinks away, so a yard with a round side takes only a thin frame.
 */
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
