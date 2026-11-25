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
     * Whether this style draws its own tumble mid-roll (the D20, a solid it can turn itself) rather than being rolled
     * as a cube of its own faces by the game. See [LocalDieTumbleMillis].
     */
    val tumblesItself: Boolean get() = false

    /**
     * For a style with loose pupils on its faces that the die's movement throws about (googly eyes), how far each can
     * roll from its socket's centre, as a fraction of the die's size - the page then keeps each die a [DieMotion] to
     * move them, handed down as [LocalDieMotion]. Null for a style with nothing loose, which needs no such tracking at
     * all.
     */
    val pupilTravel: Float? get() = null

    /**
     * The colour of the die's material itself - what shows along the rounded edge between two faces as it tumbles. Its
     * swatch, for a style that has one.
     */
    val bodyColor: Color get() = (this as? Swatched)?.swatch ?: Color.LightGray

    /**
     * The outline this die casts its ground shadow with when lying on the table - its own shape, not a generic one.
     * [dieIndex] is which die it is and [tumbleMillis] how long it's been tumbling (null at rest - see
     * [LocalDieTumbleMillis]), for a die whose outline changes with either (the D20). A rounded square by default, like
     * [BeveledDie].
     */
    fun shadowShape(value: Int, dieIndex: Int, tumbleMillis: Float?): Shape = RoundedCornerShape(cornerPercent)


    /**
     * How rounded the die's corners are, as a percentage of its size - the one figure its face, its shadow and its
     * tumble all round to, so a square-cornered die doesn't tumble rounded and then snap square as it lands.
     */
    val cornerPercent: Int get() = BEVELED_DIE_CORNER_PERCENT

    @Composable
    fun Die(value: Int, modifier: Modifier)
}

/**
 * Supplies the game screen's background, behind the board, the die, and the player cards. The top bar keeps the app's
 * own.
 */
interface TableBackground : TableArt {
    val scoreAreaBrush: Brush

    /**
     * An optional pattern drawn over [scoreAreaBrush], behind the board. Most backgrounds are a plain brush and don't
     * need one, so it's a no-op by default.
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
 * Which parts of a board are drawn: whether a start square is in its seat's colour, with the border that says it's safe
 * ([starts]), or a plain one, the border on an empty seat's too ([emptyStartsSafe]), the star squares have their marks
 * ([stars]), and there's a home, with its columns ([homes]). Each of [claims], an absolute track square and its player's
 * colour, is drawn as a home column's square of that colour is, a star's mark smaller on it.
 */
data class BoardParts(
    val starts: Boolean = true,
    val emptyStartsSafe: Boolean = true,
    val stars: Boolean = true,
    val homes: Boolean = true,
    val claims: Map<Int, Color> = emptyMap(),
) {
    /** Whether [seat]'s start square has its border, in a game of [seatColours]. */
    internal fun startBorder(seat: Int, seatColours: List<Color?>): Boolean = starts && (emptyStartsSafe || seatColours.getOrNull(seat) != null)
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
     * for each of [pawns] pawns ([BoardLayout.yardSpotOf]), and only its [parts].
     */
    fun DrawScope.drawBoard(layout: BoardLayout, unit: Float, seatColours: List<Color?>, pawns: Int = YARD_SPOTS, parts: BoardParts = BoardParts())
}

/**
 * The pawns' shape and material. A pawn always takes its player's [colour][drawPawn]: a style changes how it's made,
 * never whose it is.
 */
interface PawnStyle : TableArt {
    /**
     * How much of the classic pawn's rise up a turned square it takes ([net.zodac.ludoplus.ui.game.BoardSquare.stand]), 0 to 1:
     * less, the wider it is at its top, as that is what would cross the square's upper sides.
     */
    val lift: Float get() = 1f

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
