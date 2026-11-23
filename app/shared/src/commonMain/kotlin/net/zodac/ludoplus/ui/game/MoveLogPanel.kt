package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.zodac.ludoplus.data.settings.MotionGroup
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.LogEntry
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.common_locale
import net.zodac.ludoplus.resources.game_event_all_home
import net.zodac.ludoplus.resources.game_event_capture
import net.zodac.ludoplus.resources.game_event_finished
import net.zodac.ludoplus.resources.game_event_first
import net.zodac.ludoplus.resources.game_event_home
import net.zodac.ludoplus.resources.game_event_knocked_out
import net.zodac.ludoplus.resources.game_event_move
import net.zodac.ludoplus.resources.game_event_no_move
import net.zodac.ludoplus.resources.game_event_no_move_dice
import net.zodac.ludoplus.resources.game_event_no_move_dice_tries
import net.zodac.ludoplus.resources.game_event_no_move_tries
import net.zodac.ludoplus.resources.game_event_out
import net.zodac.ludoplus.resources.game_event_portal
import net.zodac.ludoplus.resources.game_event_team_finished
import net.zodac.ludoplus.resources.game_event_three_doubles
import net.zodac.ludoplus.resources.game_event_three_sixes
import net.zodac.ludoplus.resources.game_log_line
import net.zodac.ludoplus.resources.game_log_time_pattern
import net.zodac.ludoplus.resources.game_log_title
import net.zodac.ludoplus.ui.common.LazyListScrollbar
import net.zodac.ludoplus.ui.common.formatTimestamp
import net.zodac.ludoplus.ui.common.groupReduceMotion
import net.zodac.ludoplus.ui.common.ordinal
import net.zodac.ludoplus.ui.common.spokenList
import net.zodac.ludoplus.ui.theme.color
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Below this height the log has no room for a line under its title, so it shows its newest line alone. */
private val MIN_LOG_HEIGHT = 56.dp

/**
 * The game's move log, under the die and the prompt: every move ([StartedGame.log]), newest first, back to who went
 * first (outside the tutorial), in a card that scrolls, brought back to its top as each move comes.
 * Each line starts with when it happened, "[14:05:09]", and names its players in their colours. TalkBack hears its title
 * as a heading, the lines as a list ("1 of 12") without their times, and the newest line as it comes (a polite live
 * region). Where there isn't room for the card (a short screen, a large font), the newest line alone.
 */
@Composable
internal fun MoveLogPanel(game: StartedGame, seatColours: List<PlayerColour?>, tutorial: Boolean, modifier: Modifier = Modifier) {
    val count = game.log.size + if (tutorial) 0 else 1
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (count == 0) return@BoxWithConstraints
        if (maxHeight < MIN_LOG_HEIGHT) {
            LogLine(game, seatColours, entry = game.log.lastOrNull(), newest = true, Modifier)
            return@BoxWithConstraints
        }
        val listState = rememberLazyListState()
        val glide = !groupReduceMotion(MotionGroup.MENUS)
        LaunchedEffect(count) { if (glide) listState.animateScrollToItem(0) else listState.scrollToItem(0) }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = stringResource(Res.string.game_log_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp).semantics { heading() },
                )
                Box {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, bottom = 8.dp),
                        modifier = Modifier.fillMaxSize().semantics { collectionInfo = CollectionInfo(rowCount = count, columnCount = 1) },
                    ) {
                        // Newest first: the line at index i is the log's from its end, then who went first.
                        items(count, key = { if (it == 0) "newest" else game.log.size - 1 - it }) { index ->
                            LogLine(
                                game,
                                seatColours,
                                entry = game.log.getOrNull(game.log.size - 1 - index),
                                newest = index == 0,
                                Modifier.padding(vertical = 2.dp).semantics {
                                    collectionItemInfo = CollectionItemInfo(rowIndex = index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                                },
                            )
                        }
                    }
                    LazyListScrollbar(listState, modifier = Modifier.padding(end = 4.dp, bottom = 8.dp))
                }
            }
        }
    }
}

/**
 * One line of the log: [entry]'s, or with none, who went first (at the game's start), after its time in brackets. Its
 * players are in their colours, and the [newest] is brighter and heard as it comes (a polite live region). TalkBack hears
 * the line without its time.
 */
