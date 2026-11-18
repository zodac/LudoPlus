package net.zodac.ludoplus.ui.setup

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.CustomMode
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.TeamMode
import net.zodac.ludoplus.netplay.LocalNearby
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.common_cancel
import net.zodac.ludoplus.resources.common_cpu_cd
import net.zodac.ludoplus.resources.common_default_player_name
import net.zodac.ludoplus.resources.setup_custom_delete
import net.zodac.ludoplus.resources.setup_custom_delete_message
import net.zodac.ludoplus.resources.setup_custom_delete_title
import net.zodac.ludoplus.resources.setup_custom_edit
import net.zodac.ludoplus.resources.setup_custom_mode_description
import net.zodac.ludoplus.resources.setup_custom_new
import net.zodac.ludoplus.resources.setup_difficulty_easy
import net.zodac.ludoplus.resources.setup_difficulty_hard
import net.zodac.ludoplus.resources.setup_difficulty_medium
import net.zodac.ludoplus.resources.setup_game_mode
import net.zodac.ludoplus.resources.setup_house_rules
import net.zodac.ludoplus.resources.setup_house_rules_active_note
import net.zodac.ludoplus.resources.setup_house_rules_custom
import net.zodac.ludoplus.resources.setup_house_rules_description
import net.zodac.ludoplus.resources.setup_house_rules_locked
import net.zodac.ludoplus.resources.setup_join_nearby
import net.zodac.ludoplus.resources.setup_names_unique
import net.zodac.ludoplus.resources.setup_nearby_seat
import net.zodac.ludoplus.resources.setup_partner_captures
import net.zodac.ludoplus.resources.setup_player_count_spoken
import net.zodac.ludoplus.resources.setup_player_name_cd
import net.zodac.ludoplus.resources.setup_players
import net.zodac.ludoplus.resources.setup_roll_for_partners
import net.zodac.ludoplus.resources.setup_start
import net.zodac.ludoplus.resources.setup_teams
import net.zodac.ludoplus.resources.setup_teams_pairs_description
import net.zodac.ludoplus.resources.setup_teams_two_teams_description
import net.zodac.ludoplus.resources.setup_teams_unavailable
import net.zodac.ludoplus.resources.setup_title
import net.zodac.ludoplus.resources.setup_tutorial_link
import net.zodac.ludoplus.resources.setup_type_nearby
import net.zodac.ludoplus.resources.setup_type_user
import net.zodac.ludoplus.ui.common.ChoicePicker
import net.zodac.ludoplus.ui.common.FittedSegmentedChoiceRow
import net.zodac.ludoplus.ui.common.HoldPageCover
import net.zodac.ludoplus.ui.common.LocalLandscapePage
import net.zodac.ludoplus.ui.common.LudoPlusDialog
import net.zodac.ludoplus.ui.common.MAIN_ACTION_HEIGHT
import net.zodac.ludoplus.ui.common.MIN_READABLE_FONT_SIZE
import net.zodac.ludoplus.ui.common.ModifierPicker
import net.zodac.ludoplus.ui.common.ModifierSetting
import net.zodac.ludoplus.ui.common.ScreenScaffold
import net.zodac.ludoplus.ui.common.ScrollingColumn
import net.zodac.ludoplus.ui.common.SoraFontFamily
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.spokenList
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.common.textScale
import net.zodac.ludoplus.ui.game.GameSetupForm
import net.zodac.ludoplus.ui.game.GameSetupState
import net.zodac.ludoplus.ui.game.GameViewModel
import net.zodac.ludoplus.ui.game.PlayerSetupSlot
import net.zodac.ludoplus.ui.icons.Symbols

/**
 * Fixed width for the per-row User/CPU control, so the name fields above and below it all end at
 * the same place instead of stepping in and out with the label lengths.
 */
private val TYPE_CONTROL_WIDTH = 76.dp

