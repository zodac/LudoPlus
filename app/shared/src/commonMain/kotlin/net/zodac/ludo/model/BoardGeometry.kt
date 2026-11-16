package net.zodac.ludo.model

/**
 * The shape of a board: a looped track shared by every seat, a start square per seat, star squares, and a home column
 * per seat leading off the track to home. A pawn's place is measured by its **progress**: how many steps it has come
 * from its own start square ([PawnPosition.progressOf]). Nothing here assumes four seats, so a bigger board is just
 * other numbers (Phase 8 of `.claude/IMPLEMENTATION.md`).
 *
 * Squares on the track are numbered absolutely from 0, at seat 0's start square, in the direction of play.
 */
data class BoardGeometry(
    /** How many seats the board has room for, each with a yard, a start square, and a home column. */
    val seats: Int,
    /** How many squares the shared track has. */
    val trackLength: Int,
    /** How many track squares lie between one seat's start square and the next seat's. */
    val seatSpacing: Int,
    /** How many squares into the track from a start square the star square (a safe square) lies. */
    val starOffset: Int,
    /** How many squares each seat's home column has, before home itself. */
    val homeColumnLength: Int,
) {
    init {
        require(seats > 0 && seatSpacing * seats == trackLength) { "The seats must space evenly round the track" }
        require(starOffset in 1..<seatSpacing) { "A star square must lie between two start squares" }
        require(homeColumnLength > 0) { "A home column needs a square" }
    }

    /** How many track squares a pawn visits, its start square included, before it turns into its home column. */
    val trackSteps: Int get() = trackLength - 1

    /** The progress of a pawn that is home: its start square is 0, the end of the track [trackSteps] - 1. */
    val homeProgress: Int get() = trackSteps + homeColumnLength

    /** The absolute track square of [seat]'s start square. */
    fun startSquare(seat: Int): Int = seat * seatSpacing

    /** The absolute track square a pawn of [seat] stands on with [progress] steps from its start, while on the track. */
    fun trackSquare(seat: Int, progress: Int): Int {
        require(progress in 0..<trackSteps) { "Progress $progress is off the track" }
        return (startSquare(seat) + progress) % trackLength
    }

    /** Whether the absolute track [square] is safe: a start square or a star square. */
    fun isSafe(square: Int): Boolean = square % seatSpacing == 0 || square % seatSpacing == starOffset

    companion object {
        /** The classic 15x15 cross: 52 track squares, 13 between seats, the stars 8 on from each start, columns of 5. */
        val CLASSIC = BoardGeometry(seats = 4, trackLength = 52, seatSpacing = 13, starOffset = 8, homeColumnLength = 5)
    }
}
