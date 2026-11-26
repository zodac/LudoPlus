package net.zodac.ludoplus.ui.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.netplay.Discovery
import net.zodac.ludoplus.netplay.FoundGame
import net.zodac.ludoplus.netplay.GuestSession
import net.zodac.ludoplus.netplay.GuestState
import net.zodac.ludoplus.netplay.Joining
import net.zodac.ludoplus.netplay.LinkEvent
import net.zodac.ludoplus.netplay.Nearby
import net.zodac.ludoplus.netplay.NetCodec
import net.zodac.ludoplus.ui.game.GameViewModel.Companion.RECONNECT_MILLIS

/**
 * This phone in a Nearby game another hosts ([GameViewModel]'s; `.claude/NETPLAY.md`): joining it, its rolls and moves
 * sent to the host, and finding the host again when the link drops. Each of the host's games is handed to [onGame], with
 * this phone's seat; [pausableDelay] doesn't run while the game is off screen.
 */
internal class NearbyJoining(
    private val scope: CoroutineScope,
    private val pausableDelay: suspend (millis: Long) -> Unit,
    private val onGame: (StartedGame) -> Unit,
) {

    private var session: GuestSession? = null
    private var link: Joining? = null

    private val _guestState = MutableStateFlow<GuestState?>(null)

    /** Where this phone is in a game it joined, or null while it hasn't joined one. */
    val guestState: StateFlow<GuestState?> = _guestState.asStateFlow()

    /** Whether this phone has joined a game: from [join] until [leave]. Its rolls and moves are then the host's to make. */
    val isJoined: Boolean get() = session != null

    /** Finding the host again after the link dropped: its discovery, and the time left before giving up. */
    private var reconnecting: Discovery? = null
    private var reconnectTimeout: Job? = null

    /** While reconnecting with no link up: links to the host if the search has found it. */
    private var reconnectTo: (() -> Unit)? = null

    /** While reconnecting: drops the link to a host that wouldn't give the seat back (another of the same name), and tries the next. */
    private var reconnectRefused: (() -> Unit)? = null

    /** Joins [found] over [nearby] as [name] in [colour] (the host may give another): see [guestState]. */
    fun join(nearby: Nearby, found: FoundGame, name: String, colour: PlayerColour?) {
        if (session != null) return
        val joined = GuestSession(onRejoinRefused = { reconnectRefused?.invoke() }) { message -> link?.send(NetCodec.encode(message)) }
        session = joined
        link(nearby, joined, found, name) { joined.hello(name, colour) }
        scope.launch {
            joined.state.collect { state ->
                _guestState.value = state
                if (state is GuestState.Playing) onGame(state.game.copy(guestSeat = state.seat))
                if (state is GuestState.Reconnecting) reconnect(nearby, joined, found.name, name) else stopReconnecting()
            }
        }
    }

    /** Asks the host for this phone's roll. */
    fun roll() {
        session?.roll()
    }

    /** Asks the host for this phone's move of [pawn] ([owner]'s, with [die], landed on [swap]). */
    fun move(pawn: Int, owner: Int?, die: Int?, swap: PawnRef?) {
        session?.move(pawn, owner, die, swap)
    }

    /** Leaves the game joined, telling its host. */
    fun leave() {
        session?.leave()
        stopReconnecting()
        link?.close()
        session = null
        link = null
    }

    /** Links [joined] to [found] as [name], [onConnected] once it's up: its events count only while it's the link of the moment. */
    private fun link(nearby: Nearby, joined: GuestSession, found: FoundGame, name: String, onConnected: () -> Unit) {
        var made: Joining? = null
        made = nearby.join(found, name) { event ->
            if (link !== made) return@join
            when (event) {
                is LinkEvent.Connected -> onConnected()
                is LinkEvent.Received -> joined.received(event.bytes)
                is LinkEvent.Disconnected -> {
                    link = null
                    joined.lost()
                    // A new link that failed: try the next the search finds.
                    reconnectTo?.invoke()
                }
            }
        }
        link = made
    }

    /**
     * Looks for [hostName]'s game again, and, once found, asks for [joined]'s seat back over a new link: for
     * [RECONNECT_MILLIS] at most (while the game is on screen), then gives up. A game of that name that refuses it is
     * another host's: the next one found is tried, and that one isn't again.
     */
    private fun reconnect(nearby: Nearby, joined: GuestSession, hostName: String, name: String) {
        if (reconnecting != null) return
        link?.close()
        link = null
        var games = emptyList<FoundGame>()
        val refusedBy = mutableSetOf<String>()
        var trying: FoundGame? = null
        val tryLink = {
            val host = games.firstOrNull { it.name == hostName && it.endpoint !in refusedBy }
            if (link == null && host != null) {
                trying = host
                link(nearby, joined, host, name, joined::rejoin)
            }
        }
        reconnectTo = tryLink
        reconnectRefused = {
            trying?.let { refusedBy += it.endpoint }
            link?.close()
            link = null
            tryLink()
        }
        reconnecting = nearby.discover {
            games = it
            tryLink()
        }
        reconnectTimeout = scope.launch {
            pausableDelay(RECONNECT_MILLIS)
            joined.giveUp()
        }
    }

    private fun stopReconnecting() {
        reconnecting?.stop()
        reconnecting = null
        reconnectTo = null
        reconnectRefused = null
        reconnectTimeout?.cancel()
        reconnectTimeout = null
    }
}
