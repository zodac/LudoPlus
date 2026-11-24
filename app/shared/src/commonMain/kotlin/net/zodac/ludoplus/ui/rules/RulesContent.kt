package net.zodac.ludoplus.ui.rules

import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.mode_chaos
import net.zodac.ludoplus.resources.mode_haunted_house
import net.zodac.ludoplus.resources.mode_portals
import net.zodac.ludoplus.resources.mode_quick
import net.zodac.ludoplus.resources.mode_survival
import net.zodac.ludoplus.resources.mode_two_dice
import net.zodac.ludoplus.resources.rules_board_1
import net.zodac.ludoplus.resources.rules_board_2
import net.zodac.ludoplus.resources.rules_board_3
import net.zodac.ludoplus.resources.rules_board_tab
import net.zodac.ludoplus.resources.rules_board_title
import net.zodac.ludoplus.resources.rules_capture_1
import net.zodac.ludoplus.resources.rules_capture_2
import net.zodac.ludoplus.resources.rules_capture_3
import net.zodac.ludoplus.resources.rules_capture_4
import net.zodac.ludoplus.resources.rules_capture_tab
import net.zodac.ludoplus.resources.rules_capture_title
import net.zodac.ludoplus.resources.rules_chaos_1
import net.zodac.ludoplus.resources.rules_chaos_2
import net.zodac.ludoplus.resources.rules_chaos_3
import net.zodac.ludoplus.resources.rules_group_gameplay
import net.zodac.ludoplus.resources.rules_group_house_rules
import net.zodac.ludoplus.resources.rules_group_modes
import net.zodac.ludoplus.resources.rules_haunted_house_1
import net.zodac.ludoplus.resources.rules_haunted_house_2
import net.zodac.ludoplus.resources.rules_house_captures_tab
import net.zodac.ludoplus.resources.rules_house_captures_title
import net.zodac.ludoplus.resources.rules_house_rolling_tab
import net.zodac.ludoplus.resources.rules_house_rolling_title
import net.zodac.ludoplus.resources.rules_house_rules_1
import net.zodac.ludoplus.resources.rules_house_rules_2
import net.zodac.ludoplus.resources.rules_house_rules_tab
import net.zodac.ludoplus.resources.rules_house_rules_title
import net.zodac.ludoplus.resources.rules_house_yard_tab
import net.zodac.ludoplus.resources.rules_house_yard_title
import net.zodac.ludoplus.resources.rules_how_to_play_1
import net.zodac.ludoplus.resources.rules_how_to_play_2
import net.zodac.ludoplus.resources.rules_how_to_play_tab
import net.zodac.ludoplus.resources.rules_how_to_play_title
import net.zodac.ludoplus.resources.rules_how_to_play_tutorial
import net.zodac.ludoplus.resources.rules_modes_records
import net.zodac.ludoplus.resources.rules_portals_1
import net.zodac.ludoplus.resources.rules_portals_2
import net.zodac.ludoplus.resources.rules_portals_3
import net.zodac.ludoplus.resources.rules_quick_1
import net.zodac.ludoplus.resources.rules_survival_1
import net.zodac.ludoplus.resources.rules_survival_2
import net.zodac.ludoplus.resources.rules_survival_3
import net.zodac.ludoplus.resources.rules_survival_4
import net.zodac.ludoplus.resources.rules_survival_5
import net.zodac.ludoplus.resources.rules_survival_records
import net.zodac.ludoplus.resources.rules_turn_1
import net.zodac.ludoplus.resources.rules_turn_2
import net.zodac.ludoplus.resources.rules_turn_3
import net.zodac.ludoplus.resources.rules_turn_4
import net.zodac.ludoplus.resources.rules_turn_tab
import net.zodac.ludoplus.resources.rules_turn_title
import net.zodac.ludoplus.resources.rules_two_dice_1
import net.zodac.ludoplus.resources.rules_two_dice_2
import net.zodac.ludoplus.resources.rules_two_dice_3
import net.zodac.ludoplus.resources.rules_winning_1
import net.zodac.ludoplus.resources.rules_winning_2
import net.zodac.ludoplus.resources.rules_winning_3
import net.zodac.ludoplus.resources.rules_winning_home_roll
import net.zodac.ludoplus.resources.rules_winning_tab
import net.zodac.ludoplus.resources.rules_winning_title
import org.jetbrains.compose.resources.StringResource

/**
 * One page of [RulesScreen]. [title] heads the page itself; [tabLabel] is the shorter name its tab carries. [blocks]
 * render in order. Text may use `parseInlineMarkup`'s markers: *italic* for the name of a setting, **bold** for a number.
 */
internal data class RulesPage(val title: StringResource, val tabLabel: StringResource, val blocks: List<RulesBlock>)

/** A group of [RulesPage]s - a tab of the top row, named [label], whose [pages] are the tabs of the row under it. */
internal class RulesGroup(val label: StringResource, val pages: List<RulesPage>)

internal sealed interface RulesBlock

/** A paragraph. */
internal data class RulesText(val text: StringResource) : RulesBlock

/** The button that starts the guided tutorial: see `RulesScreen`'s `onStartTutorial`. */
internal data object RulesTutorialStart : RulesBlock

/** A house rule, as its name in italics and what it changes. */
internal data class RulesHouseRule(val rule: HouseRule) : RulesBlock

/** A power-up, as its name in italics and what it does. */
internal data class RulesPowerUp(val powerUp: PowerUp) : RulesBlock

/** A diagram of part of the board ([RulesDiagram]). */
internal data class RulesDiagramBlock(val kind: RulesDiagramKind) : RulesBlock

