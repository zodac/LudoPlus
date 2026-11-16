package net.zodac.ludo.ui.style

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import net.zodac.ludo.model.DieColour
import net.zodac.ludo.ui.theme.GoldAccent
import net.zodac.ludo.ui.theme.IrishGreenDiceBottom
import net.zodac.ludo.ui.theme.IrishGreenDiceTop
import net.zodac.ludo.ui.theme.IrishGreenPipColor
import net.zodac.ludo.ui.theme.IrishGreenStripe
import net.zodac.ludo.ui.theme.IrishGreenSwatch
import net.zodac.ludo.ui.theme.IrishOrangeDiceBottom
import net.zodac.ludo.ui.theme.IrishOrangeDiceTop
import net.zodac.ludo.ui.theme.IrishOrangePipColor
import net.zodac.ludo.ui.theme.IrishOrangeStripe
import net.zodac.ludo.ui.theme.IrishOrangeSwatch
import net.zodac.ludo.ui.theme.IrishWhiteDiceBottom
import net.zodac.ludo.ui.theme.IrishWhiteDiceTop
import net.zodac.ludo.ui.theme.IrishWhitePipColor
import net.zodac.ludo.ui.theme.IrishWhiteStripe
import net.zodac.ludo.ui.theme.IrishWhiteSwatch
import net.zodac.ludo.ui.theme.TricolourBlueDiceBottom
import net.zodac.ludo.ui.theme.TricolourBlueDiceTop
import net.zodac.ludo.ui.theme.TricolourBluePipColor
import net.zodac.ludo.ui.theme.TricolourBlueStripe
import net.zodac.ludo.ui.theme.TricolourBlueSwatch
import net.zodac.ludo.ui.theme.TricolourRedDiceBottom
import net.zodac.ludo.ui.theme.TricolourRedDiceTop
import net.zodac.ludo.ui.theme.TricolourRedPipColor
import net.zodac.ludo.ui.theme.TricolourRedStripe
import net.zodac.ludo.ui.theme.TricolourRedSwatch
import net.zodac.ludo.ui.theme.TricolourYellowDiceBottom
import net.zodac.ludo.ui.theme.TricolourYellowDiceTop
import net.zodac.ludo.ui.theme.TricolourYellowHeldRing
import net.zodac.ludo.ui.theme.TricolourYellowPipColor
import net.zodac.ludo.ui.theme.TricolourYellowStripe
import net.zodac.ludo.ui.theme.TricolourYellowSwatch

/**
 * The Classic die ([BeveledDie]) in a [DieColourPalette] - what every Classic colour becomes on a
 * roll that lands a colour of its own (see [DiceStyle.recoloured]). They're one shape in three
 * colours, so all three recolour the same.
 */
internal class ColouredClassicDiceStyle(override val id: String, private val palette: DieColourPalette) : DiceStyle {
    override val bodyColor: Color = lerp(palette.diceTop, palette.diceBottom, 0.5f)

    override fun recoloured(palette: DieColourPalette): DiceStyle = ColouredClassicDiceStyle(id, palette)

    @Composable
    override fun Die(value: Int, held: Boolean, modifier: Modifier) =
        BeveledDie(value, held, palette.diceTop, palette.diceBottom, palette.pip, modifier, heldRingColor = palette.heldRing)
}

/** Every shade one [DieColour] is drawn in, on the dice and on the scorecard. */
data class DieColourPalette(
    val diceTop: Color,
    val diceBottom: Color,
    val pip: Color,
    /** The flat colour of that colour's scorecard box. */
    val swatch: Color,
    /** A deeper shade for a tile background with a glyph drawn over it. */
    val stripe: Color,
    /** The held ring's colour on this die - [GoldAccent] for every colour except yellow, whose own
     * face is close enough to gold that the two need swapping (see [TricolourYellowHeldRing]). */
    val heldRing: Color = GoldAccent,
)

val DieColour.palette: DieColourPalette
    get() = when (this) {
        DieColour.RED -> DieColourPalette(
            TricolourRedDiceTop, TricolourRedDiceBottom, TricolourRedPipColor, TricolourRedSwatch, TricolourRedStripe,
        )
        DieColour.YELLOW -> DieColourPalette(
            TricolourYellowDiceTop, TricolourYellowDiceBottom, TricolourYellowPipColor, TricolourYellowSwatch, TricolourYellowStripe,
            heldRing = TricolourYellowHeldRing,
        )
        DieColour.BLUE -> DieColourPalette(
            TricolourBlueDiceTop, TricolourBlueDiceBottom, TricolourBluePipColor, TricolourBlueSwatch, TricolourBlueStripe,
        )
    }

/**
 * [palette] as normal, or - while [LocalIrishTricolour] is true - the Irish flag's green/white/orange
 * standing in for this colour's usual red/yellow/blue instead. Every caller that draws a Tricolour
 * colour (dice, the Reds/Yellows/Blues scorecard swatches, Coloured House's stripes) goes through
 * this one function, so re-skinning only ever needs touching this `when`.
 */
fun DieColour.palette(irish: Boolean): DieColourPalette = if (!irish) {
    palette
} else {
    when (this) {
        DieColour.RED -> DieColourPalette(IrishGreenDiceTop, IrishGreenDiceBottom, IrishGreenPipColor, IrishGreenSwatch, IrishGreenStripe)
        DieColour.YELLOW -> DieColourPalette(IrishWhiteDiceTop, IrishWhiteDiceBottom, IrishWhitePipColor, IrishWhiteSwatch, IrishWhiteStripe)
        DieColour.BLUE -> DieColourPalette(IrishOrangeDiceTop, IrishOrangeDiceBottom, IrishOrangePipColor, IrishOrangeSwatch, IrishOrangeStripe)
    }
}
