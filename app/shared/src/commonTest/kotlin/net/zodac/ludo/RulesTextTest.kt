package net.zodac.ludo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludo.platform.LocalPlatformServices
import net.zodac.ludo.platform.SilentPlatformServices
import net.zodac.ludo.ui.rules.RulesScreen
import net.zodac.ludo.ui.rules.resetRulesPageMemory
import net.zodac.ludo.ui.theme.LudoPlusTheme

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
            "How to Play Ludo Plus",
            listOf(
                "Race your four pawns a full lap of the board and into home, in the middle. The first player to bring all four pawns home wins.",
                "Players take turns clockwise, each rolling one die. To decide who goes first, everyone rolls once, and the highest roll starts. Ties roll again.",
                "Want to see it played? The tutorial takes you through it in four short parts. Nothing in it counts: no scores and no saved game.",
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
    fun `the Rules screen's words - its title, and every page's tab, title, paragraphs, and diagram`() = uiTest(TestScreen(360, 800)) {
        resetRulesPageMemory()
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { RulesScreen(onBack = {}) }
            }
        }
        compose.onAllNodesWithText("Rules").assertCountEquals(1)
        compose.onAllNodes(isTab).assertCountEquals(pages.size)
        for (page in pages) {
            compose.onNode(isTab and hasText(page.tab)).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNode(isHeading and hasText(page.title)).performScrollTo()
            page.paragraphs.forEach { compose.onNodeWithText(it).performScrollTo() }
            // The diagram is one image, described in words.
            page.diagram?.let { compose.onNodeWithContentDescription(it).performScrollTo().assert(isImage) }
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
