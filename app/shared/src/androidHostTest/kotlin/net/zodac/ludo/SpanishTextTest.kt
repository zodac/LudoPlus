package net.zodac.ludo

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludo.model.PlayerState
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.model.ScoreCategory
import net.zodac.ludo.ui.game.PlayerHeaderBar
import net.zodac.ludo.ui.game.TotalsButton
import net.zodac.ludo.ui.game.TurnTimerBadge
import net.zodac.ludo.ui.menu.MenuScreen
import net.zodac.ludo.ui.theme.LudoPlusTheme
import org.robolectric.annotation.Config

/**
 * A phone set to Spanish gets the Spanish strings, and with them the formatters' Spanish: ordinals, grouped numbers, and the
 * punctuation Spanish writes (¿ ? ¡ !). The first language, so these also show the machinery works end to end.
 */
@Config(qualifiers = "es")
class SpanishTextTest : UiTest() {

    @Test
    fun `a Spanish phone gets the Spanish strings - its ordinals - its grouping - its units - and its opening question mark`() = uiTest {
        val showcase = Showcase { compose }
        showcase.show {
            LudoPlusTheme {
                MenuScreen(
                    hasInProgressGame = false,
                    onContinue = {}, onNewGame = {}, onScores = {}, onStatistics = {}, onAchievements = {}, onStyles = {}, onRules = {}, onSettings = {},
                )
            }
        }
        listOf("Jugar", "Logros", "Estilos", "Clasificación", "Estadísticas", "Reglas", "Ajustes").forEach { compose.onNodeWithText(it).assertExists() }

        // Places are said as Spanish ordinals.
        val players = listOf(30, 25).mapIndexed { index, chance ->
            PlayerState(name = "P${index + 1}", type = PlayerType.HUMAN).let { it.copy(scorecard = it.scorecard + (ScoreCategory.CHANCE to listOf(chance))) }
        }
        showcase.show {
            LudoPlusTheme { PlayerHeaderBar(players = players, currentPlayerIndex = 1, viewedPlayerIndex = null, enabled = true, onPlayerTap = {}, modifier = Modifier.width(400.dp)) }
        }
        val state = { name: String ->
            compose.onNode(hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        }
        assertEquals("1.º puesto", state("P1"))
        assertEquals("Turno actual, 2.º puesto", state("P2"))

        // Thousands are grouped the Spanish way.
        showcase.show { LudoPlusTheme { TotalsButton(upperTotal = 70, upperBonus = 35, lowerTotal = 34521) } }
        compose.onNodeWithContentDescription("Totales").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("34.521").assertExists()

        // The turn timer's seconds carry their unit and say the time in words.
        showcase.show { LudoPlusTheme { TurnTimerBadge(secondsRemaining = 12) } }
        compose.onNodeWithContentDescription("Tiempo restante: 12 segundos").assertExists()

        // The leave-game question has Spanish's opening question mark.
        val confirmation = net.zodac.ludo.ui.game.LeaveGameConfirmation()
        confirmation.request { }
        showcase.show { LudoPlusTheme { net.zodac.ludo.ui.game.LeaveGameConfirmationDialog(confirmation) } }
        compose.onNodeWithText("¿Salir de la partida?").assertExists()
    }
}