@Composable
private fun LogLine(game: StartedGame, seatColours: List<PlayerColour?>, entry: LogEntry?, newest: Boolean, modifier: Modifier) {
    val line = if (entry == null) stringResource(Res.string.game_event_first, game.players[game.first].name) else logLine(game, entry)
    val names = if (entry == null) listOf(game.first) else logNames(game, entry)
    val pattern = stringResource(Res.string.game_log_time_pattern)
    val languageTag = stringResource(Res.string.common_locale)
    val at = entry?.atEpochMillis ?: game.startedAtEpochMillis
    val time = remember(at, pattern, languageTag) { formatTimestamp(at, pattern, languageTag) }
    // The time in a column of its own, so a line too long for one wraps under its words, not under its time.
    val shown = stringResource(Res.string.game_log_line, time, line)
    val split = shown.indexOf(line).takeIf { it >= 0 } ?: 0
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = line
            if (newest) liveRegion = LiveRegionMode.Polite
        },
    ) {
        Text(
            text = shown.substring(0, split),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = withNamesInColour(shown.substring(split), names.map { game.players[it].name to colourOf(game, seatColours, it).color }),
            style = MaterialTheme.typography.bodyMedium,
            color = if (newest) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

/** [player]'s colour in [game]: the colour at their seat, or that seat's default. */
private fun colourOf(game: StartedGame, seatColours: List<PlayerColour?>, player: Int): PlayerColour {
    val seat = game.state.players[player].seat
    return seatColours.getOrNull(seat) ?: PlayerColour.defaultFor(seat)
}

/** What [entry] says in the log: "Alex rolled 3 and had no move.", "Alex moved a pawn 4 squares.", "Alex captured 1 of Sam's pawns." */
@Composable
internal fun logLine(game: StartedGame, entry: LogEntry): String {
    val name = { index: Int -> game.players[index].name }
    return when (val event = entry.event) {
        is GameEvent.NoMove -> when {
            event.second != null && event.triesLeft > 0 ->
                pluralStringResource(Res.plurals.game_event_no_move_dice_tries, event.triesLeft, name(event.player), event.roll, event.second, event.triesLeft)
            event.second != null -> stringResource(Res.string.game_event_no_move_dice, name(event.player), event.roll, event.second)
            event.triesLeft > 0 -> pluralStringResource(Res.plurals.game_event_no_move_tries, event.triesLeft, name(event.player), event.roll, event.triesLeft)
            else -> stringResource(Res.string.game_event_no_move, name(event.player), event.roll)
        }
        is GameEvent.RollAgainLimit -> if (event.second != null) {
            stringResource(Res.string.game_event_three_doubles, name(event.player))
        } else {
            stringResource(Res.string.game_event_three_sixes, name(event.player))
        }
        is GameEvent.Moved -> {
            val move = event.move
            when {
                event.finished -> finishedLine(game, move.owner ?: event.player, entry.place)
                event.knockedOut.isNotEmpty() -> stringResource(Res.string.game_event_knocked_out, name(event.player), spokenList(event.knockedOut.map(name)))
                move.captures.isNotEmpty() -> move.captures.size.let { count ->
                    pluralStringResource(Res.plurals.game_event_capture, count, name(event.player), name(move.captures.first().player), count)
                }
                move.to == game.state.board.homeProgress -> stringResource(Res.string.game_event_home, name(event.player))
                move.from == PawnPosition.YARD_PROGRESS -> stringResource(Res.string.game_event_out, name(event.player))
                move.via != null -> (move.rolledTo - move.from).let { squares -> pluralStringResource(Res.plurals.game_event_portal, squares, name(event.player), squares) }
                else -> (move.to - move.from).let { squares -> pluralStringResource(Res.plurals.game_event_move, squares, name(event.player), squares) }
            }
        }
        // A roll waiting for its move isn't logged (MoveLog.next): the move is.
        is GameEvent.Rolled -> ""
    }
}

/** The players [entry]'s line names, to draw in their colours: the mover and anyone captured, or a placed team. */
private fun logNames(game: StartedGame, entry: LogEntry): List<Int> {
    val state = game.state
    val event = entry.event
    return when {
        event is GameEvent.Moved && event.finished -> (event.move.owner ?: event.player).let { owner ->
            state.players.indices.filter { it == owner || state.rules.arePartners(owner, it) }
        }
        event is GameEvent.Moved -> listOf(event.player) + event.move.captures.map { it.player }.distinct()
        else -> listOf(event.player)
    }
}

/**
 * What a move that brought [player]'s last pawn home says: their [place] then ("Alex has every pawn home, in 1st place");
 * in a team game, their team's once it was placed ("Alex and Sam are home, in 1st place"), and before that only that
 * they're home.
 */
@Composable
private fun finishedLine(game: StartedGame, player: Int, place: Int?): String {
    val state = game.state
    if (place == null) return stringResource(Res.string.game_event_all_home, game.players[player].name)
    val team = state.players.indices.filter { it == player || state.rules.arePartners(player, it) }
    return if (team.size == 1) {
        stringResource(Res.string.game_event_finished, game.players[player].name, ordinal(place))
    } else {
        stringResource(Res.string.game_event_team_finished, spokenList(team.map { game.players[it].name }), ordinal(place))
    }
}
