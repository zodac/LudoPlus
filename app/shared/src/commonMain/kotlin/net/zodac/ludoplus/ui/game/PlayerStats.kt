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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.game_captures_captured
import net.zodac.ludoplus.resources.game_captures_captured_spoken
import net.zodac.ludoplus.resources.game_rolls
import net.zodac.ludoplus.resources.game_rolls_face_spoken
import net.zodac.ludoplus.resources.game_rolls_spoken
import net.zodac.ludoplus.resources.gameover_captures
import net.zodac.ludoplus.resources.leaderboard_captures
import net.zodac.ludoplus.ui.common.SoraFontFamily
import net.zodac.ludoplus.ui.common.joinClauses
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.style.LocalArtFrozen
import net.zodac.ludoplus.ui.style.LocalDieCastsShadow
import net.zodac.ludoplus.ui.style.LocalGameVisualTheme
import net.zodac.ludoplus.ui.theme.color

/** Who [player] can capture and be captured by, in player order: everyone else, but their partners only where those capture each other. */
internal fun captureOpponents(state: GameState, player: Int): List<Int> =
    state.players.indices.filter { it != player && (state.rules.partnerCaptures || !state.rules.arePartners(player, it)) }

/**
 * [player]'s captures and times captured, in all and against each opponent ([captureOpponents]): a bar either side of the
 * opponent's name, in their colour, as long as its count is to the biggest. Under [player]'s own name where it's [named], and
 * over how many times they have rolled each face ([RollBars]).
 * It scrolls where a large font makes it taller than the screen. A screen reader reads whose it is and the totals as one, then each row as
 * one, with its place in the list: "Alex, 2 captures, captured by 1".
 */
@Composable
internal fun PlayerStats(game: StartedGame, player: Int, named: Boolean, modifier: Modifier = Modifier) {
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
        // A heading that wraps at a large font leaves the totals in line.
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.clearAndSetSemantics { contentDescription = totalsSpoken }) {
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        RollBars(side.rolls, state.rules.dieFaces, game.players[player].colour.color)
    }
}

/**
 * How many times each of a die's [faces] has been rolled, by [rolls], and in all: a bar for each in [colour], as long as its
 * count is to the biggest, after the face in the die's style, in two columns that each read downwards. A screen reader reads
 * "36 rolls", then each face as one, with its place among them: "Rolled a 1, 7 times".
 */
@Composable
private fun RollBars(rolls: Map<Int, Int>, faces: Int, colour: Color) {
    val total = rolls.values.sum()
    val totalSpoken = pluralStringResource(Res.plurals.game_rolls_spoken, total, total)
    val most = rolls.values.maxOrNull() ?: 0
    val (countWidth, dieSize) = with(LocalDensity.current) {
        (DIGIT_WIDTH * most.toString().length.coerceAtLeast(2)).toDp() to ROLL_DIE_SIZE.toDp().coerceAtMost(ROLL_DIE_MAX_SIZE)
    }
    Row(modifier = Modifier.clearAndSetSemantics { contentDescription = totalSpoken }) {
        Heading(stringResource(Res.string.game_rolls), TextAlign.Start, Modifier.weight(1f).alignByBaseline())
        Text(total.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.alignByBaseline())
    }
    Row(
        modifier = Modifier.semantics { collectionInfo = CollectionInfo(rowCount = faces, columnCount = 1) },
        horizontalArrangement = Arrangement.spacedBy(ROLL_COLUMN_GAP),
    ) {
        for (column in (1..faces).chunked((faces + 1) / 2)) {
            // A group, so that a screen reader reads down one column and then the other, not across them.
            Column(modifier = Modifier.weight(1f).semantics { isTraversalGroup = true }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (face in column) {
                    val count = rolls[face] ?: 0
                    val spoken = pluralStringResource(Res.plurals.game_rolls_face_spoken, count, face, count)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ROLL_GAP),
                        modifier = Modifier.clearAndSetSemantics {
                            contentDescription = spoken
                            collectionItemInfo = CollectionItemInfo(rowIndex = face - 1, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                        },
                    ) {
                        SmallDie(face, dieSize)
                        Bar(count, most, colour, Alignment.CenterStart, Modifier.weight(1f))
                        Count(count, TextAlign.End, Modifier.width(countWidth))
                    }
                }
            }
        }
    }
}

/** [face] in the die's style, [size] big and still: drawn at the size its art is made for and scaled down, as its pips' margins don't shrink with it. */
@Composable
private fun SmallDie(face: Int, size: Dp) {
    val scale = size / DIE_ART_SIZE
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalArtFrozen provides true, LocalDieCastsShadow provides false) {
            LocalGameVisualTheme.current.diceStyle.Die(
                value = face,
                modifier = Modifier.requiredSize(DIE_ART_SIZE).graphicsLayer { scaleX = scale; scaleY = scale },
            )
        }
    }
}

/** "2 captures, captured by 1". */
@Composable
private fun countsSpoken(made: Int, had: Int): String = joinClauses(
    listOf(pluralStringResource(Res.plurals.gameover_captures, made, made), stringResource(Res.string.game_captures_captured_spoken, had)),
)

@Composable
private fun Total(heading: String, count: Int, alignment: Alignment.Horizontal, textAlign: TextAlign, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = alignment) {
        Heading(heading, textAlign)
        Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Heading(text: String, textAlign: TextAlign, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = textAlign,
        modifier = modifier,
    )
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
private val ROLL_COLUMN_GAP = 20.dp
private val ROLL_GAP = 8.dp

/** Room for a digit of a roll's count. */
private val DIGIT_WIDTH = 9.sp

/** How big a roll's die is, growing with the font up to a size that leaves room for its bar, and the size a die's art is made for. */
private val ROLL_DIE_SIZE = 22.sp
private val ROLL_DIE_MAX_SIZE = 30.dp
private val DIE_ART_SIZE = 44.dp

/** How faint a count of none is. */
private const val NONE_ALPHA = 0.6f
