package net.zodac.ludo.model

import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.mode_classic
import net.zodac.ludo.resources.mode_classic_description
import org.jetbrains.compose.resources.StringResource

/**
 * A set of rules a game can be played under, chosen on the setup screen and carried for the life of the game.
 *
 * **Everything that can differ between modes lives here**, even where every mode so far agrees on it, so the engine,
 * the CPU, the achievements and the board all read the rules from one place. A new mode is a new entry here; nothing
 * else should have to learn it exists (`.claude/GAME_MODES.md`). The board's rules (track length, safe squares, and
 * the rest) join it in Phase 1 of `.claude/IMPLEMENTATION.md`.
 *
 * [id] is the stable storage key, so **never change one once released** - rename [displayName] instead.
 */
enum class GameMode(
    override val id: String,
    val displayName: StringResource,
    /** One line for the setup screen, saying what's different about this mode. */
    val description: StringResource,
    /** How many pawns each player races home. */
    val pawnsPerPlayer: Int,
) : StoredId {
    /** The classic rules, for four players (`.claude/IMPLEMENTATION.md`, "The classic rules"). */
    CLASSIC(
        id = "classic",
        displayName = Res.string.mode_classic,
        description = Res.string.mode_classic_description,
        pawnsPerPlayer = 4,
    ),
    ;

    companion object {
        /** The mode a new player's first game is set to. */
        val DEFAULT = CLASSIC

        /** How many players a game has. Fixed at four until 2-8 players come (Phase 8). */
        const val PLAYER_COUNT = 4
    }
}