/** What a chip adds round its label: its own padding on each side, and a little to spare. */
private val TYPE_CHIP_PADDING = 32.dp

/** Above this system font scale a player row stacks its controls (see [PlayerRow]). At 1.0 - and up to a
 * hair over it - the row is the one it has always been. */
private const val STACKED_PLAYER_ROW_FONT_SCALE = 1.05f

/** Shown under the form, and said by a screen reader on each clashing name field. */
@Composable
fun GameSetupScreen(
    viewModel: GameViewModel,
    onStartGame: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showTutorialLink: Boolean = false,
    onTutorial: () -> Unit = {},
    /** Opens the lobby, for a game with a Nearby seat. */
    onHost: () -> Unit = {},
    /** Opens joining a game hosted on a phone nearby, where there can be one. */
    onJoin: () -> Unit = {},
) {
    val setup by viewModel.setupForm.setup.collectAsStateWithLifecycle()
    val setupRestored by viewModel.setupForm.setupRestored.collectAsStateWithLifecycle()
    val activeSlots = setup.playerSlots
    val focusManager = LocalFocusManager.current

    val defaultNames = (1..BoardGeometry.MAX_PLAYERS).map { stringResource(Res.string.common_default_player_name, it) }
    val defaultName: (Int) -> String = { defaultNames[it - 1] }
    val duplicateNameSlots = setup.duplicateNameSlots(defaultName)
    val namesMustBeUnique = stringResource(Res.string.setup_names_unique)

    ScreenScaffold(title = stringResource(Res.string.setup_title), onBack = onBack, modifier = modifier, wide = true) {
        // Nothing but the title bar until the saved choices are back: drawing the form any sooner
        // shows the defaults for a few frames before they switch to
        // the last game's - and a Start Game tapped in that window would play the defaults. The page
        // cover is kept up over it meanwhile.
        if (!setupRestored) return@ScreenScaffold HoldPageCover()

        val playerCards = @Composable {
            SetupCard(title = stringResource(Res.string.setup_players)) {
                PlayerCountSelector(selected = setup.playerCount, onSelect = viewModel.setupForm::setPlayerCount)
            }
            PlayerCards(activeSlots = activeSlots, duplicateNameSlots = duplicateNameSlots, namesMustBeUnique = namesMustBeUnique, defaultName = defaultName, form = viewModel.setupForm)
        }
        val actions = @Composable {
            // Until a first game is finished: the way to the guided tutorial.
            if (showTutorialLink) {
                TextButton(onClick = onTutorial, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(Res.string.setup_tutorial_link), style = MaterialTheme.typography.titleSmall)
                }
            }
            if (duplicateNameSlots.isNotEmpty()) {
                Text(
                    text = namesMustBeUnique,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    // A name field still focused would keep blinking its cursor on the fading-out page, and the
                    // keyboard up, for the frames the next screen takes to arrive.
                    focusManager.clearFocus()
                    when {
                        viewModel.setupHasNearbySeats -> onHost()
                        viewModel.startGame(defaultName) -> onStartGame()
                    }
                },
                enabled = duplicateNameSlots.isEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = MAIN_ACTION_HEIGHT),
            ) {
                Text(text = stringResource(Res.string.setup_start), style = MaterialTheme.typography.titleMedium)
            }
            // A game hosted on a phone nearby, where this one can join one.
            if (LocalNearby.current.available) {
                TextButton(onClick = onJoin, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.setup_join_nearby))
                }
            }
        }

        if (LocalLandscapePage.current) {
            // Side by side: the players, then the game and the way in. Each side scrolls on its own, its scrollbar in the gap
            // or margin beside it, the button held under the second as it is under the whole form in portrait.
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                ScrollingColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { playerCards() }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScrollingColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) { GameCards(setup = setup, viewModel = viewModel) }
                    actions()
                }
            }
            return@ScreenScaffold
        }

        // The form scrolls on its own, and the Start Game button sits directly after it rather than
        // pinned to the foot of the screen, where an achievement banner can cover it. weight(1f,
        // fill = false) gives the form at most the height left over once the button is placed, and
        // lets it shrink to its own height when it needs less. A short form keeps the button right
        // underneath it; a form too tall for the screen scrolls (its scrollbar in the margin), with the button held at the bottom.
        ScrollingColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            playerCards()
            GameCards(setup = setup, viewModel = viewModel)
        }
        actions()
    }
}

