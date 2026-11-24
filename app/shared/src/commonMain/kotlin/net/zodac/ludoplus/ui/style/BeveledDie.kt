package net.zodac.ludoplus.ui.style

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** How rounded a [BeveledDie]'s corners are, as a percentage of its size. */
internal const val BEVELED_DIE_CORNER_PERCENT = 22

/**
 * The rounded, soft-bevelled die of the Classic family: only the gradient and pip colour change between its colours.
 */
@Composable
internal fun BeveledDie(
    value: Int,
    topColor: Color,
    bottomColor: Color,
    pipColor: Color,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(BEVELED_DIE_CORNER_PERCENT)
    Box(
        modifier = modifier
            .dieShadow(shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(topColor, bottomColor)))
            .border(1.dp, bottomColor.copy(alpha = 0.6f), shape),
        contentAlignment = Alignment.Center,
    ) {
        PipFace(
            value = value,
            color = pipColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            pipRadiusFraction = 0.11f,
        )
    }
}
