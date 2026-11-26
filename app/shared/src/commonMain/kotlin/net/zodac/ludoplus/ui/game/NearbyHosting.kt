package net.zodac.ludoplus.ui.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.netplay.GuestAction
import net.zodac.ludoplus.netplay.HostSession
import net.zodac.ludoplus.netplay.Hosting
import net.zodac.ludoplus.netplay.LinkEvent
import net.zodac.ludoplus.netplay.LobbySeat
import net.zodac.ludoplus.netplay.Nearby
import net.zodac.ludoplus.netplay.NetCodec
import net.zodac.ludoplus.netplay.SeatKind
import net.zodac.ludoplus.ui.game.GameViewModel.Companion.GUEST_TURN_MILLIS
import net.zodac.ludoplus.ui.game.GameViewModel.Companion.TURN_WARNING_MILLIS

/**
 * This phone hosting a Nearby game ([GameViewModel]'s; `.claude/NETPLAY.md`): the lobby, the guests' rolls and moves, a
 * guest's turn timed, and the seats whose phone is away. The game itself is the [table]'s.
 */
internal class NearbyHosting(private val table: Table) {

    /** The game this phone hosts, as [NearbyHosting] plays a guest's part in it. */
    interface Table {
        val scope: CoroutineScope

        /** The game being played, or null before one is started. */
        val game: StartedGame?

        /** A delay that doesn't run while the game is off screen or held. */
        suspend fun pausableDelay(millis: Long)

        /** Makes [game] the one being played. */
        fun commit(game: StartedGame)

        /** The current player's roll or move has brought the game to [state]. */
        fun update(state: GameState)

        /** [state]'s next roll. */
        fun rolled(state: GameState): GameState

        /** The computer plays the current turn, if it's its to play. */
        fun playCpuTurn()

        /** The computer stops playing the current turn. */
        fun stopCpuTurn()
    }

    private var session: HostSession? = null
    private var link: Hosting? = null
    private var guestActions: Job? = null

    private val _awaySeats = MutableStateFlow<Set<Int>>(emptySet())

    /** The Nearby seats whose phone has dropped out of the game this phone hosts: the CPU plays them until they're back. */
    val awaySeats: StateFlow<Set<Int>> = _awaySeats.asStateFlow()

    /** The seat whose guest let its turn run out ([GUEST_TURN_MILLIS]): the CPU plays the rest of that turn for it. */
    private var timedOutSeat: Int? = null
    private var guestTurn: Job? = null
    private var guestTurnState: GameState? = null

    private val _lobby = MutableStateFlow<List<LobbySeat>?>(null)

    /** The lobby's table while this phone hosts, or null while it doesn't. */
    val lobby: StateFlow<List<LobbySeat>?> = _lobby.asStateFlow()

    /** Whether this phone is hosting: from [open] until [close]. */
    val isOpen: Boolean get() = session != null

    private val started: HostSession? get() = session?.takeIf { it.started }

    /**
     * Makes this phone findable over [nearby] as [hostName]'s game, with [players] at the table: each Nearby seat open
     * until a guest takes it ([lobby]). Until [start] or [close].
     */
    fun open(nearby: Nearby, hostName: String, players: List<PlayerConfig>) {
        if (session != null) return
        val seats = players.map { player ->
            val kind = when (player.type) {
                PlayerType.HUMAN -> SeatKind.HOST
                PlayerType.AI -> SeatKind.CPU
                PlayerType.NEARBY -> SeatKind.NEARBY
            }
            LobbySeat(kind, player.name.takeUnless { kind == SeatKind.NEARBY }, player.colour)
        }
        lateinit var hosting: Hosting
        val opened = HostSession(seats) { endpoint, message -> hosting.send(endpoint, NetCodec.encode(message)) }
        hosting = nearby.host(hostName) { event ->
            when (event) {
                is LinkEvent.Connected -> opened.connected(event.endpoint)
                is LinkEvent.Received -> opened.received(event.endpoint, event.bytes)
                is LinkEvent.Disconnected -> opened.disconnected(event.endpoint)
            }
        }
        session = opened
        link = hosting
        table.scope.launch { opened.seats.collect { _lobby.value = it } }
        table.scope.launch {
            opened.away.collect { away ->
                _awaySeats.value = away
                // Findable again while a guest's seat waits for it.
                if (opened.started) hosting.setFindable(away.isNotEmpty())
            }
        }
        guestActions = table.scope.launch { opened.actions.collect(::onGuestAction) }
    }

