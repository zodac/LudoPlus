package net.zodac.ludoplus.game

import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PawnPosition

/**
 * What the coach card says at a [TutorialStep]. The words themselves are the UI's (`ui/game/CoachCard.kt`), one string
 * per entry, so the script stays pure.
 */
enum class TutorialMessage {
    WELCOME,
    WATCH_GREEN,
    GREEN_COMES_OUT,
    GREEN_ROLLS_AGAIN,
    GREEN_MOVES_ON,
    YOUR_TURN_OUT,
    BRING_OUT,
    SIX_ROLLS_AGAIN,
    MOVE_OUT_PAWN,
    CAPTURE_INTRO,
    CAPTURE,
    CAPTURE_ROLLS_AGAIN,
    CAPTURE_MOVE_ON,
    HOME_INTRO,
    HOME,
    HOME_ROLLS_AGAIN,
    HOME_MOVE_ON,
    WIN_INTRO,
    TOO_MANY,
    WIN_ROLL,
    WIN,
    PART_DONE,
}

/** One step of a [TutorialPart]: what the coach card says while it's waited for, and what it is. */
sealed interface TutorialStep {
    val message: TutorialMessage

    /** Whether the player carries on from this step by the Next button, having read its words: all but their own roll or move. */
    val waitsForNext: Boolean get() = this !is Roll && this !is Move

    /** The player taps the die, which rolls [value]. */
    data class Roll(override val message: TutorialMessage, val value: Int) : TutorialStep

    /** The player moves their pawn [pawn], the only one that can be moved. */
    data class Move(override val message: TutorialMessage, val pawn: Int) : TutorialStep

    /** The CPU rolls [value], once the player taps Next. */
    data class CpuRoll(override val message: TutorialMessage, val value: Int) : TutorialStep

    /** The CPU moves its pawn [pawn], once the player taps Next. */
    data class CpuMove(override val message: TutorialMessage, val pawn: Int) : TutorialStep

    /** Words alone, on the board as it stands, until the player taps Next: the end of a part that isn't the last. */
    data class Next(override val message: TutorialMessage) : TutorialStep
}

/**
 * One lesson of a [TutorialScript]: the game set out as [you] and [cpu] (each pawn's progress, as `PlayerState.pawns`),
 * the player to roll first, and its [steps] played from there.
 */
class TutorialPart(val you: List<Int>, val cpu: List<Int>, val steps: List<TutorialStep>)

/**
 * A guided game: the player ([YOU]) against one CPU ([CPU]), seated as any 2-player game is (opposite each other on the
 * classic board), in [parts], each a lesson from a position of its own, with every roll and move fixed. A part ends with
 * its last step, a [TutorialStep.Next], and the next starts from its own position; the last ends the game with the player's
 * win.
 */
class TutorialScript(val gameMode: GameMode, val parts: List<TutorialPart>) {
    init {
        require(parts.dropLast(1).all { it.steps.last() is TutorialStep.Next }) { "Each part but the last must end with a Next" }
    }

    /** The game as [part] starts: its pawns set out, and the player to roll. */
    fun start(part: Int): GameState {
        val lesson = parts[part]
        val new = GameState.new(gameMode, playerCount = 2, first = YOU)
        val pawns = listOf(lesson.you, lesson.cpu)
        return new.copy(players = new.players.mapIndexed { index, player -> player.copy(pawns = pawns[index]) })
    }

    companion object {
        /** The player's index, and the CPU's. */
        const val YOU = 0
        const val CPU = 1

        private const val Y = PawnPosition.YARD_PROGRESS

        /**
         * The classic game in four parts (`.claude/TUTORIAL.md`): getting out of the yard on a 6, with the 6's roll again;
         * a capture and its roll again; a pawn home and its roll again; and winning, after a roll too big to get home.
         */
        val CLASSIC = TutorialScript(
            GameMode.CLASSIC,
            listOf(
                // Everyone in the yard. A 3 can't bring a pawn out; Green's 6 does, and rolls again; then the player's.
                TutorialPart(
                    you = listOf(Y, Y, Y, Y),
                    cpu = listOf(Y, Y, Y, Y),
                    steps = listOf(
                        TutorialStep.Roll(TutorialMessage.WELCOME, 3),
                        TutorialStep.CpuRoll(TutorialMessage.WATCH_GREEN, 6),
                        TutorialStep.CpuMove(TutorialMessage.GREEN_COMES_OUT, 0),
                        TutorialStep.CpuRoll(TutorialMessage.GREEN_ROLLS_AGAIN, 2),
                        TutorialStep.CpuMove(TutorialMessage.GREEN_MOVES_ON, 0),
                        TutorialStep.Roll(TutorialMessage.YOUR_TURN_OUT, 6),
                        TutorialStep.Move(TutorialMessage.BRING_OUT, 0),
                        TutorialStep.Roll(TutorialMessage.SIX_ROLLS_AGAIN, 4),
                        TutorialStep.Move(TutorialMessage.MOVE_OUT_PAWN, 0),
                        TutorialStep.Next(TutorialMessage.PART_DONE),
                    ),
                ),
                // The player's pawn on square 10, Green's on 14 (40 from its own start, opposite): a 4 lands on it.
                TutorialPart(
                    you = listOf(10, Y, Y, Y),
                    cpu = listOf(40, Y, Y, Y),
                    steps = listOf(
                        TutorialStep.Roll(TutorialMessage.CAPTURE_INTRO, 4),
                        TutorialStep.Move(TutorialMessage.CAPTURE, 0),
                        TutorialStep.Roll(TutorialMessage.CAPTURE_ROLLS_AGAIN, 5),
                        TutorialStep.Move(TutorialMessage.CAPTURE_MOVE_ON, 0),
                        TutorialStep.Next(TutorialMessage.PART_DONE),
                    ),
                ),
                // A pawn 2 from home in the home column, another on the track.
                TutorialPart(
                    you = listOf(54, 20, Y, Y),
                    cpu = listOf(12, Y, Y, Y),
                    steps = listOf(
                        TutorialStep.Roll(TutorialMessage.HOME_INTRO, 2),
                        TutorialStep.Move(TutorialMessage.HOME, 0),
                        TutorialStep.Roll(TutorialMessage.HOME_ROLLS_AGAIN, 3),
                        TutorialStep.Move(TutorialMessage.HOME_MOVE_ON, 1),
                        TutorialStep.Next(TutorialMessage.PART_DONE),
                    ),
                ),
                // Three pawns home, the last 4 away: a 5 is too many, so Green plays, then a 4 wins.
                TutorialPart(
                    you = listOf(56, 56, 56, 52),
                    cpu = listOf(30, 20, Y, Y),
                    steps = listOf(
                        TutorialStep.Roll(TutorialMessage.WIN_INTRO, 5),
                        TutorialStep.CpuRoll(TutorialMessage.TOO_MANY, 3),
                        TutorialStep.CpuMove(TutorialMessage.GREEN_MOVES_ON, 0),
                        TutorialStep.Roll(TutorialMessage.WIN_ROLL, 4),
                        TutorialStep.Move(TutorialMessage.WIN, 3),
                    ),
                ),
            ),
        )
    }
}
