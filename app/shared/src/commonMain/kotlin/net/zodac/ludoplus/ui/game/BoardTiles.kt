package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.ui.common.AppTooltip
import net.zodac.ludoplus.ui.common.rememberAppTooltipState
import net.zodac.ludoplus.ui.common.stringResource

/** How near a tile's middle a tap on it lands, in squares: all of its square, and no further than its neighbours' middles. */
private const val TILE_REACH = 0.6f

/** How faint the whole board is, but for what's picked out on it: a pressed portal's pair, a Swap's pawn and those it can be with, or the pawns of a yard that's held. */
internal const val BOARD_FADE = 0.2f

/** The one of [squares] (absolute track squares) that a tap at [point] (board units) is on, or null. */
internal fun tileAt(point: Offset, layout: BoardLayout, squares: Collection<Int>): Int? =
    squares.map { it to (layout.trackSquare(it).centre - point).getDistance() }.filter { it.second <= TILE_REACH }.minByOrNull { it.second }?.first

/**
 * Whether a tap at [point] on the tile on [square] is for a pawn all the same: one of [movable] stands on the tile, or is
 * nearer the tap.
 */
internal fun pawnTakesTap(point: Offset, layout: BoardLayout, square: Int, marks: List<PawnMark>, movable: Set<PawnRef>): Boolean {
    val tile = layout.trackSquare(square).centre
    val reach = (tile - point).getDistance()
    return marks.filter { mark -> mark.pawns.any { PawnRef(mark.player, it) in movable } }
        .any { (it.centre - tile).getDistance() <= TILE_REACH || (it.centre - point).getDistance() < reach }
}

/** A power-up's popup: on the track's [square], its name, and where [detailed], what it does. */
internal data class PowerUpTip(val square: Int, val detailed: Boolean)

/**
 * [tip]'s popup by its tile, of the [powerUp] there: up until it's dismissed, by a tap anywhere ([onDismiss]). As it is
 * focusable, that tap does nothing else.
 */
@Composable
internal fun PowerUpTooltip(layout: BoardLayout, tip: PowerUpTip, powerUp: PowerUp, onDismiss: () -> Unit, modifier: Modifier) {
    val tooltip = rememberAppTooltipState(persistent = true)
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(tip) {
        // Until it's dismissed.
        tooltip.show()
        dismiss()
    }
    BoxWithConstraints(modifier = modifier) {
        val unit = maxWidth / layout.size
        val corner = layout.trackSquare(tip.square).centre - Offset(0.5f, 0.5f)
        // The box is where the tile is: a tooltip's own is only as big as what's in it.
        Box(Modifier.offset(unit * corner.x, unit * corner.y).size(unit)) {
            AppTooltip(
                body = {
                    Column {
                        Text(stringResource(powerUp.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        if (tip.detailed) Text(stringResource(powerUp.description), style = MaterialTheme.typography.bodyMedium)
                    }
                },
                state = tooltip,
                enabled = false,
            ) { Box(Modifier.fillMaxSize()) }
        }
    }
}
