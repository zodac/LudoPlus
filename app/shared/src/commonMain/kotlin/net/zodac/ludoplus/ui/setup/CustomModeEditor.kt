package net.zodac.ludoplus.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.zodac.ludoplus.model.CustomMode
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.sanitizePlayerName
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.setup_custom_base
import net.zodac.ludoplus.resources.setup_custom_editor_edit_title
import net.zodac.ludoplus.resources.setup_custom_editor_new_title
import net.zodac.ludoplus.resources.setup_custom_name
import net.zodac.ludoplus.resources.setup_custom_name_hint
import net.zodac.ludoplus.resources.setup_custom_name_taken
import net.zodac.ludoplus.resources.setup_custom_need_name
import net.zodac.ludoplus.resources.setup_custom_need_rule
import net.zodac.ludoplus.resources.setup_custom_save
import net.zodac.ludoplus.resources.setup_house_rules
import net.zodac.ludoplus.ui.common.ChoicePicker
import net.zodac.ludoplus.ui.common.MAIN_ACTION_HEIGHT
import net.zodac.ludoplus.ui.common.ScreenScaffold
import net.zodac.ludoplus.ui.common.ScrollingColumn
import net.zodac.ludoplus.ui.common.fullScreenDialogProperties
import net.zodac.ludoplus.ui.common.stringResource

/**
 * Makes a custom mode, or changes [editing]: a page over New Game with its name, the mode it's based on, and a switch for
 * each house rule. Save ([onSave], handed the mode, its id kept when editing) is offered once it has a name of its own
 * and a house rule; until then the reason shows under the form, and on the name field for TalkBack. Back, or the back
 * arrow, leaves without saving ([onDismiss]).
 *
 * A name is taken if one of [existing] has it, or a built-in mode, whatever its case.
 */
@Composable
internal fun CustomModeEditor(editing: CustomMode?, existing: List<CustomMode>, onSave: (CustomMode) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var base by remember { mutableStateOf(editing?.base ?: GameMode.DEFAULT) }
    var pickedRules by remember { mutableStateOf(editing?.houseRules.orEmpty()) }
    // Only those that change anything in the mode it's based on.
    val houseRules = pickedRules.filter { it.appliesTo(base.rules) }.toSet()
    val builtInNames = GameMode.entries.map { stringResource(it.displayName) }
    val nameTaken = CustomMode.nameTaken(name, existing, except = editing?.id) || builtInNames.any { it.equals(name.trim(), ignoreCase = true) }
    val problem = when {
        name.isBlank() -> stringResource(Res.string.setup_custom_need_name)
        nameTaken -> stringResource(Res.string.setup_custom_name_taken)
        houseRules.isEmpty() -> stringResource(Res.string.setup_custom_need_rule, stringResource(base.displayName))
        else -> null
    }
    val nameProblem = problem.takeIf { name.isBlank() || nameTaken }
    Dialog(onDismissRequest = onDismiss, properties = fullScreenDialogProperties()) {
        val title = stringResource(if (editing == null) Res.string.setup_custom_editor_new_title else Res.string.setup_custom_editor_edit_title)
        ScreenScaffold(title = title, onBack = onDismiss) {
            ScrollingColumn(modifier = Modifier.weight(1f, fill = false).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    // Held to a name's characters and the most a name takes.
                    onValueChange = { name = sanitizePlayerName(it).take(CustomMode.MAX_NAME_LENGTH) },
                    label = { Text(stringResource(Res.string.setup_custom_name)) },
                    placeholder = { Text(stringResource(Res.string.setup_custom_name_hint)) },
                    singleLine = true,
                    isError = nameTaken,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().semantics { if (nameProblem != null) error(nameProblem) },
                )
                EditorCard(stringResource(Res.string.setup_custom_base)) {
                    ChoicePicker(
                        title = stringResource(Res.string.setup_custom_base),
                        options = GameMode.entries,
                        selected = base,
                        onSelect = { base = it },
                        label = { stringResource(it.displayName) },
                        description = { stringResource(it.description) },
                    )
                }
                EditorCard(stringResource(Res.string.setup_house_rules)) {
                    HouseRule.entries.forEach { rule ->
                        val applies = rule.appliesTo(base.rules)
                        val on = rule in houseRules
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(value = on, enabled = applies, role = Role.Switch) { pickedRules = if (it) pickedRules + rule else pickedRules - rule },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(rule.title), style = MaterialTheme.typography.bodyLarge)
                                Text(if (applies) stringResource(rule.description) else idleHouseRuleNote(base), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = on, onCheckedChange = null, enabled = applies)
                        }
                    }
                }
            }
            if (problem != null) {
                Text(problem, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { onSave(CustomMode(editing?.id ?: CustomMode.nextId(existing), name.trim(), base, houseRules)) },
                enabled = problem == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = MAIN_ACTION_HEIGHT),
            ) {
                Text(stringResource(Res.string.setup_custom_save), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** A titled group of the editor's choices, as New Game's cards are. */
@Composable
private fun EditorCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
