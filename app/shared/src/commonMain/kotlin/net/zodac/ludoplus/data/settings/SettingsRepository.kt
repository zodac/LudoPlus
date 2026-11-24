package net.zodac.ludoplus.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.zodac.ludoplus.data.Stamped
import net.zodac.ludoplus.data.newerOf
import net.zodac.ludoplus.game.nowEpochMillis
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.CustomMode
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.GameRules
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.ModePage
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.TeamMode
import net.zodac.ludoplus.model.storedIdOf

/**
 * The player's settings and the setup form's remembered choices, in the settings preferences file. Every key's name is
 * permanent once released (`CLAUDE.md`, "Stored ids are permanent").
 *
 * Every setting but the Graphics page's (the Orientation, the Animations level and its Advanced settings) and the two list
 * filters goes in the cloud save and progress files ([SYNCED_KEYS]): those are about this device, what it can draw and
 * how it's held, and how this player likes to browse. Each synced key is stamped with when its value last changed
 * ([stampKey]), and a merge keeps whichever device set it last.
 */
class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val now: () -> Long = ::nowEpochMillis,
) {

    /** Every write: [block], then a stamp on each synced key whose value it changed. */
    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { prefs ->
            val before = SYNCED_KEYS.map { prefs[it] }
            block(prefs)
            val at = now()
            SYNCED_KEYS.forEachIndexed { index, key -> if (prefs[key] != before[index]) prefs[SYNCED_STAMP_KEYS[index]] = at }
        }
    }

    /** Several of the setup form's choices in one write. */
    suspend fun batch(block: Batch.() -> Unit) {
        edit { Batch(it).block() }
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

        fun setPlayerCount(count: Int) {
            prefs[PLAYER_COUNT_KEY] = count
        }

        fun setTeams(mode: TeamMode, partnerCaptures: Boolean, rollForPartners: Boolean) {
            prefs[TEAM_MODE_KEY] = mode.id
            prefs[PARTNER_CAPTURES_KEY] = partnerCaptures
            prefs[ROLL_FOR_PARTNERS_KEY] = rollForPartners
        }

        fun setHouseRules(houseRules: Set<HouseRule>) {
            prefs[HOUSE_RULES_KEY] = houseRules.map { it.id }.toSet()
        }

        /** How many pawns each the Pawns house rule has, or null while it's off. */
        fun setPawns(pawns: Int?) {
            if (pawns == null) prefs.remove(PAWNS_KEY) else prefs[PAWNS_KEY] = pawns
        }

        /** The custom mode New Game has picked, by id, or none for a built-in mode ([setGameMode]'s). */
        fun setCustomModeChoice(id: String?) {
            if (id == null) prefs.remove(CUSTOM_MODE_KEY) else prefs[CUSTOM_MODE_KEY] = id
        }
    }

    /** Emits each time a synced setting changes here, or arrives from a merge: not for the first value. */
    val syncedChanges: Flow<Unit> =
        dataStore.data.map { prefs -> SYNCED_STAMP_KEYS.map { prefs[it] } }.distinctUntilChanged().drop(1).map { }

    /** The synced settings, by key name, each with its stamp: every one that is set, or was set and since removed. */
    suspend fun cloudSettings(): Map<String, Stamped<SettingValue?>> {
        val prefs = dataStore.data.first()
        return SYNCED.mapNotNull { synced ->
            val value = synced.read(prefs)
            val at = prefs[stampKey(synced.key)]
            if (value == null && at == null) null else synced.key.name to Stamped(value, at ?: 0)
        }.toMap()
    }

    /**
     * Takes each of the cloud save's [remote] settings that was set later than this device's, keeping its stamp. A key
     * this build doesn't sync, or a value of the wrong type, is skipped.
     */
    suspend fun mergeCloud(remote: Map<String, Stamped<SettingValue?>>) {
        dataStore.edit { prefs ->
            for ((name, theirs) in remote) {
                val synced = SYNCED_BY_NAME[name] ?: continue
                val ours = Stamped(synced.read(prefs), prefs[stampKey(synced.key)] ?: 0)
                if (ours == theirs || newerOf(ours, theirs) { it.toString() } !== theirs) continue
                if (synced.write(prefs, theirs.value)) prefs[stampKey(synced.key)] = theirs.atEpochMillis
            }
        }
    }

    /** Forgets every synced setting and its stamp, as on a device that never set one: for another account signing in. */
    suspend fun wipeSynced() {
        dataStore.edit { prefs ->
            for (key in SYNCED_KEYS) {
                prefs.remove(key)
                prefs.remove(stampKey(key))
            }
        }
    }

    /**
     * The seconds played that an older version kept here, before they moved to the achievements' counters
     * ([net.zodac.ludoplus.model.AchievementCounter.PLAY_SECONDS]): 0 once [forgetRetired] has removed them.
     */
    suspend fun legacyPlaySeconds(): Int = dataStore.data.first()[PLAY_SECONDS_KEY] ?: 0

    /**
     * Removes the settings no version reads any more ([RETIRED_KEYS]). Nothing is written when none is there (DataStore
     * skips an edit that changes nothing), so it's cheap to run on every launch.
     */
    suspend fun forgetRetired() {
        dataStore.edit { prefs -> RETIRED_KEYS.forEach { prefs.remove(it) } }
    }

    private fun <T> read(transform: (Preferences) -> T): Flow<T> = dataStore.data.map(transform).distinctUntilChanged()

    /** [key]'s value as it changes, or [default] while it's unset. */
    private fun <T> read(key: Preferences.Key<T>, default: T): Flow<T> = read { prefs -> prefs[key] ?: default }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        edit { it[key] = value }
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

        /** How many players the setup form last started a game with, or null for none it can start. */
        val playerCount: Int? get() = prefs[PLAYER_COUNT_KEY]?.takeIf { it in BoardGeometry.MIN_PLAYERS..BoardGeometry.MAX_PLAYERS }

        /** How the setup form last made teams, and its two options: off, and the rules' defaults, until it's chosen. */
        val teamMode: TeamMode get() = prefs[TEAM_MODE_KEY]?.let { storedIdOf<TeamMode>(it) } ?: TeamMode.OFF
        val partnerCaptures: Boolean get() = prefs[PARTNER_CAPTURES_KEY] ?: false
        val rollForPartners: Boolean get() = prefs[ROLL_FOR_PARTNERS_KEY] ?: true

        /** The house rules the setup form last had on: none until it's chosen, any this app doesn't know dropped. */
        val houseRules: Set<HouseRule> get() = prefs[HOUSE_RULES_KEY].orEmpty().mapNotNull { storedIdOf<HouseRule>(it) }.toSet()

        /** How many pawns each the Pawns house rule last had: null for off, or for a number no game has. */
        val pawns: Int? get() = prefs[PAWNS_KEY]?.takeIf { it in GameRules.PAWN_CHOICES }

        /** The player's custom modes, in the order they were made ([CustomModesJson]). */
        val customModes: List<CustomMode> get() = prefs[CUSTOM_MODES_KEY]?.let(CustomModesJson::decode).orEmpty()

        /** The custom mode New Game last picked, by id: null for a built-in mode. */
        val customModeId: String? get() = prefs[CUSTOM_MODE_KEY]
    }

    /** Keeps [modes] as the player's custom modes, replacing those kept before. */
    suspend fun setCustomModes(modes: List<CustomMode>) = set(CUSTOM_MODES_KEY, CustomModesJson.encode(modes))

    val confirmBeforeLeavingGame: Flow<Boolean> = read(CONFIRM_BEFORE_LEAVING_GAME_KEY, default = true)

    suspend fun setConfirmBeforeLeavingGame(confirm: Boolean) = set(CONFIRM_BEFORE_LEAVING_GAME_KEY, confirm)

    /** Whether a new game ends as soon as one player has every pawn home ([net.zodac.ludoplus.model.GameRules.stopAtFirstWinner]). */
    val stopAtFirstWinner: Flow<Boolean> = read(STOP_AT_FIRST_WINNER_KEY, default = false)

    suspend fun setStopAtFirstWinner(stop: Boolean) = set(STOP_AT_FIRST_WINNER_KEY, stop)

    /** Whether the Achievements page shows only the achievements not yet earned: its Unearned switch. Off until switched on. */
    val lockedOnlyAchievements: Flow<Boolean> = read(LOCKED_ONLY_ACHIEVEMENTS_KEY, default = false)

    suspend fun setLockedOnlyAchievements(lockedOnly: Boolean) = set(LOCKED_ONLY_ACHIEVEMENTS_KEY, lockedOnly)

    /** The Leaderboard's board: the mode last picked from its drop-down, or the default for none or one nothing recognises. */
    val leaderboardMode: Flow<GameMode> = read { prefs -> prefs[LEADERBOARD_CHOICE_KEY]?.let { storedIdOf<GameMode>(it) } ?: GameMode.DEFAULT }

    suspend fun setLeaderboardMode(mode: GameMode) = set(LEADERBOARD_CHOICE_KEY, mode.id)

    /** The Statistics page last picked from its drop-down ([ModePage.parse]). */
    val statisticsPage: Flow<ModePage> = read { prefs -> ModePage.parse(prefs[STATISTICS_CHOICE_KEY]) }

    suspend fun setStatisticsPage(page: ModePage) = set(STATISTICS_CHOICE_KEY, page.stored)

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

    private companion object {
        val CONFIRM_BEFORE_LEAVING_GAME_KEY = booleanPreferencesKey("confirm_before_leaving_game")
        val STOP_AT_FIRST_WINNER_KEY = booleanPreferencesKey("stop_at_first_winner")
        val LOCKED_ONLY_ACHIEVEMENTS_KEY = booleanPreferencesKey("locked_only_achievements")
        val LEADERBOARD_CHOICE_KEY = stringPreferencesKey("leaderboard_choice")
        val STATISTICS_CHOICE_KEY = stringPreferencesKey("statistics_choice")
        val SOUND_ENABLED_KEY = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED_KEY = booleanPreferencesKey("vibration_enabled")
        val ANIMATION_LEVEL_KEY = stringPreferencesKey("animation_level")
        val SCREEN_ORIENTATION_KEY = stringPreferencesKey("screen_orientation")
        val GAME_MODE_KEY = stringPreferencesKey("game_mode")
        val PLAYER_COUNT_KEY = intPreferencesKey("player_count")
        val TEAM_MODE_KEY = stringPreferencesKey("team_mode")
        val PARTNER_CAPTURES_KEY = booleanPreferencesKey("partner_captures")
        val ROLL_FOR_PARTNERS_KEY = booleanPreferencesKey("roll_for_partners")
        val HOUSE_RULES_KEY = stringSetPreferencesKey("house_rules")
        val PAWNS_KEY = intPreferencesKey("pawns")
        val CUSTOM_MODES_KEY = stringPreferencesKey("custom_modes")
        val CUSTOM_MODE_KEY = stringPreferencesKey("custom_mode")

        /** Where the seconds played were kept until they moved to the achievements' counters: retired. */
        val PLAY_SECONDS_KEY = intPreferencesKey("play_seconds")

        // Stored ids: permanent once released. The die's and background's keys came with their art.
        val BOARD_STYLE_ID_KEY = stringPreferencesKey("board_style_id")
        val PAWN_STYLE_ID_KEY = stringPreferencesKey("pawn_style_id")
        val DICE_STYLE_ID_KEY = stringPreferencesKey("dice_style_id")
        val TABLE_BACKGROUND_ID_KEY = stringPreferencesKey("table_background_id")
        val UNLOCKED_ONLY_STYLES_KEY = booleanPreferencesKey("unlocked_only_styles")

        // The Advanced graphics settings' groups and Pause when idle, each set only once the player changes one (setMotionSettings).
        fun motionKey(group: MotionGroup) = stringPreferencesKey("motion_${group.key}")
        val MOTION_IDLE_TIMEOUT_KEY = stringPreferencesKey("motion_idle_timeout")
        val MOTION_KEYS = MotionGroup.entries.map(::motionKey) + MOTION_IDLE_TIMEOUT_KEY

        /** Settings no version reads any more, removed at launch ([forgetRetired]). Never in the cloud save. */
        val RETIRED_KEYS: List<Preferences.Key<*>> = listOf(PLAY_SECONDS_KEY)

        fun playerNameKey(slot: Int) = stringPreferencesKey("player_name_$slot")
        fun playerTypeKey(slot: Int) = stringPreferencesKey("player_type_$slot")
        fun playerColourKey(slot: Int) = stringPreferencesKey("player_colour_$slot")
        fun playerDifficultyKey(slot: Int) = stringPreferencesKey("player_difficulty_$slot")

        /**
         * What the cloud save carries: everything but the Graphics page's settings ([ANIMATION_LEVEL_KEY], [MOTION_KEYS],
         * [SCREEN_ORIENTATION_KEY]), chosen for this device, and the two list filters ([LOCKED_ONLY_ACHIEVEMENTS_KEY],
         * [UNLOCKED_ONLY_STYLES_KEY]), a way of browsing.
         */
        private val SYNCED: List<SyncedKey<*>> = buildList {
            listOf(
                GAME_MODE_KEY, TEAM_MODE_KEY, CUSTOM_MODES_KEY, CUSTOM_MODE_KEY, LEADERBOARD_CHOICE_KEY, STATISTICS_CHOICE_KEY,
                BOARD_STYLE_ID_KEY, PAWN_STYLE_ID_KEY, DICE_STYLE_ID_KEY, TABLE_BACKGROUND_ID_KEY,
            ).mapTo(this) { SyncedKey.Text(it) }
            for (slot in 1..BoardGeometry.MAX_PLAYERS) {
                listOf(playerNameKey(slot), playerTypeKey(slot), playerColourKey(slot), playerDifficultyKey(slot)).mapTo(this) { SyncedKey.Text(it) }
            }
            add(SyncedKey.Number(PLAYER_COUNT_KEY))
            add(SyncedKey.Number(PAWNS_KEY))
            listOf(
                PARTNER_CAPTURES_KEY, ROLL_FOR_PARTNERS_KEY, CONFIRM_BEFORE_LEAVING_GAME_KEY, STOP_AT_FIRST_WINNER_KEY, SOUND_ENABLED_KEY,
                VIBRATION_ENABLED_KEY,
            ).mapTo(this) { SyncedKey.Flag(it) }
            add(SyncedKey.TextSet(HOUSE_RULES_KEY))
        }
        private val SYNCED_BY_NAME = SYNCED.associateBy { it.key.name }

        /** Each of [SYNCED]'s keys. */
        val SYNCED_KEYS: List<Preferences.Key<*>> = SYNCED.map { it.key }

        /** Each of [SYNCED_KEYS]' [stampKey], in the same order. */
        private val SYNCED_STAMP_KEYS = SYNCED_KEYS.map(::stampKey)

        /** When [key] last changed: `synced_at:<key>`. */
        fun stampKey(key: Preferences.Key<*>) = longPreferencesKey("synced_at:${key.name}")
    }
}

