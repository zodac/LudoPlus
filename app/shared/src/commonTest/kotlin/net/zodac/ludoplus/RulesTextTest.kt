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

    /** A page: its tab, its title, its paragraphs, and what its diagram or board says (null for none). */
    private class Page(val tab: String, val title: String, val paragraphs: List<String>, val diagram: String?)

    private val pages = listOf(
        Page(
            "How to Play",
            "How to Play LudoPlus",
            listOf(
                "Race against other players to get your pawns around the board and back home. The first player to bring all four pawns home wins!",
                "Each player rolls one die per turn. The first player is in the bottom left corner, then you go clockwise around the board.",
                "Want to see it played? The tutorial takes you through the basics.",
            ),
            diagram = null,
        ),
        Page(
            "The Board",
            "The Board",
            listOf(
                "Each player has four pawns, waiting in their own coloured yard. Each colour has their own start square.",
                "The track runs around the board, and you share it with other players.",
                "After a full lap, a pawn enters its own home column and heads home in the middle of the board.",
            ),
            "Diagram: Red's route. From its start square, a pawn goes a lap of the track clockwise, then up Red's home column to home in the middle.",
        ),
        Page(
            "Your Turn",
            "Your Turn",
            listOf(
                "Roll the die, then move one of your pawns that many squares.",
                "You must roll a 6 to bring a pawn out of the yard onto the board. If another player brings out the first pawn and you still have none out after 5 of your turns, one comes out free.",
                "Every time you roll a 6, you may roll again (so does capturing a pawn, or bringing one home). However, roll three 6s in a row and your turn ends!",
                "If you have no valid moves, your turn passes.",
            ),
            "Diagram: the die shows 6, so a red pawn comes out of its yard onto its start square.",
        ),
        Page(
            "Captures",
            "Captures",
            listOf(
                "If you land on a rival's pawns, they are sent back to their yard and must roll another 6 to escape.",
                "Start squares and star squares are safe spots: nobody can be captured there.",
                "Your own pawns can share a square, and any pawn can pass any other.",
                "If you're lucky, you can chain your captures together!",
            ),
            "Diagram: Red rolls 4 and lands on Green's pawn, sending it back to Green's yard. Blue's pawn is safe on a star square.",
        ),
        Page(
            "Winning",
            "Winning",
            listOf(
                "To bring a pawn home, you must roll exactly the number needed.",
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
                "Pawns gives each player from one to six pawns, instead of the mode's own number.",
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
                "No Free Pawn: A player with no pawn out 5 turns after the first one came out doesn't get one out free.",
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
            "Colour Rush",
            "Colour Rush",
            listOf(
                "In Colour Rush the first pawn to land on a square of the track claims it, and it takes that player's colour. Claiming a square doesn't make it safe. Stars can be claimed too, but start squares can't.",
                "A claimed square only changes hands when another player captures a pawn on it, whoever's pawn that was. Pawns on stars can't be captured, so a claimed star never changes hands.",
                "The game ends when every square that can be claimed is, or when a player (or a team) has every pawn home. The most squares wins. If that's a tie, it goes to whoever's pawns have come furthest.",
                "The Leaderboard ranks Colour Rush wins by the most squares. Your pawns home and your captures count in Statistics.",
            ),
            diagram = "Board: a game of Colour Rush. The squares each player has claimed are in their colour, behind their pawns. Red has claimed the most.",
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
            diagram = "Board: a game of Two Dice, with pawns of every colour out on the track. Under it, the dice show 6 and 3.",
        ),
        Page(
            "Portals",
            "Portals",
            listOf(
                "Portals come in pairs, told by their colour and symbol. If your move ends on one, your pawn comes out at the other, whether that's further on or further back. Passing over a portal does nothing.",
                "Your pawn only captures where it comes out. While it waits on a portal, rivals who land there go through without capturing it, but one coming out there captures it.",
                "Long portals, with a split ring, reach into the next player's stretch of the track. One reaches past your own start square: going through it takes your pawn most of the way home, or most of the way back.",
                "Each mode has its own Leaderboard, its own page in Statistics, and achievements of its own.",
            ),
            diagram = "Board: a game of Portals, with every pair of portals on the track. A red pawn waits on one.",
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
                "The Leaderboard ranks Survival wins by the furthest travelled: the most squares the winner's pawns moved.",
            ),
            diagram = "Board: a game of Survival in the Cataclysm. Six squares of the track have vanished, the start and star squares are crossed out as no longer safe, and each yard crosses out the pawns its player has lost.",
        ),
        Page(
            "Chaos",
            "Chaos",
            listOf(
                "In Chaos there are power-ups on the track, at least one of each kind at any time. End a move on one to use it at once:",
                "Double: Your next three rolls move a pawn twice as far. A roll that would take it past home counts once.",
                "Shield: The next pawn to land on yours bounces off to the square before it, and the shield breaks.",
                "Roll Again: You get another roll.",
                "Call Up: One of your pawns comes out of the yard. With none there, it's banked: the next of your pawns to be captured comes straight back out.",
                "Swap: Your pawn swaps places with any other player's pawn on the track, if you like. You pick which.",
                "Only one power-up works in a move: a pawn moved onto another leaves it there. When the last of a kind is taken, another appears somewhere random at once. A random power-up also appears every one to five turns, up to two for each player, or seven with two or three players.",
                "Bouncing off a shield earns no roll unless your pawn captures where it settles. A pawn brought out of the yard can't bounce, so a shield doesn't stop it.",
                "Each mode has its own Leaderboard, its own page in Statistics, and achievements of its own.",
            ),
            diagram = "Board: a game of Chaos, with one of each power-up on the track. A green pawn has a shield, and Red's yard shows 2 doubled rolls left and 1 Call Up banked.",
        ),
        Page(
            "Haunted House",
            "Haunted House",
            listOf(
                "In Haunted House, ghosts haunt the track, one for every two players. After every second turn one of them drifts up to 6 squares, forwards or back, and you won't know which one or how far until it does.",
                "A ghost sends every pawn on the square it lands on back to its yard, whoever's they are, unless the square is safe. Landing on a ghost yourself does no harm: it only catches pawns when it moves.",
                "Each mode has its own Leaderboard, its own page in Statistics, and achievements of its own.",
            ),
            diagram = "Board: a game of Haunted House, with its two ghosts on the track, one 2 squares ahead of a red pawn.",
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
                // The diagram or board is one image, described in words. It isn't scrolled to: a group's last page never
                // settled, as above.
                page.diagram?.let { compose.onNodeWithContentDescription(it).assert(isImage) }
            }
        }
        // The diagrams' and boards' dice are part of their pictures, not dice of their own.
        compose.onAllNodesWithContentDescription("Die showing", substring = true).assertCountEquals(0)
        // Every diagram described is one of the pages' own.
        compose.onAllNodes(isImage).fetchSemanticsNodes().forEach { node ->
            val description = node.config[SemanticsProperties.ContentDescription].single()
            check(groups.any { (_, groupPages) -> groupPages.any { it.diagram == description } }) { "an image no page describes: $description" }
        }
    }
}
