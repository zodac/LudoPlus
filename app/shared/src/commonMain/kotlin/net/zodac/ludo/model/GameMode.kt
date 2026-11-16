package net.zodac.ludo.model

import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.mode_classic
import net.zodac.ludo.resources.mode_classic_description
import org.jetbrains.compose.resources.StringResource

/**
 * A set of rules a game can be played under, chosen on the setup screen and carried for the life of the game.
 *
 * **Everything that can differ between modes lives in its [rules]**, even where every mode so far agrees on it, so the
 * engine, the CPU, the achievements and the board all read the rules from one place. A new mode is a new entry here;
 * nothing else should have to learn it exists (`.claude/GAME_MODES.md`).
 *
 * [id] is the stable storage key, so **never change one once released** - rename [displayName] instead.
 */
enum class GameMode(
    override val id: String,
    val displayName: StringResource,
    /** One line for the setup screen, saying what's different about this mode. */
    val description: StringResource,
    val rules: GameRules,
) : StoredId {
    /** The classic rules, for four players (`.claude/IMPLEMENTATION.md`, "The classic rules"). */
    CLASSIC(
        id = "classic",
        displayName = Res.string.mode_classic,
        description = Res.string.mode_classic_description,
        rules = GameRules(),
    ),
    ;

    companion object {
        /** The mode a new player's first game is set to. */
        val DEFAULT = CLASSIC

        /** How many players a game has. Fixed at four until 2-8 players come (Phase 8). */
        const val PLAYER_COUNT = 4
    }
}

/**
 * The rules of play a game follows: its [GameMode]'s, which house-rule modifiers (Phase 9 of
 * `.claude/IMPLEMENTATION.md`) may change. Every default is the classic rules.
 */
data class GameRules(
    /** The board's shape: track length, start squares, star squares, and home columns. */
    val board: BoardGeometry = BoardGeometry.CLASSIC,
    /** How many pawns each player races home. */
    val pawnsPerPlayer: Int = 4,
    /** How many faces the die has, numbered 1 up to this. */
    val dieFaces: Int = 6,
    /** The rolls that bring a pawn out of the yard onto its start square. */
    val yardExitRolls: Set<Int> = setOf(6),
    /** The rolls that give the player another roll after their move. A roll with no legal move gives none. */
    val rollAgainRolls: Set<Int> = setOf(6),
    /**
     * Rolling one of [rollAgainRolls] this many times in a row ends the turn: the last of them isn't moved. Null for a
     * run that never ends.
     */
    val rollAgainLimit: Int? = 3,
) {
    init {
        require(pawnsPerPlayer > 0) { "A player needs a pawn" }
        require(dieFaces > 1) { "A die needs two faces" }
        require((yardExitRolls + rollAgainRolls).all { it in 1..dieFaces }) { "Every special roll must be on the die" }
        require(rollAgainLimit == null || rollAgainLimit > 0) { "A run of rolls again ends after one roll or more" }
    }
}
