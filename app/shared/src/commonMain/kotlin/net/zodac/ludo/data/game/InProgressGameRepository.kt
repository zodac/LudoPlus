package net.zodac.ludo.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.zodac.ludo.data.orNullOnFailure
import net.zodac.ludo.ui.game.StartedGame

/**
 * Keeps at most one game in progress ([StartedGame], as [SavedGameJson]) so the menu's Continue can bring it back,
 * across process death too. Cleared once the game finishes, or when a new game takes its place.
 *
 * Writes are queued rather than awaited, and conflated: while one write is under way only the latest game waiting
 * behind it is kept, since it supersedes the ones before. The queue runs in [scope], which lives as long as this
 * repository (the process), not the screen that asked, so leaving a game can't strand its last move unwritten. A write
 * that fails is dropped and the queue carries on; [load] waits for every write already queued, so it never reads a move
 * older than the last one asked for.
 *
 * DiceFive's cloud save stamps come back with Play Games' cloud save (Phase 10 of `.claude/IMPLEMENTATION.md`).
 */
class InProgressGameRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /** Whether a game is saved: only when that changes, not on every move saved. */
    val hasInProgressGame: Flow<Boolean> = dataStore.data.map { it[GAME_KEY] != null }.distinctUntilChanged()

    /** What should be on disk next, or null before anything's asked. */
    private val pending = MutableStateFlow<Pending?>(null)

    /** The [Pending.sequence] of the last write finished (or dropped), 0 before any. */
    private val written = MutableStateFlow(0)

    init {
        scope.launch {
            // A StateFlow's collector only ever sees the latest value, which is the conflation.
            pending.filterNotNull().collect { next ->
                // One lost save costs Continue a move or two, not the app: the queue stays alive for the next.
                orNullOnFailure {
                    when (next) {
                        is Pending.Save -> dataStore.edit { it[GAME_KEY] = SavedGameJson.encode(next.game) }
                        is Pending.Clear -> dataStore.edit { it.remove(GAME_KEY) }
                    }
                }
                written.value = next.sequence
            }
        }
    }

    /** Queues [game] to be saved, replacing whatever was still waiting to be. */
    fun save(game: StartedGame) {
        pending.update { current -> if (current is Pending.Save && current.game == game) current else Pending.Save(game, current.nextSequence()) }
    }

    /** Queues the saved game to be removed, replacing whatever was still waiting to be saved. */
    fun clear() {
        pending.update { current -> current as? Pending.Clear ?: Pending.Clear(current.nextSequence()) }
    }

    /**
     * The saved game, once every queued write is done, or null with none. One that can't be read (damaged, or from a
     * newer app) is removed, and is null too: offering it again would fail the same way.
     */
    suspend fun load(): StartedGame? {
        pending.value?.let { queued -> written.first { it >= queued.sequence } }
        val json = dataStore.data.first()[GAME_KEY] ?: return null
        return runCatching { SavedGameJson.decode(json) }.getOrElse {
            orNullOnFailure { dataStore.edit { prefs -> if (prefs[GAME_KEY] == json) prefs.remove(GAME_KEY) } }
            null
        }
    }

    /** One queued write, numbered in the order asked for. */
    private sealed interface Pending {
        val sequence: Int

        data class Save(val game: StartedGame, override val sequence: Int) : Pending

        data class Clear(override val sequence: Int) : Pending
    }

    private fun Pending?.nextSequence(): Int = (this?.sequence ?: 0) + 1

    private companion object {
        val GAME_KEY = stringPreferencesKey("game")
    }
}
