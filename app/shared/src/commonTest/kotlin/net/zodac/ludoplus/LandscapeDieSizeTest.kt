package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.ui.game.GameContent
import net.zodac.ludoplus.ui.game.StartedGame
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** In landscape the die is 84dp, on a phone and a tablet alike. */
class LandscapeDieSizeTest : UiTest() {

    private val players = listOf(PlayerConfig(1, PlayerType.HUMAN, "Alex"), PlayerConfig(2, PlayerType.HUMAN, "Sam"))

    /** The die's side, in dp, on the screen the test runs on. */
    private fun dieSide(): Float {
        compose.setContent {
            CompositionLocalProvider(LocalPlatformServices provides SilentPlatformServices) {
                LudoPlusTheme {
                    GameContent(
                        StartedGame(GameMode.CLASSIC, players, GameState.new(GameMode.CLASSIC, 2, first = 0), first = 0, playMillis = 0),
                        onRoll = {}, onMove = {}, onBack = {}, onResults = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        val bounds = compose.onNode(hasContentDescription("Die", substring = true)).getBoundsInRoot()
        return (bounds.right - bounds.left).value
    }

    @Test
    fun `on a phone it's 84dp`() = uiTest(TestScreen(891, 411)) {
        assertEquals(84f, dieSide(), 0.5f)
    }

    @Test
    fun `on a tablet it's 84dp`() = uiTest(TestScreen(1280, 800)) {
        assertEquals(84f, dieSide(), 0.5f)
    }
}
