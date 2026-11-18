package net.zodac.ludoplus.model

import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.house_rule_blockades
import net.zodac.ludoplus.resources.house_rule_blockades_description
import net.zodac.ludoplus.resources.house_rule_endless_sixes
import net.zodac.ludoplus.resources.house_rule_endless_sixes_description
import net.zodac.ludoplus.resources.house_rule_exit_on_one
import net.zodac.ludoplus.resources.house_rule_exit_on_one_description
import net.zodac.ludoplus.resources.house_rule_free_start
import net.zodac.ludoplus.resources.house_rule_free_start_description
import net.zodac.ludoplus.resources.house_rule_guarded_starts
import net.zodac.ludoplus.resources.house_rule_guarded_starts_description
import net.zodac.ludoplus.resources.house_rule_must_capture
import net.zodac.ludoplus.resources.house_rule_must_capture_description
import net.zodac.ludoplus.resources.house_rule_no_capture_roll
import net.zodac.ludoplus.resources.house_rule_no_capture_roll_description
import net.zodac.ludoplus.resources.house_rule_no_home_roll
import net.zodac.ludoplus.resources.house_rule_no_home_roll_description
import net.zodac.ludoplus.resources.house_rule_three_tries
import net.zodac.ludoplus.resources.house_rule_three_tries_description
import org.jetbrains.compose.resources.StringResource

/**
 * A house rule: a modifier, switched on at New Game, that changes one of the [GameRules] (Phase 9 of
 * `.claude/IMPLEMENTATION.md`). Any mix may be on. A game with any on stays off the Leaderboard and out of Statistics,
 * and earns no achievements (`.claude/DESIGN.md`, "Modifiers").
 *
 * Each is data: [applyTo] changes the rules, and the engine reads only the rules, never which house rules are on.
 * [id] is the stable storage key, so **never change one once released** - rename [title] instead.
 */
enum class HouseRule(
    override val id: String,
    val title: StringResource,
    /** One line for New Game's picker and the Rules page, saying what it changes. */
    val description: StringResource,
    private val change: (GameRules) -> GameRules,
) : StoredId {
    /** A capture earns no roll ([GameRules.rollAgainOnCapture]). */
    NO_CAPTURE_ROLL("no_capture_roll", Res.string.house_rule_no_capture_roll, Res.string.house_rule_no_capture_roll_description, { it.copy(rollAgainOnCapture = false) }),

    /** A pawn brought home earns no roll ([GameRules.rollAgainOnHome]). */
    NO_HOME_ROLL("no_home_roll", Res.string.house_rule_no_home_roll, Res.string.house_rule_no_home_roll_description, { it.copy(rollAgainOnHome = false) }),

    /** A run of 6s never ends the turn ([GameRules.rollAgainLimit]). */
    ENDLESS_SIXES("endless_sixes", Res.string.house_rule_endless_sixes, Res.string.house_rule_endless_sixes_description, { it.copy(rollAgainLimit = null) }),

    /** A 1 leaves the yard as well as a 6 ([GameRules.yardExitRolls]). */
    EXIT_ON_ONE("exit_on_one", Res.string.house_rule_exit_on_one, Res.string.house_rule_exit_on_one_description, { it.copy(yardExitRolls = it.yardExitRolls + 1) }),

    /** Any roll leaves the yard while a player has no pawn on the board ([GameRules.freeStart]). */
    FREE_START("free_start", Res.string.house_rule_free_start, Res.string.house_rule_free_start_description, { it.copy(freeStart = true) }),

    /** Three rolls to leave the yard while a player has no pawn on the board ([GameRules.yardTries]). */
    THREE_TRIES("three_tries", Res.string.house_rule_three_tries, Res.string.house_rule_three_tries_description, { it.copy(yardTries = 3) }),

    /** Two pawns of one player on a track square block every other player's ([GameRules.blockades]). */
    BLOCKADES("blockades", Res.string.house_rule_blockades, Res.string.house_rule_blockades_description, { it.copy(blockades = true) }),

    /** A move that captures must be taken over one that doesn't ([GameRules.mustCapture]). */
    MUST_CAPTURE("must_capture", Res.string.house_rule_must_capture, Res.string.house_rule_must_capture_description, { it.copy(mustCapture = true) }),

    /** A start square is safe only for its own seat's pawns ([GameRules.guardedStarts]). */
    GUARDED_STARTS("guarded_starts", Res.string.house_rule_guarded_starts, Res.string.house_rule_guarded_starts_description, { it.copy(guardedStarts = true) }),
    ;

    /** [rules] with this house rule on. */
    fun applyTo(rules: GameRules): GameRules = change(rules).copy(houseRules = rules.houseRules + this)
}

/** These rules with every one of [houseRules] on, in [HouseRule]'s order. */
fun GameRules.withHouseRules(houseRules: Set<HouseRule>): GameRules = HouseRule.entries.filter { it in houseRules }.fold(this) { rules, rule -> rule.applyTo(rules) }
