package net.zodac.ludo.model

import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.team_mode_off
import net.zodac.ludo.resources.team_mode_pairs
import net.zodac.ludo.resources.team_mode_two_teams
import org.jetbrains.compose.resources.StringResource

/**
 * How a game's players make teams, chosen on New Game (Phase 8 of `.claude/IMPLEMENTATION.md`). Teams need an even
 * count of four or more; players sit in turn order, so a team's partners are spread round the board.
 *
 * [id] is the stable storage key, so **never change one once released**.
 */
enum class TeamMode(override val id: String, val label: StringResource) : StoredId {
    /** Everyone plays alone. */
    OFF("off", Res.string.team_mode_off),

    /** Two teams, seats alternating: 2v2, 3v3, or 4v4, every turn passing to an opponent. */
    TWO_TEAMS("two_teams", Res.string.team_mode_two_teams),

    /** Teams of two, partners sitting opposite: two teams of 4 players, three of 6, and four of 8. */
    PAIRS("pairs", Res.string.team_mode_pairs),
    ;

    /** Each of [players] players' team by player index, or null with no teams (off, or a count that can't have them). */
    fun teamsFor(players: Int): List<Int>? = when {
        this == OFF || !allowsTeams(players) -> null
        this == TWO_TEAMS -> List(players) { it % 2 }
        else -> List(players) { it % (players / 2) }
    }

    companion object {
        /** Whether a game of [players] can have teams: an even count of four or more. */
        fun allowsTeams(players: Int): Boolean = players >= 4 && players % 2 == 0
    }
}
