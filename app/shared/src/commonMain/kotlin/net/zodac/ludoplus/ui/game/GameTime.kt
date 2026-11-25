package net.zodac.ludoplus.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.zodac.ludoplus.ui.common.delayWhileResumed
import net.zodac.ludoplus.ui.common.pageSubtitleStyle
import net.zodac.ludoplus.ui.records.PlayTimeLine
import net.zodac.ludoplus.ui.records.untilPlayTimeChanges

private const val MILLIS_PER_SECOND = 1_000L

/**
 * How long the game has been played, [playMillis], under its title, shown and spoken as Statistics' time played is
 * ([PlayTimeLine]): read again when that would next change, while it's [running] and the app is in front. Its state is read
 * here, so an update recomposes this line and not the page around it. [gameKey] is the game's own: another game starts it
 * afresh.
 */
@Composable
internal fun GameTime(gameKey: Any, running: Boolean, playMillis: () -> Long) {
    val read by rememberUpdatedState(playMillis)
    var seconds by remember(gameKey) { mutableIntStateOf((playMillis() / MILLIS_PER_SECOND).toInt()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(gameKey, running, lifecycle) {
        while (true) {
            val millis = read()
            seconds = (millis / MILLIS_PER_SECOND).toInt()
            if (!running) break
            lifecycle.delayWhileResumed(untilPlayTimeChanges(seconds).inWholeMilliseconds - millis % MILLIS_PER_SECOND)
        }
    }
    PlayTimeLine(seconds, style = pageSubtitleStyle)
}
