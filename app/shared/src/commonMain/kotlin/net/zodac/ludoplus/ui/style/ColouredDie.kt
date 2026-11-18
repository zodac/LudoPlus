package net.zodac.ludoplus.ui.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import net.zodac.ludoplus.ui.theme.GoldAccent
import net.zodac.ludoplus.ui.theme.TricolourBlueDiceBottom
import net.zodac.ludoplus.ui.theme.TricolourBlueDiceTop
import net.zodac.ludoplus.ui.theme.TricolourBluePipColor
import net.zodac.ludoplus.ui.theme.TricolourBlueSwatch
import net.zodac.ludoplus.ui.theme.TricolourRedDiceBottom
import net.zodac.ludoplus.ui.theme.TricolourRedDiceTop
import net.zodac.ludoplus.ui.theme.TricolourRedPipColor
import net.zodac.ludoplus.ui.theme.TricolourRedSwatch
import net.zodac.ludoplus.ui.theme.TricolourYellowDiceBottom
import net.zodac.ludoplus.ui.theme.TricolourYellowDiceTop
import net.zodac.ludoplus.ui.theme.TricolourYellowHeldRing
import net.zodac.ludoplus.ui.theme.TricolourYellowPipColor
import net.zodac.ludoplus.ui.theme.TricolourYellowSwatch

/**
 * The Classic die ([BeveledDie]) in a [DieColourPalette]: the Classic family's red, yellow, and blue, and what a
 * Classic die becomes when recoloured ([DiceStyle.recoloured]).
 */
internal class ColouredClassicDiceStyle(override val id: String, private val palette: DieColourPalette) : DiceStyle {
    override val bodyColor: Color = lerp(palette.diceTop, palette.diceBottom, 0.5f)

    override fun recoloured(palette: DieColourPalette): DiceStyle = ColouredClassicDiceStyle(id, palette)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) =
        BeveledDie(value, held, palette.diceTop, palette.diceBottom, palette.pip, modifier, heldRingColor = palette.heldRing)
}

/** Every shade a coloured die is drawn in. */
data class DieColourPalette(
    val diceTop: Color,
    val diceBottom: Color,
    val pip: Color,
    /** Its dot on the Styles screen. */
    val swatch: Color,
    /** The held ring's colour on this die - [GoldAccent] for every colour except yellow, whose own
     * face is close enough to gold that the two need swapping (see [TricolourYellowHeldRing]). */
    val heldRing: Color = GoldAccent,
)

/** The Classic die in red. */
internal val ClassicRedDie = DieColourPalette(TricolourRedDiceTop, TricolourRedDiceBottom, TricolourRedPipColor, TricolourRedSwatch)

/** The Classic die in yellow, its held ring swapped from gold. */
internal val ClassicYellowDie = DieColourPalette(
    TricolourYellowDiceTop, TricolourYellowDiceBottom, TricolourYellowPipColor, TricolourYellowSwatch, heldRing = TricolourYellowHeldRing,
)

/** The Classic die in blue. */
internal val ClassicBlueDie = DieColourPalette(TricolourBlueDiceTop, TricolourBlueDiceBottom, TricolourBluePipColor, TricolourBlueSwatch)
