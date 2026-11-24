package net.zodac.ludoplus.ui.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import net.zodac.ludoplus.data.settings.DefaultStyleIds
import net.zodac.ludoplus.ui.theme.DicePipColor
import net.zodac.ludoplus.ui.theme.IvoryDiceBottom
import net.zodac.ludoplus.ui.theme.IvoryDiceTop

/** Default [DiceStyle]: a rounded ivory die with a soft bevel, matching the reference art. */
object IvoryDiceStyle : DiceStyle {
    override val id: String = DefaultStyleIds.DICE
    override val bodyColor: Color = lerp(IvoryDiceTop, IvoryDiceBottom, 0.5f)

    @Composable
    override fun Die(value: Int, modifier: Modifier) =
        BeveledDie(value, IvoryDiceTop, IvoryDiceBottom, DicePipColor, modifier)
}
