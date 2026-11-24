package net.zodac.ludoplus.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.launch
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.app_name
import net.zodac.ludoplus.resources.sora
import net.zodac.ludoplus.ui.game.BoardLayout
import net.zodac.ludoplus.ui.game.HOP_HEIGHT
import net.zodac.ludoplus.ui.game.HOP_SWELL
import net.zodac.ludoplus.ui.game.PAWN_RADIUS as BOARD_PAWN_RADIUS
import net.zodac.ludoplus.ui.game.PAWN_STEP_MILLIS
import net.zodac.ludoplus.ui.game.drawGroundShadow
import net.zodac.ludoplus.ui.style.BoardStyle
import net.zodac.ludoplus.ui.style.GameVisualTheme
import net.zodac.ludoplus.ui.style.PawnStyle
import net.zodac.ludoplus.ui.theme.color
import org.jetbrains.compose.resources.Font

/**
 * The brand typeface: Sora (`composeResources/font/sora.ttf`), used on the logo wordmark here, on
 * [ScreenScaffold]'s page titles and every dialog title, and on the game's own marks: an unlock banner's title, and
 * the game mode and house rule names on their picker pages and in the New Game fields that open them, and the New Game
 * screen's player count and You / User / CPU labels. This is a deliberate, narrow departure from stock M3 type ([UI.md]'s "no
 * typography overrides" rule is about the type *scale*, not a call site): these are brand marks,
 * not body text, so they earn their own face the same way the game board earns its own palette.
 *
 * Never for a player's name or anything else a player types: the font only covers ASCII and
 * Latin-1, so a name in any other script (or with an emoji) would come out in two faces.
 *
 * Not for a column of numbers either (the Leaderboard's turns, Statistics): Sora's default digits are proportional - a "1" is narrower than a "0" - so right-aligned
 * numbers in it don't line up digit for digit. The system font's digits are all one width. Sora
 * does carry tabular digits (`fontFeatureSettings = "tnum"`) if a column ever has to be in it.
 *
 * The upstream font ships as a variable font (a 100-800 weight axis); since this app only ever
 * uses the bold instance, `sora.ttf` here is a static weight-700 instance produced with
 * `fontTools.varLib.instancer`, then subset with `fontTools.subset` (`layout_features=['*']`,
 * `name_IDs=['*']`) to U+0020-007E and U+00A0-00FF - printable ASCII plus Latin-1, for an
 * achievement title like "Déjà Vu" and the Hit List's "·" any-place mark. Variable-axis and
 * unused-script data was most of the original file's size. It is the only weight there is, so a
 * Sora call site asks for [FontWeight.Bold] - a lighter or heavier one would get Bold anyway.
 */
internal val SoraFontFamily: FontFamily
    @Composable
    get() = FontFamily(Font(Res.font.sora, weight = FontWeight.Bold))

/** The title style of a page's top bar: [SoraFontFamily], bold, `titleLarge` size. Its gold comes from the bar's colours. */
internal val pageTitleStyle: TextStyle
    @Composable
    get() = MaterialTheme.typography.titleLarge.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)

/** The title style of a dialog: [SoraFontFamily], bold, `headlineSmall` size, in the gold `primary`. */
internal val dialogTitleStyle: TextStyle
    @Composable
    get() = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )

/** The style of a category header on the Achievements and Styles pages, and its count: [SoraFontFamily], bold, `titleSmall` size, as written. */
internal val categoryHeaderStyle: TextStyle
    @Composable
    get() = MaterialTheme.typography.titleSmall.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)

/** Whether `sora.ttf` has a glyph for this character - printable ASCII or Latin-1, as cut (see [SoraFontFamily]). */
internal fun Char.isInSoraFont(): Boolean = this in ' '..'~' || this in '\u00A0'..'\u00FF'

/** The logo's art, wide by tall: the tilted board's diamond, with the pawn standing up out of it. */
private val LOGO_ART_WIDTH = 200.dp
private val LOGO_ART_HEIGHT = 112.dp

/** The pawn's size (its grid's 1) as a share of the board's diagonal: about a third of the pawn's height. */
private const val PAWN_RADIUS = 0.155f

/** How much the board is flattened into perspective once turned (the launcher icon's 0.56). */
private const val BOARD_TILT = 0.56f

/** The board's turn, so that red's yard, at its bottom left, is nearest (the launcher icon's). */
private const val BOARD_TURN = 315f

/** How many hops the pawn makes when it's tapped. */
const val LOGO_PAWN_HOPS = 3

/** How long the pawn's hops take: a square's hop on the board each. */
const val LOGO_PAWN_HOP_MILLIS = LOGO_PAWN_HOPS * PAWN_STEP_MILLIS

/** How far round the pawn's middle a tap still finds it, in its radii: wider and taller than it, for a fingertip. */
private const val PAWN_TAP_REACH = 1.2f

