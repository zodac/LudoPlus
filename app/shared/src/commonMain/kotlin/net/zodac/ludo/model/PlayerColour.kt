package net.zodac.ludo.model

import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.player_colour_blue
import net.zodac.ludo.resources.player_colour_green
import net.zodac.ludo.resources.player_colour_red
import net.zodac.ludo.resources.player_colour_yellow
import org.jetbrains.compose.resources.StringResource

/**
 * The colours a player can be on the setup screen, which are the colours of the board's yards and home columns. The
 * drawn value lives in `ui/theme` (`PlayerColour.color`); this stays Compose-free like the rest of the model.
 *
 * In the board's clockwise seat order, so each seat's default ([defaultFor]) is the classic board's colour there. More
 * come with 2-8 players (Phase 8 of `.claude/IMPLEMENTATION.md`).
 */
enum class PlayerColour(override val id: String, val label: StringResource) : StoredId {
    RED("RED", Res.string.player_colour_red),
    GREEN("GREEN", Res.string.player_colour_green),
    YELLOW("YELLOW", Res.string.player_colour_yellow),
    BLUE("BLUE", Res.string.player_colour_blue),
    ;

    companion object {
        /** The default colour of the player in [seat] (0 for player 1): distinct for every seat up to four. */
        fun defaultFor(seat: Int): PlayerColour = entries[(seat % entries.size + entries.size) % entries.size]
    }
}
