package net.zodac.ludoplus.ui.game

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.BoardGeometry

/** The least room any yard has for a badge, with eight players and six pawns each; and with four players or fewer. */
private const val LEAST_BADGE_ROOM = 0.5f
private const val WIDE_BADGE_ROOM = 0.66f

class BoardLayoutTest {

    private val board = BoardGeometry.CLASSIC

    private fun about(geometry: BoardGeometry, seat: Int, pawns: Int) = "$geometry seat $seat, $pawns pawns"

    // Side by side, or corner to corner where the track turns round the centre (5,6 to 6,5).
    private fun adjacent(a: Cell, b: Cell) = a != b && abs(a.column - b.column) <= 1 && abs(a.row - b.row) <= 1

    @Test
    fun `the track is 52 different squares - each touching the one before - round the cross`() {
        val cells = (0..<board.trackLength).map { ClassicBoardLayout.trackCell(it) }
        assertEquals(board.trackLength, cells.toSet().size)
        for (i in cells.indices) {
            val (a, b) = cells[i] to cells[(i + 1) % cells.size]
            assertTrue(adjacent(a, b), "$a to $b")
        }
        // Every track square is on an arm of the cross, and none in the centre.
        assertTrue(cells.all { it.column in 6..8 || it.row in 6..8 })
        assertTrue(cells.none { it.column in 6..8 && it.row in 6..8 })
    }

    @Test
    fun `each seat's start - star - home column - and yard are where the classic board has them`() {
        // Seat 0 (the first player) at the bottom left, the rest round from it clockwise.
        assertEquals(listOf(Cell(6, 13), Cell(1, 6), Cell(8, 1), Cell(13, 8)), (0..3).map { ClassicBoardLayout.trackCell(board.startSquare(it)) })
        assertEquals(listOf(Cell(2, 8), Cell(6, 2), Cell(12, 6), Cell(8, 12)), (0..3).map { ClassicBoardLayout.trackCell(board.startSquare(it) + 8) })
        assertEquals((13 downTo 9).map { Cell(7, it) }, (0..4).map { ClassicBoardLayout.homeColumnCell(0, it) })
        assertEquals((1..5).map { Cell(it, 7) }, (0..4).map { ClassicBoardLayout.homeColumnCell(1, it) })
        assertEquals((1..5).map { Cell(7, it) }, (0..4).map { ClassicBoardLayout.homeColumnCell(2, it) })
        assertEquals(listOf(Cell(0, 9), Cell(0, 0), Cell(9, 0), Cell(9, 9)), (0..3).map { ClassicBoardLayout.yardOrigin(it) })
    }

    @Test
    fun `a pawn's last track square leads straight into its home column`() {
        for (seat in 0..3) {
            assertTrue(adjacent(ClassicBoardLayout.trackCell(board.trackSquare(seat, board.trackSteps - 1)), ClassicBoardLayout.homeColumnCell(seat, 0)), "seat $seat")
        }
    }

    @Test
    fun `yard spots sit inside their seat's yard`() {
        for (seat in 0..3) {
            val origin = ClassicBoardLayout.yardOrigin(seat)
            for (slot in 0..3) {
                val spot = ClassicBoardLayout.yardSpot(seat, slot)
                assertTrue(spot.x > origin.column && spot.x < origin.column + 6 && spot.y > origin.row && spot.y < origin.row + 6, "$seat/$slot")
            }
        }
    }

    @Test
    fun `every pawn home has the whole of its spot in its own part of home - none overlapping - on every board with any number of pawns`() {
        for (geometry in (2..8).map(BoardGeometry::forPlayers)) {
            val layout = BoardLayout.of(geometry)
            for (seat in 0..<geometry.seats) for (pawns in 1..6) {
                val about = about(geometry, seat, pawns)
                val spots = List(pawns) { layout.homeSpot(seat, it, pawns) }
                val radius = layout.homeRows.spotRadius(pawns) - 0.001f
                val area = layout.homeArea(seat)
                for ((i, spot) in spots.withIndex()) {
                    for (other in spots.drop(i + 1)) assertTrue((spot - other).getDistance() >= 2 * radius, "$about: $spots")
                    val turns = area.indices.map { c ->
                        val side = area[(c + 1) % area.size] - area[c]
                        (side.x * (spot.y - area[c].y) - side.y * (spot.x - area[c].x)) / side.getDistance()
                    }
                    assertTrue(turns.all { it >= radius } || turns.all { it <= -radius }, "$about: spot $i is $turns from its edges")
                }
            }
        }
    }

