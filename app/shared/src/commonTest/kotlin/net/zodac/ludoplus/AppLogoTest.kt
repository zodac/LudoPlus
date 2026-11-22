package net.zodac.ludoplus

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import net.zodac.ludoplus.ui.common.AppLogo
import net.zodac.ludoplus.ui.style.BoardStyles
import net.zodac.ludoplus.ui.style.GameVisualTheme
import net.zodac.ludoplus.ui.style.PawnStyles
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** The test tag round the logo, for capturing it. */
private const val LOGO = "logo"

class AppLogoTest : UiTest() {
    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun `a screen reader hears only the name, as a heading - the board and pawn are decoration`() = uiTest {
        compose.setContent { LudoPlusTheme { AppLogo(GameVisualTheme()) } }
        compose.onNode(isHeading and hasText("LudoPlus")).assertIsDisplayed()
        val spoken = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().children.flatMap { node ->
            generateSequence(listOf(node)) { level -> level.flatMap { it.children }.ifEmpty { null } }.flatten().toList()
        }.flatMap { it.config.getOrElse(SemanticsProperties.Text) { emptyList() } + it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } }
        assertEquals(listOf("LudoPlus"), spoken.map { it.toString() })
    }

    @Test
    fun `the board and pawn are drawn in the player's picks`() = uiTest {
        var theme by mutableStateOf(GameVisualTheme())
        compose.setContent { LudoPlusTheme { Box(Modifier.testTag(LOGO)) { AppLogo(theme) } } }
        val classic = artPixels()

        theme = GameVisualTheme(board = BoardStyles.byId("wood_oak"))
        val wood = artPixels()
        assertNotEquals(classic, wood, "Another board is drawn")

        theme = GameVisualTheme(board = BoardStyles.byId("wood_oak"), pawns = PawnStyles.byId("animal_duck"))
        assertNotEquals(wood, artPixels(), "Another pawn is drawn")
    }

    @Test
    fun `until the picks load, the art is blank but keeps its space, so the wordmark doesn't move when it arrives`() = uiTest {
        var theme by mutableStateOf<GameVisualTheme?>(null)
        compose.setContent { LudoPlusTheme { Box(Modifier.testTag(LOGO)) { AppLogo(theme) } } }
        val blank = artPixels()
        val waiting = wordmarkBounds()
        assertEquals(1, blank.distinct().size, "Nothing drawn but what's behind")

        theme = GameVisualTheme()
        val loaded = artPixels()
        assertEquals(waiting, wordmarkBounds())
        assertNotEquals(blank, loaded)
    }

    private fun wordmarkBounds() = compose.onNode(hasText("LudoPlus")).fetchSemanticsNode().boundsInRoot

    /** The logo's pixels above the wordmark: the art alone. */
    private fun artPixels(): List<Int> {
        compose.waitForIdle()
        val image = compose.onNodeWithTag(LOGO).captureToImage()
        val pixels = image.toPixelMap()
        val artHeight = minOf(wordmarkBounds().top.toInt(), image.height)
        return (0..<artHeight).flatMap { y -> (0..<image.width).map { x -> pixels[x, y].hashCode() } }
    }
}
