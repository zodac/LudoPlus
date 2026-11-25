package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.ui.style.strokes

/** Every board but the classic one, as each player count draws it: 3 and 5-8 players. */
class StarBoardLayoutTest {

    private val layouts = listOf(3, 5, 6, 7, 8).map { BoardLayout.of(BoardGeometry.forPlayers(it)) as StarBoardLayout }

    /** Whether [point] is inside the convex [outline], at least [margin] in from every side. */
    private fun inside(outline: List<Offset>, point: Offset, margin: Float = 0f): Boolean {
        val centre = outline.reduce { a, b -> a + b } / outline.size.toFloat()
        return outline.indices.all { i ->
            val from = outline[i]
            val side = outline[(i + 1) % outline.size] - from
            fun cross(p: Offset) = side.x * (p.y - from.y) - side.y * (p.x - from.x)
            cross(point) * cross(centre) > 0 && abs(cross(point)) / side.getDistance() >= margin
        }
    }

    @Test
    fun `2 and 4 players take the classic layout - the rest a star of their own`() {
        assertSame(ClassicBoardLayout, BoardLayout.of(BoardGeometry.forPlayers(2)))
        assertSame(ClassicBoardLayout, BoardLayout.of(BoardGeometry.forPlayers(4)))
        listOf(3, 5, 6, 7, 8).forEach { assertIs<StarBoardLayout>(BoardLayout.of(BoardGeometry.forPlayers(it))) }
        assertEquals(listOf(3, 5, 6, 7, 8), layouts.map { it.geometry.seats })
    }

    @Test
    fun `the track is different squares - each touching the one before - all on the round board`() {
        for (layout in layouts) {
            val squares = (0..<layout.geometry.trackLength).map { layout.trackSquare(it) }
            assertEquals(squares.size, squares.map { it.centre }.toSet().size, "${layout.geometry.seats} seats")
            for (i in squares.indices) {
                val (a, b) = squares[i] to squares[(i + 1) % squares.size]
                // Side by side, or corner to corner where the track turns between arms; never a gap.
                assertTrue(a.corners.any { p -> b.corners.any { (it - p).getDistance() < 0.01f } }, "${layout.geometry.seats} seats: square $i to ${i + 1}")
            }
            val middle = Offset(layout.size / 2, layout.size / 2)
            assertTrue(squares.flatMap { it.corners }.all { (it - middle).getDistance() <= layout.size / 2 }, "${layout.geometry.seats} seats: off the board")
        }
    }

    @Test
    fun `every arm square is a whole square - only the three-seat board's yard sides are stretched`() {
        for (layout in layouts) {
            val track = (0..<layout.geometry.trackLength).map { layout.trackSquare(it) }
            val stretched = track.count { !it.isUnit }
            assertEquals(if (layout.geometry.seats == 3) 6 else 0, stretched, "${layout.geometry.seats} seats")
            val columns = (0..<layout.geometry.seats).flatMap { seat -> (0..4).map { layout.homeColumnSquare(seat, it) } }
            assertTrue(columns.all { it.isUnit })
        }
    }

    @Test
    fun `a pawn's last track square leads into its home column - which leads into its home`() {
        for (layout in layouts) {
            val board = layout.geometry
            for (seat in 0..<board.seats) {
                val last = layout.trackSquare(board.trackSquare(seat, board.trackSteps - 1)).centre
                assertEquals(1f, (last - layout.homeColumnSquare(seat, 0).centre).getDistance(), 0.01f, "${board.seats} seats, seat $seat")
                // Just past the middle of the column's inner end is inside the seat's part of home.
                val middle = Offset(layout.size / 2, layout.size / 2)
                val innerEnd = layout.homeColumnSquare(seat, board.homeColumnLength - 1).corners.sortedBy { (it - middle).getDistance() }.take(2)
                val doorway = (innerEnd[0] + innerEnd[1]) / 2f
                assertTrue(inside(layout.homeArea(seat), doorway + (middle - doorway) / (middle - doorway).getDistance() * 0.05f), "${board.seats} seats, seat $seat")
            }
        }
    }

