package net.zodac.ludo.ui.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import net.zodac.ludo.data.settings.DefaultStyleIds
import net.zodac.ludo.ui.theme.DicePipColor
import net.zodac.ludo.ui.theme.IvoryDiceBottom
import net.zodac.ludo.ui.theme.IvoryDiceTop

/** Default [DiceStyle]: a rounded ivory die with a soft bevel, matching the reference art. */
object IvoryDiceStyle : DiceStyle {
    override val id: String = DefaultStyleIds.DICE
    override val bodyColor: Color = lerp(IvoryDiceTop, IvoryDiceBottom, 0.5f)

    override fun recoloured(palette: DieColourPalette): DiceStyle = ColouredClassicDiceStyle(id, palette)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) =
        BeveledDie(value, held, IvoryDiceTop, IvoryDiceBottom, DicePipColor, modifier)
}
