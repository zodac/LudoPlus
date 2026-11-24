package net.zodac.ludoplus.model

import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.power_up_call_up
import net.zodac.ludoplus.resources.power_up_call_up_description
import net.zodac.ludoplus.resources.power_up_double
import net.zodac.ludoplus.resources.power_up_double_description
import net.zodac.ludoplus.resources.power_up_roll_again
import net.zodac.ludoplus.resources.power_up_roll_again_description
import net.zodac.ludoplus.resources.power_up_shield
import net.zodac.ludoplus.resources.power_up_shield_description
import net.zodac.ludoplus.resources.power_up_swap
import net.zodac.ludoplus.resources.power_up_swap_description
import org.jetbrains.compose.resources.StringResource

/**
 * What a power-up on the track does for the player whose move ends on it, at once, one a move ([GameRules.powerUps]).
 * [id] is the stable storage key, so **never change one once released** - rename [title] instead.
 */
enum class PowerUp(override val id: String, val title: StringResource, val description: StringResource) : StoredId {
    /** Their next [DOUBLED_ROLLS] rolls move a pawn twice as far, but for one that would take it past home. */
    DOUBLE("double", Res.string.power_up_double, Res.string.power_up_double_description),

    /** The pawn's next capture bounces off: the pawn that landed settles on the first square before it. */
    SHIELD("shield", Res.string.power_up_shield, Res.string.power_up_shield_description),

    /** They roll again. */
    ROLL_AGAIN("roll_again", Res.string.power_up_roll_again, Res.string.power_up_roll_again_description),

    /** One of their pawns comes out of the yard, if any is in it. */
    CALL_UP("call_up", Res.string.power_up_call_up, Res.string.power_up_call_up_description),

    /** The pawn swaps places with any other player's on the track that its player picks, or with none ([net.zodac.ludoplus.game.Move.swap]). */
    SWAP("swap", Res.string.power_up_swap, Res.string.power_up_swap_description),
    ;

    companion object {
        /** How many rolls [DOUBLE] lasts. */
        const val DOUBLED_ROLLS = 3

        /** How many rolls (anyone's) after one is taken the next comes. */
        const val RETURN_ROLLS = 8
    }
}
