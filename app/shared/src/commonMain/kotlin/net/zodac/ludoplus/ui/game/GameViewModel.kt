package net.zodac.ludoplus.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlin.uuid.Uuid
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
import net.zodac.ludoplus.app.AchievementUnlocker
import net.zodac.ludoplus.app.AppContainer
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.orNullOnFailure
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.settings.SavedStyles
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.AchievementEngine
import net.zodac.ludoplus.game.AchievementTracker
import net.zodac.ludoplus.game.CpuStrategy
import net.zodac.ludoplus.game.GameFeats
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.RollOff
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.game.TutorialScript
import net.zodac.ludoplus.game.TutorialStep
import net.zodac.ludoplus.game.localHourOfDay
import net.zodac.ludoplus.game.nowEpochMillis
import net.zodac.ludoplus.model.CustomMode
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.withHouseRules
import net.zodac.ludoplus.netplay.Discovery
import net.zodac.ludoplus.netplay.FoundGame
import net.zodac.ludoplus.netplay.GuestAction
import net.zodac.ludoplus.netplay.GuestSession
import net.zodac.ludoplus.netplay.GuestState
import net.zodac.ludoplus.netplay.HostSession
import net.zodac.ludoplus.netplay.Hosting
import net.zodac.ludoplus.netplay.Joining
import net.zodac.ludoplus.netplay.LinkEvent
import net.zodac.ludoplus.netplay.LobbySeat
import net.zodac.ludoplus.netplay.Nearby
import net.zodac.ludoplus.netplay.NetCodec
import net.zodac.ludoplus.netplay.SeatKind
import net.zodac.ludoplus.online.OnlineSyncRequests

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
    /** The player's Styles picks ([net.zodac.ludoplus.app.AppContainer.savedStyles]): null until loaded, or none at all in a test. */
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
        val previous = _game.value
        val wasFinished = previous?.state?.isFinished == true
        val stepped = if (previous != null && previous.id == game.id && previous.state != game.state) game.copy(steps = game.steps + 1) else game
        val timed = withPlayTime(stepped)
        _game.value = timed
        // The tutorial keeps nothing.
        if (tutorial != null) return
        hostSession?.takeIf { it.started }?.let { session ->
            session.gameChanged(timed)
            if (timedOutSeat != timed.state.current) timedOutSeat = null
            timeGuestTurn(timed)
        }
        tracker?.let { record(it.playTime(timed.playMillis)) }
        // A guest's game is the host's to keep: nothing to save here, but its own seat's records.
        if (timed.guestSeat != null) {
            if (timed.state.isFinished && !wasFinished) recordFinished(timed)
            return
        }
        if (!timed.state.isFinished) {
            savedGame.save(timed)
            return
        }
        savedGame.clear(finished = timed)
        if (!wasFinished) recordFinished(timed)
    }

    /** Writes the finished [game]'s records, once. */
    private fun recordFinished(game: StartedGame) {
        val endedAt = now()
        // Written even if the screen is left at once (it starts at once, on the main thread's immediate dispatcher).
        // One that fails costs this game's records, not the app.
        viewModelScope.launch {
            withContext(NonCancellable) { orNullOnFailure { records.recordGame(game, endedAt) } }
            // The win reaches the online leaderboards, and the record the cloud save.
            OnlineSyncRequests.request()
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
        // The house rules unlock with a first finished game; records that can't be read leave them locked.
        viewModelScope.launch { setupForm.restore(settingsRepository.snapshot(), houseRulesUnlocked = orNullOnFailure { records.hasFinishedGame() } ?: false) }
    }

    /**
     * Starts a game from the setup form, saving its choices for next time. False while the form can't start one (it
     * hasn't been restored yet, or two humans share a name).
     */
    fun startGame(defaultName: (slot: Int) -> String): Boolean = startGame(defaultName, lobby = null)

    /** [startGame], with each Nearby seat's player named and coloured as they took it in the [lobby]. */
    private fun startGame(defaultName: (slot: Int) -> String, lobby: List<LobbySeat>?): Boolean {
        if (!setupForm.setupRestored.value) return false
        val setup = setupForm.setup.value
        if (setup.duplicateNameSlots(defaultName).isNotEmpty()) return false
        val players = setupForm.playerConfigs(defaultName).mapIndexed { seat, player ->
            val taken = lobby?.getOrNull(seat)?.takeIf { player.type == PlayerType.NEARBY }
            if (taken == null) player else player.copy(name = taken.name ?: player.name, colour = taken.colour)
        }
        val mode = setup.playedMode
        val rollOff = RollOff.roll(players.size, mode.rules.dieFaces, random)
        cpuTurn?.cancel()
        val previousGame = _game.value?.state
        val startedAt = now()
        playMark = elapsedMillis().takeIf { foreground.value }
        val started = StartedGame(
            mode = mode,
            players = players,
            state = GameState.new(
                mode,
                players.size,
                first = rollOff.first,
                rules = mode.rules.forPlayers(players.size).copy(
                    stopAtFirstWinner = stopAtFirstWinner.value,
                    teams = setup.teams,
                    partnerCaptures = setup.partnerCaptures,
                    rollForPartners = setup.rollForPartners,
                ).withHouseRules(setup.playedHouseRules),
            ),
            rollOff = rollOff,
            startedAtEpochMillis = startedAt,
            customName = setup.customMode?.name,
            id = Uuid.random().toHexString(),
        )
        // A game with house rules earns nothing.
        tracker = if (started.earnsAchievements) AchievementTracker(players) else null
        if (started.earnsAchievements) record(AchievementEngine.atGameStart(players, previousGame, localHour(startedAt)))
        commit(started)
        viewModelScope.launch { setup.saveTo(settingsRepository) }
        playCpuTurn()
        return true
    }

    /** Keeps [mode] among the player's custom modes, picked on the form, and saves them all at once. */
    fun saveCustomMode(mode: CustomMode) {
        setupForm.saveCustomMode(mode)
        saveCustomModes()
    }

    /** Forgets the custom mode [id], and saves the rest at once. */
    fun deleteCustomMode(id: String) {
        setupForm.deleteCustomMode(id)
        saveCustomModes()
    }

    private fun saveCustomModes() {
        val modes = setupForm.setup.value.customModes
        // Written even if the screen is left at once; one that fails costs the change, not the app.
        viewModelScope.launch { withContext(NonCancellable) { orNullOnFailure { settingsRepository.setCustomModes(modes) } } }
    }

    /**
     * Brings back the saved game, for Continue, or after Android ended the app while the game was in the background.
     * False with none to bring back (one that can't be read is removed): the screen goes back to the menu.
     */
    suspend fun resumeGame(): Boolean {
        // A Nearby game's guests aren't here to continue it: the CPU plays their seats.
        val game = savedGame.load()?.let { saved -> saved.copy(players = saved.players.map { if (it.type == PlayerType.NEARBY) it.copy(type = PlayerType.AI) else it }) }
            ?: return false
        if (_game.value != null) return true
        playMark = elapsedMillis().takeIf { foreground.value }
        _game.value = game
        tracker = if (game.earnsAchievements) AchievementTracker(game.players) else null
        if (game.earnsAchievements) record(AchievementEngine.atGameResume())
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
        // A guest's roll is the host's to make.
        guestSession?.let {
            it.roll()
            return
        }
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
     * The current player moves [pawn] (a partner's, [owner]'s, when they roll for one; with two dice, by [die]), if it's a
     * person's turn and that pawn has a legal move. In the tutorial, only the move step's own pawn, once the step is shown.
     */
    fun move(pawn: Int, owner: Int? = null, die: Int? = null) {
        val game = _game.value ?: return
        val phase = game.state.phase
        if (phase !is TurnPhase.AwaitingMove || game.currentIsCpu || phase.moves.none { it.pawn == pawn && it.owner == owner && (die == null || it.roll == die) }) return
        guestSession?.let {
            it.move(pawn, owner, die)
            return
        }
        val progress = tutorialProgress
        if (progress != null) {
            val step = progress.current as? TutorialStep.Move ?: return
            if (step.pawn != pawn || progress.shown.value != step) return
        }
        update(LudoEngine.move(game.state, pawn, owner, die))
    }

    /** The current player makes [move] ([move] by pawn, owner, and die). */
    fun move(move: Move) = move(move.pawn, move.owner, move.roll)

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
        if (_game.value?.earnsAchievements != true) return
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
        // With two dice, the pick is the first; the second is fair.
        return LudoEngine.applyDice(state, listOf(picked) + List(state.rules.dice - 1) { random.nextInt(1, state.rules.dieFaces + 1) })
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

    private var automaticMove: Job? = null

    /**
     * While a person played here (or on the host, a Nearby guest) awaits a move with one choice, makes it for them
     * ([LudoEngine.automaticMove]) once the screen has caught up, unless they make it first. Never in the tutorial, and
     * never on a guest's phone: the host makes it.
     */
    private fun playAutomaticMove(game: StartedGame) {
        automaticMove?.cancel()
        automaticMove = null
        if (tutorial != null || game.guestSeat != null) return
        val move = LudoEngine.automaticMove(game.state) ?: return
        automaticMove = viewModelScope.launch {
            pausableDelay(pacing.waitAfter(game.state.lastEvent))
            if (_game.value?.state != game.state) return@launch
            automaticMove = null
            update(LudoEngine.move(game.state, move))
        }
    }

    /**
     * While a CPU player has the turn, it rolls, then moves with its strategy, each once the screen has caught up. While
     * anyone else has it, a move with no choice is made for them ([playAutomaticMove]).
     */
    private fun playCpuTurn() {
        val game = _game.value ?: return
        if (!computerPlays(game)) {
            playAutomaticMove(game)
            return
        }
        if (game.state.isFinished || cpuTurn?.isActive == true) return
        cpuTurn = viewModelScope.launch {
            pausableDelay(pacing.waitAfter(game.state.lastEvent))
            val now = _game.value ?: return@launch
            // A guest back in its seat plays it again.
            if (!computerPlays(now)) {
                cpuTurn = null
                return@launch
            }
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
                    val choice = strategy.choose(now.state, phase.moves, random)
                    LudoEngine.move(now.state, choice)
                }
                TurnPhase.Finished -> return@launch
            }
            track(now.state, next)
            commit(now.copy(state = next))
            cpuTurn = null
            playCpuTurn()
        }
    }

    // ---- Nearby: hosting (.claude/NETPLAY.md) ----

    private var hostSession: HostSession? = null
    private var hosting: Hosting? = null
    private var guestActions: Job? = null

    private val _awaySeats = MutableStateFlow<Set<Int>>(emptySet())

    /** The Nearby seats whose phone has dropped out of the game this phone hosts: the CPU plays them until they're back. */
    val awaySeats: StateFlow<Set<Int>> = _awaySeats.asStateFlow()

    /** The seat whose guest let its turn run out ([GUEST_TURN_MILLIS]): the CPU plays the rest of that turn for it. */
    private var timedOutSeat: Int? = null
    private var guestTurn: Job? = null
    private var guestTurnState: GameState? = null

    /**
     * Whether the computer plays the current player's turn: a CPU player's; and on the host, a Nearby seat's whose phone
     * is away, or whose turn has run out.
     */
    private fun computerPlays(game: StartedGame): Boolean {
        if (game.currentIsComputer) return true
        val seat = game.state.current
        val session = hostSession?.takeIf { it.started } ?: return false
        return game.players[seat].type == PlayerType.NEARBY && (seat in session.away.value || seat == timedOutSeat)
    }

    /**
     * On the host, gives a guest whose turn it is [GUEST_TURN_MILLIS] (while the game is on screen) for each roll and
     * move: one it doesn't make by then, the CPU makes, and the rest of the turn with it.
     */
    private fun timeGuestTurn(game: StartedGame) {
        val waiting = !game.state.isFinished && game.players[game.state.current].type == PlayerType.NEARBY && !computerPlays(game)
        if (waiting && guestTurn?.isActive == true && guestTurnState == game.state) return
        guestTurn?.cancel()
        guestTurn = null
        if (!waiting) return
        guestTurnState = game.state
        guestTurn = viewModelScope.launch {
            pausableDelay(GUEST_TURN_MILLIS)
            guestTurn = null
            if (_game.value?.state != game.state) return@launch
            timedOutSeat = game.state.current
            playCpuTurn()
        }
    }

    /** The lobby's table while this phone hosts, or null while it doesn't. */
    private val _lobby = MutableStateFlow<List<LobbySeat>?>(null)
    val lobby: StateFlow<List<LobbySeat>?> = _lobby.asStateFlow()

    /** Whether the form's game has a Nearby seat: Start then opens the lobby, rather than the board. */
    val setupHasNearbySeats: Boolean get() = setupForm.setup.value.playerSlots.any { it.type == PlayerType.NEARBY }

    /**
     * Makes this phone findable over [nearby] as [hostName]'s game, with the form's seats at the table: each Nearby seat
     * open until a guest takes it ([lobby]). Until [startHostedGame] or [closeLobby].
     */
    fun openLobby(nearby: Nearby, hostName: String, defaultName: (slot: Int) -> String) {
        if (hostSession != null) return
        val seats = setupForm.playerConfigs(defaultName).map { player ->
            val kind = when (player.type) {
                PlayerType.HUMAN -> SeatKind.HOST
                PlayerType.AI -> SeatKind.CPU
                PlayerType.NEARBY -> SeatKind.NEARBY
            }
            LobbySeat(kind, player.name.takeUnless { kind == SeatKind.NEARBY }, player.colour)
        }
        lateinit var link: Hosting
        val session = HostSession(seats) { endpoint, message -> link.send(endpoint, NetCodec.encode(message)) }
        link = nearby.host(hostName) { event ->
            when (event) {
                is LinkEvent.Connected -> session.connected(event.endpoint)
                is LinkEvent.Received -> session.received(event.endpoint, event.bytes)
                is LinkEvent.Disconnected -> session.disconnected(event.endpoint)
            }
        }
        hostSession = session
        hosting = link
        viewModelScope.launch { session.seats.collect { _lobby.value = it } }
        viewModelScope.launch {
            session.away.collect { away ->
                _awaySeats.value = away
                // Findable again while a guest's seat waits for it.
                if (session.started) link.setFindable(away.isNotEmpty())
            }
        }
        guestActions = viewModelScope.launch { session.actions.collect(::onGuestAction) }
    }

    /** Starts the lobby's game, once every Nearby seat is taken: false otherwise. Guests see it as it starts. */
    fun startHostedGame(defaultName: (slot: Int) -> String): Boolean {
        val session = hostSession ?: return false
        if (!session.start()) return false
        hosting?.setFindable(false)
        return startGame(defaultName, session.seats.value).also { started -> if (started) _game.value?.let(session::gameChanged) }
    }

    /** Stops hosting: every guest is told the session is over. */
    fun closeLobby() {
        hostSession?.end()
        hosting?.close()
        guestActions?.cancel()
        guestTurn?.cancel()
        hostSession = null
        hosting = null
        _lobby.value = null
        _awaySeats.value = emptySet()
    }

    /**
     * A guest's roll, move, leaving, or link dropping or coming back, checked as a tap at this phone is, from its own seat
     * on its own turn. A guest's roll or move takes back a turn that ran out.
     */
    private fun onGuestAction(action: GuestAction) {
        val game = _game.value ?: return
        if (game.players.getOrNull(action.seat)?.type != PlayerType.NEARBY) return
        val theirTurn = game.state.current == action.seat
        when (action) {
            is GuestAction.Left -> {
                commit(game.copy(players = game.players.mapIndexed { seat, it -> if (seat == action.seat) it.copy(type = PlayerType.AI) else it }))
                playCpuTurn()
            }
            is GuestAction.Away -> {
                timeGuestTurn(game)
                playCpuTurn()
            }
            is GuestAction.Back -> if (theirTurn) takeBackTurn(game)
            is GuestAction.Roll -> {
                if (!theirTurn || game.state.phase != TurnPhase.AwaitingRoll) return
                takeBackTurn(game)
                update(rolled(game.state))
            }
            is GuestAction.Move -> {
                val phase = game.state.phase as? TurnPhase.AwaitingMove ?: return
                if (!theirTurn || phase.moves.none { it.pawn == action.pawn && it.owner == action.owner && (action.die == null || it.roll == action.die) }) return
                takeBackTurn(game)
                update(LudoEngine.move(game.state, action.pawn, action.owner, action.die))
            }
        }
    }

    /** The current guest plays its own turn again: the CPU's, if it was playing it, stops, and the guest's time starts. */
    private fun takeBackTurn(game: StartedGame) {
        timedOutSeat = null
        cpuTurn?.cancel()
        cpuTurn = null
        timeGuestTurn(game)
    }

    // ---- Nearby: joining ----

    private var guestSession: GuestSession? = null
    private var joining: Joining? = null

    private val _guestState = MutableStateFlow<GuestState?>(null)

    /** Where this phone is in a game it joined, or null while it hasn't joined one. */
    val guestState: StateFlow<GuestState?> = _guestState.asStateFlow()

    /** Finding the host again after the link dropped: its discovery, and the time left before giving up. */
    private var reconnecting: Discovery? = null
    private var reconnectTimeout: Job? = null

    /** Joins [found] over [nearby] as [name] in [colour] (the host may give another): see [guestState]. */
    fun join(nearby: Nearby, found: FoundGame, name: String, colour: PlayerColour?) {
        if (guestSession != null) return
        val session = GuestSession { message -> joining?.send(NetCodec.encode(message)) }
        guestSession = session
        link(nearby, session, found, name) { session.hello(name, colour) }
        viewModelScope.launch {
            session.state.collect { state ->
                _guestState.value = state
                if (state is GuestState.Playing) showGuestGame(state.game.copy(guestSeat = state.seat))
                if (state is GuestState.Reconnecting) reconnect(nearby, session, found.name, name) else stopReconnecting()
            }
        }
    }

    /** Links [session] to [found] as [name], [onConnected] once it's up: its events count only while it's the link of the moment. */
    private fun link(nearby: Nearby, session: GuestSession, found: FoundGame, name: String, onConnected: () -> Unit) {
        var link: Joining? = null
        link = nearby.join(found, name) { event ->
            if (joining !== link) return@join
            when (event) {
                is LinkEvent.Connected -> onConnected()
                is LinkEvent.Received -> session.received(event.bytes)
                is LinkEvent.Disconnected -> {
                    joining = null
                    session.lost()
                    // A new link that failed: try the next the search finds.
                    reconnectTo?.invoke()
                }
            }
        }
        joining = link
    }

    /** While reconnecting with no link up: links to the host if the search has found it. */
    private var reconnectTo: (() -> Unit)? = null

    /**
     * Looks for [hostName]'s game again, and, once found, asks for [session]'s seat back over a new link: for
     * [RECONNECT_MILLIS] at most (while the game is on screen), then gives up.
     */
    private fun reconnect(nearby: Nearby, session: GuestSession, hostName: String, name: String) {
        if (reconnecting != null) return
        joining?.close()
        joining = null
        var games = emptyList<FoundGame>()
        val tryLink = {
            val host = games.firstOrNull { it.name == hostName }
            if (joining == null && host != null) link(nearby, session, host, name, session::rejoin)
        }
        reconnectTo = tryLink
        reconnecting = nearby.discover {
            games = it
            tryLink()
        }
        reconnectTimeout = viewModelScope.launch {
            pausableDelay(RECONNECT_MILLIS)
            session.giveUp()
        }
    }

    private fun stopReconnecting() {
        reconnecting?.stop()
        reconnecting = null
        reconnectTo = null
        reconnectTimeout?.cancel()
        reconnectTimeout = null
    }

    /** Leaves the game joined, telling its host. */
    fun leaveJoined() {
        guestSession?.leave()
        stopReconnecting()
        joining?.close()
        guestSession = null
        joining = null
    }

    /** The host's latest [game], shown here: its first sets up this phone's achievements for its own seat. */
    private fun showGuestGame(game: StartedGame) {
        val before = _game.value
        val seat = checkNotNull(game.guestSeat)
        if (before == null) {
            playMark = elapsedMillis().takeIf { foreground.value }
            tracker = if (game.earnsAchievements) AchievementTracker(game.players, mySeat = seat) else null
            if (game.earnsAchievements) record(AchievementEngine.atGameStart(game.players, previousGame = null, localHour(now()), mySeat = seat))
        } else {
            track(before.state, game.state)
        }
        commit(game.copy(playMillis = before?.playMillis ?: game.playMillis, steps = before?.steps ?: 0))
    }

    override fun onCleared() {
        closeLobby()
        leaveJoined()
    }

    companion object {
        /** How long a guest has for each roll and move before the CPU makes it: see [timeGuestTurn]. */
        const val GUEST_TURN_MILLIS = 60_000L

        /** How long a guest whose link dropped looks for its host before giving up. */
        const val RECONNECT_MILLIS = 120_000L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameViewModel(
                    settingsRepository = container.settingsRepository,
                    savedGame = container.inProgressGameRepository,
                    records = container.gameRecordRepository,
                    achievements = container.achievementUnlocker,
                    isDebugBuild = container.buildInfo.isDebug,
                    savedStyles = container.savedStyles,
                    random = container.diceRandom,
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
    /** A custom mode's name ([net.zodac.ludoplus.model.CustomMode]), the game shown under it: null for a built-in mode. */
    val customName: String? = null,
    /**
     * The game's own id, random and kept by every copy of it: a merge of two saves tells two copies of one game from two
     * games by it. Null for a game with none (the tutorial's).
     */
    val id: String? = null,
    /** How many times its [state] has changed since it started: of two copies of one game, the one with more is further on. */
    val steps: Int = 0,
    /**
     * The seat this phone plays, on a guest's phone in a Nearby game (`.claude/NETPLAY.md`): every other seat is played
     * elsewhere. Null on the host and in a game on one phone. Never saved.
     */
    val guestSeat: Int? = null,
) {
    /**
     * Whether the player whose turn it is isn't played at this phone: the computer, a Nearby seat on the host, or, on a
     * guest's phone, anyone but its own seat. The screen takes no roll or move for them.
     */
    val currentIsCpu: Boolean
        get() = if (guestSeat != null) state.current != guestSeat else players[state.current].type != PlayerType.HUMAN

    /**
     * The move made for the player whose turn it is while they have no choice to make ([LudoEngine.automaticMove]), so
     * the screen asks for no pick: null on a turn not played at this phone. Outside the tutorial, which makes none.
     */
    val automaticMove: Move? get() = if (currentIsCpu) null else LudoEngine.automaticMove(state)

    /** Whether the player whose turn it is is the computer, which the host plays. */
    val currentIsComputer: Boolean get() = guestSeat == null && players[state.current].type == PlayerType.AI

    /**
     * Whether the game earns achievements: in a mode that earns them ([GameMode.earnsAchievements]), and with no house
     * rule on ([net.zodac.ludoplus.model.GameRules.hasHouseRules]).
     */
    val earnsAchievements: Boolean get() = mode.earnsAchievements && !state.rules.hasHouseRules
}

/** Milliseconds from a fixed, unspecified start that the wall clock's changes don't move: for measuring play time. */
private fun monotonicMillis(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}