/** The form's first half: each player's colour, name and type. */
@Composable
private fun PlayerCards(
    activeSlots: List<PlayerSetupSlot>,
    duplicateNameSlots: Set<Int>,
    namesMustBeUnique: String,
    defaultName: (Int) -> String,
    form: GameSetupForm,
) {
    // Every active seat gets a row, including player 1 (always Human, at this device) - one card
    // holding all of them rather than a card per player: stacked cards, each with its own title,
    // padding and controls, is what pushed the form off the screen. No card title here - every
    // row already names its own player.
    val colourHolders = activeSlots.associate { it.colour to it.slot }
    SetupCard(rowSpacing = 2.dp) {
        activeSlots.forEachIndexed { index, slot ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            PlayerRow(
                slot = slot,
                isTypeLocked = slot.slot == 1,
                isNameDuplicate = slot.slot in duplicateNameSlots,
                namesMustBeUnique = namesMustBeUnique,
                defaultName = defaultName(slot.slot),
                colourHolders = colourHolders,
                onColourChange = { colour -> form.setPlayerColour(slot.slot, colour) },
                onTypeChange = { type -> form.setPlayerType(slot.slot, type) },
                onNameChange = { name -> form.setPlayerName(slot.slot, name) },
                onDifficultyChange = { difficulty -> form.setPlayerDifficulty(slot.slot, difficulty) },
            )
        }
    }
}

/** The form's second half: teams, the game mode, and the house rules. */
@Composable
private fun GameCards(setup: GameSetupState, viewModel: GameViewModel) {
    val form = viewModel.setupForm
    SetupCard(title = stringResource(Res.string.setup_teams)) { TeamsChoice(setup, form) }
    SetupCard(title = stringResource(Res.string.setup_game_mode)) { GameModeChoice(setup, viewModel) }
    // A custom mode sets the house rules itself: the card says which, in place of the switches.
    val custom = setup.customMode
    if (custom != null) {
        SetupCard(title = stringResource(Res.string.setup_house_rules), modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                text = stringResource(Res.string.setup_house_rules_custom, custom.name, houseRuleList(custom.houseRules)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    // Until a first game is finished, a note in place of the picker; the picks are kept, but not played.
    if (!setup.houseRulesUnlocked) {
        SetupCard(title = stringResource(Res.string.setup_house_rules), enabled = false, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                text = stringResource(Res.string.setup_house_rules_locked),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = disabledContentColour(),
            )
        }
        return
    }
    SetupCard(title = stringResource(Res.string.setup_house_rules)) { HouseRulesPicker(setup, form) }
}

/**
 * The house rules ([HouseRule]): a field counting those on, opening a page of a switch each, with what it changes. While
 * any is on, the field says what the game then doesn't count towards.
 */
@Composable
private fun HouseRulesPicker(setup: GameSetupState, form: GameSetupForm) {
    ModifierPicker(
        title = stringResource(Res.string.setup_house_rules),
        description = stringResource(Res.string.setup_house_rules_description),
        activeNote = stringResource(Res.string.setup_house_rules_active_note),
        modifiers = HouseRule.entries.map { rule ->
            ModifierSetting(
                title = stringResource(rule.title),
                description = stringResource(rule.description),
                enabled = rule in setup.houseRules,
                onEnabledChange = { on -> form.setHouseRule(rule, on) },
            )
        },
    )
}

/** The most space between a [SetupCard]'s rows on a landscape page. */
private val LANDSCAPE_ROW_SPACING = 2.dp

/**
 * One section of the form - a card is M3's grouping surface for exactly this.
 *
 * [title] is optional: a section whose contents already label themselves (the player rows) doesn't
 * need a heading repeating it. [rowSpacing] is tightened further still for the player list, where
 * four rows (plus their dividers) need to fit on screen at once alongside every other card.
 * With [enabled] false the card is darker and its title takes Material's disabled text colour.
 * Tighter on a landscape page, where the form has height to spare for nothing.
 */
@Composable
private fun SetupCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    rowSpacing: Dp = 6.dp,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colours = CardDefaults.cardColors()
    Card(
        // Disabled, the card sits back: a darker surface than the others, rather than Material's disabled fill, which is
        // lighter than this theme's cards and so read as raised.
        colors = if (enabled) colours else colours.copy(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth(),
    ) {
        val landscape = LocalLandscapePage.current
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (landscape) 4.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (landscape) minOf(rowSpacing, LANDSCAPE_ROW_SPACING) else rowSpacing),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else disabledContentColour(),
                )
            }
            content()
        }
    }
}

