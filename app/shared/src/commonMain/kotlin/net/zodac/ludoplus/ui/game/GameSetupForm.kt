package net.zodac.ludoplus.ui.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.AiNameGenerator
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.CustomMode
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.TeamMode
import net.zodac.ludoplus.model.duplicateHumanNameSlots
import net.zodac.ludoplus.model.maxPlayerNameLength
import net.zodac.ludoplus.model.resolvePlayerName
import net.zodac.ludoplus.model.sanitizePlayerName
import net.zodac.ludoplus.model.takeNameWidth

/** One seat on the setup form. Slot 1 is always [PlayerType.HUMAN]. */
data class PlayerSetupSlot(
    val slot: Int,
    val type: PlayerType = if (slot == 1) PlayerType.HUMAN else PlayerType.AI,
    /** As typed: blank means the default name for the slot. */
    val name: String = "",
    val difficulty: Difficulty = Difficulty.MEDIUM,
    /** Always distinct across every slot, played or not. */
    val colour: PlayerColour = PlayerColour.defaultFor(slot - 1),
)

/**
 * What the setup form holds: a slot for each of the most players a game takes, of which the first [playerCount] play.
 * The others keep their choices for when the count goes back up.
 */
data class GameSetupState(
    val allSlots: List<PlayerSetupSlot> = (1..BoardGeometry.MAX_PLAYERS).map { PlayerSetupSlot(slot = it) },
    val playerCount: Int = DEFAULT_PLAYER_COUNT,
    val gameMode: GameMode = GameMode.DEFAULT,
    /** How the players make teams: kept whatever the count, though only an even count of four or more has them. */
    val teamMode: TeamMode = TeamMode.OFF,
    val partnerCaptures: Boolean = false,
    val rollForPartners: Boolean = true,
    /** The house rules switched on ([HouseRule]): none by default. Kept while locked, but not played ([playedHouseRules]). */
    val houseRules: Set<HouseRule> = emptySet(),
    /** Whether the House Rules card is open to the player: once a first game is finished (`GameRecordRepository.hasFinishedGame`). */
    val houseRulesUnlocked: Boolean = false,
    /** The player's custom modes ([CustomMode]), in the order they were made. */
    val customModes: List<CustomMode> = emptyList(),
    /** The custom mode picked, by id, or null for [gameMode] itself, which is then the custom mode's base. */
    val customModeId: String? = null,
) {
    /** The custom mode a game started now plays: none while house rules are locked, as custom modes are too. */
    val customMode: CustomMode? get() = customModes.firstOrNull { it.id == customModeId }?.takeIf { houseRulesUnlocked }

    /** The mode a game started now plays: the custom mode's base, or the mode picked. */
    val playedMode: GameMode get() = customMode?.base ?: gameMode

    /** The house rules a game started now plays: the custom mode's, or those switched on, and none while they're locked. */
    val playedHouseRules: Set<HouseRule>
        get() = (customMode?.houseRules ?: if (houseRulesUnlocked) houseRules else emptySet()).filter { it.appliesTo(playedMode.rules) }.toSet()

    /** Each player's team for the game this would start, or null for none ([TeamMode.teamsFor]). */
    val teams: List<Int>? get() = teamMode.teamsFor(playerCount)

    init {
        require(playerCount in BoardGeometry.MIN_PLAYERS..allSlots.size) { "No game of $playerCount players" }
    }

    /** The slots that play. */
    val playerSlots: List<PlayerSetupSlot> get() = allSlots.take(playerCount)

    /** The human slots whose resolved name another human's matches, which the form flags until it's changed. */
    fun duplicateNameSlots(defaultName: (slot: Int) -> String): Set<Int> = duplicateHumanNameSlots(
        playerSlots.map { PlayerConfig(slot = it.slot, type = it.type, name = resolvePlayerName(it.name, defaultName(it.slot))) },
    )

    /** Saves the choices for the next time the form opens. */
    suspend fun saveTo(settingsRepository: SettingsRepository) {
        settingsRepository.batch {
            setGameMode(gameMode)
            setPlayerCount(playerCount)
            setTeams(teamMode, partnerCaptures, rollForPartners)
            setHouseRules(houseRules)
            setCustomModeChoice(customModeId)
            for (slot in allSlots) {
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

    /** Fills the form from [saved], the house rules [houseRulesUnlocked] or not, and flags it restored, in one update. */
    fun restore(saved: SettingsRepository.Snapshot, houseRulesUnlocked: Boolean) {
        val slots = _setup.value.allSlots.map { slot ->
            var updated = slot.copy(name = (saved.playerNameFor(slot.slot) ?: slot.name).takeNameWidth(NAME_CAP))
            // Slot 1 is always Human, so its type and difficulty are never restored.
            if (slot.slot >= 2) {
                saved.playerTypeFor(slot.slot)?.let { updated = updated.copy(type = it) }
                saved.playerDifficultyFor(slot.slot)?.let { updated = updated.copy(difficulty = it) }
            }
            updated
        }
        // A saved set that isn't a different colour a slot is dropped for the defaults, so no two players ever match.
        // Slots saved before there were eight have no colour yet: each takes the first left over.
        val savedColours = slots.map { saved.playerColourFor(it.slot) }
        val distinct = savedColours.filterNotNull().let { it.toSet().size == it.size }
        val spare = PlayerColour.entries.filterNot { it in savedColours }.iterator()
        val restoredColours = if (distinct) savedColours.map { it ?: spare.next() } else null
        _setup.value = GameSetupState(
            allSlots = if (restoredColours == null) slots else slots.mapIndexed { i, slot -> slot.copy(colour = restoredColours[i]) },
            playerCount = saved.playerCount ?: DEFAULT_PLAYER_COUNT,
            gameMode = saved.gameMode,
            teamMode = saved.teamMode,
            partnerCaptures = saved.partnerCaptures,
            rollForPartners = saved.rollForPartners,
            houseRules = saved.houseRules,
            houseRulesUnlocked = houseRulesUnlocked,
            customModes = saved.customModes,
            customModeId = saved.customModeId?.takeIf { id -> saved.customModes.any { it.id == id } },
        )
        _setupRestored.value = true
    }

    fun setPlayerType(slot: Int, type: PlayerType) {
        require(slot != 1) { "Player 1 is always Human" }
        updateSlot(slot) { it.copy(type = type) }
    }

    /** Capped here rather than only in the text field, so the cap holds for every path into a name. */
    fun setPlayerName(slot: Int, name: String) {
        updateSlot(slot) { it.copy(name = sanitizePlayerName(name).takeNameWidth(NAME_CAP)) }
    }

    fun setTeamMode(mode: TeamMode) {
        _setup.update { it.copy(teamMode = mode) }
    }

    fun setPartnerCaptures(on: Boolean) {
        _setup.update { it.copy(partnerCaptures = on) }
    }

    fun setRollForPartners(on: Boolean) {
        _setup.update { it.copy(rollForPartners = on) }
    }

    /** Switches [rule] on or off. */
    fun setHouseRule(rule: HouseRule, on: Boolean) {
        _setup.update { it.copy(houseRules = if (on) it.houseRules + rule else it.houseRules - rule) }
    }

    /** How many players the game has: the first [count] slots play. */
    fun setPlayerCount(count: Int) {
        _setup.update { it.copy(playerCount = count) }
    }

    /**
     * Gives [slot] the colour, and its old one to whichever slot held it, so every slot's is always distinct: a slot not
     * playing gives its up to one that is.
     */
    fun setPlayerColour(slot: Int, colour: PlayerColour) {
        _setup.update { state ->
            val previous = state.allSlots.firstOrNull { it.slot == slot }?.colour ?: return@update state
            state.copy(
                allSlots = state.allSlots.map {
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

    /** Picks a built-in mode, putting any custom mode away. */
    fun setGameMode(gameMode: GameMode) {
        _setup.update { it.copy(gameMode = gameMode, customModeId = null) }
    }

    /** Picks the custom mode [id]: its base becomes the mode picked under it. */
    fun selectCustomMode(id: String) {
        _setup.update { state ->
            val mode = state.customModes.firstOrNull { it.id == id } ?: return@update state
            state.copy(gameMode = mode.base, customModeId = id)
        }
    }

    /** Keeps [mode] (replacing the one with its id, or after the rest if it's new) and picks it. */
    fun saveCustomMode(mode: CustomMode) {
        _setup.update { state ->
            val modes = if (state.customModes.any { it.id == mode.id }) state.customModes.map { if (it.id == mode.id) mode else it } else state.customModes + mode
            state.copy(customModes = modes, gameMode = mode.base, customModeId = mode.id)
        }
    }

    /** Forgets the custom mode [id]; if it was picked, its base is left picked. */
    fun deleteCustomMode(id: String) {
        _setup.update { state ->
            state.copy(customModes = state.customModes.filter { it.id != id }, customModeId = state.customModeId.takeIf { it != id })
        }
    }

    /** The players a game started now has: each unnamed Human under [defaultName], each CPU under a name generated now. */
    fun playerConfigs(defaultName: (slot: Int) -> String): List<PlayerConfig> {
        val setup = _setup.value
        val aiNames = AiNameGenerator.generateNames(
            count = setup.playerSlots.count { it.type == PlayerType.AI },
            playerCount = setup.playerCount,
        ).iterator()
        return setup.playerSlots.map { slot ->
            val name = when (slot.type) {
                PlayerType.HUMAN -> resolvePlayerName(slot.name, defaultName(slot.slot))
                PlayerType.AI -> aiNames.next()
                // Named by the guest who takes the seat (the lobby's); this until then.
                PlayerType.NEARBY -> defaultName(slot.slot)
            }
            PlayerConfig(slot = slot.slot, type = slot.type, name = name, difficulty = slot.difficulty, colour = slot.colour)
        }
    }

    private fun updateSlot(slot: Int, transform: (PlayerSetupSlot) -> PlayerSetupSlot) {
        _setup.update { state -> state.copy(allSlots = state.allSlots.map { if (it.slot == slot) transform(it) else it }) }
    }

    private companion object {
        /** Every name is held to the most players' cap, so changing the count never cuts one. */
        val NAME_CAP = maxPlayerNameLength(BoardGeometry.MAX_PLAYERS)
    }
}

/** How many players a new player's first game has: the classic four. */
const val DEFAULT_PLAYER_COUNT = 4
