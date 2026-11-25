package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import net.zodac.ludoplus.ui.style.visualTheme
import net.zodac.ludoplus.ui.style.TableBackground
import net.zodac.ludoplus.ui.style.LocalGameVisualTheme
import net.zodac.ludoplus.ui.style.GameVisualTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.ludoplus.data.settings.MotionGroup
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.game.TutorialStep
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.netplay.GuestState
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.common_default_player_name
import net.zodac.ludoplus.resources.game_die_first_cd
import net.zodac.ludoplus.resources.netplay_ended
import net.zodac.ludoplus.resources.netplay_lost
import net.zodac.ludoplus.resources.netplay_reconnecting
import net.zodac.ludoplus.resources.netplay_seat_away
import net.zodac.ludoplus.resources.netplay_seats_away
import net.zodac.ludoplus.resources.netplay_turn_ending
import net.zodac.ludoplus.resources.game_die_second_cd
import net.zodac.ludoplus.resources.game_die_selected_spoken
import net.zodac.ludoplus.resources.game_die_showing_named_cd
import net.zodac.ludoplus.resources.game_die_used_spoken
import net.zodac.ludoplus.resources.game_over_results
import net.zodac.ludoplus.resources.game_over_team_winner
import net.zodac.ludoplus.resources.game_over_winner
import net.zodac.ludoplus.resources.gameover_squares
import net.zodac.ludoplus.resources.game_player_pawns_home_spoken
import net.zodac.ludoplus.resources.game_player_pawns_left_spoken
import net.zodac.ludoplus.resources.game_player_place
import net.zodac.ludoplus.resources.game_player_rank_spoken
import net.zodac.ludoplus.resources.game_player_rank_tied_spoken
import net.zodac.ludoplus.resources.game_player_team_spoken
import net.zodac.ludoplus.resources.game_player_turn_spoken
import net.zodac.ludoplus.resources.game_prompt_cpu_move
import net.zodac.ludoplus.resources.game_prompt_cpu_move_dice
import net.zodac.ludoplus.resources.game_prompt_cpu_move_die_left
import net.zodac.ludoplus.resources.game_prompt_cpu_roll
import net.zodac.ludoplus.resources.game_prompt_cpu_roll_again
import net.zodac.ludoplus.resources.game_prompt_move
import net.zodac.ludoplus.resources.game_prompt_move_dice
import net.zodac.ludoplus.resources.game_prompt_move_die_left
import net.zodac.ludoplus.resources.game_prompt_move_partner
import net.zodac.ludoplus.resources.game_prompt_roll
import net.zodac.ludoplus.resources.game_prompt_roll_again
import net.zodac.ludoplus.resources.game_prompt_roll_again_dice
import net.zodac.ludoplus.resources.game_prompt_roll_dice
import net.zodac.ludoplus.resources.game_cataclysm
import net.zodac.ludoplus.resources.game_cataclysm_coming
import net.zodac.ludoplus.resources.game_cataclysm_over
import net.zodac.ludoplus.resources.game_overtime_came
import net.zodac.ludoplus.resources.game_overtime_coming
import net.zodac.ludoplus.resources.game_sudden_death_came
import net.zodac.ludoplus.resources.game_sudden_death_coming
import net.zodac.ludoplus.resources.game_prompt_swap
import net.zodac.ludoplus.resources.game_swap_decline
import net.zodac.ludoplus.resources.game_tutorial_coach_name
import net.zodac.ludoplus.resources.game_tutorial_cpu_turn
import net.zodac.ludoplus.resources.game_tutorial_next
import net.zodac.ludoplus.resources.game_tutorial_part
import kotlinx.coroutines.launch
import net.zodac.ludoplus.ui.common.AppTooltip
import net.zodac.ludoplus.ui.common.BackHandler
import net.zodac.ludoplus.ui.common.LudoPlusDialog
import net.zodac.ludoplus.ui.common.BoundedArt
import net.zodac.ludoplus.ui.common.ScreenScaffold
import net.zodac.ludoplus.ui.common.delayWhileResumed
import net.zodac.ludoplus.ui.common.groupReduceMotion
import net.zodac.ludoplus.ui.common.joinClauses
import net.zodac.ludoplus.ui.common.ordinal
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.rememberAppTooltipState
import net.zodac.ludoplus.ui.common.spokenList
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.icons.Symbols
import net.zodac.ludoplus.ui.theme.ArenaFloors
import net.zodac.ludoplus.ui.theme.color

/** The narrowest the side panel beside the board gets in landscape. */
private val SIDE_PANEL_WIDTH = 280.dp

/** What the game screen shows of a finished game: the board until the results come up, then the results, or the board again to review. */
private enum class FinishedView { BOARD, RESULTS, REVIEW }

/**
 * The game screen: the players across the top, the board, the die with a line saying what to do now, and under them
 * the log of every move so far ([MoveLogPanel]). In landscape the players, die, line, and log sit beside the board. Once the game is over and its last move has
 * played out, the results ([GameOverScreen]).
 */
