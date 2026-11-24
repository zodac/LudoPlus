package net.zodac.ludoplus.ui.records

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.ModePage
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.leaderboard_title
import net.zodac.ludoplus.resources.stats_mode_all
import net.zodac.ludoplus.resources.stats_title
import net.zodac.ludoplus.ui.common.DropdownChoiceField
import net.zodac.ludoplus.ui.common.stringResource

/**
 * The drop-down at the top of the Leaderboard: every mode, with a divider after the default one. TalkBack: as
 * [DropdownChoiceField], "Leaderboard, Classic".
 */
@Composable
internal fun LeaderboardModePicker(selected: GameMode, onSelect: (GameMode) -> Unit) {
    DropdownChoiceField(
        options = GameMode.entries,
        selected = selected,
        onSelect = onSelect,
        label = stringResource(Res.string.leaderboard_title),
        optionText = { stringResource(it.displayName) },
        modifier = Modifier.fillMaxWidth(),
        dividerAfterFirst = true,
    )
}

/**
 * The drop-down at the top of Statistics: every [ModePage], with a divider after the default mode and before All Modes.
 * TalkBack: as [DropdownChoiceField], "Statistics, Classic".
 */
@Composable
internal fun StatisticsModePicker(selected: ModePage, onSelect: (ModePage) -> Unit) {
    DropdownChoiceField(
        options = ModePage.all,
        selected = selected,
        onSelect = onSelect,
        label = stringResource(Res.string.stats_title),
        optionText = { page -> stringResource(page.mode?.displayName ?: Res.string.stats_mode_all) },
        modifier = Modifier.fillMaxWidth(),
        dividerAfterFirst = true,
        dividerBeforeLast = true,
    )
}
