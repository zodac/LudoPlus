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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.ludoplus.data.settings.MotionGroup
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.LogEntry
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.game.PowerUpEffect
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.common_locale
import net.zodac.ludoplus.resources.game_event_all_home
import net.zodac.ludoplus.resources.game_event_and
import net.zodac.ludoplus.resources.game_event_bounce
import net.zodac.ludoplus.resources.game_event_call_up
import net.zodac.ludoplus.resources.game_event_call_up_banked
import net.zodac.ludoplus.resources.game_event_capture
import net.zodac.ludoplus.resources.game_event_double
import net.zodac.ludoplus.resources.game_event_finished
import net.zodac.ludoplus.resources.game_event_first
import net.zodac.ludoplus.resources.game_event_haunted
import net.zodac.ludoplus.resources.game_event_home
import net.zodac.ludoplus.resources.game_event_knocked_out
import net.zodac.ludoplus.resources.game_event_move
import net.zodac.ludoplus.resources.game_event_no_move
import net.zodac.ludoplus.resources.game_event_no_move_dice
import net.zodac.ludoplus.resources.game_event_no_move_dice_tries
import net.zodac.ludoplus.resources.game_event_no_move_tries
import net.zodac.ludoplus.resources.game_event_out
import net.zodac.ludoplus.resources.game_event_portal
import net.zodac.ludoplus.resources.game_event_power_up
import net.zodac.ludoplus.resources.game_event_recalled
import net.zodac.ludoplus.resources.game_event_swap
import net.zodac.ludoplus.resources.game_event_team_finished
import net.zodac.ludoplus.resources.game_event_three_doubles
import net.zodac.ludoplus.resources.game_event_vanished
import net.zodac.ludoplus.resources.game_event_three_sixes
import net.zodac.ludoplus.resources.game_log_line
import net.zodac.ludoplus.resources.game_log_start
import net.zodac.ludoplus.resources.game_log_time_pattern
import net.zodac.ludoplus.resources.game_log_title
import net.zodac.ludoplus.resources.game_log_turn
import net.zodac.ludoplus.ui.common.LazyListScrollbar
import net.zodac.ludoplus.ui.common.formatTimestamp
import net.zodac.ludoplus.ui.common.groupReduceMotion
import net.zodac.ludoplus.ui.common.ordinal
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.spokenList
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.theme.color

private val LOG_LINE_PADDING = 2.dp
private val LOG_TITLE_TOP = 8.dp
private val LOG_TITLE_BOTTOM = 6.dp
private val LOG_BOTTOM = 8.dp
private val LOG_HEADING_PADDING = 4.dp

/** Over the first line where the log has no title. */
private val UNTITLED_TOP = 4.dp

/** How tall the move log's card is at its shortest: no title, the newest turn's heading, and a line under it. */
@Composable
internal fun shortestLogHeight(): Dp = UNTITLED_TOP * 2 + headedLinesHeight(1)

/** How tall one of the log's lines is, where it doesn't wrap. */
@Composable
private fun logLineHeight(): Dp = with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() } + LOG_LINE_PADDING * 2

/** How tall the log's title is, and one of its headings without its padding. */
@Composable
private fun logLabelHeight(): Dp = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }

/** How tall the log's card is with its title, a heading, and two lines under it. */
@Composable
private fun titledLogHeight(): Dp = LOG_TITLE_TOP + logLabelHeight() + LOG_TITLE_BOTTOM + headedLinesHeight(2)

/** How tall a heading and [lines] lines under it are. */
@Composable
private fun headedLinesHeight(lines: Int): Dp = logLabelHeight() + LOG_HEADING_PADDING * 2 + logLineHeight() * lines

/** One row of the log's list. */
private sealed interface LogRow {
    /** The heading over [turn]'s lines, or with none, over who went first. */
    data class Heading(val turn: Int?) : LogRow

    /** The line [index]th from the newest: [entry]'s, or with none, who went first. */
    data class Line(val index: Int, val entry: LogEntry?) : LogRow
}

/**
 * [log]'s rows, newest first, each turn's lines under its heading, and where the log goes back to who went [first], that under
 * one of its own. Lines whose turn isn't known have none.
 */
private fun logRows(log: List<LogEntry>, first: Boolean): List<LogRow> = buildList {
    log.asReversed().forEachIndexed { index, entry ->
        if (entry.turn != null && (index == 0 || log[log.size - index].turn != entry.turn)) add(LogRow.Heading(entry.turn))
        add(LogRow.Line(index, entry))
    }
    if (first) {
        add(LogRow.Heading(turn = null))
        add(LogRow.Line(log.size, entry = null))
    }
}