@Composable
fun GameScreen(viewModel: GameViewModel, onBackToMenu: () -> Unit, onNewGame: () -> Unit = {}) {
    val game by viewModel.game.collectAsStateWithLifecycle()
    val started = game ?: return
    val confirmBeforeLeaving by viewModel.confirmBeforeLeavingGame.collectAsStateWithLifecycle()
    val leaveConfirmation = LocalLeaveGameConfirmation.current
    val isTutorial = viewModel.isTutorial
    // Back (the arrow or the gesture) leaves for the menu, not the setup form, asking first while the game is on. The
    // tutorial always asks, in its own words.
    val leave = {
        when {
            started.state.isFinished -> onBackToMenu()
            isTutorial -> leaveConfirmation.request(onBackToMenu, tutorial = true)
            confirmBeforeLeaving -> leaveConfirmation.request(onBackToMenu)
            else -> onBackToMenu()
        }
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

    // Each game's own: Play Again starts these afresh.
    val gameKey = started.startedAtEpochMillis
    var finishedView by rememberSaveable(gameKey) { mutableStateOf(FinishedView.BOARD) }
    // Only a game seen finishing here is celebrated, once.
    var celebrate by rememberSaveable(gameKey) { mutableStateOf(!started.state.isFinished) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(gameKey, started.state.isFinished) {
        if (!started.state.isFinished || finishedView != FinishedView.BOARD) return@LaunchedEffect
        // The last move plays out first, and a beat after it.
        if (celebrate) lifecycle.delayWhileResumed(pacing.waitAfter(started.state.lastEvent, started.state.board.homeProgress))
        finishedView = FinishedView.RESULTS
    }
    val guestState by viewModel.guestState.collectAsStateWithLifecycle()
    if (started.state.isFinished && finishedView == FinishedView.RESULTS) {
        val defaultNames = (1..started.players.size).map { stringResource(Res.string.common_default_player_name, it) }
        val playAgain: () -> Unit = { viewModel.playAgain { defaultNames[it - 1] } }
        GameOverScreen(
            game = started,
            onBackToMenu = onBackToMenu,
            // A guest's phone plays again when its host does.
            onPlayAgain = when {
                isTutorial -> onNewGame
                guestState != null -> null
                else -> playAgain
            },
            onReviewBoard = { finishedView = FinishedView.REVIEW },
            celebrate = celebrate,
            onCelebrated = { celebrate = false },
            tutorial = isTutorial,
            hostPlaysAgain = guestState is GuestState.Playing,
        )
        return
    }
    val tutorialStep by viewModel.tutorialStep.collectAsStateWithLifecycle()
    val tutorialPart by viewModel.tutorialPart.collectAsStateWithLifecycle()
    val tutorialNextReady by viewModel.tutorialNextReady.collectAsStateWithLifecycle()
    TurnEndingBuzz((guestState as? GuestState.Playing)?.turnEnding == true)
    val settled by viewModel.settled.collectAsStateWithLifecycle()
    val awaySeats by viewModel.awaySeats.collectAsStateWithLifecycle()
    SuperuserRollPicker(viewModel)
    val window = LocalWindowInfo.current.containerSize
    val sideways = window.width > window.height
    LaunchedEffect(sideways) { if (sideways) viewModel.onShownSideways() }
    // The player's picks on the Styles page, or the defaults until they've loaded.
    val picks by viewModel.savedStyles.collectAsStateWithLifecycle()
    val showLog by viewModel.showMoveLog.collectAsStateWithLifecycle()
    val theme = remember(picks) { picks?.visualTheme ?: GameVisualTheme() }
    CompositionLocalProvider(
        LocalDieLongPress provides viewModel::onDieLongPressed.takeIf { viewModel.hasSuperuserMode },
        LocalGameVisualTheme provides theme,
    ) {
        GameContent(
            started,
            onRoll = viewModel::roll,
            onMove = { viewModel.move(it) },
            onBack = leave,
            onResults = { finishedView = FinishedView.RESULTS },
            settled = settled,
            notice = netplayNotice(guestState, awaySeats, started),
            showLog = showLog,
            tutorial = if (isTutorial) {
                TutorialView(
                    tutorialStep, tutorialPart, viewModel.tutorialParts, viewModel.tutorialSteps, viewModel::jumpToTutorialPart,
                    nextReady = tutorialNextReady, onNext = viewModel::tutorialNext,
                )
            } else {
                null
            },
        )
    }
}

/**
 * How a Nearby game's link stands, where it isn't as it should be: on a guest's phone, the host's phone lost (for now or
 * for good) or the game ended by the host; on the host's, the guests' phones that dropped out. Null otherwise.
 */
@Composable
private fun netplayNotice(guestState: GuestState?, awaySeats: Set<Int>, game: StartedGame): String? = when {
    guestState is GuestState.Playing && guestState.turnEnding -> stringResource(Res.string.netplay_turn_ending)
    guestState is GuestState.Reconnecting -> stringResource(Res.string.netplay_reconnecting)
    guestState == GuestState.Lost -> stringResource(Res.string.netplay_lost)
    guestState == GuestState.Ended && !game.state.isFinished -> stringResource(Res.string.netplay_ended)
    game.state.isFinished -> null
    awaySeats.size == 1 -> stringResource(Res.string.netplay_seat_away, game.players[awaySeats.single()].name)
    awaySeats.isNotEmpty() -> pluralStringResource(Res.plurals.netplay_seats_away, awaySeats.size, awaySeats.size)
    else -> null
}

/** A buzz as a guest's turn becomes nearly up ([GuestState.Playing.turnEnding]), once each time. */
@Composable
private fun TurnEndingBuzz(turnEnding: Boolean) {
    val haptics = rememberGameHaptics()
    LaunchedEffect(turnEnding) { if (turnEnding) haptics.playTurnWarning() }
}

/**
 * Superuser mode's (debug builds only) toasts, and its pick of the next roll: a button for each number, and a fair roll.
 * Not translated: a developer's tool, never in a release build.
 */
@Composable
private fun SuperuserRollPicker(viewModel: GameViewModel) {
    val platform = LocalPlatformServices.current
    LaunchedEffect(viewModel) { viewModel.toastMessages.collect(platform::showTransientMessage) }
    val shown by viewModel.rollPickerShown.collectAsStateWithLifecycle()
    if (!shown) return
    val faces = viewModel.game.collectAsStateWithLifecycle().value?.state?.rules?.dieFaces ?: return
    LudoPlusDialog(
        icon = Symbols.Casino,
        title = "Superuser: the next roll", // i18n: not translated - a debug-build developer dialog
        message = "Whoever rolls next, a person or a CPU, rolls this.", // i18n: not translated - a debug-build developer dialog
        confirmLabel = "Fair roll", // i18n: not translated - a debug-build developer dialog
        onConfirm = { viewModel.pickNextRoll(null) },
        onDismissRequest = { viewModel.pickNextRoll(null) },
        extraContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for (value in 1..faces) {
                    FilledTonalButton(
                        onClick = { viewModel.pickNextRoll(value) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(value.toString()) }
                }
            }
        },
    )
}

/**
 * What the game screen shows of the tutorial: the [step] shown (null between parts), the [part] (from 0) of [parts], every
 * one of its [steps] (for the coach card's height), and [onJump] to the part before (-1) or after (+1). At a step that
 * waits for the Next button, [onNext] carries on, once [nextReady].
 */
class TutorialView(
    val step: TutorialStep?,
    val part: Int,
    val parts: Int,
    val steps: List<TutorialStep>,
    val onJump: (Int) -> Unit,
    val nextReady: Boolean = false,
    val onNext: () -> Unit = {},
)

/**
 * The sounds and vibrations for each thing that happens: the die landing; a pawn's steps square by square, in time with
 * its move ([moveSoundCues]), then a capture's sound (and a step as the captured pawn lands in its yard) or a pawn home's
 * chime as it lands; and a tick, or a capture's buzz.
 * The win's are the results page's.
 */
@Composable
private fun GameFeedback(state: GameState) {
    val sounds = rememberSoundEffects()
    val haptics = rememberGameHaptics()
    val event = state.lastEvent
    val firstSeen = remember { event }
    val animated = !groupReduceMotion(MotionGroup.PAWN_MOVE)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val ghostMove = state.ghostMove
    val firstGhostMove = remember { ghostMove }
    LaunchedEffect(ghostMove) {
        if (ghostMove == null || ghostMove === firstGhostMove || ghostMove.caught.isEmpty()) return@LaunchedEffect
        // As the ghost reaches them, once the turn before it has played out.
        if (animated) lifecycle.delayWhileResumed(turnMillis(state) + ghostMillis(ghostMove) - CAPTURE_FLIGHT_MILLIS.toLong())
        haptics.playCaptureBuzz()
        sounds.playCapture()
    }
    val justLost = state.justLost
    val firstLost = remember { justLost }
    LaunchedEffect(justLost) {
        if (justLost.isEmpty() || justLost == firstLost) return@LaunchedEffect
        // As they go, once the turn before has played out.
        if (animated) lifecycle.delayWhileResumed(turnMillis(state).toLong())
        haptics.playCaptureBuzz()
    }
    LaunchedEffect(event) {
        if (event == null || event === firstSeen) return@LaunchedEffect
        when (event) {
            is GameEvent.Rolled, is GameEvent.NoMove, is GameEvent.RollAgainLimit -> sounds.playDieLanding()
            is GameEvent.Moved -> {
                if (event.keptPlace) return@LaunchedEffect
                if (event.allCaptures.isEmpty()) haptics.playMoveTick() else haptics.playCaptureBuzz()
                var elapsed = 0L
                for ((at, sound) in moveSoundCues(event.move, state.board.homeProgress, animated, event.effect)) {
                    lifecycle.delayWhileResumed(at - elapsed)
                    elapsed = at
                    when (sound) {
                        MoveSound.STEP -> sounds.playStep()
                        MoveSound.CAPTURE -> sounds.playCapture()
                        MoveSound.HOME -> sounds.playHome()
                    }
                }
            }
        }
    }
}

/**
 * The game screen's content for [started]: rolling calls [onRoll], a tap on a movable pawn [onMove], the back arrow
 * [onBack], and the finished game's button [onResults]. The die can't be rolled until the last action has played out
 * ([settled]). The move log is on screen with [showLog]; without it, TalkBack still hears its newest line.
 */
@Composable
fun GameContent(
    started: StartedGame,
    onRoll: () -> Unit,
    onMove: (Move) -> Unit,
    onBack: () -> Unit,
    onResults: () -> Unit,
    settled: Boolean = true,
    notice: String? = null,
    tutorial: TutorialView? = null,
    showLog: Boolean = true,
) {
    val seatColours = List(started.state.board.seats) { seat ->
        started.state.players.indexOfFirst { it.seat == seat }.takeIf { it >= 0 }?.let { started.players[it].colour }
    }
    val phase = started.state.phase
    // A move with no choice makes itself: nothing to pick, so nothing is ringed (the tutorial makes none).
    val picks = tutorial != null || started.automaticMove == null
    // A Swap's pick isn't asked for until the pawn has landed on it.
    val landed = phase !is TurnPhase.AwaitingMove || !phase.swapping || settled
    val humanMoves = if (phase is TurnPhase.AwaitingMove && !started.currentIsCpu && picks && landed) phase.moves else emptyList()
    // In the tutorial, only the step's own move: its pawn alone is ringed, and the only one a tap or TalkBack can move.
    val allMoves = if (tutorial == null) humanMoves else humanMoves.filter { it.pawn == (tutorial.step as? TutorialStep.Move)?.pawn }
    // With two dice, the die a tap on a pawn moves by: the one tapped, or at first the first with a move. The board rings
    // only its moves; TalkBack is offered every die's.
    var pickedDie by remember(phase) { mutableStateOf<Int?>(null) }
    val dieValues = allMoves.mapNotNull { it.roll }.distinct()
    val selectedDie = pickedDie?.takeIf { it in dieValues } ?: dieValues.firstOrNull()
    val pick: (Int) -> Unit = { pickedDie = it }
    val dice = DiceChoice(selectedDie, onSelect = pick.takeIf { dieValues.size > 1 })
    // Landed on a Swap: whose to swap with, or nobody, by the pawn's swap with itself.
    val keepPlace = allMoves.firstOrNull { it.swap == PawnRef(it.ownerIn(started.state), it.pawn) }
    val offered = allMoves - setOfNotNull(keepPlace)
    val moves = offered.filter { it.roll == null || it.roll == selectedDie }
    val declineSwap: (() -> Unit)? = keepPlace?.let { move -> { onMove(move) } }
    val rollStep = tutorial?.step is TutorialStep.Roll
    val players = @Composable { compact: Boolean ->
        if (notice != null) NetplayNotice(notice)
        if (tutorial == null) PlayerCards(started, compact) else TutorialCoachCard(tutorial)
        if (started.state.rules.suddenDeath && !started.state.isFinished) SuddenDeathNotice(started.state)
    }
    val background = LocalGameVisualTheme.current.background
    ScreenScaffold(
        title = started.customName ?: stringResource(started.mode.displayName),
        onBack = onBack,
        driftingDice = false,
        wide = true,
        underlay = { TableBackgroundFill(background) },
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val (width, height) = maxWidth to maxHeight
            val board = @Composable { modifier: Modifier ->
                LudoBoard(
                    state = started.state,
                    seatColours = seatColours,
                    moves = moves,
                    onMove = onMove,
                    modifier = modifier.clip(RoundedCornerShape(8.dp)),
                    swapping = keepPlace != null,
                )
            }
            if (width > height) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    board(Modifier.size(minOf(height, width - SIDE_PANEL_WIDTH)))
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        players(false)
                        TurnPanel(started, offered, seatColours, onRoll, onMove, onResults, settled = settled, onDeclineSwap = declineSwap, dieSize = 64.dp, tutorial = tutorial, rollStep = rollStep, dice = dice)
                        MoveLogPanel(started, seatColours, tutorial = tutorial != null, shown = showLog, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                // The tutorial's log keeps no room: its coach says what matters. Nor does one not shown.
                val logHeight = if (tutorial == null && showLog) shortestLogHeight() else 0.dp
                val rows = playerCardRows(BoardLayout.of(started.state.board), started.state.players.map { it.seat }).size
                val compact = width + playerCardsHeight(rows, compact = false) + PORTRAIT_DIE_SIZE + PORTRAIT_GAP * 3 + logHeight > height
                PortraitGame(
                    logHeight = logHeight,
                    players = { players(compact) },
                    board = { board(Modifier) },
                    turn = {
                        TurnPanel(
                            started, offered, seatColours, onRoll, onMove, onResults, settled = settled, onDeclineSwap = declineSwap, dieSize = PORTRAIT_DIE_SIZE, tutorial = tutorial, rollStep = rollStep, dice = dice,
                        )
                    },
                    log = { MoveLogPanel(started, seatColours, tutorial = tutorial != null, shown = showLog) },
                )
            }
        }
    }
}

