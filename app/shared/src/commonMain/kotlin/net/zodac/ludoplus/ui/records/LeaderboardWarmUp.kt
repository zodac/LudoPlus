package net.zodac.ludoplus.ui.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.zodac.ludoplus.data.records.GameResult
import net.zodac.ludoplus.data.records.LeaderboardSource
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.ui.common.OffscreenWarmUp

/** Set once [LeaderboardWarmUp] has drawn every board it draws, for the life of the process. */
private var leaderboardWarmedUp = false

/**
 * For tests only: forgets that [LeaderboardWarmUp] has run, so a test can watch it run. The flag lives for the whole
 * process, and the unit tests share one: any earlier test that left the menu up long enough has already used it up.
 */
internal fun resetLeaderboardWarmUpForTest() {
    leaderboardWarmedUp = false
}

// After the menu's own entrance, and clear of the Styles warm-up, which starts at 600ms and runs for a few hundred.
private const val WARM_UP_DELAY_MILLIS = 2000L

// Frames each board gets: enough for the page's own fade-in (100ms) to finish, so it's really drawn.
private const val WARM_UP_FRAMES = 8

/** The boards it draws: the one a fresh install opens on, then one that ranks by the pawns left. */
private val WARM_UP_MODES = listOf(GameMode.DEFAULT, GameMode.SURVIVAL)

/**
 * Draws the Leaderboard once, out of sight, so opening it isn't the first time its code runs: loading and running the
 * page's first-time code (the drop-down, the table, the list, its scrollbar) otherwise lands on the frame the page opens
 * on. Statistics shares most of it. Placed on the menu under its opaque backdrop, in a 1dp clipped box, once the menu has
 * settled, as the Styles page's is. It reads nothing from the database: the boards are made up.
 */
@Composable
internal fun LeaderboardWarmUp(width: Dp, modifier: Modifier = Modifier) {
    if (leaderboardWarmedUp) return
    val viewModel = remember { LeaderboardViewModel(WarmUpBoards) }
    // As big as the page would be, so it lays out as it would there.
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    OffscreenWarmUp<GameMode>(
        key = Unit,
        delayMillis = WARM_UP_DELAY_MILLIS,
        pageSize = DpSize(width, height),
        modifier = modifier,
        run = {
            for (mode in WARM_UP_MODES) {
                viewModel.selectMode(mode)
                draw(mode, frames = WARM_UP_FRAMES)
            }
            leaderboardWarmedUp = true
        },
    ) {
        LeaderboardScreen(viewModel = viewModel, onBack = {}, driftingDice = false)
    }
}

/** Boards of a few made-up wins. */
private object WarmUpBoards : LeaderboardSource {

    // A new one each time: this object lives as long as the process, and what collects it mustn't.
    override val writes: StateFlow<Long> get() = MutableStateFlow(0L)

    override suspend fun leaderboard(gameMode: GameMode, playerCount: Int, pageIndex: Int, pageSize: Int): List<GameResult> = (1..4).map { win(it, gameMode, playerCount) }

    private fun win(rank: Int, gameMode: GameMode, playerCount: Int) = GameResult(
        playerName = "Player $rank", // i18n: not translated - made-up warm-up data, never shown
        playerKey = "player $rank",
        isPrimaryPlayer = false,
        gameModeId = gameMode.id,
        playerCount = playerCount,
        endedAtEpochMillis = 0,
        playMillis = 1,
        place = 1,
        won = true,
        finishMillis = 1,
        turns = 40 + rank,
        captures = 2,
        timesCaptured = 1,
        pawnsHome = 4,
    )
}