/**
 * The game's move log, under the die and the prompt: every move ([StartedGame.log]), newest first, back to who went
 * first (outside the tutorial), in a card that scrolls, brought back to its top as each move comes. Each turn's moves, one
 * or more for every player, are under a heading ("Turn 3").
 * Each line starts with when it happened, "[14:05:09]", and names its players in their colours. TalkBack hears its title
 * and each turn's as headings, the lines as a list ("1 of 12") without their times, and the newest line as it comes (a
 * polite live region). Where there isn't room for the title, the newest turn's heading, and two lines (a short screen, a
 * large font), the card has no title; where there isn't room for a line under that heading, it isn't shown; where there
 * isn't room for a line, or the log isn't [shown] (Settings' "Move log"), it isn't drawn, and TalkBack still hears the newest.
 */
@Composable
internal fun MoveLogPanel(game: StartedGame, seatColours: List<PlayerColour?>, tutorial: Boolean, shown: Boolean, modifier: Modifier = Modifier) {
    val count = game.log.size + if (tutorial) 0 else 1
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (count == 0) return@BoxWithConstraints
        if (!shown || maxHeight < UNTITLED_TOP * 2 + logLineHeight()) {
            LogLine(game, seatColours, entry = game.log.lastOrNull(), newest = true, Modifier.drawWithContent { })
            return@BoxWithConstraints
        }
        val titled = maxHeight >= titledLogHeight()
        val headed = maxHeight >= shortestLogHeight()
        val rows = remember(game.log, tutorial, headed) { logRows(game.log, first = !tutorial).let { if (headed) it else it.dropWhile { row -> row is LogRow.Heading } } }
        val listState = rememberLazyListState()
        val glide = !groupReduceMotion(MotionGroup.MENUS)
        LaunchedEffect(count) { if (glide) listState.animateScrollToItem(0) else listState.scrollToItem(0) }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.padding(top = if (titled) LOG_TITLE_TOP else 0.dp)) {
                if (titled) {
                    Text(
                        text = stringResource(Res.string.game_log_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = LOG_TITLE_BOTTOM).semantics { heading() },
                    )
                }
                Box {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = if (titled) 0.dp else UNTITLED_TOP, bottom = if (titled) LOG_BOTTOM else UNTITLED_TOP),
                        modifier = Modifier.fillMaxSize().semantics { collectionInfo = CollectionInfo(rowCount = count, columnCount = 1) },
                    ) {
                        // The newest line and the heading over it keep their keys, so the list stays at its top as they change.
                        itemsIndexed(
                            rows,
                            key = { position, row ->
                                when (row) {
                                    is LogRow.Heading -> if (position == 0) "newest turn" else "turn ${row.turn}"
                                    is LogRow.Line -> if (row.index == 0) "newest" else game.log.size - 1 - row.index
                                }
                            },
                            contentType = { _, row -> row is LogRow.Heading },
                        ) { _, row ->
                            when (row) {
                                is LogRow.Heading -> LogHeading(row.turn)
                                is LogRow.Line -> LogLine(
                                    game,
                                    seatColours,
                                    entry = row.entry,
                                    newest = row.index == 0,
                                    Modifier.padding(vertical = LOG_LINE_PADDING).semantics {
                                        collectionItemInfo = CollectionItemInfo(rowIndex = row.index, rowSpan = 1, columnIndex = 0, columnSpan = 1)
                                    },
                                )
                            }
                        }
                    }
                    LazyListScrollbar(listState, modifier = Modifier.padding(end = 4.dp, top = if (titled) 0.dp else UNTITLED_TOP, bottom = if (titled) LOG_BOTTOM else UNTITLED_TOP))
                }
            }
        }
    }
}

/** The heading over [turn]'s lines ("Turn 3"), or with none, over who went first ("Start"). TalkBack hears it as a heading. */
@Composable
private fun LogHeading(turn: Int?) {
    Text(
        text = if (turn == null) stringResource(Res.string.game_log_start) else stringResource(Res.string.game_log_turn, turn),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = LOG_HEADING_PADDING).semantics { heading() },
    )
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
    val line = eventLine(game, entry)
    if (entry.vanished > 0) return stringResource(Res.string.game_event_and, line, pluralStringResource(Res.plurals.game_event_vanished, entry.vanished, entry.vanished))
    if (entry.haunted.isEmpty()) return line
    val names = entry.haunted.map { it.player }.distinct().map { game.players[it].name }
    return stringResource(Res.string.game_event_and, line, stringResource(Res.string.game_event_haunted, spokenList(names)))
}

