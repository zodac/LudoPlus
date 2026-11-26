package net.zodac.ludoplus.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import net.zodac.ludoplus.model.LeaderboardRanking
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
import net.zodac.ludoplus.ui.common.LocalLandscapePage
import net.zodac.ludoplus.ui.common.ScreenScaffold
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.stringResource

/** Between the list's parts. */
private val PART_GAP = 16.dp

/** Player cards to a row in landscape. */
private const val LANDSCAPE_COLUMNS = 2

/**
 * Statistics: the time played in the app ([LivePlayTimeLine]), the modes (one, or all) and the player counts (all, or 2
 * to 8), then a card per person, in name order, adding up every game they've played of the mode and count picked. The time
 * played and the mode's drop-down stay at the top while the rest scrolls; in landscape the player counts share the
 * drop-down's row, and the cards are two to a row. A screen reader reads the name as a heading, then
 * each figure with its label as one.
 */
@Composable
fun StatisticsScreen(viewModel: StatisticsViewModel, onBack: () -> Unit) {
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val playerCount by viewModel.playerCount.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(Res.string.stats_title), onBack = onBack, wide = true) {
        // Every player's and none, at the top whether anyone has a card or not.
        LivePlayTimeLine(viewModel.readPlaySeconds)
        // Nothing until the figures are read, so the empty card never flashes up before players who have some.
        val all = statistics ?: return@ScreenScaffold HoldPageCover()
        val onPage = page ?: return@ScreenScaffold HoldPageCover()
        if (all.isEmpty) return@ScreenScaffold EmptyStateCard(stringResource(Res.string.stats_empty))
        val landscape = LocalLandscapePage.current
        val counts = @Composable { modifier: Modifier ->
            PlayerCountRow(options = listOf(null) + PLAYER_COUNTS, selected = playerCount, onSelect = viewModel::selectPlayerCount, modifier = modifier)
        }
        if (landscape) {
            LandscapeRecordsControls(
                picker = { StatisticsModePicker(selected = onPage, onSelect = viewModel::selectPage, modifier = it) },
                counts = counts,
            )
        } else {
            StatisticsModePicker(selected = onPage, onSelect = viewModel::selectPage)
        }
        val players = all.players(playerCount, onPage.mode)
        // Only a race is timed, and a mode with no home brings no pawns there.
        val mode = onPage.mode
        val race = mode == null || mode.ranking == LeaderboardRanking.FEWEST_TURNS
        val home = mode?.rules?.lastStanding != true
        // Each page its own list, so it starts at its top. A count is only picked from the top, where its row is, and keeps
        // the list: a screen reader stays on the count it picked.
        key(onPage) {
            RecordsList {
                if (!landscape) item(key = "counts") { counts(Modifier) }
                if (players.isEmpty()) {
                    // The controls' row is the list's top in landscape, with its own gap under it.
                    item(key = "empty") { Box(modifier = Modifier.padding(top = if (landscape) 0.dp else PART_GAP)) { EmptyStateCard(emptyText(onPage.mode, playerCount)) } }
                }
                // Two cards to a row in landscape (an odd last one keeps its half); one otherwise.
                val columns = if (landscape) LANDSCAPE_COLUMNS else 1
                itemsIndexed(players.chunked(columns), key = { _, row -> "player ${row.first().playerName}" }) { index, row ->
                    Row(
                        modifier = Modifier.padding(top = if (landscape && index == 0) 0.dp else PART_GAP).height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(PART_GAP),
                    ) {
                        for (player in row) PlayerCard(player, race = race, home = home, modifier = Modifier.weight(1f).fillMaxHeight())
                        repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** That there are no games of [mode] with [playerCount] players, or of any of either for null. */
@Composable
private fun emptyText(mode: GameMode?, playerCount: Int?): String {
    val modeName = mode?.let { stringResource(it.displayName) }
    return when {
        playerCount == null && modeName == null -> stringResource(Res.string.stats_empty)
        playerCount == null -> stringResource(Res.string.stats_empty_mode, modeName!!)
        modeName == null -> pluralStringResource(Res.plurals.stats_empty_players, playerCount, playerCount)
        else -> pluralStringResource(Res.plurals.stats_empty_mode_players, playerCount, modeName, playerCount)
    }
}

@Composable
private fun PlayerCard(player: PlayerStatistics, race: Boolean, home: Boolean, modifier: Modifier = Modifier) {
    val totals: RecordTotals = player.totals
    Card(modifier = modifier.fillMaxWidth()) {
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
            if (home) Figure(stringResource(Res.string.stats_pawns_home), totals.pawnsHome.toString())
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