/**
 * The game in portrait, top to bottom: the [players], the [board], the [turn]'s die and line, and the [log] in what's left.
 * The board is the screen's width, less what it gives up where the screen is short for the log to have [logHeight]. Its size
 * allows for the turn's die alone, so a longer prompt, or the winner at the end, takes its room from the log and never resizes the board.
 */
@Composable
private fun PortraitGame(
    logHeight: Dp,
    players: @Composable () -> Unit,
    board: @Composable () -> Unit,
    turn: @Composable () -> Unit,
    log: @Composable () -> Unit,
) {
    Layout(contents = listOf(players, board, turn, log), modifier = Modifier.fillMaxSize()) { (playerParts, boardParts, turnParts, logParts), constraints ->
        val gap = PORTRAIT_GAP.roundToPx()
        val (width, height) = constraints.maxWidth to constraints.maxHeight
        val loose = Constraints(maxWidth = width)
        val above = playerParts.map { it.measure(loose) }
        val aboveHeight = above.sumOf { it.height + gap }
        val room = height - aboveHeight - gap - PORTRAIT_DIE_SIZE.roundToPx() - gap
        val side = portraitBoardSide(width, room, logHeight.roundToPx())
        val boards = boardParts.map { it.measure(Constraints.fixed(side, side)) }
        val turns = turnParts.map { it.measure(loose) }
        val logTop = aboveHeight + side + gap + turns.sumOf { it.height + gap }
        val logs = logParts.map { it.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = (height - logTop).coerceAtLeast(0))) }
        layout(width, height) {
            var y = 0
            above.forEach { it.placeRelative(0, y); y += it.height + gap }
            boards.forEach { it.placeRelative((width - side) / 2, y); y += side + gap }
            turns.forEach { it.placeRelative(0, y); y += it.height + gap }
            logs.forEach { it.placeRelative(0, logTop) }
        }
    }
}

