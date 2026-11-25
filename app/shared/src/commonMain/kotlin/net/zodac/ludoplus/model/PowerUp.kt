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
 * What a power-up on the track does for the player whose move ends on it, at once (but for a banked [CALL_UP]), one a move ([GameRules.powerUps]).
 * [id] is the stable storage key, so **never change one once released** - rename [title] instead.
 */
enum class PowerUp(override val id: String, val title: StringResource, val description: StringResource) : StoredId {
    /** Their next [DOUBLED_ROLLS] rolls move a pawn twice as far, but for one that would take it past home. */
    DOUBLE("double", Res.string.power_up_double, Res.string.power_up_double_description),

    /** The pawn's next capture bounces off: the pawn that landed settles on the first square before it. */
    SHIELD("shield", Res.string.power_up_shield, Res.string.power_up_shield_description),

    /** They roll again. */
    ROLL_AGAIN("roll_again", Res.string.power_up_roll_again, Res.string.power_up_roll_again_description),

    /** One of their pawns comes out of the yard; with none in it, it's banked, for the next of them captured to come straight back out. */
    CALL_UP("call_up", Res.string.power_up_call_up, Res.string.power_up_call_up_description),

    /** The pawn swaps places with any other player's on the track that its player picks, or with none ([net.zodac.ludoplus.game.Move.swap]). */
    SWAP("swap", Res.string.power_up_swap, Res.string.power_up_swap_description),
    ;

    companion object {
        /** How many rolls [DOUBLE] lasts. */
        const val DOUBLED_ROLLS = 3

        /** The most turns (anyone's) between one new power-up and the next: a random number, from 1. */
        const val MOST_TURNS = 5

        /**
         * The most power-ups on the track at once in a game of [players]: a new one due with as many there doesn't come. Two
         * for each player, but always room for one of each kind and [LEAST_RANDOM] of any.
         */
        fun most(players: Int): Int = maxOf(MOST_PER_PLAYER * players, entries.size + LEAST_RANDOM)

        private const val MOST_PER_PLAYER = 2
        private const val LEAST_RANDOM = 2
    }
}