    @Test
    fun `each seat's start square is on its own arm next to its yard - its star 3 up the next arm`() {
        for (layout in layouts) {
            val board = layout.geometry
            for (seat in 0..<board.seats) {
                val start = layout.trackSquare(board.startSquare(seat)).centre
                val star = layout.trackSquare(board.startSquare(seat) + board.starOffset).centre
                assertTrue(board.isSafe(board.startSquare(seat) + board.starOffset))
                // Both a square's width from the home column they flank: the start square beside this seat's column...
                assertEquals(1f, (start - layout.homeColumnSquare(seat, 0).centre).getDistance(), 0.01f)
                // ...and the star beside the next seat's column, 3 squares from its end.
                assertEquals(1f, (star - layout.homeColumnSquare((seat + 1) % board.seats, 1).centre).getDistance(), 0.01f)
            }
        }
    }

    @Test
    fun `yard spots sit inside their yard's court - apart - each pawn on one inside the yard`() {
        for (layout in layouts) {
            for (seat in 0..<layout.geometry.seats) {
                val spots = (0..3).map { layout.yardSpot(seat, it) }
                // Each spot in the court, and the whole pawn on it in the yard.
                spots.forEach { assertTrue(inside(layout.yardCourt(seat), it), "${layout.geometry.seats} seats, seat $seat: $it") }
                spots.forEach { assertTrue(inside(layout.yard(seat), it, layout.yardPawnRadiusOf(4)), "${layout.geometry.seats} seats, seat $seat: $it") }
                val gaps = spots.indices.flatMap { i -> (i + 1..<4).map { j -> (spots[i] - spots[j]).getDistance() } }
                assertTrue(gaps.min() >= 2 * layout.yardSpotRadiusOf(4), "${layout.geometry.seats} seats: spots overlap")
                assertTrue(layout.yardPawnRadiusOf(4) >= PAWN_RADIUS, "${layout.geometry.seats} seats: yard pawns smaller than a track pawn")
            }
        }
    }

    @Test
    fun `the three-seat board's yard spots are in a line along the yard about its middle - five or six in two rows as big`() {
        val layout = layouts.first()
        val boardMiddle = Offset(layout.size / 2, layout.size / 2)
        for (seat in 0..<3) {
            val middle = layout.loneYardSpot(seat)
            val outward = (middle - boardMiddle).let { it / it.getDistance() }
            fun out(spot: Offset) = (spot - middle).let { it.x * outward.x + it.y * outward.y }
            for (pawns in 2..4) {
                val spots = (0..<pawns).map { layout.yardSpotOf(seat, it, pawns) }
                val step = spots[1] - spots[0]
                spots.zipWithNext { a, b -> assertTrue((b - a - step).getDistance() < 0.001f, "seat $seat: $spots") }
                assertTrue(((spots.first() + spots.last()) / 2f - middle).getDistance() < 0.001f, "seat $seat: $spots")
                spots.forEach { assertEquals(0f, out(it), 0.001f, "seat $seat") }
            }
            assertEquals(listOf(true, true, true, false, false), (0..4).map { out(layout.yardSpotOf(seat, it, 5)) < 0f })
            assertEquals(listOf(true, true, true, false, false, false), (0..5).map { out(layout.yardSpotOf(seat, it, 6)) < 0f })
        }
        // As big as the classic board's, however many there are.
        for (pawns in 1..6) {
            assertEquals(ClassicBoardLayout.yardSpotRadiusOf(4), layout.yardSpotRadiusOf(pawns))
            assertEquals(YARD_PAWN_RADIUS, layout.yardPawnRadiusOf(pawns), 0.001f)
        }
    }