    @Test
    fun `a classic pawn home stands clear of its part of home's lines, head and foot - on every board with any number of pawns`() {
        // A standing pawn's foot's two corners and its head, in its radii.
        val outline = listOf(Offset(-0.66f, 0.76f) to 0.205f, Offset(0.66f, 0.76f) to 0.205f, Offset(0f, -0.62f) to 0.425f)
        for (geometry in (2..8).map(BoardGeometry::forPlayers)) {
            val layout = BoardLayout.of(geometry)
            for (seat in 0..<geometry.seats) for (pawns in 1..6) for (pawn in 0..<pawns) {
                val area = layout.homeArea(seat)
                val radius = layout.homePawnRadiusOf(pawns)
                for ((at, reach) in outline) {
                    val point = layout.homeStand(seat, pawn, pawns) + at * radius
                    val turns = area.indices.map { c ->
                        val side = area[(c + 1) % area.size] - area[c]
                        (side.x * (point.y - area[c].y) - side.y * (point.x - area[c].x)) / side.getDistance()
                    }
                    val clear = maxOf(turns.min(), -turns.max()) - reach * radius
                    assertTrue(clear >= 0f, "${about(geometry, seat, pawns)}, pawn $pawn: $clear")
                }
            }
        }
    }

    @Test
    fun `the pawns home keep to their sides as one more comes - the third and fourth between them`() {
        for (geometry in (2..8).map(BoardGeometry::forPlayers)) {
            val rows = BoardLayout.of(geometry).homeRows
            for (pawns in 2..4) {
                // The first two on the left and right of the row along the base, and the third and fourth between them.
                val (left, right) = rows.spot(0, pawns) to rows.spot(1, pawns)
                assertTrue(left.x < 0f && right.x > 0f && left.y == right.y, "$geometry: $pawns")
                for (slot in 2..<pawns) assertEquals(0f, rows.spot(slot, pawns).x, 0.001f, "$geometry: $pawns")
            }
        }
    }

    @Test
    fun `pawns home are as big as there's room for - the fewer the bigger, as big as on the track where they can be, and never bigger`() {
        for (geometry in (2..8).map(BoardGeometry::forPlayers)) {
            val layout = BoardLayout.of(geometry)
            val radii = (1..6).map { layout.homePawnRadiusOf(it) }
            assertTrue(radii.zipWithNext().all { (fewer, more) -> fewer >= more - 0.001f }, "$geometry: $radii")
            assertTrue(radii.all { it <= PAWN_RADIUS }, "$geometry: $radii")
            for (pawns in 1..6) assertTrue(radii[pawns - 1] <= layout.homeRows.spotRadius(pawns) * 0.86f, "$geometry, $pawns pawns: within its spot")
            // A lone pawn is as big as on the track, and two are all but.
            assertEquals(PAWN_RADIUS, radii[0], "$geometry")
            assertTrue(radii[1] >= PAWN_RADIUS - 0.01f, "$geometry: $radii")
            // Four: as big as fit the classic triangle, and more where there's more room.
            assertTrue(radii[3] >= if (geometry.seats == 4) 0.285f else 0.32f, "$geometry: $radii")
        }
    }

