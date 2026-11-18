package net.zodac.ludo.netplay

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.zodac.ludo.data.game.SavedGameJson
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.sanitizePlayerName
import net.zodac.ludo.ui.game.StartedGame

/** What a guest's player asked the host to do, from [seat]: the host checks it as it checks a tap. */
sealed interface GuestAction {
    val seat: Int

    data class Roll(override val seat: Int) : GuestAction

    data class Move(override val seat: Int, val pawn: Int, val owner: Int?, val die: Int?) : GuestAction

    /** The guest at [seat] left a game under way: the CPU plays its seat from now on. */
    data class Left(override val seat: Int) : GuestAction
}

/**
 * The host's side of a Nearby session, as a state machine: the lobby filling its [SeatKind.NEARBY] seats from guests'
 * [NetMessage.Hello]s, then, once [start]ed, each guest's actions passed on ([actions]) and each new state of the game
 * sent to every guest ([gameChanged]). [send] hands a message to the transport for one guest, by its endpoint id. No
 * radio here: the transport calls [connected], [received], and [disconnected]. Main thread only.
 */
class HostSession(seats: List<LobbySeat>, private val send: (endpoint: String, NetMessage) -> Unit) {

    private val mutableSeats = MutableStateFlow(seats)

    /** The table as it stands: every seat, a guest's named once taken. */
    val seats: StateFlow<List<LobbySeat>> = mutableSeats.asStateFlow()

    /** Each guest's seat, by endpoint id: a guest is here from its [NetMessage.Welcome] until it leaves. */
    private val guests = LinkedHashMap<String, Int>()

    private val mutableActions = MutableSharedFlow<GuestAction>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Each guest's roll, move, or leaving, once the game has started, in the order they arrived. */
    val actions: SharedFlow<GuestAction> = mutableActions.asSharedFlow()

    /** Whether the game has started: no guest joins after, and a leaving guest's seat goes to the CPU. */
    var started: Boolean = false
        private set

    private var gameNumber = 0

    /** Whether every Nearby seat has a guest in it: the host can start only then. */
    val full: Boolean get() = mutableSeats.value.none { it.open }

    /** The seats a guest is playing, while they're connected. */
    val guestSeats: Set<Int> get() = guests.values.toSet()

    /** A guest's link is up: it isn't seated until its [NetMessage.Hello]. */
    fun connected(endpoint: String) = Unit

    fun received(endpoint: String, bytes: ByteArray) {
        NetCodec.decode(bytes)?.let { handle(endpoint, it) }
    }

    fun handle(endpoint: String, message: NetMessage) {
        when (message) {
            is NetMessage.Hello -> hello(endpoint, message)
            NetMessage.Roll -> guests[endpoint]?.takeIf { started }?.let { mutableActions.tryEmit(GuestAction.Roll(it)) }
            is NetMessage.Move -> guests[endpoint]?.takeIf { started }?.let {
                mutableActions.tryEmit(GuestAction.Move(it, message.pawn, message.owner, message.die))
            }
            NetMessage.Leave -> disconnected(endpoint)
            // A host's message, sent to a host: nothing to do.
            else -> Unit
        }
    }

    private fun hello(endpoint: String, hello: NetMessage.Hello) {
        val refusal = when {
            hello.protocol != PROTOCOL_VERSION -> RefusedReason.VERSION
            started -> RefusedReason.STARTED
            endpoint in guests -> null
            mutableSeats.value.none { it.open } -> RefusedReason.FULL
            else -> null
        }
        if (refusal != null) {
            send(endpoint, NetMessage.Refused(refusal))
            return
        }
        val seat = guests[endpoint] ?: mutableSeats.value.indexOfFirst { it.open }.also { guests[endpoint] = it }
        val name = sanitizePlayerName(hello.name).trim().ifEmpty { DEFAULT_GUEST_NAME }
        mutableSeats.value = mutableSeats.value.mapIndexed { index, lobbySeat ->
            if (index != seat) return@mapIndexed lobbySeat
            // The guest's colour where no other seat has it; the seat's own otherwise.
            val colour = hello.colour?.takeIf { wanted -> mutableSeats.value.withIndex().none { (other, it) -> other != seat && it.colour == wanted } }
            lobbySeat.copy(name = name, colour = colour ?: lobbySeat.colour)
        }
        send(endpoint, NetMessage.Welcome(seat))
        broadcast(NetMessage.Lobby(mutableSeats.value))
    }

    /** A guest's link is gone, or it left: its seat opens again in the lobby, or the CPU takes it in a game. */
    fun disconnected(endpoint: String) {
        val seat = guests.remove(endpoint) ?: return
        if (started) {
            mutableActions.tryEmit(GuestAction.Left(seat))
        } else {
            mutableSeats.value = mutableSeats.value.mapIndexed { index, it -> if (index == seat) it.copy(name = null) else it }
            broadcast(NetMessage.Lobby(mutableSeats.value))
        }
    }

