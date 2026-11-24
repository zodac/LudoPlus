package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.rules.RulesScreen
import net.zodac.ludoplus.ui.rules.resetRulesPageMemory
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** The Rules screen's words: its title, and each page's tab, title, paragraphs, and diagram, word for word. */
class RulesTextTest : UiTest() {

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
    private val isImage = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image)

    /** A page: its tab, its title, its paragraphs, and what its diagram says (null for none). */
    private class Page(val tab: String, val title: String, val paragraphs: List<String>, val diagram: String?)

    private val pages = listOf(
        Page(
            "How to Play",
            "How to Play LudoPlus",
            listOf(
                "Race your four pawns a full lap of the board and into home, in the middle. The first player to bring all four pawns home wins.",
                "Players take turns clockwise, each rolling one die. The first player listed on New Game goes first.",
                "Want to see it played? The tutorial takes you through it in four short parts. Nothing in it counts: no records and no saved game.",
            ),
            diagram = null,
        ),
        Page(
            "The Board",
            "The Board",
            listOf(
                "Each player has four pawns, waiting in their own coloured yard.",
                "The track runs all the way round the board, and everyone shares it. Each colour joins it at its own start square.",
                "After a full lap, a pawn turns into its own home column and races to home, in the middle of the board.",
            ),
            "Diagram: Red's route. From its start square, a pawn goes a lap of the track clockwise, then up Red's home column to home in the middle.",
        ),
        Page(
            "Your Turn",
            "Your Turn",
            listOf(
                "Roll the die, then move one of your pawns that many squares.",
                "Only a 6 brings a pawn out of the yard, onto its start square.",
                "Roll a 6 and you roll again. So does capturing a pawn, or bringing one home. Roll three 6s in a row and your turn ends.",
                "If none of your pawns can move, your turn passes.",
            ),
            "Diagram: the die shows 6, so a red pawn comes out of its yard onto its start square.",
        ),
        Page(
            "Captures",
            "Captures",
            listOf(
                "Land on a rival's pawns to send them back to their yard.",
                "Start squares and star squares are safe: nobody can be captured there.",
                "Your own pawns can share a square, and any pawn can pass any other.",
                "Every capture earns you another roll.",
            ),
            "Diagram: Red rolls 4 and lands on Green's pawn, sending it back to Green's yard. Blue's pawn is safe on a star square.",
        ),
        Page(
            "Winning",
            "Winning",
            listOf(
                "A pawn needs the exact roll to reach home.",
                "Bringing a pawn home earns you another roll, unless it was your last.",
                "The first player to bring all four pawns home wins, and everyone else plays on for the places.",
                "With Stop at first winner on in Settings, the game ends as soon as one player has every pawn home. Everyone else is placed by how far their pawns have come.",
            ),
            "Diagram: a red pawn 3 squares from home needs exactly 3 to get there. Another red pawn is already home.",
        ),
    )

    /** The House Rules group's pages: what they are, then every house rule, its name in italics before what it changes. */
    private val houseRulePages = listOf(
        Page(
            "About",
            "House Rules",
            listOf(
                "House rules are common changes to the classic rules. Switch them on in House Rules on New Game, in any mix.",
                "A game with any house rule on doesn't count towards the Leaderboard or Statistics, and only earns the house rules' own achievements.",
            ),
            diagram = null,
        ),
        Page(
            "Rolling",
            "Rolling Again",
            listOf(
                "No Capture Roll: Capturing a pawn doesn't earn another roll.",
                "No Home Roll: Bringing a pawn home doesn't earn another roll.",
                "Endless 6s: Three 6s in a row don't end your turn: keep rolling while the 6s come.",
            ),
            diagram = null,
        ),
        Page(
            "The Yard",
            "Leaving the Yard",
            listOf(
                "Out on a 1: A 1 brings a pawn out of the yard, as well as a 6.",
                "Free Start: While none of your pawns is on the board, any roll brings one out of the yard.",
                "Three Tries: While none of your pawns is on the board, roll up to three times to bring one out.",
            ),
            diagram = null,
        ),
        Page(
            "Captures",
            "Captures and Blockades",
            listOf(
                "Must Capture: If a move can capture, you have to make a capture.",
                "Guarded Starts: A start square is safe only for its own colour. Other pawns there can be captured.",
                "Blockades: Two of your pawns on one square make a blockade: no other player's pawn can pass it or land on it.",
            ),
            diagram = null,
        ),
    )

    /** The Game Modes group's pages: a mode each beyond Classic. */
    private val modePages = listOf(
        Page(
            "Quick",
            "Quick",
            listOf(
                "Quick plays the classic rules with two pawns each instead of four, for a shorter race. Pick it under Game Mode on New Game.",
                "Each mode has its own Leaderboard, its own page in Statistics, and achievements of its own.",
            ),
            diagram = null,
        ),
        Page(
            "Two Dice",
            "Two Dice",
            listOf(
                "Two Dice rolls two dice at once. Move a pawn by each die, one after the other, in either order: the same pawn twice, or two pawns. Tap a die to pick the one your next move uses.",
                "A 6 on either die brings a pawn out of the yard, and the other die can move it on. If a die has no move, it's lost.",
                "Roll a double and you roll again once both dice are moved, but three doubles in a row end your turn. A capture, or a pawn brought home, still earns another roll.",
                "Each mode has its own Leaderboard, its own page in Statistics, and achievements of its own.",
            ),
            diagram = null,
        ),
        Page(
            "Portals",
            "Portals",
            listOf(
                "Portals come in pairs, told by their colour and symbol. If your move ends on one, your pawn comes out at the other, whether that's further on or further back. Passing over a portal does nothing.",
                "Your pawn only captures where it comes out. While it waits on a portal, rivals who land there go through without capturing it, but one coming out there captures it.",
                "Short portals are open to everyone. Long portals, with a split ring, are shut to the colour on their lock, whose start square they would take a pawn past.",
                "Each mode has its own Leaderboard, its own page in Statistics, and achievements of its own.",
            ),
            diagram = null,
        ),
        Page(
            "Survival",
            "Survival",
            listOf(
                "In Survival there's no home to reach. Each player's six pawns start out on their start square and go round the track until one player is left.",
                "A captured pawn is out of the game. Lose all six and you're out: the last player with a pawn left wins, and everyone else is placed by how long they lasted.",
                "Start squares and star squares are safe at first. In Overtime the start squares stop being safe, and in Sudden Death the star squares do too. The line above the board counts the rounds, and with more players it takes longer.",
                "On a square that isn't safe, all your pawns there are captured together, so don't leave them stacked on your start square when Overtime comes.",
                "Ten turns after that comes the Cataclysm: after every five turns, up to three squares with no pawn on them vanish, and pawns step over the gaps. The track shrinks until there are only seven squares left, so nobody can keep away for ever.",
                "The Leaderboard ranks Survival wins by the most pawns left.",
            ),
            diagram = null,
        ),
        Page(
            "Chaos",
            "Chaos",
            listOf(
                "In Chaos there are power-ups on the track, one on each player's stretch of it. End a move on one to use it at once:",
                "Double: Your next 3 rolls move a pawn twice as far. A roll that would take it past home counts once.",
                "Shield: The next pawn to land on yours bounces off to the square before it, and the shield breaks.",
                "Teleport: Your pawn jumps 1 to 6 squares further on, capturing where it lands.",
                "Roll Again: You get another roll.",
                "Call Up: One of your pawns comes out of the yard.",
                "Swap: Your pawn swaps places with the nearest rival ahead that's alone on a square, unless it's safe or shielded, or either pawn would pass its own start.",
                "Only one power-up works in a move: a pawn moved onto another leaves it there. A power-up that's taken comes back somewhere else a few rolls later.",
                "Bouncing off a shield earns no roll unless your pawn captures where it settles. A pawn brought out of the yard can't bounce, so a shield doesn't stop it.",
                "Each mode has its own Leaderboard, its own page in Statistics, and achievements of its own.",
            ),
            diagram = null,
        ),
        Page(
            "Haunted House",
            "Haunted House",
            listOf(
                "In Haunted House, ghosts haunt the track, one for every two players. After every second turn one of them drifts up to 6 squares, forwards or back, and you won't know which one or how far until it does.",
                "A ghost sends every pawn on the square it lands on back to its yard, whoever's they are, unless the square is safe. Landing on a ghost yourself does no harm: it only catches pawns when it moves.",
                "Each mode has its own Leaderboard, its own page in Statistics, and achievements of its own.",
            ),
            diagram = null,
        ),
    )

    /** Each group's tab, and its pages. */
    private val groups = listOf("Gameplay" to pages, "Game Modes" to modePages, "House Rules" to houseRulePages)

    @Test
    fun `How to Play's button starts the tutorial`() = uiTest(TestScreen(360, 800)) {
        resetRulesPageMemory()
        var started = 0
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { RulesScreen(onBack = {}, onStartTutorial = { started++ }) }
            }
        }
        compose.onNodeWithText("Play the Tutorial").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertEquals(1, started)
    }

    @Test
    fun `the Rules screen's words - its title, and every group's tab - and every page's tab, title, paragraphs, and diagram`() = uiTest(TestScreen(360, 800)) {
        resetRulesPageMemory()
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { RulesScreen(onBack = {}) }
            }
        }
        compose.onAllNodesWithText("Rules").assertCountEquals(1)
        for ((group, groupPages) in groups) {
            compose.onNode(isTab and hasText(group)).performScrollTo().performClick()
            compose.waitForIdle()
            // A tab a group, then a tab a page of the group showing.
            compose.onAllNodes(isTab).assertCountEquals(groups.size + groupPages.size)
            for (page in groupPages) {
                compose.onNode(isTab and hasText(page.tab)).performScrollTo().performClick()
                compose.waitForIdle()
                // A page's title heads it, in view as it opens. (Scrolling to it never settled on a group's last page, the
                // pager still: the test's scroll, not the screen.)
                compose.onNode(isHeading and hasText(page.title)).assertIsDisplayed()
                // Nor is a paragraph already in view scrolled to, for the same reason.
                page.paragraphs.forEach { paragraph -> compose.onNodeWithText(paragraph).let { if (!it.isDisplayed()) it.performScrollTo() } }
                // The diagram is one image, described in words.
                page.diagram?.let { compose.onNodeWithContentDescription(it).performScrollTo().assert(isImage) }
            }
        }
        // The diagrams' dice are part of their pictures, not dice of their own.
        compose.onAllNodesWithContentDescription("Die showing", substring = true).assertCountEquals(0)
        // Every diagram described is one of the pages' own.
        compose.onAllNodes(isImage).fetchSemanticsNodes().forEach { node ->
            val description = node.config[SemanticsProperties.ContentDescription].single()
            check(pages.any { it.diagram == description }) { "an image no page describes: $description" }
        }
    }
}
