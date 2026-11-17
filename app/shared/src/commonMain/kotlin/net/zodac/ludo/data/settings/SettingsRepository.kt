package net.zodac.ludo.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.model.storedIdOf

/**
 * The player's settings and the setup form's remembered choices, in the settings preferences file. Every key's name is
 * permanent once released (`CLAUDE.md`, "Stored ids are permanent").
 *
 * The cloud save's stamping of synced keys (DiceFive's) comes back with Play Games, in Phase 10 of
 * `.claude/IMPLEMENTATION.md`.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    /** Several of the setup form's choices in one write. */
    suspend fun batch(block: Batch.() -> Unit) {
        dataStore.edit { Batch(it).block() }
    }

    class Batch internal constructor(private val prefs: MutablePreferences) {
        fun setPlayerName(slot: Int, name: String) {
            prefs[playerNameKey(slot)] = name
        }

        fun setPlayerType(slot: Int, type: PlayerType) {
            prefs[playerTypeKey(slot)] = type.id
        }

        fun setPlayerDifficulty(slot: Int, difficulty: Difficulty) {
            prefs[playerDifficultyKey(slot)] = difficulty.id
        }

        fun setPlayerColour(slot: Int, colour: PlayerColour) {
            prefs[playerColourKey(slot)] = colour.id
        }

        fun setGameMode(gameMode: GameMode) {
            prefs[GAME_MODE_KEY] = gameMode.id
        }
    }

    /** Settings no version reads any more, removed at launch. None yet. */
    suspend fun forgetRetired() {
        if (RETIRED_KEYS.isEmpty()) return
        dataStore.edit { prefs -> RETIRED_KEYS.forEach { prefs.remove(it) } }
    }

    private fun <T> read(transform: (Preferences) -> T): Flow<T> = dataStore.data.map(transform).distinctUntilChanged()

    /** [key]'s value as it changes, or [default] while it's unset. */
    private fun <T> read(key: Preferences.Key<T>, default: T): Flow<T> = read { prefs -> prefs[key] ?: default }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    /** The settings as they are now, read once. */
    suspend fun snapshot(): Snapshot = Snapshot(dataStore.data.first())

    /** The settings at one moment. */
    class Snapshot internal constructor(private val prefs: Preferences) {

        fun playerNameFor(slot: Int): String? = prefs[playerNameKey(slot)]

        fun playerTypeFor(slot: Int): PlayerType? = prefs[playerTypeKey(slot)]?.let { storedIdOf<PlayerType>(it) }

        fun playerDifficultyFor(slot: Int): Difficulty? = prefs[playerDifficultyKey(slot)]?.let { storedIdOf<Difficulty>(it) }

        fun playerColourFor(slot: Int): PlayerColour? = prefs[playerColourKey(slot)]?.let { storedIdOf<PlayerColour>(it) }

        /** The mode the setup form last started a game in, or the default for one nothing recognises. */
        val gameMode: GameMode get() = prefs[GAME_MODE_KEY]?.let { storedIdOf<GameMode>(it) } ?: GameMode.DEFAULT
    }

    val confirmBeforeLeavingGame: Flow<Boolean> = read(CONFIRM_BEFORE_LEAVING_GAME_KEY, default = true)

    suspend fun setConfirmBeforeLeavingGame(confirm: Boolean) = set(CONFIRM_BEFORE_LEAVING_GAME_KEY, confirm)

    /** Whether a new game ends as soon as one player has every pawn home ([net.zodac.ludo.model.GameRules.stopAtFirstWinner]). */
    val stopAtFirstWinner: Flow<Boolean> = read(STOP_AT_FIRST_WINNER_KEY, default = false)

    suspend fun setStopAtFirstWinner(stop: Boolean) = set(STOP_AT_FIRST_WINNER_KEY, stop)

    /** Whether the Achievements page shows only the achievements not yet earned: its Unearned switch. Off until switched on. */
    val lockedOnlyAchievements: Flow<Boolean> = read(LOCKED_ONLY_ACHIEVEMENTS_KEY, default = false)

    suspend fun setLockedOnlyAchievements(lockedOnly: Boolean) = set(LOCKED_ONLY_ACHIEVEMENTS_KEY, lockedOnly)

    val soundEnabled: Flow<Boolean> = read(SOUND_ENABLED_KEY, default = true)

    suspend fun setSoundEnabled(enabled: Boolean) = set(SOUND_ENABLED_KEY, enabled)

    val vibrationEnabled: Flow<Boolean> = read(VIBRATION_ENABLED_KEY, default = true)

    suspend fun setVibrationEnabled(enabled: Boolean) = set(VIBRATION_ENABLED_KEY, enabled)

    /** The player's "Animations" level - see [AnimationLevel]. */
    val animationLevel: Flow<AnimationLevel> = read { prefs ->
        prefs[ANIMATION_LEVEL_KEY]?.let { storedIdOf<AnimationLevel>(it) } ?: AnimationLevel.default
    }

    /** Sets the "Animations" level, putting every group of the Advanced graphics settings back to its preset. */
    suspend fun setAnimationLevel(level: AnimationLevel) {
        dataStore.edit { prefs ->
            prefs[ANIMATION_LEVEL_KEY] = level.id
            MOTION_KEYS.forEach { prefs.remove(it) }
        }
    }

    /**
     * How the app moves: the Advanced graphics settings the player last set, or the [animationLevel]'s preset where they
     * haven't (since it was last picked). A group saved with an id nothing recognises is the level's.
     */
    val motionSettings: Flow<MotionSettings> = read { prefs ->
        val preset = (prefs[ANIMATION_LEVEL_KEY]?.let { storedIdOf<AnimationLevel>(it) } ?: AnimationLevel.default).motion
        MotionSettings(
            MotionGroup.entries.associateWith { group -> prefs[motionKey(group)]?.let { storedIdOf<FrameRate>(it) } ?: preset.rate(group) },
            prefs[MOTION_IDLE_TIMEOUT_KEY]?.let { storedIdOf<IdleTimeout>(it) } ?: preset.idleTimeout,
        )
    }

    /** Sets every group of the Advanced graphics settings, leaving the saved [animationLevel] for them to fall back to. */
    suspend fun setMotionSettings(motion: MotionSettings) {
        dataStore.edit { prefs ->
            MotionGroup.entries.forEach { prefs[motionKey(it)] = motion.rate(it).id }
            prefs[MOTION_IDLE_TIMEOUT_KEY] = motion.idleTimeout.id
        }
    }

    /** The player's "Orientation" - see [ScreenOrientation]. */
    val screenOrientation: Flow<ScreenOrientation> = read { prefs ->
        prefs[SCREEN_ORIENTATION_KEY]?.let { storedIdOf<ScreenOrientation>(it) } ?: ScreenOrientation.default
    }

    suspend fun setScreenOrientation(orientation: ScreenOrientation) = set(SCREEN_ORIENTATION_KEY, orientation.id)

    // Style ids, not the ui.style types themselves - this is the data layer, and resolving an id to its art is
    // ui.style's job (StyleCatalog). The defaults are DefaultStyleIds, which the default styles themselves are given.
    val boardStyleId: Flow<String> = read(BOARD_STYLE_ID_KEY, default = DefaultStyleIds.BOARD)

    suspend fun setBoardStyleId(id: String) = set(BOARD_STYLE_ID_KEY, id)

    val pawnStyleId: Flow<String> = read(PAWN_STYLE_ID_KEY, default = DefaultStyleIds.PAWNS)

    suspend fun setPawnStyleId(id: String) = set(PAWN_STYLE_ID_KEY, id)

    val diceStyleId: Flow<String> = read(DICE_STYLE_ID_KEY, default = DefaultStyleIds.DICE)

    suspend fun setDiceStyleId(id: String) = set(DICE_STYLE_ID_KEY, id)

    val tableBackgroundId: Flow<String> = read(TABLE_BACKGROUND_ID_KEY, default = DefaultStyleIds.TABLE_BACKGROUND)

    suspend fun setTableBackgroundId(id: String) = set(TABLE_BACKGROUND_ID_KEY, id)

    /** The Styles screen's Unlocked filter: only unlocked styles shown. */
    val unlockedOnlyStyles: Flow<Boolean> = read(UNLOCKED_ONLY_STYLES_KEY, default = false)

    suspend fun setUnlockedOnlyStyles(unlockedOnly: Boolean) = set(UNLOCKED_ONLY_STYLES_KEY, unlockedOnly)

    /**
     * The seconds the app has been in front of the player, as [net.zodac.ludo.app.PlayTimeTracker] saves them: Statistics'
     * time played. Not cleared by anything the player can reset. (DiceFive kept it with the achievements' counters; it
     * can move there with them in Phase 6.)
     */
    val playSeconds: Flow<Int> = read(PLAY_SECONDS_KEY, default = 0)

    /** Adds [seconds] to [playSeconds], in one read and write. */
    suspend fun addPlaySeconds(seconds: Int) {
        dataStore.edit { it[PLAY_SECONDS_KEY] = (it[PLAY_SECONDS_KEY] ?: 0) + seconds }
    }

    private companion object {
        val CONFIRM_BEFORE_LEAVING_GAME_KEY = booleanPreferencesKey("confirm_before_leaving_game")
        val STOP_AT_FIRST_WINNER_KEY = booleanPreferencesKey("stop_at_first_winner")
        val LOCKED_ONLY_ACHIEVEMENTS_KEY = booleanPreferencesKey("locked_only_achievements")
        val SOUND_ENABLED_KEY = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED_KEY = booleanPreferencesKey("vibration_enabled")
        val ANIMATION_LEVEL_KEY = stringPreferencesKey("animation_level")
        val SCREEN_ORIENTATION_KEY = stringPreferencesKey("screen_orientation")
        val GAME_MODE_KEY = stringPreferencesKey("game_mode")
        val PLAY_SECONDS_KEY = intPreferencesKey("play_seconds")

        // Stored ids: permanent once released. The die's and background's keys are DiceFive's.
        val BOARD_STYLE_ID_KEY = stringPreferencesKey("board_style_id")
        val PAWN_STYLE_ID_KEY = stringPreferencesKey("pawn_style_id")
        val DICE_STYLE_ID_KEY = stringPreferencesKey("dice_style_id")
        val TABLE_BACKGROUND_ID_KEY = stringPreferencesKey("table_background_id")
        val UNLOCKED_ONLY_STYLES_KEY = booleanPreferencesKey("unlocked_only_styles")

        // The Advanced graphics settings' groups and Pause when idle, each set only once the player changes one (setMotionSettings).
        fun motionKey(group: MotionGroup) = stringPreferencesKey("motion_${group.key}")
        val MOTION_IDLE_TIMEOUT_KEY = stringPreferencesKey("motion_idle_timeout")
        val MOTION_KEYS = MotionGroup.entries.map(::motionKey) + MOTION_IDLE_TIMEOUT_KEY

        /** Settings no version reads any more, removed at launch ([forgetRetired]). */
        val RETIRED_KEYS: List<Preferences.Key<*>> = emptyList()

        fun playerNameKey(slot: Int) = stringPreferencesKey("player_name_$slot")
        fun playerTypeKey(slot: Int) = stringPreferencesKey("player_type_$slot")
        fun playerColourKey(slot: Int) = stringPreferencesKey("player_colour_$slot")
        fun playerDifficultyKey(slot: Int) = stringPreferencesKey("player_difficulty_$slot")
    }
}
