package net.zodac.ludoplus.ui.style

import androidx.compose.ui.graphics.Brush
import net.zodac.ludoplus.ui.theme.FireBackgroundBottom
import net.zodac.ludoplus.ui.theme.FireBackgroundTop

/** The Classic background in Red [TableBackground]: a red felt background behind the board. */
object FireTableBackground : TableBackground {
    override val id: String = "classic_red"

    override val scoreAreaBrush: Brush = Brush.verticalGradient(listOf(FireBackgroundTop, FireBackgroundBottom))
}
