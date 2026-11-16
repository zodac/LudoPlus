package net.zodac.ludo.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerConfig

/**
 * The play graph's state: the setup form, and the game it starts. Shared by the setup screen and the game screen, so a
 * game never travels through navigation arguments.
 *
 * Phase 0 of `.claude/IMPLEMENTATION.md`: a game is only its players and mode. The rules engine (Phase 1) and the board
 * (Phase 2) build on this.
 */
class GameViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val setupForm = GameSetupForm()

    private val _game = MutableStateFlow<StartedGame?>(null)

    /** The game being played, or null before one is started. */
    val game: StateFlow<StartedGame?> = _game.asStateFlow()

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
        _game.value = StartedGame(mode = setup.gameMode, players = setupForm.playerConfigs(defaultName))
        viewModelScope.launch { setup.saveTo(settingsRepository) }
        return true
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GameViewModel(settingsRepository = container.settingsRepository) }
        }
    }
}

/** A game as the setup form started it: its [mode] and its [players], in seat order. */
data class StartedGame(val mode: GameMode, val players: List<PlayerConfig>)