    @Test
    fun `a wedge of a yard has its pawns in pairs about its middle - an odd one at its tip`() {
        for (layout in layouts.drop(1)) {
            val boardMiddle = Offset(layout.size / 2, layout.size / 2)
            for (seat in 0..<layout.geometry.seats) for (pawns in 2..6) {
                val outward = (layout.loneYardSpot(seat) - boardMiddle).let { it / it.getDistance() }
                fun out(spot: Offset) = (spot - boardMiddle).let { it.x * outward.x + it.y * outward.y }
                fun across(spot: Offset) = (spot - boardMiddle).let { it.x * -outward.y + it.y * outward.x }
                val spots = (0..<pawns).map { layout.yardSpotOf(seat, it, pawns) }
                val about = "${layout.geometry.seats} seats, seat $seat: $spots"
                for ((a, b) in spots.chunked(2).filter { it.size == 2 }) {
                    assertEquals(out(a), out(b), 0.001f, about)
                    assertEquals(across(a), -across(b), 0.001f, about)
                }
                if (pawns % 2 == 1) {
                    assertEquals(0f, across(spots.last()), 0.001f, about)
                    assertTrue(spots.dropLast(1).all { out(it) > out(spots.last()) }, about)
                }
            }
        }
    }

    @Test
    fun `a yard's outer side - and its court's - is one smooth arc about the board's middle - from arm to arm`() {
        for (layout in layouts) {
            val middle = Offset(layout.size / 2, layout.size / 2)
            for (seat in 0..<layout.geometry.seats) {
                val corners = layout.yardCorners(seat)
                val yard = layout.yard(seat)
                val about = "${layout.geometry.seats} seats, seat $seat"
                for (outline in listOf(yard, layout.yardCourt(seat))) {
                    val sides = strokes(outline)
                    // As many sides as a straight-sided yard, but for its outer two, which are one.
                    assertEquals(corners.size - 1, sides.size, about)
                    val arc = sides.single { it.size > 2 }
                    val radius = (arc.first() - middle).getDistance()
                    arc.forEach { assertEquals(radius, (it - middle).getDistance(), 0.001f, about) }
                    // No step of it strays from the circle by as much as a pixel of a big board's square.
                    arc.zipWithNext { a, b -> assertTrue(radius - ((a + b) / 2f - middle).getDistance() < 0.005f, about) }
                }
                // The arc runs between the arms' ends, through the rim.
                val arc = strokes(yard).single { it.size > 2 }
                assertTrue((arc.first() - corners[1]).getDistance() < 0.001f, about)
                assertTrue((arc.last() - corners[3]).getDistance() < 0.001f, about)
                assertEquals((corners[2] - middle).getDistance(), (arc.first() - middle).getDistance(), 0.001f, about)
            }
        }
    }

    @Test
    fun `a yard's court is as far from every one of its sides`() {
        for (layout in layouts) {
            for (seat in 0..<layout.geometry.seats) {
                val (yard, court) = strokes(layout.yard(seat)) to strokes(layout.yardCourt(seat))
                val middle = Offset(layout.size / 2, layout.size / 2)
                val gaps = yard.zip(court) { outer, inner ->
                    val (from, to) = outer.first() to outer.last()
                    val side = to - from
                    if (outer.size > 2) {
                        (from - middle).getDistance() - (inner.first() - middle).getDistance()
                    } else {
                        abs(side.x * (inner.first().y - from.y) - side.y * (inner.first().x - from.x)) / side.getDistance()
                    }
                }
                gaps.forEach { assertEquals(gaps.first(), it, 0.001f, "${layout.geometry.seats} seats, seat $seat: $gaps") }
            }
        }
    }

    @Test
    fun `no yard covers a track square`() {
        for (layout in layouts) {
            val board = layout.geometry
            val centres = (0..<board.trackLength).map { layout.trackSquare(it).centre }
            for (seat in 0..<board.seats) {
                assertTrue(centres.none { inside(layout.yard(seat), it) }, "${board.seats} seats, seat $seat")
            }
        }
    }
}
