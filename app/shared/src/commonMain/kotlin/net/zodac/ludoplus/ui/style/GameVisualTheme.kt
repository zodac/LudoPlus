package net.zodac.ludoplus.ui.style

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import net.zodac.ludoplus.ui.game.BoardLayout

/**
 * One swappable piece of table art in one colour - a [BoardStyle], [PawnStyle], [DiceStyle], or [TableBackground]. [id] is what the player's pick is saved as, so it must never change once shipped. It is
 * its family's name then its colour's, from their string keys - `classic_gold` - which `StyleCatalogTest` holds it to.
 */
interface TableArt {
    val id: String
}

/**
 * Renders a single die face. Implementations are swapped wholesale via
 * [GameVisualTheme.diceStyle] - e.g. a future "user picked a dice skin"
 * setting would just provide a different [DiceStyle] instance.
 */
interface DiceStyle : TableArt {
    /**
     * Whether this style draws its own tumble mid-roll (the D20, a solid it can turn itself) rather
     * than being rolled as a cube of its own faces by the tray. See [LocalDieTumbleMillis].
     */
    val tumblesItself: Boolean get() = false

    /**
     * Whether this die comes to rest standing straight up (the Egg) rather than lying at its own
     * angle on the mat, as a cube does - the tray then lands it unturned.
     */
    val standsUpright: Boolean get() = false


    /**
     * For a style with loose pupils on its faces that the die's movement throws about (googly eyes),
     * how far each can roll from its socket's centre, as a fraction of the die's size - the tray then
     * keeps each die a [DieMotion] to move them, handed down as [LocalDieMotion]. Null for a style
     * with nothing loose, which needs no such tracking at all.
     */
    val pupilTravel: Float? get() = null

    /**
     * The colour of the die's material itself - what shows along the rounded edge between two faces
     * as [TossedCube] tumbles it. Its swatch, for a style that has one.
     */
    val bodyColor: Color get() = (this as? Swatched)?.swatch ?: Color.LightGray

    /**
     * The outline this die casts its ground shadow with when lying on the mat - its own shape, not a
     * generic one. [dieIndex] is which die it is and [tumbleMillis] how long it's been tumbling
     * (null at rest - see [LocalDieTumbleMillis]), for a die whose outline changes with either (the
     * D20). A rounded square by default, like [BeveledDie].
     */
    fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = RoundedCornerShape(cornerPercent)


    /**
     * How rounded the die's corners are, as a percentage of its size - the one figure its face, its
     * shadow and its tumble ([TossedCube]) all round to, so a square-cornered die doesn't tumble
     * rounded and then snap square as it lands.
     */
    val cornerPercent: Int get() = BEVELED_DIE_CORNER_PERCENT

    /**
     * The face this style's art shows above [value] on the die, for a style drawn as a solid with
     * more than one face in view (the Cube), or null when only [value] shows. A toss lands with this
     * face on top, so the tossed die settles into exactly what [Die] then draws.
     */
    fun topFace(value: Int): Int? = null

    /**
     * The die tumbling mid-toss, [roll] quarter-turns along its way through [ring] - see
     * [TossedCube], which by default rolls this style's own [Die] faces as a cube. A style whose
     * faces already draw a whole solid (the Cube) draws its tumble itself instead, since rolling
     * those as cards would show every card's painted sides at once.
     */
    @Composable
    fun TossedDie(roll: Float, finalTurns: Int, ring: List<Int>, modifier: Modifier) {
        TossedCube(roll = roll, finalTurns = finalTurns, ring = ring, body = bodyColor, cornerPercent = cornerPercent, modifier = modifier) { value, faceModifier ->
            Die(value = value, held = false, modifier = faceModifier)
        }
    }

    /**
     * This style's shape and finish in [palette]'s colours instead of its own - how a die whose
     * colour is part of the roll (Tricolour) is drawn, so the player's style still shows while the
     * colour still reads. Its pips, numbers or digits always take [DieColourPalette.pip], picked to
     * read on that colour, never the style's own, which could vanish against it (the blue D20's gold
     * numbers on a yellow die), and its held ring [DieColourPalette.heldRing], for the same reason.
     */
    fun recoloured(palette: DieColourPalette): DiceStyle

    @Composable
    fun Die(value: Int, held: Boolean, modifier: Modifier)
}

/**
 * Supplies the game screen's background, behind the board, the die, and the player cards. The top bar keeps the app's
 * own.
 */
interface TableBackground : TableArt {
    val scoreAreaBrush: Brush

    /**
     * An optional pattern drawn over [scoreAreaBrush], behind the scorecard. Most backgrounds are a
     * plain brush and don't need one, so it's a no-op by default.
     */
    fun DrawScope.drawScoreAreaDecoration() {}

    /**
     * Composed by whoever draws [drawScoreAreaDecoration], for a decoration that moves: keeps it
     * running for as long as it's on screen. A no-op for one that doesn't.
     */
    @Composable
    fun Animate() {}
}

/**
 * A board: its track, safe squares, yards, home columns, and centre, drawn under the pawns on a square
 * [BoardLayout.size] units across. The seats' colours are the players' own (a seat with no player is null) and must
 * stay readable: a style changes the track, the safe squares' marks, the surround, and the texture, never which colour
 * a seat is.
 */
interface BoardStyle : TableArt {
    /**
     * The board of [layout] itself, no pawns, at [unit] pixels a square, in [seatColours] by seat, each yard with a spot
     * for each of [pawns] pawns ([BoardLayout.yardSpotOf]).
     */
    fun DrawScope.drawBoard(layout: BoardLayout, unit: Float, seatColours: List<Color?>, pawns: Int = YARD_SPOTS)

    /** The spot a pawn that is home stands on, under it: it lifts the pawn off its own colour's triangle. */
    fun DrawScope.drawHomeSpot(centre: Offset, radius: Float)
}

/**
 * The pawns' shape and material. A pawn always takes its player's [colour][drawPawn]: a style changes how it's made,
 * never whose it is.
 */
interface PawnStyle : TableArt {
    /**
     * A pawn standing within [radius] across of [centre] (a little taller than wide) in its player's [colour], with the
     * violet ring round it when [ringed]: it can be moved.
     */
    fun DrawScope.drawPawn(centre: Offset, radius: Float, colour: Color, ringed: Boolean)
}

/** Bundles the pluggable game-table art. Swap any field to re-skin that piece independently. */
data class GameVisualTheme(
    val board: BoardStyle = BoardStyles.default,
    val pawns: PawnStyle = PawnStyles.default,
    val diceStyle: DiceStyle = DiceStyles.default,
    val background: TableBackground = TableBackgrounds.default,
)

val LocalGameVisualTheme = staticCompositionLocalOf { GameVisualTheme() }

/** How many spots a yard has: one for each of the classic four pawns. */
const val YARD_SPOTS = 4