/** A setting the cloud save carries: its [key], and its value as the [SettingValue] the cloud save holds. */
private sealed class SyncedKey<T : Any>(val key: Preferences.Key<T>) {

    /** [stored] as the cloud save holds it. */
    protected abstract fun valueOf(stored: T): SettingValue

    /** [value] as [key] stores it, or null for a value of another type. */
    protected abstract fun storedOf(value: SettingValue): T?

    fun read(prefs: Preferences): SettingValue? = prefs[key]?.let(::valueOf)

    /** Sets [key] to [value] (null: removes it). False if [value] isn't [key]'s type, and nothing is written. */
    fun write(prefs: MutablePreferences, value: SettingValue?): Boolean {
        if (value == null) {
            prefs.remove(key)
            return true
        }
        prefs[key] = storedOf(value) ?: return false
        return true
    }

    class Text(key: Preferences.Key<String>) : SyncedKey<String>(key) {
        override fun valueOf(stored: String) = SettingValue.Text(stored)

        override fun storedOf(value: SettingValue) = (value as? SettingValue.Text)?.value
    }

    class Number(key: Preferences.Key<Int>) : SyncedKey<Int>(key) {
        override fun valueOf(stored: Int) = SettingValue.Number(stored)

        override fun storedOf(value: SettingValue) = (value as? SettingValue.Number)?.value
    }

    class Flag(key: Preferences.Key<Boolean>) : SyncedKey<Boolean>(key) {
        override fun valueOf(stored: Boolean) = SettingValue.Flag(stored)

        override fun storedOf(value: SettingValue) = (value as? SettingValue.Flag)?.value
    }

    class TextSet(key: Preferences.Key<Set<String>>) : SyncedKey<Set<String>>(key) {
        override fun valueOf(stored: Set<String>) = SettingValue.TextSet(stored)

        override fun storedOf(value: SettingValue) = (value as? SettingValue.TextSet)?.value
    }
}
