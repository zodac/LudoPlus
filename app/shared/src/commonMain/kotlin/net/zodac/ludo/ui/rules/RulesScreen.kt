package net.zodac.ludo.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.app_name
import net.zodac.ludo.resources.rules_board_1
import net.zodac.ludo.resources.rules_board_2
import net.zodac.ludo.resources.rules_board_3
import net.zodac.ludo.resources.rules_board_heading
import net.zodac.ludo.resources.rules_capture_1
import net.zodac.ludo.resources.rules_capture_2
import net.zodac.ludo.resources.rules_capture_3
import net.zodac.ludo.resources.rules_capture_heading
import net.zodac.ludo.resources.rules_how_to_play_title
import net.zodac.ludo.resources.rules_title
import net.zodac.ludo.resources.rules_turn_1
import net.zodac.ludo.resources.rules_turn_2
import net.zodac.ludo.resources.rules_turn_3
import net.zodac.ludo.resources.rules_turn_4
import net.zodac.ludo.resources.rules_turn_heading
import net.zodac.ludo.resources.rules_winning_1
import net.zodac.ludo.resources.rules_winning_2
import net.zodac.ludo.resources.rules_winning_heading
import net.zodac.ludo.ui.common.BulletedList
import net.zodac.ludo.ui.common.ScreenScaffold
import net.zodac.ludo.ui.common.stringResource
import org.jetbrains.compose.resources.StringResource

/** One card of the rules: its heading, then its points. */
private class RulesSection(val heading: StringResource, val points: List<StringResource>)

/** The classic rules (`.claude/IMPLEMENTATION.md`, "The classic rules"), a card a part. Diagrams come in Phase 5. */
private val SECTIONS = listOf(
    RulesSection(Res.string.rules_board_heading, listOf(Res.string.rules_board_1, Res.string.rules_board_2, Res.string.rules_board_3)),
    RulesSection(
        Res.string.rules_turn_heading,
        listOf(Res.string.rules_turn_1, Res.string.rules_turn_2, Res.string.rules_turn_3, Res.string.rules_turn_4),
    ),
    RulesSection(Res.string.rules_capture_heading, listOf(Res.string.rules_capture_1, Res.string.rules_capture_2, Res.string.rules_capture_3)),
    RulesSection(Res.string.rules_winning_heading, listOf(Res.string.rules_winning_1, Res.string.rules_winning_2)),
)

/** How to play: the classic rules, card by card, under the page's own heading. Each card's heading is a screen reader heading. */
@Composable
fun RulesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(title = stringResource(Res.string.rules_title), onBack = onBack, modifier = modifier, scrollable = true) {
        Text(
            text = stringResource(Res.string.rules_how_to_play_title, stringResource(Res.string.app_name)),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        for (section in SECTIONS) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(section.heading),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    BulletedList(items = section.points.map { stringResource(it) })
                }
            }
        }
    }
}
