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
    /** Whether a move that captures gives the player another roll (one, however many pawns it captures). */
    val rollAgainOnCapture: Boolean = true,
    /** Whether a move that brings a pawn home gives the player another roll, unless it was their last. */
    val rollAgainOnHome: Boolean = true,
    /**
     * Rolling one of [rollAgainRolls] this many times in a row ends the turn: the last of them isn't moved. Null for a
     * run that never ends.
     */
    val rollAgainLimit: Int? = 3,
    /**
     * Whether the game ends as soon as one player has every pawn home (Settings' "Stop at first winner"). The others
     * are then placed by how far their pawns have come, and a tie by who plays sooner after the winner.
     */
    val stopAtFirstWinner: Boolean = false,
    /**
     * Each player's team, by player index, or null for a game where everyone plays alone (`.claude/IMPLEMENTATION.md`,
     * Phase 8). A team is placed when every one of its pawns is home, its players together; the rest play on for the
     * places after.
     */
    val teams: List<Int>? = null,
    /** In a team game, whether a pawn captures a partner's as it would an opponent's. Otherwise partners share squares. */
    val partnerCaptures: Boolean = false,
    /**
     * In a team game, whether a player with every pawn home rolls on, moving a partner's pawns, until their team is home.
     * Otherwise their turns pass them by.
     */
    val rollForPartners: Boolean = true,
    /** Whether any roll brings a pawn out of the yard while none of the player's pawns is on the board (track or column). */
    val freeStart: Boolean = false,
    /**
     * How many rolls a player gets for a move while none of their pawns is on the board (each in the yard or home): a roll
     * with no legal move, with tries left, rolls again in the same turn. 1 for a single roll.
     */
    val yardTries: Int = 1,
    /**
     * Whether two or more pawns of one player on a track square make a blockade: no other player's pawn may pass it, or
     * land on it (so none is captured). Its own player's pawns pass and join it.
     */
    val blockades: Boolean = false,
    /** Whether a roll with a move that captures allows only such moves. */
    val mustCapture: Boolean = false,
    /**
     * Whether a start square is safe only for pawns of its own seat: any other pawn landed on there is captured, by any
     * pawn, its own seat's leaving the yard included. Otherwise it's safe for every pawn.
     */
    val guardedStarts: Boolean = false,
    /** The house rules these rules were made with ([withHouseRules]): empty for a mode's own rules. */
    val houseRules: Set<HouseRule> = emptySet(),
) {
    /** Whether any house rule is on: such a game stays off the Leaderboard and out of Statistics, and earns no achievements. */
    val hasHouseRules: Boolean get() = houseRules.isNotEmpty()

    /**
     * Whether a pawn of [seat] on the absolute track [square] is safe from capture: a star square, or a start square
     * (with [guardedStarts], only its own seat's).
     */
    fun isSafeFor(seat: Int, square: Int): Boolean = when {
        !board.isSafe(square) -> false
        guardedStarts && square % board.seatSpacing == 0 -> square == board.startSquare(seat)
        else -> true
    }

    /** [player]'s team: their own, alone, in a game without teams. */
    fun teamOf(player: Int): Int = teams?.get(player) ?: TEAMLESS + player

    /** Whether [a] and [b] are on one team, [a] and [b] being different players. */
    fun arePartners(a: Int, b: Int): Boolean = a != b && teams != null && teams[a] == teams[b]

    /** These rules on the board for [players] players ([BoardGeometry.forPlayers]). */
    fun forPlayers(players: Int): GameRules = copy(board = BoardGeometry.forPlayers(players))

    init {
        require(pawnsPerPlayer > 0) { "A player needs a pawn" }
        require(dieFaces > 1) { "A die needs two faces" }
        require((yardExitRolls + rollAgainRolls).all { it in 1..dieFaces }) { "Every special roll must be on the die" }
        require(rollAgainLimit == null || rollAgainLimit > 0) { "A run of rolls again ends after one roll or more" }
        require(yardTries > 0) { "A player has a roll or more to leave the yard" }
        require(teams == null || teams.all { it >= 0 } && teams.toSet().size >= 2) { "A team game needs two teams or more" }
    }

    private companion object {
        /** Where a teamless game's one-player teams are numbered from, clear of any real team's number. */
        const val TEAMLESS = 1_000
    }
}
