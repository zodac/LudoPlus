package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.game_captures_captured
import net.zodac.ludoplus.resources.gameover_captured
import net.zodac.ludoplus.resources.gameover_captures
import net.zodac.ludoplus.resources.leaderboard_captures
import net.zodac.ludoplus.ui.common.SoraFontFamily
import net.zodac.ludoplus.ui.common.joinClauses
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.theme.color

/** Who [player] can capture and be captured by, in player order: everyone else, but their partners only where those capture each other. */
internal fun captureOpponents(state: GameState, player: Int): List<Int> =
    state.players.indices.filter { it != player && (state.rules.partnerCaptures || !state.rules.arePartners(player, it)) }

/**
 * [player]'s captures and times captured, in all and against each opponent ([captureOpponents]): a bar either side of the
 * opponent's name, in their colour, as long as its count is to the biggest. Under [player]'s own name where it's [named].
 * It scrolls where a large font makes it taller than the screen. A screen reader reads whose it is and the totals as one, then each row as
 * one, with its place in the list: "Alex, 2 captures, 1 captured".
 */
@Composable
internal fun CaptureBars(game: StartedGame, player: Int, named: Boolean, modifier: Modifier = Modifier) {
    val state = game.state
    val side = state.players[player]
    val rows = captureOpponents(state, player).map { Triple(it, side.capturesOf[it] ?: 0, state.players[it].capturesOf[player] ?: 0) }
    val most = rows.maxOfOrNull { (_, made, had) -> maxOf(made, had) } ?: 0
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val nameStyle = MaterialTheme.typography.bodyMedium
    val names = rows.map { game.players[it.first].name }
    // Every row's bars start in step, either side of the widest name.
    val nameWidth = remember(names, nameStyle, density) {
        with(density) { (names.maxOfOrNull { measurer.measure(it, nameStyle, maxLines = 1).size.width } ?: 0).toDp() } + NAME_PADDING * 2
    }.coerceIn(NAME_MIN_WIDTH, NAME_MAX_WIDTH)
    val totalsSpoken = joinClauses(listOf(game.players[player].name, countsSpoken(side.captures, side.timesCaptured)))
    Column(
        modifier = modifier.width(WIDTH).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (named) {
            Text(
                game.players[player].name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = game.players[player].colour.color,
                textAlign = TextAlign.Center,
                // Read with the totals.
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            )
        }
        Row(modifier = Modifier.clearAndSetSemantics { contentDescription = totalsSpoken }) {
            Total(stringResource(Res.string.leaderboard_captures), side.captures, Alignment.Start, TextAlign.Start, Modifier.weight(1f))
            Total(stringResource(Res.string.game_captures_captured), side.timesCaptured, Alignment.End, TextAlign.End, Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val countWidth = with(density) { COUNT_WIDTH.toDp() }
        Column(
            modifier = Modifier.semantics { collectionInfo = CollectionInfo(rowCount = rows.size, columnCount = 1) },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEachIndexed { row, (opponent, made, had) ->
                val config = game.players[opponent]
                val spoken = joinClauses(listOf(config.name, countsSpoken(made, had)))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {
                    contentDescription = spoken
                    collectionItemInfo = CollectionItemInfo(rowIndex = row, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                }) {
                    Count(made, TextAlign.Start, Modifier.width(countWidth))
                    Bar(made, most, config.colour.color, Alignment.CenterEnd, Modifier.weight(1f))
                    Text(
                        config.name,
                        style = nameStyle,
                        color = config.colour.color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(nameWidth).padding(horizontal = NAME_PADDING),
                    )
                    Bar(had, most, config.colour.color, Alignment.CenterStart, Modifier.weight(1f))
                    Count(had, TextAlign.End, Modifier.width(countWidth))
                }
            }
        }
    }
}

/** "2 captures, 1 captured". */
@Composable
private fun countsSpoken(made: Int, had: Int): String = joinClauses(
    listOf(pluralStringResource(Res.plurals.gameover_captures, made, made), pluralStringResource(Res.plurals.gameover_captured, had, had)),
)

@Composable
private fun Total(heading: String, count: Int, alignment: Alignment.Horizontal, textAlign: TextAlign, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Text(
            heading,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = textAlign,
        )
        Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** A row's [count], fainter for none. */
@Composable
private fun Count(count: Int, textAlign: TextAlign, modifier: Modifier) {
    Text(
        count.toString(),
        style = MaterialTheme.typography.bodyMedium,
        color = if (count == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = NONE_ALPHA) else MaterialTheme.colorScheme.onSurface,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/** [count] as a share of [most], in [colour] along a dark track from its [from] end. */
@Composable
private fun Bar(count: Int, most: Int, colour: Color, from: Alignment, modifier: Modifier) {
    Box(modifier = modifier.height(BAR_HEIGHT).background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape), contentAlignment = from) {
        if (count == 0) return@Box
        Box(Modifier.fillMaxHeight().widthIn(min = BAR_HEIGHT).fillMaxWidth(count / most.toFloat()).background(colour, CircleShape))
    }
}

private val WIDTH = 302.dp
private val NAME_PADDING = 6.dp
private val NAME_MIN_WIDTH = 60.dp
private val NAME_MAX_WIDTH = 140.dp
private val COUNT_WIDTH = 18.sp
private val BAR_HEIGHT = 8.dp

/** How faint a count of none is. */
private const val NONE_ALPHA = 0.6f
