package net.zodac.ludoplus.ui.netplay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.netplay.FoundGame
import net.zodac.ludoplus.netplay.GuestState
import net.zodac.ludoplus.netplay.LobbySeat
import net.zodac.ludoplus.netplay.LocalNearby
import net.zodac.ludoplus.netplay.RefusedReason
import net.zodac.ludoplus.netplay.SeatKind
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.common_default_player_name
import net.zodac.ludoplus.resources.netplay_cpu_seat
import net.zodac.ludoplus.resources.netplay_found_games
import net.zodac.ludoplus.resources.netplay_host_hint
import net.zodac.ludoplus.resources.netplay_join_action
import net.zodac.ludoplus.resources.netplay_join_title
import net.zodac.ludoplus.resources.netplay_joining
import net.zodac.ludoplus.resources.netplay_lobby_title
import net.zodac.ludoplus.resources.netplay_looking
import net.zodac.ludoplus.resources.netplay_lost
import net.zodac.ludoplus.resources.netplay_no_permission
import net.zodac.ludoplus.resources.netplay_open_seat
import net.zodac.ludoplus.resources.netplay_refused_full
import net.zodac.ludoplus.resources.netplay_refused_started
import net.zodac.ludoplus.resources.netplay_refused_version
import net.zodac.ludoplus.resources.netplay_ended
import net.zodac.ludoplus.resources.netplay_seated
import net.zodac.ludoplus.resources.netplay_start
import net.zodac.ludoplus.ui.common.ScreenScaffold
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.game.GameViewModel
import net.zodac.ludoplus.ui.theme.color

/**
 * The host's lobby, from New Game's Start with a Nearby seat: this phone findable as its player's game, and the table
 * filling as phones nearby take its Nearby seats. Start is enabled once every one is taken; leaving the page closes the
 * lobby. Asks for Nearby's permissions as it opens. TalkBack hears each seat as one line ("Seat 2, Jo"), and each change
 * to the table as it happens (a polite live region).
 */
@Composable
fun HostLobbyScreen(viewModel: GameViewModel, onStart: () -> Unit, onBack: () -> Unit) {
    val nearby = LocalNearby.current
    val defaultNames = (1..BoardGeometry.MAX_PLAYERS).map { stringResource(Res.string.common_default_player_name, it) }
    val defaultName: (Int) -> String = { defaultNames[it - 1] }
    var permitted by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(nearby) {
        permitted = nearby.requestPermissions()
        if (permitted == true) {
            val hostName = viewModel.setupForm.playerConfigs(defaultName).first().name
            viewModel.openLobby(nearby, hostName, defaultName)
        }
    }
    val seats by viewModel.lobby.collectAsStateWithLifecycle()
    ScreenScaffold(title = stringResource(Res.string.netplay_lobby_title), onBack = {
        viewModel.closeLobby()
        onBack()
    }, scrollable = true) {
        if (permitted == false) {
            Text(stringResource(Res.string.netplay_no_permission), style = MaterialTheme.typography.bodyLarge)
            return@ScreenScaffold
        }
        Text(stringResource(Res.string.netplay_host_hint), style = MaterialTheme.typography.bodyLarge)
        val table = seats ?: return@ScreenScaffold
        SeatList(table)
        Button(
            onClick = { if (viewModel.startHostedGame(defaultName)) onStart() },
            enabled = table.none { it.open },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(Res.string.netplay_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Joining a game nearby, from New Game: the games found nearby, each a button; then, once one is picked, where joining
 * it has got to (seated and waiting for its host to start, refused, or the host gone), until the game starts, when
 * [onPlaying] takes this phone to the board. The player joins under [name] in [colour]: New Game's first seat's. Asks
 * for Nearby's permissions as it opens. Leaving the page leaves the game. TalkBack hears each game as a button ("Join
 * Sam's game") and each change of state as it happens.
 */
@Composable
fun JoinScreen(viewModel: GameViewModel, onPlaying: () -> Unit, onBack: () -> Unit) {
    val nearby = LocalNearby.current
    val defaultName = stringResource(Res.string.common_default_player_name, 1)
    var permitted by remember { mutableStateOf<Boolean?>(null) }
    var found by remember { mutableStateOf(emptyList<FoundGame>()) }
    LaunchedEffect(nearby) {
        permitted = nearby.requestPermissions()
    }
    val state by viewModel.guestState.collectAsStateWithLifecycle()
    // Looking for games until one is picked.
    if (permitted == true && state == null) {
        DisposableEffect(nearby) {
            val discovery = nearby.discover { found = it }
            onDispose { discovery.stop() }
        }
    }
    LaunchedEffect(state) { if (state is GuestState.Playing) onPlaying() }
    ScreenScaffold(title = stringResource(Res.string.netplay_join_title), onBack = {
        viewModel.leaveJoined()
        onBack()
    }, scrollable = true) {
        if (permitted == false) {
            Text(stringResource(Res.string.netplay_no_permission), style = MaterialTheme.typography.bodyLarge)
            return@ScreenScaffold
        }
        val status = when (val now = state) {
            null -> if (found.isEmpty()) stringResource(Res.string.netplay_looking) else stringResource(Res.string.netplay_found_games)
            GuestState.Joining -> stringResource(Res.string.netplay_joining)
            is GuestState.Seated -> stringResource(Res.string.netplay_seated)
            is GuestState.Refused -> when (now.reason) {
                RefusedReason.FULL -> stringResource(Res.string.netplay_refused_full)
                RefusedReason.STARTED -> stringResource(Res.string.netplay_refused_started)
                RefusedReason.VERSION -> stringResource(Res.string.netplay_refused_version)
            }
            GuestState.Lost -> stringResource(Res.string.netplay_lost)
            GuestState.Ended -> stringResource(Res.string.netplay_ended)
            // On the board by then.
            is GuestState.Playing, is GuestState.Reconnecting -> ""
        }
        Text(status, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        when (val now = state) {
            null -> if (found.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    found.forEachIndexed { index, game ->
                        if (index > 0) HorizontalDivider()
                        val action = stringResource(Res.string.netplay_join_action, game.name)
                        Text(
                            text = action,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                .clickable(role = Role.Button) {
                                    val first = viewModel.setupForm.setup.value.playerSlots.firstOrNull()
                                    val name = first?.name?.trim().orEmpty().ifEmpty { defaultName }
                                    viewModel.join(nearby, game, name, first?.colour)
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                }
            }
            is GuestState.Seated -> if (now.seats.isNotEmpty()) SeatList(now.seats, mine = now.seat)
            else -> Unit
        }
    }
}

/** The table, a row a seat: its colour, and its player's name, "CPU" after the computer's, or an open seat's wait. */
@Composable
private fun SeatList(seats: List<LobbySeat>, mine: Int? = null) {
    val openSeat = stringResource(Res.string.netplay_open_seat)
    Card(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            seats.forEachIndexed { index, seat ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                val name = seat.name ?: openSeat
                val label = if (seat.kind == SeatKind.CPU) stringResource(Res.string.netplay_cpu_seat, name) else name
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp)
                        .clearAndSetSemantics { contentDescription = label },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(16.dp).background(seat.colour.color, CircleShape))
                    Text(
                        label,
                        style = if (index == mine) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        color = if (seat.open) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
