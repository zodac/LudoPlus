package net.zodac.ludoplus.model

/**
 * A pair of portals on a board: a pawn whose move ends on one comes out at the other, either way round. [first] and
 * [second] are absolute track squares, [steps] apart in the direction of play ([second] may lie past the track's last
 * square, back at its first). [seat] and [long] name the pair, and its symbol: [seat] is whose stretch of the track [first]
 * is on as a game starts ([BoardGeometry.portals]), and [long] whether the pair then reaches into the next seat's, so past
 * its start square. A pair keeps its name and [steps] wherever it reopens.
 */
data class Portal(val seat: Int, val long: Boolean, val first: Int, val second: Int, val steps: Int) {
    /** The other end from the absolute track [square], one of the pair's. */
    fun otherEnd(square: Int): Int = if (square == first) second else first

    /** Whether [other] is this pair, wherever either is open. */
    fun isPair(other: Portal): Boolean = seat == other.seat && long == other.long

    /** This pair, open at the absolute track [square] and [steps] on from it on [board]. */
    fun openAt(board: BoardGeometry, square: Int): Portal = copy(first = square, second = (square + steps) % board.trackLength)
}

/**
 * The board's portals as a game starts: for each seat, a short pair within its own stretch of the track and a long one
 * into the next seat's, the same for every seat so none is favoured, and open to all. None is on a safe square.
 */
val BoardGeometry.portals: List<Portal>
    get() = (0..<seats).flatMap { listOf(shortPortal(it), longPortal(it)) }

private fun BoardGeometry.shortPortal(seat: Int): Portal = portal(seat, long = false, from = SHORT_FROM, to = SHORT_TO)

private fun BoardGeometry.longPortal(seat: Int): Portal = portal(seat, long = true, from = LONG_FROM, to = seatSpacing + LONG_TO)

private fun BoardGeometry.portal(seat: Int, long: Boolean, from: Int, to: Int): Portal =
    Portal(seat, long, startSquare(seat) + from, (startSquare(seat) + to) % trackLength, to - from)

/**
 * Whether a portal may open on the absolute track [square] of this board: never on a safe square, nor on the square before
 * a start square, which that seat's pawns never stand on.
 */
fun BoardGeometry.canHavePortal(square: Int): Boolean = !isSafe(square) && (square + 1) % seatSpacing != 0

/** The pair of these portals with an end on the absolute track [square], or null. */
fun List<Portal>.portalAt(square: Int): Portal? = firstOrNull { it.first == square || it.second == square }

/**
 * Where a pawn of [seat] comes out, as progress, after a move that ends at [progress], [portals] open on this board: its
 * portal's other end, counted from its own start square as any square is, or [progress] itself where there's none. So a
 * long pair either side of its own start takes a pawn most of the way round, or most of the way back.
 */
fun BoardGeometry.throughPortal(portals: List<Portal>, seat: Int, progress: Int): Int {
    if (progress !in 0..<trackSteps) return progress
    val square = trackSquare(seat, progress)
    return portals.portalAt(square)?.let { progressAt(seat, it.otherEnd(square)) } ?: progress
}

/** A short pair's squares, and a long one's, from the seat's start square (the long one's second from the next seat's). */
private const val SHORT_FROM = 3
private const val SHORT_TO = 10
private const val LONG_FROM = 6
private const val LONG_TO = 9
