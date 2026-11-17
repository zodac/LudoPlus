package net.zodac.ludo.model

import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.player_colour_blue
import net.zodac.ludo.resources.player_colour_cyan
import net.zodac.ludo.resources.player_colour_green
import net.zodac.ludo.resources.player_colour_orange
import net.zodac.ludo.resources.player_colour_pink
import net.zodac.ludo.resources.player_colour_purple
import net.zodac.ludo.resources.player_colour_red
import net.zodac.ludo.resources.player_colour_yellow
import org.jetbrains.compose.resources.StringResource

/**
 * The colours a player can be on the setup screen, which are the colours of the board's yards and home columns. The
 * drawn value lives in `ui/theme` (`PlayerColour.color`); this stays Compose-free like the rest of the model.
 *
 * The classic board's four first, in its clockwise seat order, then the four more that 5-8 players need, so each
 * player's default ([defaultFor]) is distinct.
 */
enum class PlayerColour(override val id: String, val label: StringResource) : StoredId {
    RED("RED", Res.string.player_colour_red),
    GREEN("GREEN", Res.string.player_colour_green),
    YELLOW("YELLOW", Res.string.player_colour_yellow),
    BLUE("BLUE", Res.string.player_colour_blue),
    ORANGE("ORANGE", Res.string.player_colour_orange),
    CYAN("CYAN", Res.string.player_colour_cyan),
    PINK("PINK", Res.string.player_colour_pink),
    PURPLE("PURPLE", Res.string.player_colour_purple),
    ;

    companion object {
        /** The default colour of the player in [seat] (0 for player 1): distinct for every seat up to eight. */
        fun defaultFor(seat: Int): PlayerColour = entries[(seat % entries.size + entries.size) % entries.size]
    }
}