/**
 * How wide the board is in portrait, [width] at most, where it shares [room] with the log, which needs [logHeight]: as
 * [PortraitGame] says, and never under [SMALLEST_BOARD] of [width].
 */
internal fun portraitBoardSide(width: Int, room: Int, logHeight: Int): Int = (room - logHeight).coerceIn((width * SMALLEST_BOARD).toInt(), width)

/** The share of the screen's width the board always keeps. */
private const val SMALLEST_BOARD = 0.6f

private val PORTRAIT_DIE_SIZE = 72.dp
private val PORTRAIT_GAP = 12.dp

// M3's button is a 40dp pill.
private val NEXT_CORNER = 20.dp

/** A Nearby game's [notice] over the players: TalkBack hears it as it comes and as it changes (a polite live region). */
@Composable
private fun NetplayNotice(notice: String) {
    Text(
        text = notice,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * How sudden death stands, under the players, in the stage's own colour: its name and what it does in the round it comes,
 * and then its next stage and the rounds (or for the Cataclysm, turns) left to it, or in the Cataclysm, the turns left until
 * squares vanish. TalkBack
 * hears it as it changes (a polite live region) in the last [SUDDEN_DEATH_WARNING] of those before a stage, and as the stage
 * comes; as squares vanish, the move log says so.
 */
@Composable
private fun SuddenDeathNotice(state: GameState) {
    val stage = state.suddenDeathStage
    val rounds = state.roundsToSuddenDeath
    val toCataclysm = state.turnsToCataclysm
    val toLoss = state.turnsToLoss
    val text = when {
        state.suddenDeathJustCame && !state.cataclysm -> stringResource(if (stage == 1) Res.string.game_overtime_came else Res.string.game_sudden_death_came)
        rounds != null -> pluralStringResource(if (stage == 0) Res.plurals.game_overtime_coming else Res.plurals.game_sudden_death_coming, rounds, rounds)
        toCataclysm != null -> pluralStringResource(Res.plurals.game_cataclysm_coming, toCataclysm, toCataclysm)
        toLoss != null -> pluralStringResource(Res.plurals.game_cataclysm, toLoss, toLoss)
        else -> stringResource(Res.string.game_cataclysm_over)
    }
    // Not at every turn of the Cataclysm's own count.
    val announced = state.suddenDeathJustCame || listOfNotNull(rounds, toCataclysm).any { it <= SUDDEN_DEATH_WARNING } || (state.cataclysm && toLoss == null)
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
            .background(ArenaFloors[stage], RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { if (announced) liveRegion = LiveRegionMode.Polite },
    )
}

/** How many rounds (turns, for the Cataclysm) before a stage of sudden death its countdown is heard. */
private const val SUDDEN_DEATH_WARNING = 3

/** [background] filling the room it's given: its brush, and over it any decoration it draws, running while it's shown. */
@Composable
private fun BoxScope.TableBackgroundFill(background: TableBackground) {
    Box(modifier = Modifier.matchParentSize().background(background.scoreAreaBrush))
    background.Animate()
    // Its own node: a moving decoration's frames redraw only it, not the page round it.
    BoundedArt(modifier = Modifier.matchParentSize()) { with(background) { drawScoreAreaDecoration() } }
}

/** The tutorial's coach card, in place of the players: as tall as its longest words need, so it never changes height. */
@Composable
private fun TutorialCoachCard(tutorial: TutorialView) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val height = coachCardHeight(tutorial.steps, maxWidth)
        val step = tutorial.step
        if (step == null) {
            Box(modifier = Modifier.fillMaxWidth().height(height))
            return@BoxWithConstraints
        }
        CoachCard(
            step = step,
            coachName = stringResource(Res.string.game_tutorial_coach_name),
            part = stringResource(Res.string.game_tutorial_part, tutorial.part + 1, tutorial.parts),
            onBack = { tutorial.onJump(-1) },
            onForward = if (tutorial.part < tutorial.parts - 1) ({ tutorial.onJump(1) }) else null,
            modifier = Modifier.height(height),
        )
    }
}

