package net.zodac.ludo.ui.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.ludo.ui.common.LocalReduceMotion
import net.zodac.ludo.ui.common.rememberPacedLoop
import net.zodac.ludo.ui.theme.Primary

/** The tutorial outline's strength, from [PULSE_LOW] to full and back - steady at full under reduced motion. */
@Composable
internal fun rememberTutorialPulse(): () -> Float {
    if (LocalReduceMotion.current) return { 1f }
    val alpha = rememberPacedLoop(PULSE_LOW, 1f, infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse))
    return { alpha.value }
}

/**
 * A violet outline ("press this", as the movable pawns' ring) round this, at [pulse]'s strength - read as it's drawn, so
 * the pulse redraws without recomposing. It's drawn just outside the bounds, so it goes before any `clip` in the chain.
 */
internal fun Modifier.tutorialOutline(pulse: () -> Float, cornerRadius: Dp = OUTLINE_CORNER): Modifier = drawWithContent {
    drawContent()
    val stroke = OUTLINE_WIDTH.toPx()
    val outset = OUTLINE_GAP.toPx() + stroke / 2
    drawRoundRect(
        color = Primary.copy(alpha = pulse()),
        topLeft = Offset(-outset, -outset),
        size = Size(size.width + outset * 2, size.height + outset * 2),
        cornerRadius = CornerRadius(cornerRadius.toPx() + outset),
        style = Stroke(width = stroke),
    )
}

private const val PULSE_LOW = 0.35f
private const val PULSE_MILLIS = 700
private val OUTLINE_WIDTH = 3.dp
private val OUTLINE_GAP = 4.dp
private val OUTLINE_CORNER = 16.dp