private fun texts(vararg text: StringResource): List<RulesBlock> = text.map(::RulesText)

/** The classic rules (`.claude/IMPLEMENTATION.md`, "The classic rules"), a page an idea, each with its diagram. */
private val GAMEPLAY = RulesGroup(
    Res.string.rules_group_gameplay,
    listOf(
        RulesPage(
            Res.string.rules_how_to_play_title,
            Res.string.rules_how_to_play_tab,
            texts(Res.string.rules_how_to_play_1, Res.string.rules_how_to_play_2, Res.string.rules_how_to_play_tutorial) + RulesTutorialStart,
        ),
        RulesPage(
            Res.string.rules_board_title,
            Res.string.rules_board_tab,
            texts(Res.string.rules_board_1, Res.string.rules_board_2, Res.string.rules_board_3) + RulesDiagramBlock(RulesDiagramKind.BOARD),
        ),
        RulesPage(
            Res.string.rules_turn_title,
            Res.string.rules_turn_tab,
            texts(Res.string.rules_turn_1, Res.string.rules_turn_2, Res.string.rules_turn_3, Res.string.rules_turn_4) +
                RulesDiagramBlock(RulesDiagramKind.TURN),
        ),
        RulesPage(
            Res.string.rules_capture_title,
            Res.string.rules_capture_tab,
            texts(Res.string.rules_capture_1, Res.string.rules_capture_2, Res.string.rules_capture_3, Res.string.rules_capture_4) + RulesDiagramBlock(RulesDiagramKind.CAPTURE),
        ),
        RulesPage(
            Res.string.rules_winning_title,
            Res.string.rules_winning_tab,
            texts(Res.string.rules_winning_1, Res.string.rules_winning_home_roll, Res.string.rules_winning_2, Res.string.rules_winning_3) + RulesDiagramBlock(RulesDiagramKind.WINNING),
        ),
    ),
)

/** The house rules ([HouseRule]): what they are, then each one, a page for what they change. */
private val HOUSE_RULES = RulesGroup(
    Res.string.rules_group_house_rules,
    listOf(
        RulesPage(Res.string.rules_house_rules_title, Res.string.rules_house_rules_tab, texts(Res.string.rules_house_rules_1, Res.string.rules_house_rules_2)),
        RulesPage(
            Res.string.rules_house_rolling_title,
            Res.string.rules_house_rolling_tab,
            listOf(HouseRule.NO_CAPTURE_ROLL, HouseRule.NO_HOME_ROLL, HouseRule.ENDLESS_SIXES).map(::RulesHouseRule),
        ),
        RulesPage(
            Res.string.rules_house_yard_title,
            Res.string.rules_house_yard_tab,
            listOf(HouseRule.EXIT_ON_ONE, HouseRule.FREE_START, HouseRule.THREE_TRIES).map(::RulesHouseRule),
        ),
        RulesPage(
            Res.string.rules_house_captures_title,
            Res.string.rules_house_captures_tab,
            listOf(HouseRule.MUST_CAPTURE, HouseRule.GUARDED_STARTS, HouseRule.BLOCKADES).map(::RulesHouseRule),
        ),
    ),
)

/** The game modes beyond Classic: a page each. */
private val GAME_MODES = RulesGroup(
    Res.string.rules_group_modes,
    listOf(
        RulesPage(Res.string.mode_quick, Res.string.mode_quick, texts(Res.string.rules_quick_1, Res.string.rules_modes_records)),
        RulesPage(
            Res.string.mode_two_dice,
            Res.string.mode_two_dice,
            texts(Res.string.rules_two_dice_1, Res.string.rules_two_dice_2, Res.string.rules_two_dice_3, Res.string.rules_modes_records),
        ),
        RulesPage(
            Res.string.mode_portals,
            Res.string.mode_portals,
            texts(Res.string.rules_portals_1, Res.string.rules_portals_2, Res.string.rules_portals_3, Res.string.rules_modes_records),
        ),
        RulesPage(
            Res.string.mode_survival,
            Res.string.mode_survival,
            texts(Res.string.rules_survival_1, Res.string.rules_survival_2, Res.string.rules_survival_3, Res.string.rules_survival_4, Res.string.rules_survival_5, Res.string.rules_survival_records),
        ),
        RulesPage(
            Res.string.mode_chaos,
            Res.string.mode_chaos,
            texts(Res.string.rules_chaos_1) + PowerUp.entries.map(::RulesPowerUp) + texts(Res.string.rules_chaos_2, Res.string.rules_chaos_3, Res.string.rules_modes_records),
        ),
        RulesPage(
            Res.string.mode_haunted_house,
            Res.string.mode_haunted_house,
            texts(Res.string.rules_haunted_house_1, Res.string.rules_haunted_house_2, Res.string.rules_modes_records),
        ),
    ),
)

/** The groups of pages, in the top tab row's order. */
internal val RULES_GROUPS: List<RulesGroup> = listOf(GAMEPLAY, GAME_MODES, HOUSE_RULES)

/** Every page, group after group: the pager's pages. */
internal val RULES_PAGES: List<RulesPage> = RULES_GROUPS.flatMap { it.pages }

/** The index in [RULES_PAGES] of each group's first page. */
internal val GROUP_STARTS: List<Int> = RULES_GROUPS.runningFold(0) { start, group -> start + group.pages.size }.dropLast(1)

/** The group [page] (an index in [RULES_PAGES]) belongs to. */
internal fun groupOf(page: Int): Int = GROUP_STARTS.indexOfLast { it <= page }.coerceAtLeast(0)
