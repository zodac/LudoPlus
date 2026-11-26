package net.zodac.ludoplus.ui.records

import net.zodac.ludoplus.ui.icons.Symbols
import net.zodac.ludoplus.resources.leaderboard_global_cd
import net.zodac.ludoplus.resources.leaderboard_global
import net.zodac.ludoplus.online.LocalGameServices
import net.zodac.ludoplus.online.GameServicesState
import net.zodac.ludoplus.online.GameServices
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.ludoplus.data.records.GameResult
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.LeaderboardRanking
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.gameover_captures
import net.zodac.ludoplus.resources.gameover_squares
import net.zodac.ludoplus.resources.gameover_turns
import net.zodac.ludoplus.resources.leaderboard_captures
import net.zodac.ludoplus.resources.leaderboard_empty_players
import net.zodac.ludoplus.resources.leaderboard_heading
import net.zodac.ludoplus.resources.leaderboard_heading_squares
import net.zodac.ludoplus.resources.leaderboard_heading_travelled
import net.zodac.ludoplus.resources.leaderboard_player
import net.zodac.ludoplus.resources.leaderboard_title
import net.zodac.ludoplus.resources.leaderboard_squares
import net.zodac.ludoplus.resources.leaderboard_squares_travelled
import net.zodac.ludoplus.resources.leaderboard_travelled
import net.zodac.ludoplus.resources.leaderboard_travelled_unknown
import net.zodac.ludoplus.resources.leaderboard_travelled_unknown_short
import net.zodac.ludoplus.resources.leaderboard_turns
import net.zodac.ludoplus.ui.common.EmptyStateCard
import net.zodac.ludoplus.ui.common.HoldPageCover
import net.zodac.ludoplus.ui.common.LocalLandscapePage
import net.zodac.ludoplus.ui.common.ScreenScaffold
import net.zodac.ludoplus.ui.common.joinClauses
import net.zodac.ludoplus.ui.common.ordinal
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.theme.BoardInk
import net.zodac.ludoplus.ui.theme.GoldAccent
import net.zodac.ludoplus.ui.theme.LeaderboardBronze
import net.zodac.ludoplus.ui.theme.LeaderboardSilver

/** The rank column's width: "100th" at the rank's size, with room to spare. */
private val RANK_WIDTH = 60.sp

/** Each number column's width: the header's word at its size. */
private val NUMBER_WIDTH = 72.sp

/** The least a name needs beside the numbers before each row goes to two lines. */
private val MIN_NAME_WIDTH = 80.sp

/** A row's padding and gaps besides its columns. */
private val ROW_CHROME = 56.dp

/** Between the list's parts. */
private val PART_GAP = 16.dp

/** Above the table's first row, and under its last. */
private val TABLE_END_PADDING = 4.dp

/**
 * The Leaderboard: the fastest wins for the mode and player count picked (2 to 8; at first, the mode last picked here and
 * the count New Game last started), fewest turns first (or where the last standing wins, most pawns left) and then most
 * captures, the top three ranked in gold, silver, and bronze. The mode's drop-down stays at the top while the rest scrolls;
 * in landscape the player counts share its row, and the page is at most [LANDSCAPE_TABLE_WIDTH] wide.
 * Where a name hasn't room beside the number columns (a large font), each row is two lines: the rank and name, then its
 * turns and captures. A screen reader reads each row as one: its rank, name, turns, and captures.
 *
 * [driftingDice] is as [ScreenScaffold]'s.
 */
