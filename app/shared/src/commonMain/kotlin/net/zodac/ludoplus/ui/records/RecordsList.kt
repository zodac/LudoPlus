package net.zodac.ludoplus.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.ludoplus.ui.common.LazyListScrollbar

/** The widest the Leaderboard's page (its table, and the row of controls over it) is in landscape. */
internal val LANDSCAPE_TABLE_WIDTH = 640.dp

/**
 * A records page's controls in landscape: the mode's [picker] and the player [counts] side by side on one row, so the
 * list under them has the height. (In portrait the picker has a row of its own and the counts scroll with the list.)
 */
@Composable
internal fun LandscapeRecordsControls(picker: @Composable (Modifier) -> Unit, counts: @Composable (Modifier) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CONTROLS_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        picker(Modifier.weight(1f))
        counts(Modifier.weight(1f))
    }
}

private val CONTROLS_GAP = 16.dp

/** How far into the page's margin the list's scrollbar sits, past the cards' edge (as on Achievements). */
private val SCROLLBAR_OFFSET = 12.dp

/**
 * What's under a records page's drop-down, which stays put as this scrolls: only the [content] on screen is built, so a
 * pick costs no more on a full page than on a short one. [content] is told the list's width.
 */
@Composable
internal fun ColumnScope.RecordsList(content: LazyListScope.(width: Dp) -> Unit) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val width = maxWidth
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) { content(width) }
        }
        LazyListScrollbar(listState = listState, modifier = Modifier.offset(x = SCROLLBAR_OFFSET))
    }
}
