package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import net.zodac.ludoplus.resources.game_event_out_free
import net.zodac.ludoplus.resources.game_event_pawn
import net.zodac.ludoplus.resources.game_event_pawn_of
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
import net.zodac.ludoplus.resources.game_log_round
import net.zodac.ludoplus.ui.common.LazyListScrollbar
import net.zodac.ludoplus.ui.common.formatTimestamp
import net.zodac.ludoplus.ui.common.groupReduceMotion
import net.zodac.ludoplus.ui.common.ordinal
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.spokenList
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.theme.color

private val LOG_LINE_PADDING = 2.dp
private val LOG_HEADING_PADDING = 4.dp

/** Over the first row of the log, and under its last. */
private val LOG_PADDING = 4.dp

/** How tall the move log's card is at its shortest: the newest round's heading, and a line under it. */
@Composable
internal fun shortestLogHeight(): Dp = LOG_PADDING * 2 + logHeadingHeight() + logLineHeight()

/** How tall one of the log's lines is, where it doesn't wrap. */
@Composable
private fun logLineHeight(): Dp = with(LocalDensity.current) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() } + LOG_LINE_PADDING * 2

/** How tall one of the log's headings is. */
@Composable
private fun logHeadingHeight(): Dp = with(LocalDensity.current) { MaterialTheme.typography.labelMedium.lineHeight.toDp() } + LOG_HEADING_PADDING * 2

/** One row of the log's list. */
private sealed interface LogRow {
    /** The heading over [round]'s lines, or with none, over who went first. */
    data class Heading(val round: Int?) : LogRow

    /** The line [index]th from the newest: [entry]'s, or with none, who went first. */
    data class Line(val index: Int, val entry: LogEntry?) : LogRow
}

/**
 * [log]'s rows, newest first, each round's lines under its heading, and where the log goes back to who went [first], that under
 * one of its own. A line's round is which of its player's turns it was in ([LogEntry.turn]); lines whose turn isn't known have none.
 */
private fun logRows(log: List<LogEntry>, first: Boolean): List<LogRow> = buildList {
    log.asReversed().forEachIndexed { index, entry ->
        if (entry.turn != null && (index == 0 || log[log.size - index].turn != entry.turn)) add(LogRow.Heading(round = entry.turn))
        add(LogRow.Line(index, entry))
    }
    if (first) {
        add(LogRow.Heading(round = null))
        add(LogRow.Line(log.size, entry = null))
    }
}

/**
 * The game's move log, under the die and the prompt: every move ([StartedGame.log]), newest first, back to who went
 * first (outside the tutorial), in a card that scrolls, brought back to its top as each move comes. Each round's moves, one
 * or more for every player, are under a heading ("Round 3").
 * Each line starts with when it happened, "[14:05:09]", and names its players in their colours. TalkBack hears each round's
 * heading as one, the lines as a list ("1 of 12") without their times, and the newest line as it comes (a polite live
 * region). Where there isn't room for a line under the newest round's heading (a short screen, a large font), that heading
 * isn't shown; where there isn't room for a line, or the log isn't [shown] (Settings' "Move log"), it isn't drawn, and
 * TalkBack still hears the newest.
 */
@Composable
internal fun MoveLogPanel(game: StartedGame, seatColours: List<PlayerColour?>, tutorial: Boolean, shown: Boolean, modifier: Modifier = Modifier) {
    val count = game.log.size + if (tutorial) 0 else 1
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (count == 0) return@BoxWithConstraints
        if (!shown || maxHeight < LOG_PADDING * 2 + logLineHeight()) {
            LogLine(game, seatColours, entry = game.log.lastOrNull(), newest = true, Modifier.drawWithContent { })
            return@BoxWithConstraints
        }
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
            Box {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = LOG_PADDING, bottom = LOG_PADDING),
                    modifier = Modifier.fillMaxSize().semantics { collectionInfo = CollectionInfo(rowCount = count, columnCount = 1) },
                ) {
                    // The newest line and the heading over it keep their keys, so the list stays at its top as they change.
                    itemsIndexed(
                        rows,
                        key = { position, row ->
                            when (row) {
                                is LogRow.Heading -> if (position == 0) "newest round" else "round ${row.round}"
                                is LogRow.Line -> if (row.index == 0) "newest" else game.log.size - 1 - row.index
                            }
                        },
                        contentType = { _, row -> row is LogRow.Heading },
                    ) { _, row ->
                        when (row) {
                            is LogRow.Heading -> LogHeading(row.round)
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
                LazyListScrollbar(listState, modifier = Modifier.padding(end = 4.dp, top = LOG_PADDING, bottom = LOG_PADDING))
            }
        }
    }
}

