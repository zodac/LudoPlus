package net.zodac.ludo.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
import net.zodac.ludo.data.Stamped
import net.zodac.ludo.data.orNullOnFailure
import net.zodac.ludo.game.nowEpochMillis
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
 * Every write, the clearing one too, is stamped with when it was written ([STAMP_KEY]): a merge keeps whichever device's
 * game is newest, and a finished game's clearing beats an older copy of it. A stamp is always later than the one before
 * it, even with the device's clock set back, and of two copies of the same game the one further on wins whatever their
 * stamps ([furtherOn]); a game finished here ([FINISHED_KEY]) is never taken from a merge again.
 */
class InProgressGameRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val now: () -> Long = ::nowEpochMillis,
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
                        is Pending.Save -> dataStore.edit {
                            it[GAME_KEY] = SavedGameJson.encode(next.game)
                            it[STAMP_KEY] = nextStamp(it)
                        }
                        is Pending.Clear -> dataStore.edit {
                            it.remove(GAME_KEY)
                            it[STAMP_KEY] = nextStamp(it)
                            next.finished?.let { id -> it[FINISHED_KEY] = id }
                        }
                    }
                }
                written.value = next.sequence
            }
        }
    }

    /** Now, or just after the last stamp if the clock says earlier: a later write never looks older than the one before. */
    private fun nextStamp(prefs: Preferences): Long = maxOf(now(), (prefs[STAMP_KEY] ?: 0) + 1)

    /** Queues [game] to be saved, replacing whatever was still waiting to be. */
    fun save(game: StartedGame) {
        pending.update { current -> if (current is Pending.Save && current.game == game) current else Pending.Save(game, current.nextSequence()) }
    }

    /**
     * Queues the saved game to be removed, replacing whatever was still waiting to be saved. With [finished], the game
     * just over, no copy of that game is taken from a merge again.
     */
    fun clear(finished: StartedGame? = null) {
        pending.update { current ->
            if (current is Pending.Clear && current.finished == finished?.id) current else Pending.Clear(finished?.id, current.nextSequence())
        }
    }

    /**
     * The saved game, once every queued write is done, or null with none. One that can't be read (damaged, or from a
     * newer app) is removed, and is null too: offering it again would fail the same way.
     */
    suspend fun load(): StartedGame? {
        awaitWrites()
        val json = dataStore.data.first()[GAME_KEY] ?: return null
        return runCatching { SavedGameJson.decode(json) }.getOrElse {
            orNullOnFailure { dataStore.edit { prefs -> if (prefs[GAME_KEY] == json) prefs.remove(GAME_KEY) } }
            null
        }
    }

    /** The saved game (as [SavedGameJson]) and when it was written, for the cloud save, once every queued write is done. */
    suspend fun cloudGame(): Stamped<String?> {
        awaitWrites()
        val prefs = dataStore.data.first()
        return Stamped(prefs[GAME_KEY], prefs[STAMP_KEY] ?: 0)
    }

    /**
     * Takes [remote] (another device's game) in place of this device's if it's a copy of the same game further on
     * ([furtherOn]), or else was written later; but never a copy of a game finished here, and never one this app can't
     * read, rather than offering a Continue that can't load.
     */
    suspend fun mergeCloud(remote: Stamped<String?>) {
        awaitWrites()
        val remoteGame = remote.value?.let { readableGame(it) ?: return }
        dataStore.edit { prefs ->
            if (remoteGame?.id != null && remoteGame.id == prefs[FINISHED_KEY]) return@edit
            val local = Stamped(prefs[GAME_KEY], prefs[STAMP_KEY] ?: 0)
            if (!furtherOn(remote, local) && (remote.atEpochMillis <= local.atEpochMillis || furtherOn(local, remote))) return@edit
            remote.value?.let { prefs[GAME_KEY] = it } ?: prefs.remove(GAME_KEY)
            prefs[STAMP_KEY] = maxOf(remote.atEpochMillis, local.atEpochMillis)
        }
    }

    /** Forgets the saved game and its stamps, as on a device that never had one: for another account signing in. */
    suspend fun wipe() {
        awaitWrites()
        dataStore.edit { it.clear() }
    }

    private suspend fun awaitWrites() {
        pending.value?.let { queued -> written.first { it >= queued.sequence } }
    }

    /** One queued write, numbered in the order asked for. */
    private sealed interface Pending {
        val sequence: Int

        data class Save(val game: StartedGame, override val sequence: Int) : Pending

        /** [finished]: the id of the game just over, or null. */
        data class Clear(val finished: String?, override val sequence: Int) : Pending
    }

    private fun Pending?.nextSequence(): Int = (this?.sequence ?: 0) + 1

    private companion object {
        val GAME_KEY = stringPreferencesKey("game")
        val STAMP_KEY = longPreferencesKey("saved_at")

        /** The [StartedGame.id] of the last game finished here. */
        val FINISHED_KEY = stringPreferencesKey("finished_id")
    }
}

/**
 * Whether [a] is a copy of the same game as [b] (the same [StartedGame.id]) further on: more [StartedGame.steps]. Of two
 * copies of one game the further one wins whatever their stamps, so a copy set back by a device's clock, or kept on
 * another device, never takes back a move. False when either is cleared, can't be read, or has no id.
 */
internal fun furtherOn(a: Stamped<String?>, b: Stamped<String?>): Boolean {
    val ours = a.value?.let(::readableGame) ?: return false
    val theirs = b.value?.let(::readableGame) ?: return false
    return ours.id != null && ours.id == theirs.id && ours.steps > theirs.steps
}

private fun readableGame(json: String): StartedGame? = runCatching { SavedGameJson.decode(json) }.getOrNull()
