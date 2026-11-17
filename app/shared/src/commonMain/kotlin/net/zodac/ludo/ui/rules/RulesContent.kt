package net.zodac.ludo.ui.rules

import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.rules_board_1
import net.zodac.ludo.resources.rules_board_2
import net.zodac.ludo.resources.rules_board_3
import net.zodac.ludo.resources.rules_board_tab
import net.zodac.ludo.resources.rules_board_title
import net.zodac.ludo.resources.rules_capture_1
import net.zodac.ludo.resources.rules_capture_2
import net.zodac.ludo.resources.rules_capture_3
import net.zodac.ludo.resources.rules_capture_4
import net.zodac.ludo.resources.rules_capture_tab
import net.zodac.ludo.resources.rules_capture_title
import net.zodac.ludo.resources.rules_group_gameplay
import net.zodac.ludo.resources.rules_how_to_play_1
import net.zodac.ludo.resources.rules_how_to_play_2
import net.zodac.ludo.resources.rules_how_to_play_tab
import net.zodac.ludo.resources.rules_how_to_play_title
import net.zodac.ludo.resources.rules_turn_1
import net.zodac.ludo.resources.rules_turn_2
import net.zodac.ludo.resources.rules_turn_3
import net.zodac.ludo.resources.rules_turn_4
import net.zodac.ludo.resources.rules_turn_tab
import net.zodac.ludo.resources.rules_turn_title
import net.zodac.ludo.resources.rules_winning_1
import net.zodac.ludo.resources.rules_winning_2
import net.zodac.ludo.resources.rules_winning_3
import net.zodac.ludo.resources.rules_winning_home_roll
import net.zodac.ludo.resources.rules_winning_tab
import net.zodac.ludo.resources.rules_winning_title
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

/** A diagram of part of the board ([RulesDiagram]). */
internal data class RulesDiagramBlock(val kind: RulesDiagramKind) : RulesBlock

private fun texts(vararg text: StringResource): List<RulesBlock> = text.map(::RulesText)

/** The classic rules (`.claude/IMPLEMENTATION.md`, "The classic rules"), a page an idea, each with its diagram. */
private val GAMEPLAY = RulesGroup(
    Res.string.rules_group_gameplay,
    listOf(
        RulesPage(Res.string.rules_how_to_play_title, Res.string.rules_how_to_play_tab, texts(Res.string.rules_how_to_play_1, Res.string.rules_how_to_play_2)),
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

/** The groups of pages, in the top tab row's order. The game modes and the modifiers join the gameplay in Phase 9. */
internal val RULES_GROUPS: List<RulesGroup> = listOf(GAMEPLAY)

/** Every page, group after group: the pager's pages. */
internal val RULES_PAGES: List<RulesPage> = RULES_GROUPS.flatMap { it.pages }

/** The index in [RULES_PAGES] of each group's first page. */
internal val GROUP_STARTS: List<Int> = RULES_GROUPS.runningFold(0) { start, group -> start + group.pages.size }.dropLast(1)

/** The group [page] (an index in [RULES_PAGES]) belongs to. */
internal fun groupOf(page: Int): Int = GROUP_STARTS.indexOfLast { it <= page }.coerceAtLeast(0)