@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel, onBack: () -> Unit, driftingDice: Boolean = true) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(Res.string.leaderboard_title), onBack = onBack, driftingDice = driftingDice, landscapeWidth = LANDSCAPE_TABLE_WIDTH) {
        // Nothing until the board is read, so the empty card never flashes up before a board that has rows.
        val (board, rows) = state ?: return@ScreenScaffold HoldPageCover()
        val (mode, count) = board
        val landscape = LocalLandscapePage.current
        val counts = @Composable { modifier: Modifier ->
            PlayerCountRow(options = PLAYER_COUNTS, selected = count, onSelect = { viewModel.selectPlayerCount(it!!) }, modifier = modifier)
        }
        if (landscape) {
            LandscapeRecordsControls(
                picker = { LeaderboardModePicker(selected = mode, onSelect = viewModel::selectMode, modifier = it) },
                counts = counts,
            )
        } else {
            LeaderboardModePicker(selected = mode, onSelect = viewModel::selectMode)
        }
        val services = LocalGameServices.current
        // The same board on the online service, while signed in to one: shown with or without rows here.
        val online = services.state.collectAsStateWithLifecycle().value is GameServicesState.SignedIn && services.canShowLeaderboardUi
        // The least width that holds a name beside the numbers; at a large font, each row is two lines instead.
        val columnsWidth = with(LocalDensity.current) { RANK_WIDTH.toDp() + NUMBER_WIDTH.toDp() * 2 + MIN_NAME_WIDTH.toDp() } + ROW_CHROME
        // Each mode's board its own list, so it starts at its top. A count is only picked from the top, where its row is, and
        // keeps the list: a screen reader stays on the count it picked.
        key(mode) {
            RecordsList { width ->
                val compact = width < columnsWidth
                item(key = "heading") { BoardHeading(mode, modifier = Modifier.padding(bottom = PART_GAP)) }
                if (!landscape) {
                    item(key = "counts") { Box(modifier = Modifier.padding(bottom = PART_GAP)) { counts(Modifier) } }
                }
                if (online) {
                    item(key = "global") {
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = PART_GAP), contentAlignment = Alignment.Center) { GlobalLeaderboardButton(mode, count, services) }
                    }
                }
                if (rows.isEmpty()) {
                    item(key = "empty") { EmptyStateCard(pluralStringResource(Res.plurals.leaderboard_empty_players, count, stringResource(mode.displayName), count)) }
                } else {
                    // One card to look at, a piece of it to each row: only the rows on screen are built.
                    if (!compact) {
                        item(key = "header") { TablePiece(first = true, last = false) { HeaderRow(mode) } }
                    }
                    itemsIndexed(rows, key = { _, row -> row.uuid }) { index, row ->
                        TablePiece(first = compact && index == 0, last = index == rows.lastIndex) {
                            if (index > 0 || !compact) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            LeaderboardRow(rank = index + 1, row = row, mode = mode, compact = compact)
                        }
                    }
                }
            }
        }
    }
}

/** What [mode]'s board ranks, a heading: "Classic: fastest wins". */
@Composable
private fun BoardHeading(mode: GameMode, modifier: Modifier) {
    val heading = when (mode.ranking) {
        LeaderboardRanking.FEWEST_TURNS -> Res.string.leaderboard_heading
        LeaderboardRanking.MOST_SQUARES_MOVED -> Res.string.leaderboard_heading_travelled
        LeaderboardRanking.MOST_SQUARES -> Res.string.leaderboard_heading_squares
    }
    Text(
        text = stringResource(heading, stringResource(mode.displayName)),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().semantics { heading() },
    )
}

/** A piece of the table's card: rounded at the top when it's the [first], and at the bottom when it's the [last]. */
@Composable
private fun TablePiece(first: Boolean, last: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val card = MaterialTheme.shapes.medium
    val square = CornerSize(0.dp)
    val shape = card.copy(
        topStart = if (first) card.topStart else square,
        topEnd = if (first) card.topEnd else square,
        bottomStart = if (last) card.bottomStart else square,
        bottomEnd = if (last) card.bottomEnd else square,
    )
    val colours = CardDefaults.cardColors()
    Surface(modifier = Modifier.fillMaxWidth(), shape = shape, color = colours.containerColor, contentColor = colours.contentColor) {
        Column(
            modifier = Modifier.padding(top = if (first) TABLE_END_PADDING else 0.dp, bottom = if (last) TABLE_END_PADDING else 0.dp),
            content = content,
        )
    }
}

/**
 * Opens [mode]'s leaderboard for [playerCount] players on [services]: a globe and "Global leaderboard", spoken with the
 * board and the service's name.
 */
@Composable
private fun GlobalLeaderboardButton(mode: GameMode, playerCount: Int, services: GameServices) {
    val description = pluralStringResource(
        Res.plurals.leaderboard_global_cd,
        playerCount,
        stringResource(mode.displayName),
        playerCount,
        services.serviceName,
    )
    TextButton(
        onClick = { services.showLeaderboardUi(mode.onlineLeaderboardKey(playerCount)) },
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(imageVector = Symbols.Public, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(8.dp))
        Text(stringResource(Res.string.leaderboard_global))
    }
}

