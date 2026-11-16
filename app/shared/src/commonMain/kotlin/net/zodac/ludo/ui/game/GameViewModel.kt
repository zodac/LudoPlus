package net.zodac.ludo.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlin.random.Random
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
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.CpuStrategy
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.LudoEngine
import net.zodac.ludo.game.RollOff
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType

/**
 * The play graph's state: the setup form, and the game it starts. Shared by the setup screen and the game screen, so a
 * game never travels through navigation arguments.
 *
 * The die is drawn from [random]. A CPU player's turn plays itself with its difficulty's [CpuStrategy], waiting before
 * each roll and move for the screen to finish showing the last one ([GamePacing], set by the screen).
 */
class GameViewModel(
    private val settingsRepository: SettingsRepository,
    private val random: Random = Random.Default,
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
        foreground.value = onScreen && !held
    }

    /** Holds the CPU's turn while a dialog over the game (the leave confirmation) is up, as if off screen. */
    fun setHeld(value: Boolean) {
        held = value
        foreground.value = onScreen && !held
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
        _game.value = StartedGame(
            mode = setup.gameMode,
            players = players,
            state = GameState.new(setup.gameMode, players.size, first = rollOff.first),
            rollOff = rollOff,
        )
        viewModelScope.launch { setup.saveTo(settingsRepository) }
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
        _game.value = _game.value?.copy(state = state)
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
            _game.value = now.copy(state = next)
            cpuTurn = null
            playCpuTurn()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameViewModel(settingsRepository = container.settingsRepository) }
        }
    }
}

/**
 * A game being played: its [mode], its [players] in seat order (each sits at the seat of their index), its [state], and
 * the [rollOff] that chose who went first.
 */
data class StartedGame(val mode: GameMode, val players: List<PlayerConfig>, val state: GameState, val rollOff: RollOff) {
    /** Whether the player whose turn it is is the computer. */
    val currentIsCpu: Boolean get() = players[state.current].type == PlayerType.AI
}