/** Material's disabled text colour: `onSurface` at 38%. */
@Composable
private fun disabledContentColour() = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_CONTENT_ALPHA)

private const val DISABLED_CONTENT_ALPHA = 0.38f

/**
 * A player on one line: their name (editable for a User, automatic for a CPU) and the control
 * that switches between the two. No separate "Player N" label - the name field's own value (or,
 * for a CPU, the difficulty picker itself) already identifies the row.
 *
 * [isTypeLocked] is true only for player 1, who is always Human (this device's own player) - their
 * row has no [FilterChip], just an empty space the chip's width, so every row's name field still
 * ends at the same right edge. (It used to say "You"; the row needs no label to be player 1's.)
 */
@Composable
private fun PlayerRow(
    slot: PlayerSetupSlot,
    isTypeLocked: Boolean,
    isNameDuplicate: Boolean,
    namesMustBeUnique: String,
    defaultName: String,
    colourHolders: Map<PlayerColour, Int>,
    onColourChange: (PlayerColour) -> Unit,
    onTypeChange: (PlayerType) -> Unit,
    onNameChange: (String) -> Unit,
    onDifficultyChange: (Difficulty) -> Unit,
) {
    // The name (or difficulty) and the User/CPU switch share a row at the normal font size. At a larger one
    // the difficulty's three labels no longer fit beside the switch, so it drops beneath - the controls
    // keep the width their labels need instead of being squeezed or cut off.
    val fontScale = LocalDensity.current.textScale
    val nameFieldDescription = stringResource(Res.string.setup_player_name_cd, slot.slot)
    val userLabel = stringResource(Res.string.setup_type_user)
    val cpuLabel = stringResource(Res.string.common_cpu_cd)
    val stacked = fontScale > STACKED_PLAYER_ROW_FONT_SCALE
    val nameOrDifficulty: @Composable (Modifier) -> Unit = { controlModifier ->
        // The colour circle leads the name (or difficulty), whichever the row shows, so both seat types line up.
        Row(modifier = controlModifier, verticalAlignment = Alignment.CenterVertically) {
            PlayerColourPicker(slot = slot.slot, colour = slot.colour, holders = colourHolders, onColourChange = onColourChange)
            when (slot.type) {
                PlayerType.HUMAN -> CompactNameField(
                    value = slot.name,
                    onValueChange = onNameChange,
                    isError = isNameDuplicate,
                    placeholder = defaultName,
                    // The field has no visible label (its value names the row), so a screen reader is
                    // given one - and told why it's red, which the outline alone only shows.
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = nameFieldDescription
                        if (isNameDuplicate) error(namesMustBeUnique)
                    },
                )

                PlayerType.AI -> DifficultySelector(
                    selected = slot.difficulty,
                    onSelect = onDifficultyChange,
                    modifier = Modifier.weight(1f),
                )

                PlayerType.NEARBY -> Text(
                    text = stringResource(Res.string.setup_nearby_seat),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
        }
    }
    // Grows with the font so "Player" and "CPU" fit; player 1's empty space grows with it, so every row still lines up.
    // ...and with the words: "Jugador" is wider than "Player", and a label that doesn't fit its chip would break mid-word, so
    // the chip is as wide as the longer of its two labels needs when that's more than the usual width.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)
    val nearbyLabel = stringResource(Res.string.setup_type_nearby)
    val nearby = LocalNearby.current.available
    val labelWidth = with(density) {
        listOf(userLabel, cpuLabel, nearbyLabel).maxOf { measurer.measure(it, labelStyle).size.width }.toDp()
    }
    val typeControlWidth = maxOf(TYPE_CONTROL_WIDTH * fontScale.coerceAtLeast(1f), labelWidth + TYPE_CHIP_PADDING)
    val typeControl: @Composable () -> Unit = {
        if (isTypeLocked) {
            Spacer(modifier = Modifier.width(typeControlWidth))
        } else {
            FilterChip(
                selected = slot.type != PlayerType.HUMAN,
                // Player, then CPU, then (where phones nearby can join) a Nearby player, then Player again.
                onClick = {
                    onTypeChange(
                        when (slot.type) {
                            PlayerType.HUMAN -> PlayerType.AI
                            PlayerType.AI -> if (nearby) PlayerType.NEARBY else PlayerType.HUMAN
                            PlayerType.NEARBY -> PlayerType.HUMAN
                        },
                    )
                },
                label = {
                    // FilterChip's own Row left-aligns its label rather than centering it, so at a
                    // fixed chip width the leftover space all landed on one side - most visible as
                    // "CPU" and "Player" sitting at different horizontal positions. A label that fills
                    // the whole slot and centers its own text isn't subject to that.
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val label = when (slot.type) {
                            PlayerType.HUMAN -> userLabel
                            PlayerType.AI -> cpuLabel
                            PlayerType.NEARBY -> nearbyLabel
                        }
                        Text(label, fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                },
                modifier = Modifier.width(typeControlWidth),
            )
        }
    }

    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            nameOrDifficulty(Modifier.fillMaxWidth())
            // Player 1 has no switch, and on a line of its own its empty space would only leave a gap.
            if (!isTypeLocked) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { typeControl() }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            nameOrDifficulty(Modifier.weight(1f))
            typeControl()
        }
    }
}

