package net.zodac.ludo.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.ludo.game.GameEvent
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.Move
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.game_event_capture
import net.zodac.ludo.resources.game_event_finished
import net.zodac.ludo.resources.game_event_first
import net.zodac.ludo.resources.game_event_home
import net.zodac.ludo.resources.game_event_no_move
import net.zodac.ludo.resources.game_event_three_sixes
import net.zodac.ludo.resources.game_over_menu
import net.zodac.ludo.resources.game_over_winner
import net.zodac.ludo.resources.game_player_pawns_home_spoken
import net.zodac.ludo.resources.game_player_place
import net.zodac.ludo.resources.game_player_turn_spoken
import net.zodac.ludo.resources.game_prompt_cpu_move
import net.zodac.ludo.resources.game_prompt_cpu_roll
import net.zodac.ludo.resources.game_prompt_move
import net.zodac.ludo.resources.game_prompt_roll
import net.zodac.ludo.data.settings.MotionGroup
import net.zodac.ludo.ui.common.BackHandler
import net.zodac.ludo.ui.common.groupReduceMotion
import net.zodac.ludo.ui.common.ScreenScaffold
import net.zodac.ludo.ui.common.joinClauses
import net.zodac.ludo.ui.common.ordinal
import net.zodac.ludo.ui.common.pluralStringResource
import net.zodac.ludo.ui.common.stringResource
import net.zodac.ludo.ui.theme.color

/** The narrowest the side panel beside the board gets in landscape. */
private val SIDE_PANEL_WIDTH = 280.dp

/**
 * The game screen: the players across the top, the board, and the die with a line saying what just happened and what
 * to do now. In landscape the players, die, and lines sit beside the board.
 */
@Composable
fun GameScreen(viewModel: GameViewModel, onBackToMenu: () -> Unit) {
    val game by viewModel.game.collectAsStateWithLifecycle()
    val started = game ?: return
    val confirmBeforeLeaving by viewModel.confirmBeforeLeavingGame.collectAsStateWithLifecycle()
    val leaveConfirmation = LocalLeaveGameConfirmation.current
    // Back (the arrow or the gesture) leaves for the menu, not the setup form, asking first while the game is on.
    val leave = {
        if (!started.state.isFinished && confirmBeforeLeaving) leaveConfirmation.request(onBackToMenu) else onBackToMenu()
    }
    BackHandler(onBack = leave)
    LifecycleResumeEffect(viewModel) {
        viewModel.setForeground(true)
        onPauseOrDispose { viewModel.setForeground(false) }
    }
    val pacing = GamePacing(
        pawnsAnimated = !groupReduceMotion(MotionGroup.PAWN_MOVE),
        dieAnimated = !groupReduceMotion(MotionGroup.DIE_ROLL),
    )
    LaunchedEffect(pacing) { viewModel.setPacing(pacing) }
    val held = leaveConfirmation.isShowing
    LaunchedEffect(held) { viewModel.setHeld(held) }
    GameFeedback(started.state)
    GameContent(started, onRoll = viewModel::roll, onMove = viewModel::move, onBack = leave, onBackToMenu = onBackToMenu)
}

/** The sounds and vibrations for each thing that happens: the die landing, a pawn moved, a capture, and the win. */
@Composable
private fun GameFeedback(state: GameState) {
    val sounds = rememberSoundEffects()
    val haptics = rememberGameHaptics()
    val event = state.lastEvent
    val firstSeen = remember { event }
    LaunchedEffect(event) {
        if (event == null || event === firstSeen) return@LaunchedEffect
        when (event) {
            is GameEvent.Rolled, is GameEvent.NoMove, is GameEvent.RollAgainLimit -> sounds.playDieLanding()
            is GameEvent.Moved -> {
                sounds.playMove()
                if (event.move.captures.isEmpty()) haptics.playMoveTick() else haptics.playCaptureBuzz()
                if (state.isFinished) sounds.playCelebration()
            }
        }
    }
}

/**
 * The game screen's content for [started]: rolling calls [onRoll], a tap on a movable pawn [onMove], the back arrow
 * [onBack], and the finished game's button [onBackToMenu].
 */
@Composable
fun GameContent(started: StartedGame, onRoll: () -> Unit, onMove: (pawn: Int) -> Unit, onBack: () -> Unit, onBackToMenu: () -> Unit) {
    val seatColours = List(started.state.board.seats) { seat -> started.players.getOrNull(seat)?.colour }
    val phase = started.state.phase
    val moves = if (phase is TurnPhase.AwaitingMove && !started.currentIsCpu) phase.moves else emptyList()
    ScreenScaffold(title = stringResource(started.mode.displayName), onBack = onBack, driftingDice = false, wide = true) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val (width, height) = maxWidth to maxHeight
            val board = @Composable { modifier: Modifier ->
                LudoBoard(
                    state = started.state,
                    seatColours = seatColours,
                    moves = moves,
                    onPawn = onMove,
                    modifier = modifier.clip(RoundedCornerShape(8.dp)),
                )
            }
            if (width > height) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    board(Modifier.size(minOf(height, width - SIDE_PANEL_WIDTH)))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlayerCards(started, columns = 2)
                        TurnPanel(started, moves, seatColours, onRoll, onMove, onBackToMenu, dieSize = 64.dp)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PlayerCards(started, columns = 2)
                    board(Modifier.fillMaxWidth())
                    TurnPanel(started, moves, seatColours, onRoll, onMove, onBackToMenu, dieSize = 72.dp)
                }
            }
        }
    }
}

