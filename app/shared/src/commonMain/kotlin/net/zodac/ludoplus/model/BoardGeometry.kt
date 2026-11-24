package net.zodac.ludoplus.model

/**
 * The shape of a board: a looped track shared by every seat, a start square per seat, star squares, and a home column
 * per seat leading off the track to home. A pawn's place is measured by its **progress**: how many steps it has come
 * from its own start square ([PawnPosition.progressOf]). Nothing here assumes four seats: [forPlayers] gives each
 * player count its board.
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
    /**
     * Whether the track is all there is: a pawn never turns into a home column but goes round again, over every square
     * of the track, its progress counting on lap after lap.
     */
    val endless: Boolean = false,
) {
    init {
        require(seats > 0 && seatSpacing * seats == trackLength) { "The seats must space evenly round the track" }
        require(starOffset in 1..<seatSpacing) { "A star square must lie between two start squares" }
        require(homeColumnLength > 0) { "A home column needs a square" }
    }

    /** How many track squares a pawn visits, its start square included, before it turns into its home column: more than any game reaches, where the board is [endless]. */
    val trackSteps: Int get() = if (endless) ENDLESS_STEPS else trackLength - 1

    /** The progress of a pawn that is home: its start square is 0, the end of the track [trackSteps] - 1. */
    val homeProgress: Int get() = trackSteps + homeColumnLength

    /** The absolute track square of [seat]'s start square. */
    fun startSquare(seat: Int): Int = seat * seatSpacing

    /** The absolute track square a pawn of [seat] stands on with [progress] steps from its start, while on the track. */
    fun trackSquare(seat: Int, progress: Int): Int {
        require(progress in 0..<trackSteps) { "Progress $progress is off the track" }
        return (startSquare(seat) + progress) % trackLength
    }

    /** The progress a pawn of [seat] on the absolute track [square] has, counted from its own start square, or null on the square its way never takes it over. */
    fun progressAt(seat: Int, square: Int): Int? = (square - startSquare(seat)).mod(trackLength).takeIf { it < trackSteps }

    /** Whether the absolute track [square] is safe: a start square or a star square. */
    fun isSafe(square: Int): Boolean = square % seatSpacing == 0 || square % seatSpacing == starOffset

    /** The seats [players] players sit at, spread as evenly round the board as they go: 2 on 4 seats sit opposite. */
    fun seatsFor(players: Int): List<Int> {
        require(players in 1..seats) { "A $seats-seat board takes 1 to $seats players" }
        return (0..<players).map { it * seats / players }
    }

    companion object {
        /** The classic 15x15 cross: 52 track squares, 13 between seats, the stars 8 on from each start, columns of 5. */
        val CLASSIC = BoardGeometry(seats = 4, trackLength = 52, seatSpacing = 13, starOffset = 8, homeColumnLength = 5)

        /**
         * Three arms round a hexagon, each yard's side of it 3 more track squares: 16 a seat, the stars 3 up the next arm
         * as on the classic board (11 on).
         */
        val THREE_SEAT = BoardGeometry(seats = 3, trackLength = 48, seatSpacing = 16, starOffset = 11, homeColumnLength = 5)

        /** An endless board's track steps: over any game's, so a pawn there is always on the track. */
        private const val ENDLESS_STEPS = 1_000_000

        /** The fewest and most players a game takes. */
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 8

        /** [seats] arms round a [seats]-sided centre, each arm as the classic board's: 13 track squares a seat. */
        fun star(seats: Int): BoardGeometry =
            BoardGeometry(seats = seats, trackLength = 13 * seats, seatSpacing = 13, starOffset = 8, homeColumnLength = 5)

        /** The board [players] players race on: the classic one for 2 and 4, and one seat a player for 3 and 5-8. */
        fun forPlayers(players: Int): BoardGeometry = when (players) {
            2, 4 -> CLASSIC
            3 -> THREE_SEAT
            in 5..MAX_PLAYERS -> star(players)
            else -> throw IllegalArgumentException("No board for $players players")
        }
    }
}
