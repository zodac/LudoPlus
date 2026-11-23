package net.zodac.ludoplus.model

/** Where a pawn is, relative to its own seat. A pawn's state is stored as its progress ([progressOf]). */
sealed interface PawnPosition {
    /** Waiting in its seat's yard, not yet on the board. */
    data object Yard : PawnPosition

    /** On the shared track, [steps] squares on from its own start square (0 is the start square). */
    data class Track(val steps: Int) : PawnPosition

    /** In its seat's home column, on square [index] (0 is the first, nearest the track). */
    data class HomeColumn(val index: Int) : PawnPosition

    /** Home: finished, in the centre. */
    data object Home : PawnPosition

    /** Captured, where that puts a pawn out of the game ([GameRules.lastStanding]). */
    data object Out : PawnPosition

    companion object {
        /** The progress a pawn that is out of the game is stored as. */
        const val OUT_PROGRESS = -2

        /** The progress a pawn in its yard is stored as. */
        const val YARD_PROGRESS = -1

        /** The position of a pawn with [progress] steps from its start square on [board]. */
        fun of(progress: Int, board: BoardGeometry): PawnPosition = when {
            progress == OUT_PROGRESS -> Out
            progress == YARD_PROGRESS -> Yard
            progress in 0..<board.trackSteps -> Track(progress)
            progress in board.trackSteps..<board.homeProgress -> HomeColumn(progress - board.trackSteps)
            progress == board.homeProgress -> Home
            else -> throw IllegalArgumentException("Progress $progress is off the board")
        }

        /** The progress that [position] is stored as on [board]: the inverse of [of]. */
        fun progressOf(position: PawnPosition, board: BoardGeometry): Int = when (position) {
            Out -> OUT_PROGRESS
            Yard -> YARD_PROGRESS
            is Track -> position.steps
            is HomeColumn -> board.trackSteps + position.index
            Home -> board.homeProgress
        }
    }
}

/** How many steps a pawn at [position] on [board] has still to go to reach home: none once home. */
fun stepsToHome(position: PawnPosition, board: BoardGeometry): Int = when (position) {
    // A yard pawn's first step is onto its start square.
    PawnPosition.Yard -> board.homeProgress + 1
    else -> board.homeProgress - PawnPosition.progressOf(position, board)
}
