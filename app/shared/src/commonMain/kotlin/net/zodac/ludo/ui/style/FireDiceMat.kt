package net.zodac.ludo.ui.style

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import net.zodac.ludo.ui.theme.FireSlotBorder
import net.zodac.ludo.ui.theme.FireSlotBottom
import net.zodac.ludo.ui.theme.FireSlotTop
import net.zodac.ludo.ui.theme.FireTrayBottom
import net.zodac.ludo.ui.theme.FireTrayTop

/** The Classic mat in Red [DiceMat]: a plain red tray. */
object FireDiceMat : DiceMat {
    override val id: String = "classic_red"

    override val diceTrayBrush: Brush = Brush.verticalGradient(listOf(FireTrayTop, FireTrayBottom))
    override val slotSocketBrush: Brush = Brush.verticalGradient(listOf(FireSlotTop, FireSlotBottom))
    override val slotSocketBorder: Color = FireSlotBorder
}
