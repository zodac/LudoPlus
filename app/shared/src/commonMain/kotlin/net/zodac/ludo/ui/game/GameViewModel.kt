package net.zodac.ludo.ui.game

import net.zodac.ludo.data.settings.SavedStyles
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.zodac.ludo.app.AchievementUnlocker
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.data.game.InProgressGameRepository
import net.zodac.ludo.data.orNullOnFailure
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.AchievementEngine
import net.zodac.ludo.game.AchievementTracker
import net.zodac.ludo.game.CpuStrategy
import net.zodac.ludo.game.GameFeats
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.LudoEngine
import net.zodac.ludo.game.RollOff
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.game.TutorialScript
import net.zodac.ludo.game.TutorialStep
import net.zodac.ludo.game.localHourOfDay
import net.zodac.ludo.game.nowEpochMillis
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType

/**
 * The play graph's state: the setup form, and the game it starts. Shared by the setup screen and the game screen, so a
 * game never travels through navigation arguments.
 *
 * The die is drawn from [random]. A CPU player's turn plays itself with its difficulty's [CpuStrategy], waiting before
 * each roll and move for the screen to finish showing the last one ([GamePacing], set by the screen).
 *
 * The game is saved to [savedGame] as it starts and after every roll and move, and the save is cleared when it finishes,
 * as it is written to the game [records].
 * Its play time ([StartedGame.playMillis]) counts, by [elapsedMillis], only while it's on screen and not held.
 *
 * Given a [tutorial], it plays that guided game instead ([startTutorial]): every roll and move is the script's, only the
 * step's own action is taken, and nothing is kept (no save, no records, no setup saved, and no achievements but
 * finishing it).
 *
 * Every step of a game is shown to an [AchievementTracker], and what it earns player 1 goes to [achievements] (none
 * without one): a new game's start, at [localHour] of its start time, and a saved game's continuing too.
 */
