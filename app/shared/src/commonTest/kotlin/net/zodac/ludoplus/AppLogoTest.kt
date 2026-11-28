package net.zodac.ludoplus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import net.zodac.ludoplus.ui.common.AppLogo
import net.zodac.ludoplus.ui.common.DEBUG_MARK_TAG
import net.zodac.ludoplus.ui.common.LOGO_PAWN_HOP_MILLIS
import net.zodac.ludoplus.ui.common.LocalReduceMotion
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
    fun `a debug build's logo has a small (d) after the name - for the eye only - and a release build's none`() = uiTest {
        var debug by mutableStateOf(true)
        // The mark hangs off the name's end, outside the logo's own width, as on the menu's full-width page.
        compose.setContent { LudoPlusTheme { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AppLogo(GameVisualTheme(), debugMark = debug) } } }
        compose.onNodeWithTag(DEBUG_MARK_TAG, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("(d)", substring = true), useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(isHeading and hasText("LudoPlus")).assertIsDisplayed()

        debug = false
        compose.waitForIdle()
        compose.onNodeWithTag(DEBUG_MARK_TAG, useUnmergedTree = true).assertDoesNotExist()
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

    @Test
    fun `a tap on the pawn sets it hopping, and it's back where it stood once its three hops are done`() = uiTest {
        var taps = 0
        compose.setContent { LudoPlusTheme { Box(Modifier.testTag(LOGO)) { AppLogo(GameVisualTheme(), onPawnTapped = { taps++ }) } } }
        val standing = artPixels()
        compose.mainClock.autoAdvance = false
        tapPawn()
        // Half a hop in: at the top of the first.
        compose.mainClock.advanceTimeBy(LOGO_PAWN_HOP_MILLIS / 6L)
        assertNotEquals(standing, artPixels(), "The pawn is up in the air")
        assertEquals(1, taps)
        compose.mainClock.advanceTimeBy(LOGO_PAWN_HOP_MILLIS.toLong())
        assertEquals(standing, artPixels(), "The pawn is back on its spot")
    }

    @Test
    fun `a tap off the pawn does nothing`() = uiTest {
        var taps = 0
        compose.setContent { LudoPlusTheme { Box(Modifier.testTag(LOGO)) { AppLogo(GameVisualTheme(), onPawnTapped = { taps++ }) } } }
        val standing = artPixels()
        compose.mainClock.autoAdvance = false
        // The board's far corner, at the art's top.
        compose.onNodeWithTag(LOGO).performTouchInput { click(Offset(centerX, 8.dp.toPx())) }
        compose.mainClock.advanceTimeBy(LOGO_PAWN_HOP_MILLIS / 6L)
        assertEquals(standing, artPixels())
        assertEquals(0, taps)
    }

    @Test
    fun `under reduced motion a tap on the pawn still counts, but it stays still`() = uiTest {
        var taps = 0
        compose.setContent {
            LudoPlusTheme {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    Box(Modifier.testTag(LOGO)) { AppLogo(GameVisualTheme(), onPawnTapped = { taps++ }) }
                }
            }
        }
        val standing = artPixels()
        compose.mainClock.autoAdvance = false
        tapPawn()
        compose.mainClock.advanceTimeBy(LOGO_PAWN_HOP_MILLIS / 6L)
        assertEquals(standing, artPixels())
        assertEquals(1, taps)
    }

    /** A tap on the pawn's body: the art's middle, 62dp down (its feet 90dp down the 112dp art, its middle a third of it above). */
    private fun tapPawn() = compose.onNodeWithTag(LOGO).performTouchInput { click(Offset(centerX, 62.dp.toPx())) }

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