    /** Starts the lobby's game, once every Nearby seat is taken: its seats, or null otherwise. */
    fun start(): List<LobbySeat>? {
        val opened = session ?: return null
        if (!opened.start()) return null
        link?.setFindable(false)
        return opened.seats.value
    }

    /** Game Over's Play Again in a game this phone hosts: the same seats, or null when it hosts none. */
    fun playAgainSeats(): List<LobbySeat>? {
        val opened = started ?: return null
        timedOutSeat = null
        return opened.seats.value
    }

    /** Sends [game] to the guests. */
    fun send(game: StartedGame) {
        session?.gameChanged(game)
    }

    /** [game] is now the one being played: the guests are sent it, and a guest whose turn it is is timed. */
    fun gameChanged(game: StartedGame) {
        val opened = started ?: return
        opened.gameChanged(game)
        if (timedOutSeat != game.state.current) timedOutSeat = null
        timeGuestTurn(game)
    }

    /** Whether the computer plays the current player's turn for its guest: a Nearby seat whose phone is away, or whose turn has run out. */
    fun computerPlaysFor(game: StartedGame): Boolean {
        val seat = game.state.current
        val opened = started ?: return false
        return game.players[seat].type == PlayerType.NEARBY && (seat in opened.away.value || seat == timedOutSeat)
    }

    /** Stops hosting: every guest is told the session is over. */
    fun close() {
        session?.end()
        link?.close()
        guestActions?.cancel()
        guestTurn?.cancel()
        session = null
        link = null
        _lobby.value = null
        _awaySeats.value = emptySet()
    }

    /**
     * Gives a guest whose turn it is [GUEST_TURN_MILLIS] (while the game is on screen) for each roll and move, warning it
     * [TURN_WARNING_MILLIS] before the end: one it doesn't make by then, the CPU makes, and the rest of the turn with it.
     */
    private fun timeGuestTurn(game: StartedGame) {
        val waiting = !game.state.isFinished && game.players[game.state.current].type == PlayerType.NEARBY && !computerPlaysFor(game)
        if (waiting && guestTurn?.isActive == true && guestTurnState == game.state) return
        guestTurn?.cancel()
        guestTurn = null
        if (!waiting) return
        guestTurnState = game.state
        guestTurn = table.scope.launch {
            table.pausableDelay(GUEST_TURN_MILLIS - TURN_WARNING_MILLIS)
            if (table.game?.state != game.state) return@launch
            session?.turnEnding(game.state.current)
            table.pausableDelay(TURN_WARNING_MILLIS)
            guestTurn = null
            if (table.game?.state != game.state) return@launch
            timedOutSeat = game.state.current
            table.playCpuTurn()
        }
    }

    /**
     * A guest's roll, move, leaving, or link dropping or coming back, checked as a tap at this phone is, from its own seat
     * on its own turn. A guest's roll or move takes back a turn that ran out.
     */
    private fun onGuestAction(action: GuestAction) {
        val game = table.game ?: return
        if (game.players.getOrNull(action.seat)?.type != PlayerType.NEARBY) return
        val theirTurn = game.state.current == action.seat
        when (action) {
            is GuestAction.Left -> {
                table.commit(game.copy(players = game.players.mapIndexed { seat, it -> if (seat == action.seat) it.copy(type = PlayerType.AI) else it }))
                table.playCpuTurn()
            }
            is GuestAction.Away -> {
                timeGuestTurn(game)
                table.playCpuTurn()
            }
            is GuestAction.Back -> if (theirTurn) takeBackTurn(game)
            is GuestAction.Roll -> {
                if (!theirTurn || game.state.phase != TurnPhase.AwaitingRoll) return
                takeBackTurn(game)
                table.update(table.rolled(game.state))
            }
            is GuestAction.Move -> {
                val phase = game.state.phase as? TurnPhase.AwaitingMove ?: return
                if (!theirTurn || phase.moves.none { it.pawn == action.pawn && it.owner == action.owner && (action.die == null || it.roll == action.die) && it.swap == action.swap }) return
                takeBackTurn(game)
                table.update(LudoEngine.move(game.state, action.pawn, action.owner, action.die, action.swap))
            }
        }
    }

    /** The current guest plays its own turn again: the CPU's, if it was playing it, stops, and the guest's time starts. */
    private fun takeBackTurn(game: StartedGame) {
        timedOutSeat = null
        table.stopCpuTurn()
        timeGuestTurn(game)
    }
}