/**
 * The players, laid out as their yards are round the board ([playerCardRows]): the one whose turn it is outlined in their
 * colour. TalkBack reads them in player order, whatever the rows. [compact] on a short screen: closer together, for the
 * log's sake.
 */
@Composable
private fun PlayerCards(game: StartedGame, compact: Boolean) {
    val seats = game.state.players.map { it.seat }
    val rows = remember(game.state.board, seats) { playerCardRows(BoardLayout.of(game.state.board), seats) }
    Column(verticalArrangement = Arrangement.spacedBy(cardGap(compact)), modifier = Modifier.semantics { isTraversalGroup = true }) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { index ->
                    if (index == null) {
                        Box(Modifier.weight(1f))
                    } else {
                        PlayerCard(game, index, compact, Modifier.weight(1f).semantics { traversalIndex = index.toFloat() })
                    }
                }
            }
        }
    }
}

private fun cardGap(compact: Boolean): Dp = if (compact) 4.dp else 8.dp

private fun cardPadding(compact: Boolean): Dp = if (compact) 4.dp else 8.dp

/** How tall [rows] of player cards are. */
@Composable
private fun playerCardsHeight(rows: Int, compact: Boolean): Dp {
    val line = with(LocalDensity.current) { MaterialTheme.typography.titleSmall.lineHeight.toDp() }
    return (line + cardPadding(compact) * 2) * rows + cardGap(compact) * (rows - 1)
}