/** The heading over [round]'s lines ("Round 3"), or with none, over who went first ("Start"). TalkBack hears it as a heading. */
@Composable
private fun LogHeading(round: Int?) {
    Text(
        text = if (round == null) stringResource(Res.string.game_log_start) else stringResource(Res.string.game_log_round, round),
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

/** What [entry] says in the log: "Alex rolled 3 and had no move.", "Alex's Pawn 2 moved 4 tiles.", "Alex captured Sam's Pawn 1 with Pawn 2." */
@Composable
internal fun logLine(game: StartedGame, entry: LogEntry): String {
    val line = eventLine(game, entry)
    if (entry.vanished > 0) return stringResource(Res.string.game_event_and, line, pluralStringResource(Res.plurals.game_event_vanished, entry.vanished, entry.vanished))
    if (entry.haunted.isEmpty()) return line
    return stringResource(Res.string.game_event_and, line, stringResource(Res.string.game_event_haunted, pawnsNamed(game, entry.haunted)))
}

/** [pawns] as a list, the first of each player's with their name: "Sam's Pawn 1, Pawn 3, and Jo's Pawn 2". */
@Composable
private fun pawnsNamed(game: StartedGame, pawns: List<PawnRef>): String {
    val named = pawns.groupBy { it.player }.flatMap { (player, own) ->
        own.mapIndexed { index, pawn ->
            if (index == 0) stringResource(Res.string.game_event_pawn_of, game.players[player].name, pawn.pawn + 1) else pawnNamed(game, player, pawn)
        }
    }
    return spokenList(named)
}

/** [pawn] in a line that starts with [player]'s name: "Pawn 2", or where it's a partner's, "Sam's Pawn 2". */
@Composable
private fun pawnNamed(game: StartedGame, player: Int, pawn: PawnRef): String =
    if (pawn.player == player) stringResource(Res.string.game_event_pawn, pawn.pawn + 1) else pawnsNamed(game, listOf(pawn))

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
                val brought = spokenList(pawns.map { pawnNamed(game, player, it) })
                told = stringResource(Res.string.game_event_and, told, pluralStringResource(Res.plurals.game_event_recalled, pawns.size, game.players[player].name, brought))
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
    val mover = event.mover
    val pawn = pawnNamed(game, event.player, mover)
    // What starts the line, with its player's name.
    val named = pawnsNamed(game, listOf(mover))
    return when {
        event.finished -> finishedLine(game, move.owner ?: event.player, entry.place)
        event.knockedOut.isNotEmpty() -> stringResource(Res.string.game_event_knocked_out, name(event.player), spokenList(event.knockedOut.map(name)), pawn)
        move.free -> {
            val rolls = game.state.rules.pityRelease ?: 0
            val out = pluralStringResource(Res.plurals.game_event_out_free, rolls, name(event.player), pawn, rolls)
            if (move.captures.isEmpty()) out else stringResource(Res.string.game_event_and, out, captureLine(game, event.player, move.captures, by = mover))
        }
        move.captures.isNotEmpty() -> captureLine(game, event.player, move.captures, by = mover)
        move.to == game.state.board.homeProgress -> stringResource(Res.string.game_event_home, name(event.player), pawn)
        move.from == PawnPosition.YARD_PROGRESS -> stringResource(Res.string.game_event_out, name(event.player), pawn)
        move.via != null -> pluralStringResource(Res.plurals.game_event_portal, squares, named, squares)
        move.brokenShields.isNotEmpty() ->
            pluralStringResource(Res.plurals.game_event_bounce, squares, named, squares, name(move.brokenShields.first().player))
        else -> pluralStringResource(Res.plurals.game_event_move, squares, named, squares)
    }
}

/** What [player]'s capturing [captures] with the pawn [by] says. */
@Composable
private fun captureLine(game: StartedGame, player: Int, captures: List<PawnRef>, by: PawnRef): String =
    stringResource(Res.string.game_event_capture, game.players[player].name, pawnsNamed(game, captures), pawnNamed(game, player, by))

/** What the power-up [event]'s move ended on did, as [effect] says: "Alex swapped Pawn 1 with Sam's Pawn 3.", and a capture it made. */
@Composable
private fun effectLine(game: StartedGame, event: GameEvent.Moved, effect: PowerUpEffect): String {
    val name = game.players[event.player].name
    val swapped = effect.swapped
    val mover = event.mover
    val calledUp = effect.calledUp?.let { PawnRef(mover.player, it) }
    val line = when {
        effect.powerUp == PowerUp.DOUBLE -> pluralStringResource(Res.plurals.game_event_double, PowerUp.DOUBLED_ROLLS, name, PowerUp.DOUBLED_ROLLS)
        effect.powerUp == PowerUp.CALL_UP && calledUp != null -> stringResource(Res.string.game_event_call_up, name, pawnNamed(game, event.player, calledUp))
        effect.banked -> stringResource(Res.string.game_event_call_up_banked, name)
        swapped != null -> stringResource(Res.string.game_event_swap, name, pawnNamed(game, event.player, mover), pawnsNamed(game, listOf(swapped)))
        else -> stringResource(Res.string.game_event_power_up, name, stringResource(effect.powerUp.title))
    }
    return if (effect.captures.isEmpty()) line else stringResource(Res.string.game_event_and, line, captureLine(game, event.player, effect.captures, by = calledUp ?: mover))
}

/** The players [entry]'s line names, to draw in their colours: the mover, a partner whose pawn it moved, and anyone captured, or a placed team. */
private fun logNames(game: StartedGame, entry: LogEntry): List<Int> {
    val state = game.state
    val event = entry.event
    val haunted = entry.haunted.map { it.player }
    val named = when {
        event is GameEvent.Moved && event.finished -> (event.move.owner ?: event.player).let { owner ->
            state.players.indices.filter { it == owner || state.rules.arePartners(owner, it) }
        }
        event is GameEvent.Moved -> (
            listOf(event.player, event.mover.player) + event.allCaptures.map { it.player } + event.move.brokenShields.map { it.player } + listOfNotNull(event.effect?.swapped?.player)
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
