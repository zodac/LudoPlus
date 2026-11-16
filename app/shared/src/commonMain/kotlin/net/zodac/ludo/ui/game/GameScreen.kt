package net.zodac.ludo.ui.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.game_board_coming
import net.zodac.ludo.resources.game_player_cpu
import net.zodac.ludo.resources.game_player_human
import net.zodac.ludo.ui.common.BackHandler
import net.zodac.ludo.ui.common.ColourDisc
import net.zodac.ludo.ui.common.EmptyStateCard
import net.zodac.ludo.ui.common.ScreenScaffold
import net.zodac.ludo.ui.common.stringResource
import net.zodac.ludo.ui.theme.color

/**
 * The game screen. Phase 0 of `.claude/IMPLEMENTATION.md`: a placeholder listing the players the setup form started the
 * game with, until the board comes in Phase 2.
 */
@Composable
fun GameScreen(viewModel: GameViewModel, onBackToMenu: () -> Unit) {
    val game by viewModel.game.collectAsStateWithLifecycle()
    val started = game ?: return
    // Back leaves the game for the menu, as the top bar's arrow does, rather than going back to the setup form.
    BackHandler(onBack = onBackToMenu)
    ScreenScaffold(title = stringResource(started.mode.displayName), onBack = onBackToMenu, scrollable = true) {
        for (player in started.players) {
            val type = stringResource(if (player.type == PlayerType.AI) Res.string.game_player_cpu else Res.string.game_player_human)
            Card(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ColourDisc(colour = player.colour.color, size = 24.dp, ring = MaterialTheme.colorScheme.outline)
                    Text(text = player.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(text = type, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        EmptyStateCard(text = stringResource(Res.string.game_board_coming))
    }
}