/** What [entry]'s event did, before any ghost came or square vanished. */
@Composable
private fun eventLine(game: StartedGame, entry: LogEntry): String {
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
            val effect = event.effect
            val line = movedLine(game, entry, event)
            // A swap made once its pawn has landed is all its event did: the landing is the line before.
            if (effect != null && event.move.swap != null && event.move.squares == 0) return effectLine(game, event, effect)
            val taken = if (effect == null || event.finished) line else stringResource(Res.string.game_event_and, line, effectLine(game, event, effect))
            var told = taken
            for ((player, pawns) in event.recalled.groupBy { it.player }) {
                told = stringResource(Res.string.game_event_and, told, pluralStringResource(Res.plurals.game_event_recalled, pawns.size, game.players[player].name, pawns.size))
            }
            told
        }
        // A roll waiting for its move isn't logged (MoveLog.next): the move is.
        is GameEvent.Rolled -> ""
    }
}

/** What [event]'s move did, before any power-up it ended on. */
@Composable
private fun movedLine(game: StartedGame, entry: LogEntry, event: GameEvent.Moved): String {
    val name = { index: Int -> game.players[index].name }
    val move = event.move
    val squares = move.squares
    return when {
        event.finished -> finishedLine(game, move.owner ?: event.player, entry.place)
        event.knockedOut.isNotEmpty() -> stringResource(Res.string.game_event_knocked_out, name(event.player), spokenList(event.knockedOut.map(name)))
        move.captures.isNotEmpty() -> captureLine(game, event.player, move.captures)
        move.to == game.state.board.homeProgress -> stringResource(Res.string.game_event_home, name(event.player))
        move.from == PawnPosition.YARD_PROGRESS -> stringResource(Res.string.game_event_out, name(event.player))
        move.via != null -> pluralStringResource(Res.plurals.game_event_portal, squares, name(event.player), squares)
        move.brokenShields.isNotEmpty() ->
            pluralStringResource(Res.plurals.game_event_bounce, squares, name(event.player), squares, name(move.brokenShields.first().player))
        else -> pluralStringResource(Res.plurals.game_event_move, squares, name(event.player), squares)
    }
}

@Composable
private fun captureLine(game: StartedGame, player: Int, captures: List<PawnRef>): String =
    pluralStringResource(Res.plurals.game_event_capture, captures.size, game.players[player].name, game.players[captures.first().player].name, captures.size)

/** What the power-up [event]'s move ended on did, as [effect] says: "Alex swapped places with Sam.", and a capture it made. */
@Composable
private fun effectLine(game: StartedGame, event: GameEvent.Moved, effect: PowerUpEffect): String {
    val name = game.players[event.player].name
    val swapped = effect.swapped
    val line = when {
        effect.powerUp == PowerUp.DOUBLE -> pluralStringResource(Res.plurals.game_event_double, PowerUp.DOUBLED_ROLLS, name, PowerUp.DOUBLED_ROLLS)
        effect.powerUp == PowerUp.CALL_UP && effect.calledUp != null -> stringResource(Res.string.game_event_call_up, name)
        effect.banked -> stringResource(Res.string.game_event_call_up_banked, name)
        swapped != null -> stringResource(Res.string.game_event_swap, name, game.players[swapped.player].name)
        else -> stringResource(Res.string.game_event_power_up, name, stringResource(effect.powerUp.title))
    }
    return if (effect.captures.isEmpty()) line else stringResource(Res.string.game_event_and, line, captureLine(game, event.player, effect.captures))
}

/** The players [entry]'s line names, to draw in their colours: the mover and anyone captured, or a placed team. */
private fun logNames(game: StartedGame, entry: LogEntry): List<Int> {
    val state = game.state
    val event = entry.event
    val haunted = entry.haunted.map { it.player }
    val named = when {
        event is GameEvent.Moved && event.finished -> (event.move.owner ?: event.player).let { owner ->
            state.players.indices.filter { it == owner || state.rules.arePartners(owner, it) }
        }
        event is GameEvent.Moved -> (
            listOf(event.player) + event.allCaptures.map { it.player } + event.move.brokenShields.map { it.player } + listOfNotNull(event.effect?.swapped?.player)
        ).distinct()
        else -> listOf(event.player)
    }
    return (named + haunted).distinct()
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
