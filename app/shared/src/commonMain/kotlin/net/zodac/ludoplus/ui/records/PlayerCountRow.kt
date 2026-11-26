package net.zodac.ludoplus.ui.records

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.setup_player_count_spoken
import net.zodac.ludoplus.resources.stats_count_all
import net.zodac.ludoplus.resources.stats_count_all_spoken
import net.zodac.ludoplus.ui.common.FittedSegmentedChoiceRow
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.stringResource

/** Every player count a game can have. */
internal val PLAYER_COUNTS: List<Int?> = (BoardGeometry.MIN_PLAYERS..BoardGeometry.MAX_PLAYERS).toList()

/**
 * Which player count's records a page shows: a segment a count, "4" said as "4 players", and null, where offered, as
 * "All" ("All games").
 */
@Composable
internal fun PlayerCountRow(options: List<Int?>, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    FittedSegmentedChoiceRow(
        options = options,
        selected = selected,
        onSelect = onSelect,
        label = { count -> count?.toString() ?: stringResource(Res.string.stats_count_all) },
        compactLabel = { count -> count?.toString() ?: stringResource(Res.string.stats_count_all) },
        spokenLabel = { count -> if (count == null) stringResource(Res.string.stats_count_all_spoken) else pluralStringResource(Res.plurals.setup_player_count_spoken, count, count) },
        modifier = modifier.fillMaxWidth(),
    )
}