    @Test
    fun `a yard's two badges are on its court - clear of its edge, every pawn's spot, and each other - on every board with any number of pawns`() {
        for (geometry in (2..8).map(BoardGeometry::forPlayers)) {
            val layout = BoardLayout.of(geometry)
            for (seat in 0..<geometry.seats) for (pawns in 1..6) {
                val (first, second) = layout.yardBadges(seat, pawns)
                val court = layout.yardCourt(seat)
                for (badge in listOf(first, second)) {
                    val about = about(geometry, seat, pawns)
                    assertTrue(badge.room >= if (geometry.seats <= 4) WIDE_BADGE_ROOM else LEAST_BADGE_ROOM, "$about: room for only ${badge.room}")
                    val turns = court.indices.map { c ->
                        val side = court[(c + 1) % court.size] - court[c]
                        (side.x * (badge.centre.y - court[c].y) - side.y * (badge.centre.x - court[c].x)) / side.getDistance()
                    }
                    assertTrue(turns.all { it >= badge.room - 0.01f } || turns.all { it <= 0.01f - badge.room }, "$about: $turns from its court's edges")
                    for (pawn in 0..<pawns) {
                        val gap = (layout.yardSpotOf(seat, pawn, pawns) - badge.centre).getDistance() - layout.yardSpotRadiusOf(pawns)
                        assertTrue(gap >= badge.room - 0.01f, "$about: $gap from pawn $pawn's spot")
                    }
                }
                assertEquals(first.room, second.room)
                // The same way round in every yard of the classic board, side by side between three pawns or more.
                if (layout == ClassicBoardLayout && pawns > 2) {
                    assertTrue(first.centre.x < second.centre.x, about(geometry, seat, pawns))
                    assertEquals(first.centre.y, second.centre.y, 0.001f, about(geometry, seat, pawns))
                }
                assertTrue((first.centre - second.centre).getDistance() >= 2 * first.room - 0.01f, about(geometry, seat, pawns))
            }
        }
    }

    @Test
    fun `every pawn's whole spot is on its yard's court - none overlapping - on every board with any number of pawns`() {
        for (geometry in (2..8).map(BoardGeometry::forPlayers)) {
            val layout = BoardLayout.of(geometry)
            for (seat in 0..<geometry.seats) for (pawns in 1..6) {
                val about = about(geometry, seat, pawns)
                val spots = List(pawns) { layout.yardSpotOf(seat, it, pawns) }
                val radius = layout.yardSpotRadiusOf(pawns)
                val court = layout.yardCourt(seat)
                for ((i, spot) in spots.withIndex()) {
                    for (other in spots.drop(i + 1)) assertTrue((spot - other).getDistance() >= 2 * radius, "$about: $spots")
                    val turns = court.indices.map { c ->
                        val side = court[(c + 1) % court.size] - court[c]
                        (side.x * (spot.y - court[c].y) - side.y * (spot.x - court[c].x)) / side.getDistance()
                    }
                    assertTrue(turns.all { it >= radius } || turns.all { it <= -radius }, "$about: spot $i is $turns from its court's edges")
                }
            }
        }
    }

    @Test
    fun `spots are as big as the fewer pawns there are - never bigger than the classic board's - each pawn within its spot`() {
        for (geometry in (2..8).map(BoardGeometry::forPlayers)) {
            val layout = BoardLayout.of(geometry)
            val spots = (1..6).map { layout.yardSpotRadiusOf(it) }
            assertEquals(spots.sortedDescending(), spots, "$geometry")
            assertTrue(spots.all { it <= ClassicBoardLayout.yardSpotRadiusOf(4) }, "$geometry: $spots")
            for (pawns in 1..6) {
                val (spot, pawn) = layout.yardSpotRadiusOf(pawns) to layout.yardPawnRadiusOf(pawns)
                assertTrue(pawn >= spot * 0.8f && pawn <= spot * 0.91f, "$geometry, $pawns pawns: $pawn on $spot")
            }
        }
    }

    @Test
    fun `an endless board is laid out as the same board with a home`() {
        for (geometry in (2..8).map(BoardGeometry::forPlayers)) assertEquals(BoardLayout.of(geometry), BoardLayout.of(geometry.copy(endless = true)))
    }
}
