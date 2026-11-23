package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import net.zodac.ludoplus.data.InMemoryPreferences
import net.zodac.ludoplus.data.game.InProgressGameRepository
import net.zodac.ludoplus.data.game.SavedGameJson
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.data.settings.SettingsRepository
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.RollOff
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.netplay.FoundGame
import net.zodac.ludoplus.netplay.LinkEvent
import net.zodac.ludoplus.netplay.NetMessage
import net.zodac.ludoplus.netplay.PROTOCOL_VERSION
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.game.FakeNearby
import net.zodac.ludoplus.ui.game.GameScreen
import net.zodac.ludoplus.ui.game.GameViewModel
import net.zodac.ludoplus.ui.game.StartedGame
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** The board's note of a Nearby game's link, on a guest's phone and on the host's, as it's shown and spoken. */
class NetplayNoticeTest : UiTest() {

    private val showcase = Showcase { compose }

    private fun viewModel() = GameViewModel(SettingsRepository(InMemoryPreferences()), InProgressGameRepository(InMemoryPreferences()), GameRecordRepository(InMemoryGameResultDao()))

    private fun show(viewModel: GameViewModel) = showcase.show {
        CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
            LudoPlusTheme { GameScreen(viewModel, onBackToMenu = {}) }
        }
    }

    @Test
    fun `a guest that loses the host hears it reconnecting - then that the host ended the game`() = uiTest(TestScreen(411, 891)) {
        val nearby = FakeNearby()
        val guest = viewModel()
        guest.join(nearby, FoundGame("host", "Sam"), "Jo", null)
        checkNotNull(nearby.joinEvents)(LinkEvent.Connected("host"))
        nearby.fromHost(NetMessage.Welcome(1, "t1"))
        val players = listOf(PlayerConfig(1, PlayerType.HUMAN, "Sam"), PlayerConfig(2, PlayerType.NEARBY, "Jo"))
        val game = StartedGame(GameMode.CLASSIC, players, GameState.new(GameMode.CLASSIC, 2, first = 0), RollOff(emptyList(), 0), id = "a".repeat(32))
        nearby.fromHost(NetMessage.Game(1, SavedGameJson.encode(game)))
        compose.waitForIdle()
        show(guest)
        compose.onNodeWithText("Reconnecting", substring = true).assertDoesNotExist()

        checkNotNull(nearby.joinEvents)(LinkEvent.Disconnected("host"))
        compose.waitForIdle()
        compose.onNodeWithText("Reconnecting to the host...").assertExists()

        // Found again, and the host has ended it.
        checkNotNull(nearby.found)(listOf(FoundGame("host2", "Sam")))
        checkNotNull(nearby.joinEvents)(LinkEvent.Connected("host2"))
        nearby.fromHost(NetMessage.Ended)
        compose.waitForIdle()
        compose.onNodeWithText("The host ended the game.").assertExists()
    }

    @Test
    fun `the host hears which guest dropped out - and that the CPU plays for them - until they're back`() = uiTest(TestScreen(411, 891)) {
        val nearby = FakeNearby()
        val host = viewModel()
        compose.waitForIdle()
        host.setupForm.setPlayerCount(2)
        host.setupForm.setPlayerType(2, PlayerType.NEARBY)
        host.openLobby(nearby, "Sam") { "Player $it" }
        checkNotNull(nearby.hostEvents)(LinkEvent.Connected("jo"))
        nearby.fromGuest("jo", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null))
        compose.waitForIdle()
        check(host.startHostedGame { "Player $it" })
        show(host)

        checkNotNull(nearby.hostEvents)(LinkEvent.Disconnected("jo"))
        compose.waitForIdle()
        compose.onNodeWithText("Jo dropped out: the CPU plays for now").assertExists()

        val token = (nearby.hostSent.first { it.second is NetMessage.Welcome }.second as NetMessage.Welcome).token
        nearby.fromGuest("jo-again", NetMessage.Hello(PROTOCOL_VERSION, "Jo", null, token))
        compose.waitForIdle()
        compose.onNodeWithText("dropped out", substring = true).assertDoesNotExist()
    }

    /** A guest's phone, seated at Jo's seat (2) in Sam's game. */
    private fun guest(nearby: FakeNearby): GameViewModel {
        val guest = viewModel()
        guest.join(nearby, FoundGame("host", "Sam"), "Jo", null)
        checkNotNull(nearby.joinEvents)(LinkEvent.Connected("host"))
        nearby.fromHost(NetMessage.Welcome(1, "t1"))
        return guest
    }

    private val players = listOf(PlayerConfig(1, PlayerType.HUMAN, "Sam"), PlayerConfig(2, PlayerType.NEARBY, "Jo"))

    @Test
    fun `a guest hears that its turn is nearly up - until it's played`() = uiTest(TestScreen(411, 891)) {
        val nearby = FakeNearby()
        val guest = guest(nearby)
        val game = StartedGame(GameMode.CLASSIC, players, GameState.new(GameMode.CLASSIC, 2, first = 1), RollOff(emptyList(), 1), id = "a".repeat(32))
        nearby.fromHost(NetMessage.Game(1, SavedGameJson.encode(game)))
        compose.waitForIdle()
        show(guest)
        compose.onNodeWithText("nearly up", substring = true).assertDoesNotExist()

        nearby.fromHost(NetMessage.TurnEnding)
        compose.waitForIdle()
        compose.onNodeWithText("Your turn is nearly up: play now, or the CPU will.").assertExists()

        nearby.fromHost(NetMessage.Game(2, SavedGameJson.encode(game.copy(state = LudoEngine.applyRoll(game.state, 3)))))
        compose.waitForIdle()
        compose.onNodeWithText("nearly up", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a guest's Game Over has no Play Again - it says the host's starts the next game - which then shows`() = uiTest(TestScreen(411, 891)) {
        val nearby = FakeNearby()
        val guest = guest(nearby)
        val start = StartedGame(GameMode.CLASSIC, players, GameState.new(GameMode.CLASSIC, 2, first = 0), RollOff(emptyList(), 0), id = "a".repeat(32))
        val home = start.state.board.homeProgress
        val finished = start.copy(
            state = start.state.copy(phase = TurnPhase.Finished, finishingOrder = listOf(1, 0), players = start.state.players.map { it.copy(pawns = List(4) { home }) }),
            finishMillis = mapOf(1 to 0L, 0 to 0L),
        )
        nearby.fromHost(NetMessage.Game(1, SavedGameJson.encode(finished)))
        compose.waitForIdle()
        show(guest)
        compose.onNodeWithText("If the host plays again, the new game starts here.").assertExists()
        compose.onNodeWithText("Play Again").assertDoesNotExist()
        compose.onNodeWithText("Main Menu").assertExists()

        nearby.fromHost(NetMessage.Game(2, SavedGameJson.encode(start.copy(id = "b".repeat(32), startedAtEpochMillis = 2_000L))))
        compose.waitForIdle()
        compose.onNodeWithText("If the host plays again", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Main Menu").assertDoesNotExist()
    }
}
