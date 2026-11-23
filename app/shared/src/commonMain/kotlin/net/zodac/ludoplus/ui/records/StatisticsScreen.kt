package net.zodac.ludoplus.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import net.zodac.ludoplus.data.records.PlayerStatistics
import net.zodac.ludoplus.data.records.RecordTotals
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.stats_best_streak
import net.zodac.ludoplus.resources.stats_captures
import net.zodac.ludoplus.resources.stats_current_streak
import net.zodac.ludoplus.resources.stats_empty
import net.zodac.ludoplus.resources.stats_empty_mode
import net.zodac.ludoplus.resources.stats_empty_mode_players
import net.zodac.ludoplus.resources.stats_empty_players
import net.zodac.ludoplus.resources.stats_fewest_turns
import net.zodac.ludoplus.resources.stats_games
import net.zodac.ludoplus.resources.stats_none
import net.zodac.ludoplus.resources.stats_pawns_home
import net.zodac.ludoplus.resources.stats_times_captured
import net.zodac.ludoplus.resources.stats_title
import net.zodac.ludoplus.resources.stats_win_rate
import net.zodac.ludoplus.resources.stats_win_rate_value
import net.zodac.ludoplus.resources.stats_wins
import net.zodac.ludoplus.ui.common.EmptyStateCard
import net.zodac.ludoplus.ui.common.HoldPageCover
import net.zodac.ludoplus.ui.common.ScreenScaffold
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.stringResource

/**
 * Statistics: the time played in the app ([LivePlayTimeLine]), the modes (all, or one) and the player counts (all, or 2
 * to 8), then a card per person, in name order, adding up every game they've played of the mode and count picked. A screen reader reads the name as a heading, then each figure with its label as one.
 */
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, onBack: () -> Unit) {
    val players by viewModel.players.collectAsStateWithLifecycle()
    val playerCount by viewModel.playerCount.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(Res.string.stats_title), onBack = onBack, scrollable = true) {
        // Every player's and none, at the top whether anyone has a card or not.
        LivePlayTimeLine(viewModel.readPlaySeconds)
        // Nothing until the figures are read, so the empty card never flashes up before players who have some.
        val all = players ?: return@ScreenScaffold HoldPageCover()
        ModeRow(options = listOf(null) + GameMode.entries, selected = mode, onSelect = viewModel::selectMode)
        PlayerCountRow(options = listOf(null) + PLAYER_COUNTS, selected = playerCount, onSelect = viewModel::selectPlayerCount)
        if (all.isEmpty()) {
            val count = playerCount
            val modeName = mode?.let { stringResource(it.displayName) }
            EmptyStateCard(
                when {
                    count == null && modeName == null -> stringResource(Res.string.stats_empty)
                    count == null -> stringResource(Res.string.stats_empty_mode, modeName!!)
                    modeName == null -> pluralStringResource(Res.plurals.stats_empty_players, count, count)
                    else -> pluralStringResource(Res.plurals.stats_empty_mode_players, count, modeName, count)
                },
            )
            return@ScreenScaffold
        }
        // A mode with no home has no race to time and brings no pawns there.
        all.forEach { PlayerCard(it, race = mode?.rules?.lastStanding != true) }
    }
}

@Composable
private fun PlayerCard(player: PlayerStatistics, race: Boolean) {
    val totals: RecordTotals = player.totals
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = player.playerName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Figure(stringResource(Res.string.stats_games), totals.gamesPlayed.toString())
            Figure(stringResource(Res.string.stats_wins), totals.gamesWon.toString())
            Figure(stringResource(Res.string.stats_win_rate), stringResource(Res.string.stats_win_rate_value, (totals.winRate * 100).roundToInt()))
            Figure(stringResource(Res.string.stats_current_streak), totals.currentWinStreak.toString())
            Figure(stringResource(Res.string.stats_best_streak), totals.bestWinStreak.toString())
            if (race) Figure(stringResource(Res.string.stats_fewest_turns), totals.fewestTurnsToWin?.toString() ?: stringResource(Res.string.stats_none))
            Figure(stringResource(Res.string.stats_captures), totals.captures.toString())
            Figure(stringResource(Res.string.stats_times_captured), totals.timesCaptured.toString())
            if (race) Figure(stringResource(Res.string.stats_pawns_home), totals.pawnsHome.toString())
        }
    }
}

/** A [label] with its [value] at the line's end, read as one. */
@Composable
private fun Figure(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}