@Composable
private fun HeaderRow(mode: GameMode) {
    val style = MaterialTheme.typography.labelMedium
    val colour = MaterialTheme.colorScheme.onSurfaceVariant
    val column = when (mode.ranking) {
        LeaderboardRanking.FEWEST_TURNS -> Res.string.leaderboard_turns
        LeaderboardRanking.MOST_SQUARES_MOVED -> Res.string.leaderboard_travelled
        LeaderboardRanking.MOST_SQUARES -> Res.string.leaderboard_squares
    }
    // The rows say what each number is; the headings are for the eye.
    TableRow(
        modifier = Modifier.clearAndSetSemantics { },
        rank = { },
        name = { Text(stringResource(Res.string.leaderboard_player), style = style, color = colour) },
        turns = { Text(stringResource(column), style = style, color = colour, textAlign = TextAlign.End) },
        captures = { Text(stringResource(Res.string.leaderboard_captures), style = style, color = colour, textAlign = TextAlign.End) },
    )
}

@Composable
private fun LeaderboardRow(rank: Int, row: GameResult, mode: GameMode, compact: Boolean) {
    // What the mode ranks by: the turns, the squares the winner's pawns travelled (unknown for a win from before they were
    // kept: a dash), or the squares they held.
    val (number, words) = when (mode.ranking) {
        LeaderboardRanking.FEWEST_TURNS -> row.turns to Res.plurals.gameover_turns
        LeaderboardRanking.MOST_SQUARES_MOVED -> row.squaresMoved to Res.plurals.leaderboard_squares_travelled
        LeaderboardRanking.MOST_SQUARES -> row.squares to Res.plurals.gameover_squares
    }
    val ranked = if (number == null) stringResource(Res.string.leaderboard_travelled_unknown) else pluralStringResource(words, number, number)
    val spoken = joinClauses(
        listOf(
            ordinal(rank),
            row.playerName,
            ranked,
            pluralStringResource(Res.plurals.gameover_captures, row.captures, row.captures),
        ),
    )
    val numberStyle = MaterialTheme.typography.bodyLarge
    if (compact) {
        Row(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.widthIn(min = with(LocalDensity.current) { RANK_WIDTH.toDp() })) { RankText(rank) }
            Column(modifier = Modifier.weight(1f)) {
                Text(row.playerName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    joinClauses(
                        listOf(
                            ranked,
                            pluralStringResource(Res.plurals.gameover_captures, row.captures, row.captures),
                        ),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    TableRow(
        modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
        rank = { RankText(rank) },
        name = { Text(row.playerName, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        turns = { Text(number?.toString() ?: stringResource(Res.string.leaderboard_travelled_unknown_short), style = numberStyle, fontWeight = FontWeight.Bold, textAlign = TextAlign.End) },
        captures = { Text(row.captures.toString(), style = numberStyle, textAlign = TextAlign.End) },
    )
}

/** [rank] as "1st": the top three on a pill in gold, silver, and bronze. */
@Composable
private fun RankText(rank: Int) {
    val medal = when (rank) {
        1 -> GoldAccent
        2 -> LeaderboardSilver
        3 -> LeaderboardBronze
        else -> null
    }
    val style = MaterialTheme.typography.titleSmall
    if (medal == null) {
        Text(ordinal(rank), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false, modifier = Modifier.padding(horizontal = 8.dp))
        return
    }
    Surface(color = medal, shape = CircleShape) {
        Text(ordinal(rank), style = style, fontWeight = FontWeight.Bold, color = BoardInk, softWrap = false, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** One line of the table: the rank, the name taking what's left, then the two numbers, each column in step. */
@Composable
private fun TableRow(
    modifier: Modifier,
    rank: @Composable () -> Unit,
    name: @Composable () -> Unit,
    turns: @Composable () -> Unit,
    captures: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val rankWidth = with(density) { RANK_WIDTH.toDp() }
    val numberWidth = with(density) { NUMBER_WIDTH.toDp() }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.width(rankWidth)) { rank() }
        Row(modifier = Modifier.weight(1f)) { name() }
        Row(modifier = Modifier.width(numberWidth), horizontalArrangement = Arrangement.End) { turns() }
        Row(modifier = Modifier.width(numberWidth), horizontalArrangement = Arrangement.End) { captures() }
    }
}
