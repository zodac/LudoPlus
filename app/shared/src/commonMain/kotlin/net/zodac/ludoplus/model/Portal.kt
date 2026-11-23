package net.zodac.ludoplus.model

/**
 * A pair of portals on a board: a pawn whose move ends on one comes out at the other, either way round. [first] and
 * [second] are absolute track squares, [steps] apart in the direction of play ([second] may lie past the track's last
 * square, back at its first). [seat] is whose stretch of the track [first] is on, and [long] whether the pair reaches into
 * the next seat's, so past its start square.
 */
data class Portal(val seat: Int, val long: Boolean, val first: Int, val second: Int, val steps: Int) {
    /** How far a pawn going in at the absolute track [square], one of the pair's, is taken: forwards, or back. */
    fun stepsFrom(square: Int): Int = if (square == first) steps else -steps
}

/**
 * The board's portals: for each seat, a short pair within its own stretch of the track and a long one into the next
 * seat's, the same for every seat so none is favoured. None is on a safe square.
 */
val BoardGeometry.portals: List<Portal>
    get() = (0..<seats).flatMap { listOf(shortPortal(it), longPortal(it)) }

private fun BoardGeometry.shortPortal(seat: Int): Portal = portal(seat, long = false, from = SHORT_FROM, to = SHORT_TO)

private fun BoardGeometry.longPortal(seat: Int): Portal = portal(seat, long = true, from = LONG_FROM, to = seatSpacing + LONG_TO)

private fun BoardGeometry.portal(seat: Int, long: Boolean, from: Int, to: Int): Portal =
    Portal(seat, long, startSquare(seat) + from, (startSquare(seat) + to) % trackLength, to - from)

/** The portal with an end on the absolute track [square], or null. */
fun BoardGeometry.portalAt(square: Int): Portal? {
    val seat = square / seatSpacing
    return when (square % seatSpacing) {
        SHORT_FROM, SHORT_TO -> shortPortal(seat)
        LONG_FROM -> longPortal(seat)
        LONG_TO -> longPortal((seat + seats - 1) % seats)
        else -> null
    }
}

/**
 * Where a pawn of [seat] comes out, as progress, after a move that ends at [progress]: its portal's other end, or
 * [progress] itself where there's none, or where going through would take it past its own start square, so into its home
 * column early or round for another lap.
 */
fun BoardGeometry.throughPortal(seat: Int, progress: Int): Int {
    if (progress !in 0..<trackSteps) return progress
    val square = trackSquare(seat, progress)
    val out = progress + (portalAt(square)?.stepsFrom(square) ?: 0)
    return if (out in 0..<trackSteps) out else progress
}

/** A short pair's squares, and a long one's, from the seat's start square (the long one's second from the next seat's). */
private const val SHORT_FROM = 3
private const val SHORT_TO = 10
private const val LONG_FROM = 6
private const val LONG_TO = 9