/**
 * A single-line name field shrunk close to [DifficultySelector]'s own height so toggling User/CPU
 * doesn't resize the whole row - the public `OutlinedTextField` composable enforces a 56dp minimum
 * height that isn't reachable through its own modifier/parameters, so this builds the same look
 * from [BasicTextField] plus [OutlinedTextFieldDefaults.DecorationBox], which takes an explicit
 * [contentPadding][OutlinedTextFieldDefaults.contentPadding] instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactNameField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    placeholder: String = "",
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onFocusChanged { focusState ->
            // Trimmed only once the player has moved on, not on every keystroke - trimming a
            // trailing space the instant it's typed would make it impossible to type a second
            // word, and leading/trailing whitespace can't matter until the value is final anyway.
            if (!focusState.isFocused) {
                val trimmed = value.trim()
                if (trimmed != value) onValueChange(trimmed)
            }
        },
        // Content: a name typed in a right-to-left script runs and aligns that way, whatever the app's own language.
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface, textDirection = TextDirection.Content),
        singleLine = true,
        // Names read as Capitalized Words, not lowercase - and this keeps the keyboard's own
        // shift state in sync with that after the field is cleared back to empty, which a plain
        // default keyboard doesn't do on its own (it just keeps whatever case the last edit left
        // it in).
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interactionSource,
    ) { innerTextField ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = value,
            innerTextField = innerTextField,
            enabled = true,
            singleLine = true,
            isError = isError,
            visualTransformation = VisualTransformation.None,
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            interactionSource = interactionSource,
            colors = colors,
            contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 6.dp, bottom = 6.dp),
        )
    }
}

/**
 * Compact Easy/Medium/Hard picker for one AI slot - a segmented row rather than a full row of
 * chips, since it has to fit inside the player row alongside the name column.
 *
 * The labels are sized together, so the three always match: [DIFFICULTY_LABEL_MAX_SIZE] when the widest
 * ("Medium") fits its segment, stepping down to [MIN_READABLE_FONT_SIZE] when it doesn't, and only if
 * even that is too wide - a narrow screen - all three become their initials (E / M / H) at that size.
 * A screen reader still hears the full word. See [FittedSegmentedChoiceRow].
 */
