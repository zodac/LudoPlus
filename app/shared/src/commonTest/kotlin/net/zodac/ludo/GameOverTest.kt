package net.zodac.ludo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.zodac.ludo.data.InMemoryPreferences
import net.zodac.ludo.data.game.InProgressGameRepository
import net.zodac.ludo.data.records.GameRecordRepository
import net.zodac.ludo.data.records.InMemoryGameResultDao
import net.zodac.ludo.data.settings.SettingsRepository
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.PlayerState
import net.zodac.ludo.game.RollOff
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.platform.LocalPlatformServices
import net.zodac.ludo.platform.SilentPlatformServices
import net.zodac.ludo.ui.game.GameOverScreen
import net.zodac.ludo.ui.game.GamePacing
import net.zodac.ludo.ui.game.GameScreen
import net.zodac.ludo.ui.game.GameViewModel
import net.zodac.ludo.ui.game.StartedGame
import net.zodac.ludo.ui.theme.LudoPlusTheme

/** The results page: who placed where, with what, and the ways on; and how a finished game gets there. */
class GameOverTest : UiTest() {

    private val showcase = Showcase { compose }

    private val players = listOf(
        PlayerConfig(1, PlayerType.HUMAN, "Alex", colour = PlayerColour.RED),
        PlayerConfig(2, PlayerType.AI, "Gambit", Difficulty.HARD, PlayerColour.GREEN),
        PlayerConfig(3, PlayerType.AI, "Pip", Difficulty.EASY, PlayerColour.YELLOW),
        PlayerConfig(4, PlayerType.HUMAN, "Sam", colour = PlayerColour.BLUE),
    )

    /** Gambit first, then Alex, Sam, and Pip, who got one pawn home. */
    private val finished: StartedGame = run {
        val state = GameState.new(GameMode.CLASSIC, 4)
        StartedGame(
            mode = GameMode.CLASSIC,
            players = players,
            state = state.copy(
                players = listOf(
                    PlayerState(0, List(4) { 56 }, turns = 38, captures = 1, timesCaptured = 1),
                    PlayerState(1, List(4) { 56 }, turns = 35, captures = 3, timesCaptured = 0),
                    PlayerState(2, listOf(56, 40, -1, 12), turns = 51, captures = 1, timesCaptured = 6),
                    PlayerState(3, List(4) { 56 }, turns = 1, captures = 1, timesCaptured = 1),
                ),
                phase = TurnPhase.Finished,
                finishingOrder = listOf(1, 0, 3, 2),
            ),
            rollOff = RollOff(emptyList(), 0),
            playMillis = 4_400_000,
            finishMillis = mapOf(1 to 900_000L, 0 to 997_000L, 3 to 3_599_999L, 2 to 4_400_000L),
        )
    }

    private fun showResults(onMenu: () -> Unit = {}, onAgain: () -> Unit = {}, onReview: () -> Unit = {}) {
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { GameOverScreen(finished, onBackToMenu = onMenu, onPlayAgain = onAgain, onReviewBoard = onReview, celebrate = false) }
            }
        }
    }

    @Test
    fun `the winner and each placing are said in full - the last with their pawns home`() = uiTest(TestScreen(411, 891)) {
        showResults()
        compose.onNodeWithText("Game Over").assertExists()
        // Each card and row is one node for a screen reader, read top to bottom.
        compose.onNode(hasText("Winner") and hasText("Gambit") and hasText("Finished in 15m 0s") and hasText("35 turns, 3 captures, 0 captured"))
            .assertExists()
        compose.onNode(hasText("2nd") and hasText("Alex") and hasText("Finished in 16m 37s") and hasText("38 turns, 1 capture, 1 captured"))
            .assertExists()
        // Under an hour in minutes and seconds; from an hour in hours and minutes. One of everything is singular.
        compose.onNode(hasText("3rd") and hasText("Sam") and hasText("Finished in 59m 59s") and hasText("1 turn, 1 capture, 1 captured"))
            .assertExists()
        compose.onNode(hasText("4th") and hasText("Pip") and hasText("Finished in 1h 13m") and hasText("1 of 4 pawns home, 1 capture, 6 captured"))
            .assertExists()
    }

    @Test
    fun `the buttons lead on - review, menu, and play again`() = uiTest(TestScreen(411, 891)) {
        val pressed = mutableListOf<String>()
        showResults(onMenu = { pressed += "menu" }, onAgain = { pressed += "again" }, onReview = { pressed += "review" })
        compose.onNodeWithText("Review Board").performClick()
        compose.onNodeWithText("Main Menu").performClick()
        compose.onNodeWithText("Play Again").performClick()
        assertEquals(listOf("review", "menu", "again"), pressed)
    }

    /** A die that always rolls [roll]. */
    private class LoadedDie(private val roll: Int) : Random() {
        override fun nextBits(bitCount: Int): Int = 0

        override fun nextInt(from: Int, until: Int): Int = roll
    }

    @Test
    fun `the last move plays out, then the results - Review Board shows the board and Results goes back`() = uiTest(TestScreen(411, 891)) {
        // Alex is a square from home with the rest of their pawns in, and Gambit and Pip are done: a 1 ends the game.
        val saved = InProgressGameRepository(InMemoryPreferences())
        val nearlyDone = finished.state.copy(
            players = finished.state.players.mapIndexed { index, player ->
                when (index) {
                    0 -> player.copy(pawns = listOf(56, 56, 56, 55))
                    2 -> player.copy(pawns = List(4) { 56 })
                    3 -> player.copy(pawns = listOf(56, -1, 10, 20))
                    else -> player
                }
            },
            current = 0,
            phase = TurnPhase.AwaitingRoll,
            finishingOrder = listOf(1, 2),
        )
        saved.save(finished.copy(state = nearlyDone, finishMillis = mapOf(1 to 900_000L, 2 to 1_000_000L)))
        val viewModel = GameViewModel(SettingsRepository(InMemoryPreferences()), saved, GameRecordRepository(InMemoryGameResultDao()), LoadedDie(1))
        viewModel.setPacing(GamePacing(thinkMillis = 500))
        assertTrue(runBlocking { viewModel.resumeGame() })
        showcase.show {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme { GameScreen(viewModel, onBackToMenu = {}) }
            }
        }
        compose.mainClock.autoAdvance = false
        viewModel.roll()
        compose.waitForIdle()
        val move = (viewModel.game.value!!.state.phase as TurnPhase.AwaitingMove).moves.single()
        viewModel.move(move.pawn)
        compose.mainClock.advanceTimeByFrame()
        // The board while the last move plays out.
        compose.onNodeWithText("Game Over").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Game Over")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Review Board").performClick()
        compose.onNodeWithText("Game Over").assertDoesNotExist()
        compose.onNodeWithText("Gambit wins!").assertExists()
        compose.onNodeWithText("Results").performClick()
        compose.onNodeWithText("Game Over").assertExists()
    }
}