    /** Starts the game, once every Nearby seat is [full]: false otherwise, and nothing changes. */
    fun start(): Boolean {
        if (!full) return false
        started = true
        return true
    }

    /** Sends every guest the game as it now stands. */
    fun gameChanged(game: StartedGame) {
        broadcast(NetMessage.Game(++gameNumber, SavedGameJson.encode(game)))
    }

    /** Ends the session for every guest. */
    fun end() {
        broadcast(NetMessage.Ended)
        guests.clear()
    }

    private fun broadcast(message: NetMessage) {
        for (endpoint in guests.keys) send(endpoint, message)
    }

    private companion object {
        /** A guest whose name is nothing but what [sanitizePlayerName] removes. */
        const val DEFAULT_GUEST_NAME = "Guest" // i18n: not translated - a fallback name, as a typed one would be
    }
}

/** Where a guest is in a session. */
sealed interface GuestState {
    /** Its [NetMessage.Hello] sent, waiting for the host's answer. */
    data object Joining : GuestState

    /** Seated at [seat], waiting for the host to start; [seats] the table as the host last sent it. */
    data class Seated(val seat: Int, val seats: List<LobbySeat>) : GuestState

    /** In the game, at [seat]: [game] as the host last sent it. */
    data class Playing(val seat: Int, val game: StartedGame) : GuestState

    data class Refused(val reason: RefusedReason) : GuestState

    /** The host ended the session. */
    data object Ended : GuestState

    /** The link to the host is gone. */
    data object Lost : GuestState
}

/**
 * A guest's side of a Nearby session, as a state machine: its [NetMessage.Hello], then the host's answers ([state]), and
 * its player's roll and move sent while it's their turn. [send] hands a message to the transport for the host. No radio
 * here: the transport calls [received] and [lost]. Main thread only.
 */
class GuestSession(private val send: (NetMessage) -> Unit) {

    private val mutableState = MutableStateFlow<GuestState>(GuestState.Joining)
    val state: StateFlow<GuestState> = mutableState.asStateFlow()

    /** The number of the last game the host sent: an older one, arriving late, is ignored. */
    private var gameNumber = 0

    /** Asks the host for a seat, for a player called [name] in [colour], if it's free. */
    fun hello(name: String, colour: PlayerColour?) {
        send(NetMessage.Hello(PROTOCOL_VERSION, name, colour))
    }

    fun received(bytes: ByteArray) {
        NetCodec.decode(bytes)?.let(::handle)
    }

    fun handle(message: NetMessage) {
        val now = mutableState.value
        mutableState.value = when (message) {
            is NetMessage.Welcome -> GuestState.Seated(message.seat, (now as? GuestState.Seated)?.seats.orEmpty())
            is NetMessage.Lobby -> (now as? GuestState.Seated)?.copy(seats = message.seats) ?: now
            is NetMessage.Game -> {
                val seat = seatOf(now) ?: return
                if (message.number <= gameNumber) return
                val game = runCatching { SavedGameJson.decode(message.json) }.getOrNull() ?: return
                gameNumber = message.number
                GuestState.Playing(seat, game)
            }
            is NetMessage.Refused -> GuestState.Refused(message.reason)
            NetMessage.Ended -> GuestState.Ended
            // A guest's message, sent to a guest: nothing to do.
            else -> now
        }
    }

    /** The link to the host is gone, unless the session had already ended. */
    fun lost() {
        if (mutableState.value !is GuestState.Ended && mutableState.value !is GuestState.Refused) mutableState.value = GuestState.Lost
    }

    /** Rolls, while it's this guest's player's turn to. */
    fun roll() {
        val playing = mutableState.value as? GuestState.Playing ?: return
        if (playing.game.state.current == playing.seat && playing.game.state.phase == TurnPhase.AwaitingRoll) send(NetMessage.Roll)
    }

    /** Moves [pawn] (of [owner], a partner's) with [die], while it's this guest's player's turn to. */
    fun move(pawn: Int, owner: Int? = null, die: Int? = null) {
        val playing = mutableState.value as? GuestState.Playing ?: return
        if (playing.game.state.current == playing.seat && playing.game.state.phase is TurnPhase.AwaitingMove) send(NetMessage.Move(pawn, owner, die))
    }

    /** Leaves the session. */
    fun leave() {
        send(NetMessage.Leave)
        mutableState.value = GuestState.Ended
    }

    private fun seatOf(state: GuestState): Int? = when (state) {
        is GuestState.Seated -> state.seat
        is GuestState.Playing -> state.seat
        else -> null
    }
}