@Composable
fun DifficultySelector(selected: Difficulty, onSelect: (Difficulty) -> Unit, modifier: Modifier = Modifier) {
    FittedSegmentedChoiceRow(
        options = Difficulty.entries,
        selected = selected,
        onSelect = onSelect,
        label = { it.label() },
        modifier = modifier,
        maxFontSize = DIFFICULTY_LABEL_MAX_SIZE,
        // Tighter than M3's 12dp a side: with the colour circle beside it each segment is narrow.
        labelPadding = DIFFICULTY_LABEL_PADDING,
    )
}

private val DIFFICULTY_LABEL_PADDING = 4.dp
private val DIFFICULTY_LABEL_MAX_SIZE = 14.sp

@Composable
private fun Difficulty.label(): String = stringResource(
    when (this) {
        Difficulty.EASY -> Res.string.setup_difficulty_easy
        Difficulty.MEDIUM -> Res.string.setup_difficulty_medium
        Difficulty.HARD -> Res.string.setup_difficulty_hard
    },
)

/**
 * The mode picker: one field showing the current mode and its one-line description, opening a
 * scrollable list of every [GameMode] - the list is long enough to outgrow the form, so it isn't
 * laid out inline. See [ChoicePicker].
 */
/**
 * How the players make teams, with an even count of four or more: Off, Two Teams, or Pairs, a line saying what that
 * makes of this game, and while teams are on, the switches for partners capturing each other and rolling for a partner.
 * Any other count says why there are none; the choice is kept for when the count changes back.
 */
