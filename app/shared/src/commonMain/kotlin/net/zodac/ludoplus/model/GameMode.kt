package net.zodac.ludoplus.model

import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.mode_chaos
import net.zodac.ludoplus.resources.mode_chaos_description
import net.zodac.ludoplus.resources.mode_classic
import net.zodac.ludoplus.resources.mode_classic_description
import net.zodac.ludoplus.resources.mode_colour_rush
import net.zodac.ludoplus.resources.mode_colour_rush_description
import net.zodac.ludoplus.resources.mode_haunted_house
import net.zodac.ludoplus.resources.mode_haunted_house_description
import net.zodac.ludoplus.resources.mode_portals
import net.zodac.ludoplus.resources.mode_portals_description
import net.zodac.ludoplus.resources.mode_survival
import net.zodac.ludoplus.resources.mode_survival_description
import net.zodac.ludoplus.resources.mode_two_dice
import net.zodac.ludoplus.resources.mode_two_dice_description
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
    /**
     * Its online leaderboards' key (Play Games), one board for each player count ([onlineLeaderboardKey]). Permanent once
     * released, as the boards are: a rule change that makes old wins unfair needs a new key (`.claude/PLAY_GAMES.md`).
     * They rank its wins by its [ranking].
     */
    val onlineLeaderboardKey: String,
    /** What its Leaderboard, and its online boards, rank wins by. */
    val ranking: LeaderboardRanking = LeaderboardRanking.FEWEST_TURNS,
) : StoredId {
    /** The classic rules, for four players (`.claude/IMPLEMENTATION.md`, "The classic rules"). */
    CLASSIC(
        id = "classic",
        displayName = Res.string.mode_classic,
        description = Res.string.mode_classic_description,
        rules = GameRules(),
        onlineLeaderboardKey = "classic",
    ),

    /** The classic moves, for the squares of the track: each is the first to land there's, and the most squares at the end wins. */
    COLOUR_RUSH(
        id = "colour_rush",
        displayName = Res.string.mode_colour_rush,
        description = Res.string.mode_colour_rush_description,
        rules = GameRules(claims = true),
        onlineLeaderboardKey = "colour_rush",
        ranking = LeaderboardRanking.MOST_SQUARES,
    ),

    /**
     * No home: pawns lap the track from their start squares, a captured pawn is out of the game, and the last player with
     * one wins. Late in the game sudden death makes the start squares unsafe, then the stars too, then takes the track away.
     */
    SURVIVAL(
        id = "survival",
        displayName = Res.string.mode_survival,
        description = Res.string.mode_survival_description,
        rules = GameRules(board = BoardGeometry.CLASSIC.copy(endless = true), pawnsPerPlayer = 6, startsOut = true, lastStanding = true, suddenDeath = true),
        onlineLeaderboardKey = "survival",
        ranking = LeaderboardRanking.MOST_SQUARES_MOVED,
    ),

    /** The classic rules, with power-ups on the track ([PowerUp]). */
    CHAOS(
        id = "chaos",
        displayName = Res.string.mode_chaos,
        description = Res.string.mode_chaos_description,
        rules = GameRules(powerUps = true),
        onlineLeaderboardKey = "chaos",
    ),

    /** The classic rules, with ghosts on the track that send home the pawns they land on. */
    HAUNTED_HOUSE(
        id = "haunted_house",
        displayName = Res.string.mode_haunted_house,
        description = Res.string.mode_haunted_house_description,
        rules = GameRules(ghosts = true),
        onlineLeaderboardKey = "haunted_house",
    ),
    /** The classic rules, with pairs of portals on the track ([BoardGeometry.portals]). */
    PORTALS(
        id = "portals",
        displayName = Res.string.mode_portals,
        description = Res.string.mode_portals_description,
        rules = GameRules(portals = true),
        onlineLeaderboardKey = "portals",
    ),

    /**
     * Two dice, each moved on its own, one after the other, by any pawn: a 6 on either brings a pawn out with that die,
     * and doubles roll again (three in a row end the turn), not 6s.
     */
    TWO_DICE(
        id = "two_dice",
        displayName = Res.string.mode_two_dice,
        description = Res.string.mode_two_dice_description,
        rules = GameRules(dice = 2, rollAgainRolls = emptySet(), rollAgainOnDoubles = true),
        onlineLeaderboardKey = "two_dice",
    ),

    ;

    /** Its online leaderboard for games of [playerCount]: a bigger board's lap is longer, so each count has its own. */
    fun onlineLeaderboardKey(playerCount: Int): String = "${onlineLeaderboardKey}_${playerCount}p"

    companion object {
        /** The mode a new player's first game is set to. */
        val DEFAULT = CLASSIC
    }
}

