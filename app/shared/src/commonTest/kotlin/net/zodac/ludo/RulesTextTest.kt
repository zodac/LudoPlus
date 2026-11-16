package net.zodac.ludo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import net.zodac.ludo.platform.LocalPlatformServices
import net.zodac.ludo.platform.SilentPlatformServices
import net.zodac.ludo.ui.rules.RulesScreen
import net.zodac.ludo.ui.theme.LudoPlusTheme

/** The Rules screen's words: its title, its heading, and each card's heading and points, word for word. */
class RulesTextTest : UiTest() {

    @Test
    fun `the Rules screen's words - its title and heading - and every card's heading and points`() = uiTest {
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { RulesScreen(onBack = {}) }
            }
        }
        compose.onAllNodesWithText("Rules").assertCountEquals(1)
        compose.onNodeWithText("How to Play Ludo Plus").assert(isHeading())

        val cards = mapOf(
            "The board" to listOf(
                "Each player has four pawns, waiting in their own coloured yard.",
                "The track runs all the way round the board, and everyone shares it. Each colour joins it at its own start square.",
                "After a full lap, a pawn turns into its own home column and races to home, in the middle of the board.",
            ),
            "Your turn" to listOf(
                "Roll the die, then move one of your pawns that many squares.",
                "Only a 6 brings a pawn out of the yard, onto its start square.",
                "Roll a 6 and you roll again. Roll three 6s in a row and your turn ends.",
                "If none of your pawns can move, your turn passes.",
            ),
            "Captures" to listOf(
                "Land on a single rival pawn to send it back to its yard.",
                "Start squares and star squares are safe: nobody can be captured there.",
                "Your own pawns can share a square, and any pawn can pass any other.",
            ),
            "Winning" to listOf(
                "A pawn needs the exact roll to reach home.",
                "The first player to bring all four pawns home wins, and everyone else plays on for the places.",
            ),
        )
        for ((heading, points) in cards) {
            compose.onNodeWithText(heading).performScrollTo().assert(isHeading())
            points.forEach { compose.onNodeWithText(it).performScrollTo() }
        }
    }
}