@Composable
private fun PlayerCard(game: StartedGame, index: Int, compact: Boolean, modifier: Modifier) {
    val player = game.players[index]
    val state = game.state
    val side = state.players[index]
    val colour = player.colour.color
    val current = state.current == index && !state.isFinished
    val lastStanding = state.rules.lastStanding
    // The card's dots: the pawns home, or where the last standing wins, the pawns still in.
    val counted = if (lastStanding) side.pawnsLeft else side.pawnsHome(state.board)
    // A player still racing when the game ended has a place but no pawns all home: their card keeps its dots.
    val place = state.placeOf(index).takeIf { if (lastStanding) side.pawnsLeft == 0 else side.isFinished(state.board) }
    val placeText = place?.let { stringResource(Res.string.game_player_place, ordinal(it)) }
    val team = teamNumber(state, index)
    val ranks = remember(state) { state.ranks() }
    val rank = ranks?.get(index)
    // Once the game is over the rank is the place, said as one even where the card keeps its dots.
    val spokenRank = when {
        rank == null || placeText != null -> null
        state.placeOf(index) != null -> stringResource(Res.string.game_player_place, ordinal(rank))
        // Partners share a rank without being level.
        state.players.indices.any { ranks[it] == rank && state.rules.teamOf(it) != state.rules.teamOf(index) } -> stringResource(Res.string.game_player_rank_tied_spoken, ordinal(rank))
        else -> stringResource(Res.string.game_player_rank_spoken, ordinal(rank))
    }
    var nameCut by remember(player.name) { mutableStateOf(false) }
    val nameTooltip = rememberAppTooltipState()
    val scope = rememberCoroutineScope()
    val spoken = joinClauses(
        listOfNotNull(
            team?.let { stringResource(Res.string.game_player_team_spoken, it) },
            pluralStringResource(if (lastStanding) Res.plurals.game_player_pawns_left_spoken else Res.plurals.game_player_pawns_home_spoken, counted, counted),
            if (state.rules.claims) state.squaresOf(index).let { pluralStringResource(Res.plurals.gameover_squares, it, it) } else null,
            if (current) stringResource(Res.string.game_player_turn_spoken) else null,
            placeText ?: spokenRank,
        ),
    )
    Surface(
        // Raw taps, not a button: TalkBack reads the whole name with the card, so it has nothing to ask for.
        modifier = modifier
            .semantics(mergeDescendants = true) { stateDescription = spoken }
            .then(if (nameCut) Modifier.pointerInput(Unit) { detectTapGestures { scope.launch { nameTooltip.show() } } } else Modifier),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (current) BorderStroke(2.dp, colour) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = cardPadding(compact)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (team != null) TeamBadge(team, Modifier.clearAndSetSemantics { })
            RankDisc(rank, colour, Modifier.clearAndSetSemantics { })
            // The box takes the room: a tooltip's own is only as wide as what's in it.
            Box(modifier = Modifier.weight(1f)) {
                // Not focusable: a tap elsewhere, on the die say, does its own job as the tooltip goes.
                AppTooltip(
                    body = { Text(text = player.name, style = MaterialTheme.typography.bodyMedium) },
                    state = nameTooltip,
                    enabled = false,
                    focusable = false,
                ) {
                    Text(
                        text = player.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { nameCut = it.hasVisualOverflow },
                    )
                }
            }
            if (player.type == PlayerType.AI) CpuPlayerIcon(size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.rules.claims) SquaresBadge(state.squaresOf(index), colour, Modifier.clearAndSetSemantics { })
            if (placeText != null) {
                Text(placeText, style = MaterialTheme.typography.labelMedium, color = colour, modifier = Modifier.clearAndSetSemantics { })
            } else {
                PawnDots(counted, state.rules.pawnsPerPlayer, colour, Modifier.clearAndSetSemantics { })
            }
        }
    }
}

/**
 * The player's [colour], with their [rank] as the game stands on it ([GameState.ranks]), or none while everyone is level.
 * It grows with the font, and no taller than the name's line. Drawn only; the card says the rank.
 */
@Composable
private fun RankDisc(rank: Int?, colour: Color, modifier: Modifier) {
    val size = with(LocalDensity.current) { RANK_DISC_SIZE.toDp() }
    Surface(color = colour, shape = CircleShape, modifier = modifier.size(size)) {
        if (rank == null) return@Surface
        Box(contentAlignment = Alignment.Center) {
            Text(
                rank.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                maxLines = 1,
                softWrap = false,
                // A large font's line is taller than the disc: centred on it all the same.
                modifier = Modifier.wrapContentSize(unbounded = true),
            )
        }
    }
}

/** How wide a player card's rank disc is, in the font's own size. */
private val RANK_DISC_SIZE = 16.sp

/** [player]'s team as players see it, numbered from 1 in seat order, or null in a game without teams. */
internal fun teamNumber(state: GameState, player: Int): Int? {
    if (state.rules.teams == null) return null
    val order = state.players.indices.map(state.rules::teamOf).distinct()
    return order.indexOf(state.rules.teamOf(player)) + 1
}

