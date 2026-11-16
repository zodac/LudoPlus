package net.zodac.ludo.ui.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.AiNameGenerator
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.model.duplicateHumanNameSlots
import net.zodac.ludo.model.maxPlayerNameLength
import net.zodac.ludo.model.resolvePlayerName
import net.zodac.ludo.model.sanitizePlayerName
import net.zodac.ludo.model.takeNameWidth

/** One seat on the setup form. Slot 1 is always [PlayerType.HUMAN]. */
data class PlayerSetupSlot(
    val slot: Int,
    val type: PlayerType = if (slot == 1) PlayerType.HUMAN else PlayerType.AI,
    /** As typed: blank means the default name for the slot. */
    val name: String = "",
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /** Always distinct across the four slots. */
    val colour: PlayerColour = PlayerColour.defaultFor(slot - 1),
)

/** What the setup form holds. */
data class GameSetupState(
    val playerSlots: List<PlayerSetupSlot> = (1..GameMode.PLAYER_COUNT).map { PlayerSetupSlot(slot = it) },
    val gameMode: GameMode = GameMode.DEFAULT,
) {
    /** The human slots whose resolved name another human's matches, which the form flags until it's changed. */
    fun duplicateNameSlots(defaultName: (slot: Int) -> String): Set<Int> = duplicateHumanNameSlots(
        playerSlots.map { PlayerConfig(slot = it.slot, type = it.type, name = resolvePlayerName(it.name, defaultName(it.slot))) },
    )

    /** Saves the choices for the next time the form opens. */
    suspend fun saveTo(settingsRepository: SettingsRepository) {
        settingsRepository.batch {
            setGameMode(gameMode)
            for (slot in playerSlots) {
                setPlayerName(slot.slot, slot.name)
                setPlayerType(slot.slot, slot.type)
                setPlayerDifficulty(slot.slot, slot.difficulty)
                setPlayerColour(slot.slot, slot.colour)
            }
        }
    }
}

/** The setup form's state and its edits, held by the game's view model so it survives a trip to the board and back. */
class GameSetupForm {

    private val _setup = MutableStateFlow(GameSetupState())
    val setup: StateFlow<GameSetupState> = _setup.asStateFlow()

    /** Whether [setup] holds the player's saved choices yet. Until then the setup screen shows no form. */
    private val _setupRestored = MutableStateFlow(false)
    val setupRestored: StateFlow<Boolean> = _setupRestored.asStateFlow()

    /** Fills the form from [saved] and flags it restored, in one update. */
    fun restore(saved: SettingsRepository.Snapshot) {
        val cap = maxPlayerNameLength(GameMode.PLAYER_COUNT)
        val slots = _setup.value.playerSlots.map { slot ->
            var updated = slot.copy(name = (saved.playerNameFor(slot.slot) ?: slot.name).takeNameWidth(cap))
            // Slot 1 is always Human, so its type and difficulty are never restored.
            if (slot.slot >= 2) {
                saved.playerTypeFor(slot.slot)?.let { updated = updated.copy(type = it) }
                saved.playerDifficultyFor(slot.slot)?.let { updated = updated.copy(difficulty = it) }
            }
            updated
        }
        // A saved set that isn't four different colours is dropped for the defaults, so no two players ever match.
        val colours = slots.map { saved.playerColourFor(it.slot) }
        val restoredColours = colours.filterNotNull().takeIf { it.size == colours.size && it.toSet().size == it.size }
        _setup.value = GameSetupState(
            playerSlots = if (restoredColours == null) slots else slots.mapIndexed { i, slot -> slot.copy(colour = restoredColours[i]) },
            gameMode = saved.gameMode,
        )
        _setupRestored.value = true
    }

    fun setPlayerType(slot: Int, type: PlayerType) {
        require(slot != 1) { "Player 1 is always Human" }
        updateSlot(slot) { it.copy(type = type) }
    }

    /** Capped here rather than only in the text field, so the cap holds for every path into a name. */
    fun setPlayerName(slot: Int, name: String) {
        val cap = maxPlayerNameLength(GameMode.PLAYER_COUNT)
        updateSlot(slot) { it.copy(name = sanitizePlayerName(name).takeNameWidth(cap)) }
    }

    /** Gives [slot] the colour, and its old one to whichever slot held it, so the four are always distinct. */
    fun setPlayerColour(slot: Int, colour: PlayerColour) {
        _setup.update { state ->
            val previous = state.playerSlots.firstOrNull { it.slot == slot }?.colour ?: return@update state
            state.copy(
                playerSlots = state.playerSlots.map {
                    when {
                        it.slot == slot -> it.copy(colour = colour)
                        it.colour == colour -> it.copy(colour = previous)
                        else -> it
                    }
                },
            )
        }
    }

    fun setPlayerDifficulty(slot: Int, difficulty: Difficulty) {
        updateSlot(slot) { it.copy(difficulty = difficulty) }
    }

    fun setGameMode(gameMode: GameMode) {
        _setup.update { it.copy(gameMode = gameMode) }
    }

    /** The players a game started now has: each unnamed Human under [defaultName], each CPU under a name generated now. */
    fun playerConfigs(defaultName: (slot: Int) -> String): List<PlayerConfig> {
        val setup = _setup.value
        val aiNames = AiNameGenerator.generateNames(
            count = setup.playerSlots.count { it.type == PlayerType.AI },
            playerCount = GameMode.PLAYER_COUNT,
        ).iterator()
        return setup.playerSlots.map { slot ->
            val name = when (slot.type) {
                PlayerType.HUMAN -> resolvePlayerName(slot.name, defaultName(slot.slot))
                PlayerType.AI -> aiNames.next()
            }
            PlayerConfig(slot = slot.slot, type = slot.type, name = name, difficulty = slot.difficulty, colour = slot.colour)
        }
    }

    private fun updateSlot(slot: Int, transform: (PlayerSetupSlot) -> PlayerSetupSlot) {
        _setup.update { state -> state.copy(playerSlots = state.playerSlots.map { if (it.slot == slot) transform(it) else it }) }
    }
}