/** The players, [columns] to a row, in seat order: the one whose turn it is outlined in their colour. */
@Composable
private fun PlayerCards(game: StartedGame, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        game.players.indices.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { index -> PlayerCard(game, index, Modifier.weight(1f)) }
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PlayerCard(game: StartedGame, index: Int, modifier: Modifier) {
    val player = game.players[index]
    val state = game.state
    val side = state.players[index]
    val colour = player.colour.color
    val current = state.current == index && !state.isFinished
    val home = side.pawnsHome(state.board)
    val place = state.finishingOrder.indexOf(index).takeIf { it >= 0 && state.players[index].isFinished(state.board) }
    val placeText = place?.let { stringResource(Res.string.game_player_place, ordinal(it + 1)) }
    val spoken = joinClauses(
        listOfNotNull(
            pluralStringResource(Res.plurals.game_player_pawns_home_spoken, home, home),
            if (current) stringResource(Res.string.game_player_turn_spoken) else null,
            placeText,
        ),
    )
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { stateDescription = spoken },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (current) BorderStroke(2.dp, colour) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(color = colour, shape = CircleShape, modifier = Modifier.size(14.dp)) {}
            Text(
                text = player.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (player.type == PlayerType.AI) CpuPlayerIcon(size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (placeText != null) {
                Text(placeText, style = MaterialTheme.typography.labelMedium, color = colour, modifier = Modifier.clearAndSetSemantics { })
            } else {
                PawnsHomeDots(home, state.rules.pawnsPerPlayer, colour, Modifier.clearAndSetSemantics { })
            }
        }
    }
}

/** A dot per pawn: filled in [colour] for each of the [home] that are home. Drawn only; the card says the count. */
@Composable
private fun PawnsHomeDots(home: Int, total: Int, colour: Color, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(total) { i ->
            Surface(
                shape = CircleShape,
                color = if (i < home) colour else Color.Transparent,
                border = BorderStroke(1.dp, colour.copy(alpha = 0.7f)),
                modifier = Modifier.size(7.dp),
            ) {}
        }
    }
}

/** The die, and the lines saying what just happened and what to do now; once the game is over, the winner. */
@Composable
private fun TurnPanel(
    game: StartedGame,
    moves: List<Move>,
    seatColours: List<PlayerColour?>,
    onRoll: () -> Unit,
    onMove: (Int) -> Unit,
    onBackToMenu: () -> Unit,
    dieSize: Dp,
) {
    val state = game.state
    val name = { index: Int -> game.players[index].name }
    if (state.isFinished) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.game_over_winner, name(state.finishingOrder.first())),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Button(onClick = onBackToMenu) { Text(stringResource(Res.string.game_over_menu)) }
        }
        return
    }
    val phase = state.phase
    val roll = (phase as? TurnPhase.AwaitingMove)?.roll ?: lastRoll(state.lastEvent)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GameDie(
            value = roll,
            roll = state.lastEvent.takeIf { it !is GameEvent.Moved },
            canRoll = phase == TurnPhase.AwaitingRoll && !game.currentIsCpu,
            onRoll = onRoll,
            modifier = Modifier.size(dieSize),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusLines(game, moves, seatColours, onMove)
        }
    }
}

@Composable
private fun ColumnScope.StatusLines(game: StartedGame, moves: List<Move>, seatColours: List<PlayerColour?>, onMove: (Int) -> Unit) {
    val state = game.state
    val name = { index: Int -> game.players[index].name }
    val event = state.lastEvent
    val happened = when {
        event == null -> stringResource(Res.string.game_event_first, name(game.rollOff.first))
        event is GameEvent.NoMove -> stringResource(Res.string.game_event_no_move, name(event.player), event.roll)
        event is GameEvent.RollAgainLimit -> stringResource(Res.string.game_event_three_sixes, name(event.player))
        event is GameEvent.Moved && event.finished -> stringResource(
            Res.string.game_event_finished,
            name(event.player),
            ordinal(state.finishingOrder.indexOf(event.player) + 1),
        )

        event is GameEvent.Moved && event.move.captures.isNotEmpty() -> event.move.captures.size.let { count ->
            pluralStringResource(Res.plurals.game_event_capture, count, name(event.player), name(event.move.captures.first().player), count)
        }

        event is GameEvent.Moved && event.move.to == state.board.homeProgress -> stringResource(Res.string.game_event_home, name(event.player))
        else -> null
    }
    val phase = state.phase
    val current = name(state.current)
    val prompt = when {
        phase is TurnPhase.AwaitingMove && game.currentIsCpu -> stringResource(Res.string.game_prompt_cpu_move, current, phase.roll)
        phase is TurnPhase.AwaitingMove -> stringResource(Res.string.game_prompt_move, current, phase.roll)
        game.currentIsCpu -> stringResource(Res.string.game_prompt_cpu_roll, current)
        else -> stringResource(Res.string.game_prompt_roll, current)
    }
    Text(
        text = happened.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        minLines = 2,
        maxLines = 2,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    // A screen reader can make any move from here as well as from its pawn's square.
    val colourOf = { player: Int -> seatColours[state.players[player].seat] ?: PlayerColour.defaultFor(state.players[player].seat) }
    val actions = moves.map { move -> CustomAccessibilityAction(moveLabel(state, move, colourOf)) { onMove(move.pawn); true } }
    Text(
        text = prompt,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Start,
        modifier = Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = prompt
            if (actions.isNotEmpty()) customActions = actions
        },
    )
}

/** The roll the last action showed, for the die to keep showing until the next. */
private fun lastRoll(event: GameEvent?): Int? = when (event) {
    null -> null
    is GameEvent.Rolled -> event.roll
    is GameEvent.NoMove -> event.roll
    is GameEvent.RollAgainLimit -> event.roll
    is GameEvent.Moved -> event.roll
}