/**
 * The app's mark on the main menu: the launcher icon's art, drawn live in Compose in [theme]'s board and pawns (the
 * player's Styles picks). The classic board, turned so red's yard is nearest and flattened into perspective; a red pawn
 * standing in red's yard; and the wordmark under them.
 *
 * With [onPawnTapped], a tap on the pawn makes it hop [LOGO_PAWN_HOPS] times where it stands, each a hop of the game's
 * from square to square (none under reduced motion), and calls [onPawnTapped] as it sets off.
 *
 * With no [theme] (the picks not loaded yet), the art is held invisible, still taking its space, so it never flashes the
 * defaults. The art is decoration, so a screen reader hears only the name, as a heading.
 */
@Composable
fun AppLogo(theme: GameVisualTheme?, modifier: Modifier = Modifier, onPawnTapped: (() -> Unit)? = null) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        val art = Modifier.size(LOGO_ART_WIDTH, LOGO_ART_HEIGHT).clearAndSetSemantics {}
        if (theme == null) Spacer(art) else LogoArt(theme.board, theme.pawns, onPawnTapped, art)
        Text(
            text = stringResource(Res.string.app_name),
            modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.displayMedium,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The board in [board]'s style and the pawn in [pawns]', each on a layer of its own: the board drawn again only when its
 * style changes, the pawn as it hops too. A tap on the pawn sets it hopping (see [AppLogo]).
 */
@Composable
private fun LogoArt(board: BoardStyle, pawns: PawnStyle, onPawnTapped: (() -> Unit)?, modifier: Modifier = Modifier) {
    val layout = remember { BoardLayout.of(BoardGeometry.CLASSIC) }
    val colours = remember { PlayerColour.entries.take(BoardGeometry.CLASSIC.seats).map { it.color } }
    // How many hops in, 0 to LOGO_PAWN_HOPS: each whole number is the pawn standing on the ground.
    val hop = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val pace by rememberUpdatedState(LocalFramePace.current)
    val reduceMotion by rememberUpdatedState(LocalReduceMotion.current)
    val currentOnPawnTapped by rememberUpdatedState(onPawnTapped)
    val taps = if (onPawnTapped == null) {
        Modifier
    } else {
        // A raw gesture, not a button: the pawn is decoration, and a screen reader isn't handed an unnamed button.
        Modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val pawn = pawnPlace(size.width.toFloat(), size.height.toFloat(), layout.size)
                if (abs(tap.x - pawn.centre.x) > PAWN_TAP_REACH * pawn.radius || abs(tap.y - pawn.centre.y) > PAWN_TAP_REACH * pawn.radius) {
                    return@detectTapGestures
                }
                if (hop.isRunning) return@detectTapGestures
                currentOnPawnTapped?.invoke()
                if (reduceMotion) return@detectTapGestures
                scope.launch {
                    pace.run {
                        hop.snapTo(0f)
                        hop.animateTo(LOGO_PAWN_HOPS.toFloat(), tween(LOGO_PAWN_HOP_MILLIS, easing = LinearEasing))
                        hop.snapTo(0f)
                    }
                }
            }
        }
    }
    Box(modifier = modifier.then(taps)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // The board's diagonal spans the art's width, its nearest corner at the art's foot.
            val side = size.width / sqrt(2f)
            val unit = side / layout.size
            val boardCentre = boardCentre()
            withTransform({
                scale(1f, BOARD_TILT, pivot = boardCentre)
                rotate(BOARD_TURN, pivot = boardCentre)
                translate(boardCentre.x - side / 2, boardCentre.y - side / 2)
            }) {
                with(board) { drawBoard(layout, unit, colours) }
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pawn = pawnPlace(size.width, size.height, layout.size)
            // Up and down again each hop, swelling as it rises, as a pawn hops from square to square on the board.
            val fraction = hop.value - hop.value.toInt()
            val arc = 4f * fraction * (1f - fraction)
            val radius = pawn.radius * (1f + HOP_SWELL * arc)
            val lift = HOP_HEIGHT / BOARD_PAWN_RADIUS * pawn.radius * arc
            if (lift > 0f) drawGroundShadow(pawn.centre, radius, arc)
            with(pawns) { drawPawn(pawn.centre - Offset(0f, lift), radius, PlayerColour.RED.color, ringed = false) }
        }
    }
}

/** Where the pawn stands on the ground and how big it is. */
private class PawnPlace(val centre: Offset, val radius: Float)

/** The pawn's place in art [width] by [height], on a board of [squares] a side: its feet in the middle of red's yard. */
private fun pawnPlace(width: Float, height: Float, squares: Float): PawnPlace {
    val radius = width * PAWN_RADIUS
    // Its feet (the grid's y 0.9) in the middle of red's yard, the nearest: 4.5 of the board's 15 squares from its
    // middle along the diagonal, which the turn makes straight down, flattened with the board.
    val yard = 4.5f / squares * width * BOARD_TILT
    return PawnPlace(boardCentre(width, height) + Offset(0f, yard - 0.9f * radius), radius)
}

/** The middle of the board: its flattened diagonal, half the art's width, reaching down to the art's foot. */
private fun DrawScope.boardCentre() = boardCentre(size.width, size.height)

private fun boardCentre(width: Float, height: Float) = Offset(width / 2, height - width / 2 * BOARD_TILT)
