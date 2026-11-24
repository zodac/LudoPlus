package net.zodac.ludoplus

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.launch
import net.zodac.ludoplus.ui.common.ChoicePicker
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** A press on a rounded card stays inside its corners: a click on a card's `modifier` sits outside the card's own clip. */
class PressFeedbackClipTest : UiTest() {

    /** Paints the whole of whatever's pressed, unlike a ripple (which Robolectric doesn't draw), so a missing clip shows in its corners. */
    private object FillOnPress : IndicationNodeFactory {
        override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node(), DrawModifierNode {
            private var pressed = false

            override fun onAttach() {
                coroutineScope.launch {
                    interactionSource.interactions.collect {
                        pressed = it is PressInteraction.Press
                        invalidateDraw()
                    }
                }
            }

            override fun ContentDrawScope.draw() {
                drawContent()
                if (pressed) drawRect(Color.Magenta)
            }
        }

        override fun equals(other: Any?) = other === this

        override fun hashCode() = 0
    }

    private fun ImageBitmap.at(x: Float, y: Float) = toPixelMap()[x.toInt(), y.toInt()]

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            LudoPlusTheme { CompositionLocalProvider(LocalIndication provides FillOnPress, content = content) }
        }
    }

    private fun assertPressStaysInCorners(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val before = compose.onRoot().captureToImage()
        compose.mainClock.autoAdvance = false
        node.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(400)
        val pressed = compose.onRoot().captureToImage()

        assertEquals(before.at(bounds.left, bounds.top), pressed.at(bounds.left, bounds.top), "The press reached the corner")
        assertEquals(Color.Magenta, pressed.at(bounds.center.x, bounds.top + 2), "The press didn't show")
    }

    @Test
    fun `a picker field's press stays inside its rounded corners`() = uiTest(TestScreen(360, 800)) {
        show {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                ChoicePicker(title = "Thing", options = listOf("One"), selected = "One", onSelect = {}, label = { it }, description = { "About $it" })
            }
        }
        assertPressStaysInCorners(compose.onNodeWithText("One"))
    }
}