@Composable
private fun TeamsChoice(setup: GameSetupState, form: GameSetupForm) {
    val count = setup.playerCount
    if (!TeamMode.allowsTeams(count)) {
        Text(
            text = stringResource(Res.string.setup_teams_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FittedSegmentedChoiceRow(
        options = TeamMode.entries,
        selected = setup.teamMode,
        onSelect = form::setTeamMode,
        label = { stringResource(it.label) },
        modifier = Modifier.fillMaxWidth(),
    )
    val description = when (setup.teamMode) {
        TeamMode.OFF -> null
        TeamMode.TWO_TEAMS -> pluralStringResource(Res.plurals.setup_teams_two_teams_description, count / 2, count / 2)
        TeamMode.PAIRS -> pluralStringResource(Res.plurals.setup_teams_pairs_description, count / 2, count / 2)
    }
    if (description == null) return
    Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SetupSwitch(stringResource(Res.string.setup_partner_captures), setup.partnerCaptures, form::setPartnerCaptures)
    SetupSwitch(stringResource(Res.string.setup_roll_for_partners), setup.rollForPartners, form::setRollForPartners)
}

/** An on/off choice: the whole row is the toggle, said as one ("Partners can capture each other, switch, off"). */
@Composable
private fun SetupSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** How many play, 2 to 8: one segment a number, each said as "4 players". */
@Composable
private fun PlayerCountSelector(selected: Int, onSelect: (Int) -> Unit) {
    FittedSegmentedChoiceRow(
        options = (BoardGeometry.MIN_PLAYERS..BoardGeometry.MAX_PLAYERS).toList(),
        selected = selected,
        onSelect = onSelect,
        label = { it.toString() },
        spokenLabel = { pluralStringResource(Res.plurals.setup_player_count_spoken, it, it) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A pick on the Game Mode card: a built-in mode, or one of the player's own. */
private sealed interface ModeOption {
    data class BuiltIn(val mode: GameMode) : ModeOption

    data class Custom(val mode: CustomMode) : ModeOption
}

/**
 * The mode picker: every built-in mode, then (once house rules are unlocked) the player's custom modes, each a name and
 * one line (a custom mode's: its base and house rules). Under it, a button that makes a custom mode, and while one is
 * picked, its Edit and Delete; Delete asks first.
 */
@Composable
private fun GameModeChoice(setup: GameSetupState, viewModel: GameViewModel) {
    val form = viewModel.setupForm
    val custom = setup.customMode
    val options = GameMode.entries.map(ModeOption::BuiltIn) + if (setup.houseRulesUnlocked) setup.customModes.map(ModeOption::Custom) else emptyList()
    ChoicePicker(
        title = stringResource(Res.string.setup_game_mode),
        options = options,
        selected = custom?.let(ModeOption::Custom) ?: ModeOption.BuiltIn(setup.gameMode),
        onSelect = { option ->
            when (option) {
                is ModeOption.BuiltIn -> form.setGameMode(option.mode)
                is ModeOption.Custom -> form.selectCustomMode(option.mode.id)
            }
        },
        label = { option ->
            when (option) {
                is ModeOption.BuiltIn -> stringResource(option.mode.displayName)
                is ModeOption.Custom -> option.mode.name
            }
        },
        description = { option ->
            when (option) {
                is ModeOption.BuiltIn -> stringResource(option.mode.description)
                is ModeOption.Custom -> stringResource(Res.string.setup_custom_mode_description, stringResource(option.mode.base.displayName), houseRuleList(option.mode.houseRules))
            }
        },
    )
    if (!setup.houseRulesUnlocked) return
    // The custom mode being made or changed (null for none), and the one waiting to be deleted.
    var editing by remember { mutableStateOf<EditorOpen?>(null) }
    var deleting by remember { mutableStateOf<CustomMode?>(null) }
    // Wrapping at a large font rather than running off the card.
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
        if (custom != null) {
            TextButton(onClick = { editing = EditorOpen(custom) }) { Text(stringResource(Res.string.setup_custom_edit)) }
            TextButton(onClick = { deleting = custom }) { Text(stringResource(Res.string.setup_custom_delete)) }
        }
        TextButton(onClick = { editing = EditorOpen(null) }) { Text(stringResource(Res.string.setup_custom_new)) }
    }
    editing?.let { open ->
        CustomModeEditor(
            editing = open.mode,
            existing = setup.customModes,
            onSave = { mode ->
                editing = null
                viewModel.saveCustomMode(mode)
            },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { mode ->
        LudoPlusDialog(
            icon = Symbols.DeleteForever,
            title = stringResource(Res.string.setup_custom_delete_title, mode.name),
            message = stringResource(Res.string.setup_custom_delete_message),
            confirmLabel = stringResource(Res.string.setup_custom_delete),
            onConfirm = {
                deleting = null
                viewModel.deleteCustomMode(mode.id)
            },
            dismissLabel = stringResource(Res.string.common_cancel),
            onDismiss = { deleting = null },
            onDismissRequest = { deleting = null },
        )
    }
}

/** The editor, open on [mode] (null for a new one). */
private class EditorOpen(val mode: CustomMode?)

/** [houseRules]' names as a list ("Blockades, Must Capture, and Free Start"), in [HouseRule]'s order. */
@Composable
private fun houseRuleList(houseRules: Set<HouseRule>): String =
    spokenList(HouseRule.entries.filter { it in houseRules }.map { stringResource(it.title) })
