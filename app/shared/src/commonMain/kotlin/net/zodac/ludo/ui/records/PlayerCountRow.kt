package net.zodac.ludo.ui.records

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.zodac.ludo.model.BoardGeometry
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.setup_player_count_spoken
import net.zodac.ludo.resources.stats_count_all
import net.zodac.ludo.resources.stats_count_all_spoken
import net.zodac.ludo.resources.stats_mode_all_spoken
import net.zodac.ludo.ui.common.FittedSegmentedChoiceRow
import net.zodac.ludo.ui.common.pluralStringResource
import net.zodac.ludo.ui.common.stringResource

/** Every player count a game can have. */
internal val PLAYER_COUNTS: List<Int?> = (BoardGeometry.MIN_PLAYERS..BoardGeometry.MAX_PLAYERS).toList()

/**
 * Which player count's records a page shows: a segment a count, "4" said as "4 players", and null, where offered, as
 * "All" ("All games").
 */
@Composable
internal fun PlayerCountRow(options: List<Int?>, selected: Int?, onSelect: (Int?) -> Unit) {
    FittedSegmentedChoiceRow(
        options = options,
        selected = selected,
        onSelect = onSelect,
        label = { count -> count?.toString() ?: stringResource(Res.string.stats_count_all) },
        compactLabel = { count -> count?.toString() ?: stringResource(Res.string.stats_count_all) },
        spokenLabel = { count -> if (count == null) stringResource(Res.string.stats_count_all_spoken) else pluralStringResource(Res.plurals.setup_player_count_spoken, count, count) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Which game mode's records a page shows: a segment a mode ([GameMode.entries]), by its name, and null, where offered,
 * as "All" ("All modes").
 */
@Composable
internal fun ModeRow(options: List<GameMode?>, selected: GameMode?, onSelect: (GameMode?) -> Unit) {
    FittedSegmentedChoiceRow(
        options = options,
        selected = selected,
        onSelect = onSelect,
        label = { mode -> mode?.let { stringResource(it.displayName) } ?: stringResource(Res.string.stats_count_all) },
        spokenLabel = { mode -> mode?.let { stringResource(it.displayName) } ?: stringResource(Res.string.stats_mode_all_spoken) },
        modifier = Modifier.fillMaxWidth(),
    )
}
