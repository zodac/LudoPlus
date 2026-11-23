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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import net.zodac.ludoplus.resources.gameover_turns
import net.zodac.ludoplus.resources.leaderboard_captures
import net.zodac.ludoplus.resources.leaderboard_empty_players
import net.zodac.ludoplus.resources.leaderboard_heading
import net.zodac.ludoplus.resources.game_player_pawns_left_spoken
import net.zodac.ludoplus.resources.leaderboard_heading_pawns
import net.zodac.ludoplus.resources.leaderboard_pawns
import net.zodac.ludoplus.resources.leaderboard_player
import net.zodac.ludoplus.resources.leaderboard_title
import net.zodac.ludoplus.resources.leaderboard_turns
import net.zodac.ludoplus.ui.common.EmptyStateCard
import net.zodac.ludoplus.ui.common.HoldPageCover
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

/**
 * The Leaderboard: the fastest wins for the mode and player count picked (2 to 8; at first, the mode and count New Game
 * last started), fewest turns first (or where the last standing wins, most pawns left) and then most captures, the top three ranked in gold, silver, and bronze. Where a name hasn't room beside the number columns (a large font), each row is two lines: the
 * rank and name, then its turns and captures. A screen reader reads each row as one: its rank, name, turns, and captures.
 */
@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel, onBack: () -> Unit) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val playerCount by viewModel.playerCount.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(Res.string.leaderboard_title), onBack = onBack, scrollable = true) {
        // Nothing until the board is read, so the empty card never flashes up before a board that has rows.
        val board = rows ?: return@ScreenScaffold HoldPageCover()
        val count = playerCount ?: return@ScreenScaffold HoldPageCover()
        val shownMode = mode ?: return@ScreenScaffold HoldPageCover()
        val modeName = stringResource(shownMode.displayName)
        ModeRow(options = GameMode.entries, selected = shownMode, onSelect = { viewModel.selectMode(it!!) })
        Text(
            text = stringResource(if (shownMode.ranking == LeaderboardRanking.MOST_PAWNS_LEFT) Res.string.leaderboard_heading_pawns else Res.string.leaderboard_heading, modeName),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        PlayerCountRow(options = PLAYER_COUNTS, selected = count, onSelect = { viewModel.selectPlayerCount(it!!) })
        // The same board on the online service, while signed in to one: shown with or without rows here.
        val services = LocalGameServices.current
        if (services.state.collectAsStateWithLifecycle().value is GameServicesState.SignedIn && services.canShowLeaderboardUi && shownMode.onlineLeaderboardKey != null) {
            GlobalLeaderboardButton(shownMode, count, services)
        }
        if (board.isEmpty()) {
            EmptyStateCard(pluralStringResource(Res.plurals.leaderboard_empty_players, count, modeName, count))
            return@ScreenScaffold
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // Columns while a name still has room beside the numbers; at a large font, two lines a row instead.
            val compact = maxWidth < with(LocalDensity.current) { RANK_WIDTH.toDp() + NUMBER_WIDTH.toDp() * 2 + MIN_NAME_WIDTH.toDp() } + ROW_CHROME
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    if (!compact) HeaderRow(shownMode)
                    board.forEachIndexed { index, row ->
                        if (index > 0 || !compact) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        LeaderboardRow(rank = index + 1, row = row, mode = shownMode, compact = compact)
                    }
                }
            }
        }
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
    // The rows say what each number is; the headings are for the eye.
    TableRow(
        modifier = Modifier.clearAndSetSemantics { },
        rank = { },
        name = { Text(stringResource(Res.string.leaderboard_player), style = style, color = colour) },
        turns = { Text(stringResource(if (mode.ranking == LeaderboardRanking.MOST_PAWNS_LEFT) Res.string.leaderboard_pawns else Res.string.leaderboard_turns), style = style, color = colour, textAlign = TextAlign.End) },
        captures = { Text(stringResource(Res.string.leaderboard_captures), style = style, color = colour, textAlign = TextAlign.End) },
    )
}

@Composable
private fun LeaderboardRow(rank: Int, row: GameResult, mode: GameMode, compact: Boolean) {
    // What the mode ranks by: the turns, or the pawns the winner had left (every capture of theirs took one for good).
    val pawnsLeft = (mode.rules.pawnsPerPlayer - row.timesCaptured).takeIf { mode.ranking == LeaderboardRanking.MOST_PAWNS_LEFT }
    val ranked = if (pawnsLeft != null) {
        pluralStringResource(Res.plurals.game_player_pawns_left_spoken, pawnsLeft, pawnsLeft)
    } else {
        pluralStringResource(Res.plurals.gameover_turns, row.turns, row.turns)
    }
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
        turns = { Text((pawnsLeft ?: row.turns).toString(), style = numberStyle, fontWeight = FontWeight.Bold, textAlign = TextAlign.End) },
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