/** What a mode's Leaderboard ranks its wins by, most captures settling a tie; its online boards rank by the number alone. */
enum class LeaderboardRanking(
    /** Whether a bigger number ranks higher. */
    val largerIsBetter: Boolean,
) {
    /** The fewest turns to win. */
    FEWEST_TURNS(largerIsBetter = false),

    /** The most squares the winner's pawns walked in the game: the longest survival. */
    MOST_SQUARES_MOVED(largerIsBetter = true),

    /** The most squares the winner held at the end. */
    MOST_SQUARES(largerIsBetter = true),
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
    /** How many faces each die has, numbered 1 up to this. */
    val dieFaces: Int = 6,
    /** How many dice a roll rolls: 1, or 2, each moved on its own, one after the other, by any pawn. */
    val dice: Int = 1,
    /** With two dice, whether doubles roll again (counted against [rollAgainLimit]). */
    val rollAgainOnDoubles: Boolean = false,
    /** The rolls that bring a pawn out of the yard onto its start square. */
    val yardExitRolls: Set<Int> = setOf(6),
    /** The rolls of one die that give the player another roll after their move. A roll with no legal move gives none. */
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
     * Once the game's first pawn has come out of a yard, how many turns each other player starts before, if they have never
     * had a pawn out of theirs, one comes out free onto its start square as their next turn starts, before they roll
     * ([net.zodac.ludoplus.game.LudoEngine.pityMoves]). Null for never.
     */
    val pityRelease: Int? = PITY_RELEASE_ROLLS,
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
    /** Whether the board's portals ([BoardGeometry.portals]) are open: a move that ends on one comes out at its pair. */
    val portals: Boolean = false,
    /** Whether there are power-ups on the track ([PowerUp]): at least one of each kind at any time, and a new one of any kind every few turns. */
    val powerUps: Boolean = false,
    /** Whether ghosts haunt the track, one for every two players, sending home the pawns they land on. */
    val ghosts: Boolean = false,
    /** Whether every pawn starts the game on its start square, rather than in its yard. */
    val startsOut: Boolean = false,
    /**
     * Whether a captured pawn is out of the game, and the last player (or team) with a pawn left wins: the others are
     * placed by how late they lost their last. Only on an endless board ([BoardGeometry.endless]), from the start squares.
     */
    val lastStanding: Boolean = false,
    /** Whether sudden death comes, round by round, making the start squares unsafe, then the stars, and then taking squares from the track ([suddenDeathRound]). */
    val suddenDeath: Boolean = false,
    /**
     * Whether the track's squares are claimed, all but the start squares: each is the first player's whose pawn
     * lands on it, and another's only by a capture there. The game ends once every one is claimed, or a player (or team) has every pawn home, and the players
     * (or teams) are placed by how many they have.
     */
    val claims: Boolean = false,
    /** Whether [pawnsPerPlayer] isn't the mode's own ([withPawns]). */
    val pawnsChanged: Boolean = false,
    /** The house rules these rules were made with ([withHouseRules]): empty for a mode's own rules. */
    val houseRules: Set<HouseRule> = emptySet(),
) {
    /** Whether a game of these rules draws chances of its own, besides the dice: power-ups', ghosts', or sudden death's. */
    val hasChances: Boolean get() = powerUps || ghosts || suddenDeath

    /** Whether any house rule is on, a number of pawns among them: such a game stays off the Leaderboard and out of Statistics, and earns only the house rules' own achievements. */
    val hasHouseRules: Boolean get() = houseRules.isNotEmpty() || pawnsChanged

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

    /** These rules with [pawns] each, a house rule; as they are with the mode's own number, or null. */
    fun withPawns(pawns: Int?): GameRules = if (pawns == null || pawns == pawnsPerPlayer) this else copy(pawnsPerPlayer = pawns, pawnsChanged = true)

    /** These rules on the board for [players] players ([BoardGeometry.forPlayers]). */
    fun forPlayers(players: Int): GameRules = copy(board = BoardGeometry.forPlayers(players).copy(endless = board.endless))

    /**
     * How many rounds (a turn for every player still in) are played before sudden death's [stage] comes, in a game of
     * [players]: 1 makes the start squares unsafe, and 2 the star squares too. Later the more players there are.
     */
    fun suddenDeathRound(stage: Int, players: Int): Int {
        val first = SUDDEN_DEATH_ROUNDS + SUDDEN_DEATH_ROUNDS_A_PLAYER * players
        return if (stage == 1) first else first * 3 / 2
    }

    init {
        require(pawnsPerPlayer > 0) { "A player needs a pawn" }
        require(dieFaces > 1) { "A die needs two faces" }
        require(dice in 1..2) { "A roll is one die or two" }
        require((yardExitRolls + rollAgainRolls).all { it in 1..dieFaces }) { "Every special roll must be on the die" }
        require(rollAgainLimit == null || rollAgainLimit > 0) { "A run of rolls again ends after one roll or more" }
        require(yardTries > 0) { "A player has a roll or more to leave the yard" }
        require(pityRelease == null || pityRelease > 0) { "A pawn comes out free after a turn or more" }
        require(lastStanding == board.endless && (!lastStanding || startsOut)) { "Only an endless board has a last player standing, and no yard to leave" }
        require(!claims || !lastStanding) { "A game is won by squares or by lasting, not both" }
        require(teams == null || teams.all { it >= 0 } && teams.toSet().size >= 2) { "A team game needs two teams or more" }
    }

    companion object {
        /** How many turns after the game's first pawn out a player with none out yet gets one out free ([pityRelease]). */
        const val PITY_RELEASE_ROLLS = 5

        /** How many pawns each a game may be played with, by a house rule ([withPawns]). */
        val PAWN_CHOICES = 1..6

        /** The stage of sudden death with nowhere safe, the last that comes by rounds ([suddenDeathRound]). */
        const val NOWHERE_SAFE_STAGE = 2

        /** Where a teamless game's one-player teams are numbered from, clear of any real team's number. */
        private const val TEAMLESS = 1_000

        /** When sudden death's first stage comes ([suddenDeathRound]). */
        private const val SUDDEN_DEATH_ROUNDS = 30
        private const val SUDDEN_DEATH_ROUNDS_A_PLAYER = 5
    }
}