class GameViewModel(
    private val settingsRepository: SettingsRepository,
    private val savedGame: InProgressGameRepository,
    private val records: GameRecordRepository,
    private val random: Random = Random.Default,
    private val elapsedMillis: () -> Long = monotonicMillis(),
    private val now: () -> Long = ::nowEpochMillis,
    private val tutorial: TutorialScript? = null,
    private val achievements: AchievementUnlocker? = null,
    private val localHour: (epochMillis: Long) -> Int = ::localHourOfDay,
    /** Whether this is a debug build: superuser mode ([onDieLongPressed]) never activates without it. */
    private val isDebugBuild: Boolean = false,
    /** The player's Styles picks ([net.zodac.ludo.app.AppContainer.savedStyles]): null until loaded, or none at all in a test. */
    val savedStyles: StateFlow<SavedStyles?> = MutableStateFlow(null),
) : ViewModel() {

    private var pacing = GamePacing()

    /** How long a CPU waits on the screen's animations: set by the screen from the motion settings. */
    fun setPacing(pacing: GamePacing) {
        this.pacing = pacing
    }

    val setupForm = GameSetupForm()

    private val _game = MutableStateFlow<StartedGame?>(null)

    /** The game being played, or null before one is started. */
    val game: StateFlow<StartedGame?> = _game.asStateFlow()

    private var cpuTurn: Job? = null

    /** Whether the game is in front of the player, and not held under a dialog - see [setForeground]. */
    private val foreground = MutableStateFlow(true)
    private var onScreen = true
    private var held = false

    /**
     * Freezes the CPU's turn while the game isn't on screen (the app in the background): it stops where it is and
     * carries on when the player is back, rather than playing out behind their back. Every wait goes through
     * [pausableDelay]; one already under way starts again on return.
     */
    fun setForeground(value: Boolean) {
        onScreen = value
        foregroundChanged()
    }

    /** Holds the CPU's turn while a dialog over the game (the leave confirmation) is up, as if off screen. */
    fun setHeld(value: Boolean) {
        held = value
        foregroundChanged()
    }

    /** When the play time last counted from, while a game is on screen and not over; null otherwise. */
    private var playMark: Long? = null

    private fun foregroundChanged() {
        val value = onScreen && !held
        if (value == foreground.value) return
        foreground.value = value
        val game = _game.value ?: return
        if (value) {
            if (!game.state.isFinished) playMark = elapsedMillis()
        } else {
            // Leaving the screen: what was played so far is saved with it, and counted.
            commit(game)
            playMark = null
            tracker?.let { record(it.flush()) }
        }
    }

    /**
     * [game] with the time played since [playMark] added, which then counts from now (none once it's over), and that
     * time noted for any player who has just been placed: the last one too, as the game ends.
     */
    private fun withPlayTime(game: StartedGame): StartedGame {
        val timed = playMark?.let { mark ->
            val time = elapsedMillis()
            playMark = time.takeUnless { game.state.isFinished }
            game.copy(playMillis = game.playMillis + (time - mark))
        } ?: game
        val finished = timed.state.finishingOrder.filter { it !in timed.finishMillis }
        return if (finished.isEmpty()) timed else timed.copy(finishMillis = timed.finishMillis + finished.associateWith { timed.playMillis })
    }

    /** Makes [game] the one being played, with its play time brought up to date, and saves it, or clears the save once it's over. */
    private fun commit(game: StartedGame) {
        val wasFinished = _game.value?.state?.isFinished == true
        val timed = withPlayTime(game)
        _game.value = timed
        // The tutorial keeps nothing.
        if (tutorial != null) return
        tracker?.let { record(it.playTime(timed.playMillis)) }
        if (!timed.state.isFinished) {
            savedGame.save(timed)
            return
        }
        savedGame.clear()
        if (!wasFinished) {
            val endedAt = now()
            // Written even if the screen is left at once (it starts at once, on the main thread's immediate dispatcher).
            // One that fails costs this game's records, not the app.
            viewModelScope.launch { withContext(NonCancellable) { orNullOnFailure { records.recordGame(timed, endedAt) } } }
        }
    }

    /** [delay] that doesn't run while [foreground] is false - see [setForeground]. */
    private suspend fun pausableDelay(millis: Long) {
        while (true) {
            foreground.first { it }
            if (withTimeoutOrNull(millis) { foreground.first { !it } } == null) return
        }
    }

    /** Whether leaving a game that isn't over asks first (Settings' "Confirm leaving game"). */
    val confirmBeforeLeavingGame: StateFlow<Boolean> =
        settingsRepository.confirmBeforeLeavingGame.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Whether a game started now stops at its first winner (Settings' "Stop at first winner"). */
    private val stopAtFirstWinner: StateFlow<Boolean> =
        settingsRepository.stopAtFirstWinner.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch { setupForm.restore(settingsRepository.snapshot()) }
    }

    /**
     * Starts a game from the setup form, saving its choices for next time. False while the form can't start one (it
     * hasn't been restored yet, or two humans share a name).
     */
    fun startGame(defaultName: (slot: Int) -> String): Boolean {
        if (!setupForm.setupRestored.value) return false
        val setup = setupForm.setup.value
        if (setup.duplicateNameSlots(defaultName).isNotEmpty()) return false
        val players = setupForm.playerConfigs(defaultName)
        val rollOff = RollOff.roll(players.size, setup.gameMode.rules.dieFaces, random)
        cpuTurn?.cancel()
        val previousGame = _game.value?.state
        val startedAt = now()
        tracker = AchievementTracker(players)
        record(AchievementEngine.atGameStart(players, previousGame, localHour(startedAt)))
        playMark = elapsedMillis().takeIf { foreground.value }
        commit(
            StartedGame(
                mode = setup.gameMode,
                players = players,
                state = GameState.new(
                    setup.gameMode,
                    players.size,
                    first = rollOff.first,
                    rules = setup.gameMode.rules.copy(stopAtFirstWinner = stopAtFirstWinner.value),
                ),
                rollOff = rollOff,
                startedAtEpochMillis = startedAt,
            ),
        )
        viewModelScope.launch { setup.saveTo(settingsRepository) }
        playCpuTurn()
        return true
    }

    /**
     * Brings back the saved game, for Continue, or after Android ended the app while the game was in the background.
     * False with none to bring back (one that can't be read is removed): the screen goes back to the menu.
     */
    suspend fun resumeGame(): Boolean {
        val game = savedGame.load() ?: return false
        if (_game.value != null) return true
        playMark = elapsedMillis().takeIf { foreground.value }
        _game.value = game
        tracker = AchievementTracker(game.players)
        record(AchievementEngine.atGameResume())
        playCpuTurn()
        return true
    }

    /**
     * The current player rolls the die, if it's a person's turn and the game is waiting for a roll. In the tutorial, only
     * at a roll step once it's shown, rolling the script's number.
     */
    fun roll() {
        val game = _game.value ?: return
        if (game.state.phase != TurnPhase.AwaitingRoll || game.currentIsCpu) return
        val progress = tutorialProgress
        if (progress == null) {
            update(rolled(game.state))
            return
        }
        val step = progress.current as? TutorialStep.Roll ?: return
        if (progress.shown.value != step) return
        update(LudoEngine.applyRoll(game.state, step.value))
    }

    /**
     * The current player moves [pawn], if it's a person's turn and that pawn has a legal move. In the tutorial, only the
     * move step's own pawn, once the step is shown.
     */
    fun move(pawn: Int) {
        val game = _game.value ?: return
        val phase = game.state.phase
        if (phase !is TurnPhase.AwaitingMove || game.currentIsCpu || phase.moves.none { it.pawn == pawn }) return
        val progress = tutorialProgress
        if (progress != null) {
            val step = progress.current as? TutorialStep.Move ?: return
            if (step.pawn != pawn || progress.shown.value != step) return
        }
        update(LudoEngine.move(game.state, pawn))
    }

    private fun update(state: GameState) {
        val game = _game.value ?: return
        track(game.state, state)
        commit(game.copy(state = state))
        advanceTutorial()
        playCpuTurn()
    }

    /** What the game being played has earned so far: see [achievements]. Null in the tutorial, and before a game. */
    private var tracker: AchievementTracker? = null

    /** Shows the step from [before] to [after] to the [tracker], and records what it earned. */
    private fun track(before: GameState, after: GameState) {
        if (tutorial != null) return
        tracker?.let { record(it.observe(before, after)) }
    }

    /**
     * Records [feats] with [achievements], even if the screen is left at once (it starts at once, on the main thread's
     * immediate dispatcher). Each waits its turn in the unlocker, in the order played.
     */
    private fun record(feats: GameFeats) {
        val unlocker = achievements ?: return
        if (tutorial != null || feats.isEmpty) return
        viewModelScope.launch { withContext(NonCancellable) { orNullOnFailure { unlocker.record(feats) } } }
    }

    // ---- Superuser mode (debug builds only) ----------------------------------------------------
    // A tester's cheat for checking achievements: a long press on the die turns it on (with a toast), and each long press
    // after that offers the next roll's number, whoever rolls it - a person's tap or a CPU's turn. Never in the tutorial.

    private val _superuserModeActive = MutableStateFlow(false)
    val superuserModeActive: StateFlow<Boolean> = _superuserModeActive.asStateFlow()

    private val _rollPickerShown = MutableStateFlow(false)

    /** Whether the superuser's pick of the next roll is up. */
    val rollPickerShown: StateFlow<Boolean> = _rollPickerShown.asStateFlow()

    private val _nextRoll = MutableStateFlow<Int?>(null)

    /** The number the next roll will be, picked in superuser mode; null for a fair roll. */
    val nextRoll: StateFlow<Int?> = _nextRoll.asStateFlow()

    // A Channel, not a StateFlow: a one-shot toast rather than state to replay.
    private val _toastMessages = Channel<String>(Channel.BUFFERED)
    val toastMessages: Flow<String> = _toastMessages.receiveAsFlow()

    /** The game is on screen with the phone on its side: "Sideways", for a game (never the tutorial). */
    fun onShownSideways() {
        if (_game.value == null) return
        record(AchievementEngine.atSideways())
    }

    /** Whether superuser mode can be turned on here: a debug build's game, never the tutorial. */
    val hasSuperuserMode: Boolean
        get() = isDebugBuild && tutorial == null

    /** A long press on the die, in a debug build: turns superuser mode on, then offers the next roll's number. */
    fun onDieLongPressed() {
        if (!hasSuperuserMode) return
        if (!_superuserModeActive.value) {
            _superuserModeActive.value = true
            _toastMessages.trySend("Superuser mode activated! Long-press the die to set the next roll") // i18n: not translated - a debug-build developer toast
        } else {
            _rollPickerShown.value = true
        }
    }

    /** The superuser's pick for the next roll ([value] on the die), or null to put the picker away with the roll left fair. */
    fun pickNextRoll(value: Int?) {
        _rollPickerShown.value = false
        if (!_superuserModeActive.value || value == null) return
        _nextRoll.value = value.coerceIn(1, _game.value?.state?.rules?.dieFaces ?: value)
    }

    /** [state]'s next roll: the superuser's pick, once, or else a fair one. */
    private fun rolled(state: GameState): GameState {
        val picked = _nextRoll.value ?: return LudoEngine.roll(state, random)
        _nextRoll.value = null
        return LudoEngine.applyRoll(state, picked)
    }

    /** Whether this is the guided game: see [tutorial]. */
    val isTutorial: Boolean
        get() = tutorial != null

    /** How far through [tutorial] the player is: null outside one. */
    private val tutorialProgress: TutorialProgress? = tutorial?.let(::TutorialProgress)

    /**
     * The tutorial's step for the coach card and the board: the one waited for, shown once the screen has played out the
     * action before it (the die's tumble, a pawn's move). Null outside a tutorial, and between parts.
     */
    val tutorialStep: StateFlow<TutorialStep?> = tutorialProgress?.shown ?: MutableStateFlow(null)

    /** Which of [tutorial]'s parts is being played, from 0. */
    val tutorialPart: StateFlow<Int> = tutorialProgress?.part ?: MutableStateFlow(0)

    /** Every step of [tutorial], part after part: none outside one. */
    val tutorialSteps: List<TutorialStep>
        get() = tutorial?.parts?.flatMap { it.steps }.orEmpty()

    /** How many parts [tutorial] has. */
    val tutorialParts: Int
        get() = tutorial?.parts?.size ?: 0

    private var tutorialShow: Job? = null

    /** On to the tutorial's next step, shown once the action just taken has played out; at a part's end, the next part. */
    private fun advanceTutorial() {
        val progress = tutorialProgress ?: return
        progress.next()
        val event = _game.value?.state?.lastEvent
        // Only the action's own animation: the coach doesn't think.
        val playOut = pacing.waitAfter(event) - pacing.thinkMillis
        tutorialShow?.cancel()
        tutorialShow = viewModelScope.launch {
            pausableDelay(playOut)
            val step = progress.current
            when {
                step != null -> progress.shown.value = step
                progress.part.value == progress.script.parts.lastIndex -> {
                    // Its last step played: the one thing the tutorial earns.
                    achievements?.let { withContext(NonCancellable) { orNullOnFailure { it.record(AchievementEngine.atTutorialEnd()) } } }
                }
                progress.part.value < progress.script.parts.lastIndex -> {
                    // A beat on the part's last move before the board is set out for the next.
                    pausableDelay(pacing.thinkMillis)
                    startTutorialPart(progress.part.value + 1)
                }
            }
        }
    }

    /** Sets the board out for [tutorial]'s part [part], from its first step. */
    private fun startTutorialPart(part: Int) {
        val progress = tutorialProgress ?: return
        val game = _game.value ?: return
        cpuTurn?.cancel()
        cpuTurn = null
        tutorialShow?.cancel()
        progress.begin(part)
        commit(game.copy(state = progress.script.start(part)))
    }

    /**
     * Starts [tutorial]'s guided game from its first part: the player under their saved name for player 1 ([defaultName]
     * without one), and the CPU as [cpuName].
     */
    suspend fun startTutorial(defaultName: String, cpuName: String) {
        val script = tutorial ?: return
        if (_game.value != null) return
        setupForm.setupRestored.first { it }
        val name = setupForm.setup.value.playerSlots.first().name.trim().ifEmpty { defaultName }
        val players = listOf(
            PlayerConfig(1, PlayerType.HUMAN, name, colour = PlayerColour.defaultFor(TutorialScript.YOU)),
            PlayerConfig(2, PlayerType.AI, cpuName, colour = PlayerColour.defaultFor(TutorialScript.CPU)),
        )
        playMark = elapsedMillis().takeIf { foreground.value }
        tutorialProgress?.begin(0)
        commit(StartedGame(mode = script.gameMode, players = players, state = script.start(0), rollOff = RollOff(emptyList(), TutorialScript.YOU), startedAtEpochMillis = now()))
    }

    /** The coach card's back (-1) and forward (+1): the start of the part before or after, or of this one going back. */
    fun jumpToTutorialPart(direction: Int) {
        val progress = tutorialProgress ?: return
        val target = if (direction < 0 && progress.step > 0) progress.part.value else progress.part.value + direction
        if (target !in progress.script.parts.indices) return
        startTutorialPart(target)
    }

    /** While a CPU player has the turn, it rolls, then moves with its strategy, each once the screen has caught up. */
    private fun playCpuTurn() {
        val game = _game.value ?: return
        if (!game.currentIsCpu || game.state.isFinished || cpuTurn?.isActive == true) return
        cpuTurn = viewModelScope.launch {
            pausableDelay(pacing.waitAfter(game.state.lastEvent))
            val now = _game.value ?: return@launch
            val progress = tutorialProgress
            if (progress != null) {
                // In the tutorial, the CPU plays only its scripted steps.
                val next = when (val step = progress.current) {
                    is TutorialStep.CpuRoll -> LudoEngine.applyRoll(now.state, step.value)
                    is TutorialStep.CpuMove -> LudoEngine.move(now.state, step.pawn)
                    else -> return@launch
                }
                cpuTurn = null
                update(next)
                return@launch
            }
            val next = when (val phase = now.state.phase) {
                TurnPhase.AwaitingRoll -> rolled(now.state)
                is TurnPhase.AwaitingMove -> {
                    val strategy = CpuStrategy.of(now.players[now.state.current].difficulty)
                    LudoEngine.move(now.state, strategy.choose(now.state, phase.moves, random).pawn)
                }
                TurnPhase.Finished -> return@launch
            }
            track(now.state, next)
            commit(now.copy(state = next))
            cpuTurn = null
            playCpuTurn()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameViewModel(
                    settingsRepository = container.settingsRepository,
                    savedGame = container.inProgressGameRepository,
                    records = container.gameRecordRepository,
                    achievements = container.achievementUnlocker,
                    isDebugBuild = container.buildInfo.isDebug,
                    savedStyles = container.savedStyles,
                )
            }
        }

        /** The guided game's: [script] played, nothing kept. */
        fun tutorialFactory(container: AppContainer, script: TutorialScript = TutorialScript.CLASSIC): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameViewModel(
                    settingsRepository = container.settingsRepository,
                    savedGame = container.inProgressGameRepository,
                    records = container.gameRecordRepository,
                    tutorial = script,
                    achievements = container.achievementUnlocker,
                    savedStyles = container.savedStyles,
                )
            }
        }
    }
}

/**
 * A game being played: its [mode], its [players] in seat order (each sits at the seat of their index), its [state], and
 * the [rollOff] that chose who went first. It started at [startedAtEpochMillis], and has been on screen for [playMillis].
 * [finishMillis] holds, for each player placed, the [playMillis] when they were: when they brought every pawn home, or
 * for the last, when the game ended.
 */
data class StartedGame(
    val mode: GameMode,
    val players: List<PlayerConfig>,
    val state: GameState,
    val rollOff: RollOff,
    val startedAtEpochMillis: Long = 0,
    val playMillis: Long = 0,
    val finishMillis: Map<Int, Long> = emptyMap(),
) {
    /** Whether the player whose turn it is is the computer. */
    val currentIsCpu: Boolean get() = players[state.current].type == PlayerType.AI
}

/** Milliseconds from a fixed, unspecified start that the wall clock's changes don't move: for measuring play time. */
private fun monotonicMillis(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}
