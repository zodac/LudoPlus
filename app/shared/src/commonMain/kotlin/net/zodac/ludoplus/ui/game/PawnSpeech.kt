package net.zodac.ludoplus.ui.game

import androidx.compose.runtime.Composable
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.stepsToHome
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.game_pawn_home_column_spoken
import net.zodac.ludoplus.resources.game_pawn_home_spoken
import net.zodac.ludoplus.resources.game_pawn_track_spoken
import net.zodac.ludoplus.resources.game_pawn_yard_spoken
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.stringResource

/**
 * What TalkBack says for [colour]'s pawn [number] (from 1) at [position] on [board]: "Red pawn 2, 14 squares from
 * home". On the track and in the home column it counts the steps still to go ([stepsToHome]).
 */
@Composable
fun pawnSpoken(colour: PlayerColour, number: Int, position: PawnPosition, board: BoardGeometry): String {
    val colourName = stringResource(colour.label)
    return when (position) {
        PawnPosition.Yard -> stringResource(Res.string.game_pawn_yard_spoken, colourName, number)
        PawnPosition.Home -> stringResource(Res.string.game_pawn_home_spoken, colourName, number)
        is PawnPosition.Track -> stepsToHome(position, board).let {
            pluralStringResource(Res.plurals.game_pawn_track_spoken, it, colourName, number, it)
        }

        is PawnPosition.HomeColumn -> stepsToHome(position, board).let {
            pluralStringResource(Res.plurals.game_pawn_home_column_spoken, it, colourName, number, it)
        }
    }
}
