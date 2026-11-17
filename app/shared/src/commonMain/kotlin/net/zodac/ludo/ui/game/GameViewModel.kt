package net.zodac.ludo.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.data.game.InProgressGameRepository
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.CpuStrategy
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.LudoEngine
import net.zodac.ludo.game.RollOff
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.game.nowEpochMillis
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType

/**
 * The play graph's state: the setup form, and the game it starts. Shared by the setup screen and the game screen, so a
 * game never travels through navigation arguments.
 *
 * The die is drawn from [random]. A CPU player's turn plays itself with its difficulty's [CpuStrategy], waiting before
 * each roll and move for the screen to finish showing the last one ([GamePacing], set by the screen).
 *
 * The game is saved to [savedGame] as it starts and after every roll and move, and the save is cleared when it finishes.
 * Its play time ([StartedGame.playMillis]) counts, by [elapsedMillis], only while it's on screen and not held.
 */
class GameViewModel(
    private val settingsRepository: SettingsRepository,
    private val savedGame: InProgressGameRepository,
    private val random: Random = Random.Default,
    private val elapsedMillis: () -> Long = monotonicMillis(),
    private val now: () -> Long = ::nowEpochMillis,
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
            // Leaving the screen: what was played so far is saved with it.
            commit(game)
            playMark = null
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
        val timed = withPlayTime(game)
        _game.value = timed
        if (timed.state.isFinished) savedGame.clear() else savedGame.save(timed)
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
        playMark = elapsedMillis().takeIf { foreground.value }
        commit(
            StartedGame(
                mode = setup.gameMode,
                players = players,
                state = GameState.new(setup.gameMode, players.size, first = rollOff.first),
                rollOff = rollOff,
                startedAtEpochMillis = now(),
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
        playCpuTurn()
        return true
    }

    /** The current player rolls the die, if it's a person's turn and the game is waiting for a roll. */
    fun roll() {
        val game = _game.value ?: return
        if (game.state.phase != TurnPhase.AwaitingRoll || game.currentIsCpu) return
        update(LudoEngine.roll(game.state, random))
    }

    /** The current player moves [pawn], if it's a person's turn and that pawn has a legal move. */
    fun move(pawn: Int) {
        val game = _game.value ?: return
        val phase = game.state.phase
        if (phase !is TurnPhase.AwaitingMove || game.currentIsCpu || phase.moves.none { it.pawn == pawn }) return
        update(LudoEngine.move(game.state, pawn))
    }

    private fun update(state: GameState) {
        val game = _game.value ?: return
        commit(game.copy(state = state))
        playCpuTurn()
    }

    /** While a CPU player has the turn, it rolls, then moves with its strategy, each once the screen has caught up. */
    private fun playCpuTurn() {
        val game = _game.value ?: return
        if (!game.currentIsCpu || game.state.isFinished || cpuTurn?.isActive == true) return
        cpuTurn = viewModelScope.launch {
            pausableDelay(pacing.waitAfter(game.state.lastEvent))
            val now = _game.value ?: return@launch
            val next = when (val phase = now.state.phase) {
                TurnPhase.AwaitingRoll -> LudoEngine.roll(now.state, random)
                is TurnPhase.AwaitingMove -> {
                    val strategy = CpuStrategy.of(now.players[now.state.current].difficulty)
                    LudoEngine.move(now.state, strategy.choose(now.state, phase.moves, random).pawn)
                }
                TurnPhase.Finished -> return@launch
            }
            commit(now.copy(state = next))
            cpuTurn = null
            playCpuTurn()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameViewModel(settingsRepository = container.settingsRepository, savedGame = container.inProgressGameRepository) }
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