/** How many [squares] a player has claimed, on a square of their [colour]. Drawn only; the card says it. */
@Composable
private fun SquaresBadge(squares: Int, colour: Color, modifier: Modifier) {
    Surface(color = colour, shape = RoundedCornerShape(3.dp), modifier = modifier) {
        Text(
            squares.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** A team's number in a small ring, on each of its players' cards. Drawn only; the card says "Team 1". */
@Composable
private fun TeamBadge(team: Int, modifier: Modifier) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
        modifier = modifier.size(18.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(team.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Who won, once the game is over: "Alex wins!", or a team's players, "Alex and Sam win!" */
@Composable
private fun winnerLine(game: StartedGame): String {
    val state = game.state
    val winners = state.finishingOrder.filter { state.placeOf(it) == 1 }
    return if (winners.size == 1) {
        stringResource(Res.string.game_over_winner, game.players[winners.single()].name)
    } else {
        stringResource(Res.string.game_over_team_winner, spokenList(winners.map { game.players[it].name }))
    }
}

/** A dot per pawn: filled in [colour] for each of the [counted] that are home, or still in. Drawn only; the card says the count. */
@Composable
private fun PawnDots(counted: Int, total: Int, colour: Color, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(total) { i ->
            Surface(
                shape = CircleShape,
                color = if (i < counted) colour else Color.Transparent,
                border = BorderStroke(1.dp, colour.copy(alpha = 0.7f)),
                modifier = Modifier.size(7.dp),
            ) {}
        }
    }
}

/** The die, and the line saying what to do now, with a button not to swap while a swap is picked ([onDeclineSwap]); once the game is over, the winner and the way back to the results. */
@Composable
private fun TurnPanel(
    game: StartedGame,
    moves: List<Move>,
    seatColours: List<PlayerColour?>,
    onRoll: () -> Unit,
    onMove: (Move) -> Unit,
    onResults: () -> Unit,
    dieSize: Dp,
    settled: Boolean,
    onDeclineSwap: (() -> Unit)? = null,
    tutorial: TutorialView? = null,
    rollStep: Boolean = false,
    dice: DiceChoice = DiceChoice(),
) {
    val state = game.state
    val name = { index: Int -> game.players[index].name }
    if (state.isFinished) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = winnerLine(game),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Button(onClick = onResults) { Text(stringResource(Res.string.game_over_results)) }
        }
        return
    }
    val phase = state.phase
    val roll = (phase as? TurnPhase.AwaitingMove)?.roll ?: lastRoll(state.lastEvent)
    val canRoll = phase == TurnPhase.AwaitingRoll && settled && !game.currentIsCpu && (tutorial == null || rollStep)
    val seat = state.currentPlayer.seat
    val turnColour = (seatColours.getOrNull(seat) ?: PlayerColour.defaultFor(seat)).color
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.rules.dice > 1) {
            TwoDice(state, canRoll, onRoll, dice, dieSize, turnColour)
        } else {
            GameDie(
                value = roll,
                roll = state.lastEvent.takeIf { it !is GameEvent.Moved },
                canRoll = canRoll,
                onRoll = onRoll,
                ringColour = turnColour,
                turnRing = true,
                modifier = Modifier
                    .then(if (rollStep) Modifier.tutorialOutline(rememberTutorialPulse()) else Modifier)
                    .size(dieSize),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusLines(game, moves, seatColours, onMove, tutorial = tutorial != null, selectedDie = dice.selected, swapping = onDeclineSwap != null)
            // Under the prompt, not beside it: a large font has room for only one of them across.
            if (onDeclineSwap != null) FilledTonalButton(onClick = onDeclineSwap) { Text(stringResource(Res.string.game_swap_decline)) }
        }
        // Kept, but off, while the action it asked for plays out, so a screen reader's focus stays on it.
        if (tutorial?.step?.waitsForNext == true) {
            Button(
                onClick = tutorial.onNext,
                enabled = tutorial.nextReady,
                modifier = if (tutorial.nextReady) Modifier.tutorialOutline(rememberTutorialPulse(), NEXT_CORNER) else Modifier,
            ) { Text(stringResource(Res.string.game_tutorial_next)) }
        }
    }
}

@Composable
private fun ColumnScope.StatusLines(
    game: StartedGame,
    moves: List<Move>,
    seatColours: List<PlayerColour?>,
    onMove: (Move) -> Unit,
    tutorial: Boolean = false,
    selectedDie: Int? = null,
    swapping: Boolean = false,
) {
    val state = game.state
    val name = { index: Int -> game.players[index].name }
    val phase = state.phase
    val current = name(state.current)
    val twoDice = phase is TurnPhase.AwaitingMove && state.rules.dice > 1
    // A move that makes itself is told as the computer's is, no pick asked for, and so is one onto a Swap until it has landed.
    val movesItself = game.currentIsCpu || (!tutorial && game.automaticMove != null) || (phase is TurnPhase.AwaitingMove && phase.swapping)
    val prompt = when {
        swapping -> stringResource(Res.string.game_prompt_swap, current)
        // Two dice: the roll, or the die left, and the die a pawn moves by.
        twoDice && phase.dice.size > 1 && movesItself -> stringResource(Res.string.game_prompt_cpu_move_dice, current, phase.rolled[0], phase.rolled[1])
        twoDice && movesItself -> stringResource(Res.string.game_prompt_cpu_move_die_left, current, phase.roll)
        twoDice && phase.dice.size > 1 -> stringResource(Res.string.game_prompt_move_dice, current, phase.rolled[0], phase.rolled[1], selectedDie ?: phase.roll)
        twoDice -> stringResource(Res.string.game_prompt_move_die_left, current, phase.roll)
        phase is TurnPhase.AwaitingMove && movesItself -> stringResource(Res.string.game_prompt_cpu_move, current, phase.roll)
        phase is TurnPhase.AwaitingMove && phase.moves.all { it.owner != null } -> stringResource(Res.string.game_prompt_move_partner, current, phase.roll)
        phase is TurnPhase.AwaitingMove -> stringResource(Res.string.game_prompt_move, current, phase.roll)
        game.currentIsCpu && state.rollingAgain -> stringResource(Res.string.game_prompt_cpu_roll_again, current)
        game.currentIsCpu && tutorial -> stringResource(Res.string.game_tutorial_cpu_turn, current)
        game.currentIsCpu -> stringResource(Res.string.game_prompt_cpu_roll, current)
        state.rollingAgain && state.rules.dice > 1 -> stringResource(Res.string.game_prompt_roll_again_dice, current)
        state.rollingAgain -> stringResource(Res.string.game_prompt_roll_again, current)
        state.rules.dice > 1 -> stringResource(Res.string.game_prompt_roll_dice, current)
        else -> stringResource(Res.string.game_prompt_roll, current)
    }
    val colourOf = { player: Int -> seatColours[state.players[player].seat] ?: PlayerColour.defaultFor(state.players[player].seat) }
    // A screen reader can make any move from here as well as from its pawn's square.
    val actions = moves.map { move -> CustomAccessibilityAction(if (swapping) swapLabel(move, colourOf) else moveLabel(state, move, colourOf)) { onMove(move); true } }
    Text(
        text = withNamesInColour(prompt, listOf(name(state.current) to colourOf(state.current).color), bold = true),
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

/**
 * [text] with each of [names] in it drawn in its colour, a player's name in their own, and with [bold], bold: the first
 * place each is found that no longer name has taken.
 */
internal fun withNamesInColour(text: String, names: List<Pair<String, Color>>, bold: Boolean = false): AnnotatedString = buildAnnotatedString {
    append(text)
    val taken = mutableListOf<IntRange>()
    for ((name, colour) in names.filter { it.first.isNotEmpty() }.sortedByDescending { it.first.length }) {
        var start = text.indexOf(name)
        while (start >= 0 && taken.any { start <= it.last && start + name.length - 1 >= it.first }) start = text.indexOf(name, start + 1)
        if (start < 0) continue
        taken += start until start + name.length
        addStyle(SpanStyle(color = colour, fontWeight = FontWeight.Bold.takeIf { bold }), start, start + name.length)
    }
}

/** With two dice, the die the next move uses ([selected]), and while there's a choice, how to pick another ([onSelect]). */
class DiceChoice(val selected: Int? = null, val onSelect: ((Int) -> Unit)? = null)

/**
 * Two dice side by side, each a little smaller than one die, showing the roll: the one awaiting its moves
 * ([TurnPhase.AwaitingMove.rolled]), or the last one rolled, kept until the next. A die already moved is faded; while
 * both are still to move, a tap on one picks it for the next move (ringed in full in [turnColour], the turn's ring
 * faint round the other), and TalkBack hears "First die showing 3,
 * selected". Before a roll, a tap on either rolls both.
 */
@Composable
private fun TwoDice(state: GameState, canRoll: Boolean, onRoll: () -> Unit, choice: DiceChoice, dieSize: Dp, turnColour: Color) {
    val phase = state.phase as? TurnPhase.AwaitingMove
    val current = phase?.rolled ?: lastDice(state.lastEvent)
    var kept by remember { mutableStateOf(current) }
    LaunchedEffect(current) { if (current != null) kept = current }
    val shown = current ?: kept
    // Which of the roll's dice are still to move: a double's first, of two the same.
    val left = phase?.dice?.toMutableList()
    val used = List(2) { index -> left != null && shown != null && !left.remove(shown[index]) }
    val selectedIndex = shown?.indices?.firstOrNull { !used[it] && shown[it] == choice.selected }
    val tumble = state.lastEvent.takeIf { it !is GameEvent.Moved }
    val names = listOf(Res.string.game_die_first_cd, Res.string.game_die_second_cd)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (index in 0..1) {
            val value = shown?.get(index)
            val description = joinClauses(
                listOfNotNull(
                    value?.let { stringResource(Res.string.game_die_showing_named_cd, stringResource(names[index]), it) } ?: stringResource(names[index]),
                    stringResource(Res.string.game_die_used_spoken).takeIf { used[index] },
                    stringResource(Res.string.game_die_selected_spoken).takeIf { index == selectedIndex && choice.onSelect != null },
                ),
            )
            GameDie(
                value = value,
                roll = tumble,
                canRoll = canRoll,
                onRoll = onRoll,
                modifier = Modifier.size(dieSize * TWO_DICE_SCALE),
                description = description,
                used = used[index],
                selected = index == selectedIndex && choice.onSelect != null,
                onSelect = choice.onSelect?.takeIf { !used[index] && value != null && index != selectedIndex }?.let { pick -> { pick(value!!) } },
                ringColour = turnColour,
                turnRing = true,
            )
        }
    }
}

/** How big each of two dice is, of one die's size. */
private const val TWO_DICE_SCALE = 0.8f

/** The two dice the last action showed, for them to keep showing until the next: null for a move, which shows none. */
private fun lastDice(event: GameEvent?): List<Int>? = when (event) {
    is GameEvent.Rolled -> event.second?.let { listOf(event.roll, it) }
    is GameEvent.NoMove -> event.second?.let { listOf(event.roll, it) }
    is GameEvent.RollAgainLimit -> event.second?.let { listOf(event.roll, it) }
    else -> null
}

/** The roll the last action showed, for the die to keep showing until the next. */
private fun lastRoll(event: GameEvent?): Int? = when (event) {
    null -> null
    is GameEvent.Rolled -> event.roll
    is GameEvent.NoMove -> event.roll
    is GameEvent.RollAgainLimit -> event.roll
    is GameEvent.Moved -> event.roll
}
