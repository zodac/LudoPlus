package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.zodac.ludoplus.game.TutorialMessage
import net.zodac.ludoplus.game.TutorialStep
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.app_name
import net.zodac.ludoplus.resources.game_tutorial_welcome
import net.zodac.ludoplus.resources.game_tutorial_watch_green
import net.zodac.ludoplus.resources.game_tutorial_green_comes_out
import net.zodac.ludoplus.resources.game_tutorial_green_rolls_again
import net.zodac.ludoplus.resources.game_tutorial_green_moves_on
import net.zodac.ludoplus.resources.game_tutorial_your_turn_out
import net.zodac.ludoplus.resources.game_tutorial_bring_out
import net.zodac.ludoplus.resources.game_tutorial_six_rolls_again
import net.zodac.ludoplus.resources.game_tutorial_move_out_pawn
import net.zodac.ludoplus.resources.game_tutorial_capture_intro
import net.zodac.ludoplus.resources.game_tutorial_capture
import net.zodac.ludoplus.resources.game_tutorial_capture_rolls_again
import net.zodac.ludoplus.resources.game_tutorial_capture_move_on
import net.zodac.ludoplus.resources.game_tutorial_home_intro
import net.zodac.ludoplus.resources.game_tutorial_home
import net.zodac.ludoplus.resources.game_tutorial_home_rolls_again
import net.zodac.ludoplus.resources.game_tutorial_home_move_on
import net.zodac.ludoplus.resources.game_tutorial_win_intro
import net.zodac.ludoplus.resources.game_tutorial_too_many
import net.zodac.ludoplus.resources.game_tutorial_win_roll
import net.zodac.ludoplus.resources.game_tutorial_win
import net.zodac.ludoplus.resources.game_tutorial_part_done
import net.zodac.ludoplus.resources.game_tutorial_next_part_cd
import net.zodac.ludoplus.resources.game_tutorial_previous_part_cd
import net.zodac.ludoplus.ui.common.SoraFontFamily
import net.zodac.ludoplus.ui.common.VerticalScrollbar
import net.zodac.ludoplus.ui.common.parseInlineMarkup
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.icons.Symbols
import org.jetbrains.compose.resources.StringResource

/**
 * The tutorial's card, in place of the player cards: [coachName] and the [part] it is, over what [step] says. The name
 * and words are one node for a screen reader, a polite live region, so each new step is read out as it arrives; the
 * part is a node of its own. **Bold** text is a number. Its height is the caller's (see [coachCardHeight]): at a larger
 * font the words scroll inside it, with the app's scrollbar. [onBack] and [onForward] go to another part.
 */
@Composable
internal fun CoachCard(
    step: TutorialStep,
    coachName: String,
    part: String,
    onBack: (() -> Unit)?,
    onForward: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val text = tutorialText(step.message)
    val accent = MaterialTheme.colorScheme.primary
    val styled = remember(text) { parseInlineMarkup(text) }
    val scroll = rememberScrollState()
    // Each step's words start from the top.
    LaunchedEffect(step) { scroll.scrollTo(0) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        border = BorderStroke(1.dp, accent),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            PartArrow(back = true, onClick = onBack)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(vertical = CARD_PADDING_VERTICAL)
                        // The name and the words: one node, read out as each step arrives.
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = coachName,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            modifier = Modifier.weight(1f),
                        )
                        // A node of its own, so it isn't read out again with every step.
                        Text(
                            text = part,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics(mergeDescendants = true) {},
                        )
                    }
                    Text(text = styled, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            // Takes room beside the words only while they scroll (a larger font); at the standard size it draws nothing. Inset
            // as the words are, so the thumb's rounded ends stay clear of the card's edge and border.
            VerticalScrollbar(scroll, width = SCROLLBAR_WIDTH, modifier = Modifier.fillMaxHeight().padding(vertical = CARD_PADDING_VERTICAL))
            PartArrow(back = false, onClick = onForward)
        }
    }
}

/**
 * The card's back or forward to another part: a strip down its edge, the card's height and [ARROW_WIDTH] wide, so it's
 * an easy target. Shown, but dimmed and off, with nowhere to go ([onClick] null).
 */
@Composable
private fun PartArrow(back: Boolean, onClick: (() -> Unit)?) {
    val label = stringResource(if (back) Res.string.game_tutorial_previous_part_cd else Res.string.game_tutorial_next_part_cd)
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(ARROW_WIDTH)
            .fillMaxHeight()
            // The whole strip takes the tap, but shows it as an icon button does: the pressed state layer and ripple
            // on a circle round the arrow (below), not the strip's full height.
            .clickable(interactionSource = interactions, indication = null, enabled = onClick != null, role = Role.Button) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(ARROW_STATE_LAYER)
                .clip(CircleShape)
                .indication(interactions, ripple()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (back) Symbols.KeyboardArrowLeft else Symbols.KeyboardArrowRight,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (onClick != null) 1f else DISABLED_ARROW_ALPHA),
            )
        }
    }
}

/**
 * The height a [CoachCard] [width] wide needs for the longest of [steps]' words, at the font size in use up to
 * [MAX_MEASURED_FONT_SCALE] - so a card held at it never grows or shrinks between steps, every step's words show whole up
 * to that size, and at a larger font they scroll rather than push the die off the screen.
 */
@Composable
internal fun coachCardHeight(steps: List<TutorialStep>, width: Dp): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current.let { Density(it.density, fontScale = it.fontScale.coerceAtMost(MAX_MEASURED_FONT_SCALE)) }
    val body = MaterialTheme.typography.bodyMedium
    val label = MaterialTheme.typography.labelLarge.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)
    val texts = steps.map { tutorialText(it.message) }
    return remember(texts, width, density, body, label) {
        with(density) {
            val textWidth = (width - ARROW_WIDTH * 2).coerceAtLeast(1.dp).roundToPx()
            val labelHeight = measurer.measure("Coach", label, density = density).size.height.toDp() // i18n: not translated - measured for its line height, not shown
            texts.maxOf { text ->
                val words = measurer.measure(parseInlineMarkup(text), body, constraints = Constraints(maxWidth = textWidth), density = density).size.height.toDp()
                labelHeight + words + CARD_PADDING_VERTICAL * 2 + CARD_BORDER * 2
            }
        }
    }
}

/** The largest font scale the card grows for: past it, the words scroll inside it. */
private const val MAX_MEASURED_FONT_SCALE = 1.3f

// The back and forward strips at the card's edges: M3's 48dp minimum touch target across.
private val ARROW_WIDTH = 48.dp
// M3's icon button state layer: a 40dp circle round the glyph.
private val ARROW_STATE_LAYER = 40.dp
private val CARD_PADDING_VERTICAL = 8.dp
private const val DISABLED_ARROW_ALPHA = 0.3f
private val CARD_BORDER = 1.dp
// The scrollbar beside the words, when they scroll.
private val SCROLLBAR_WIDTH = 10.dp

/** [message]'s words, given the app's name to use where it's written (the welcome). */
@Composable
private fun tutorialText(message: TutorialMessage): String = stringResource(message.resource, stringResource(Res.string.app_name))

/** The string each [TutorialMessage] is written in: `game_tutorial_` and its name. */
private val TutorialMessage.resource: StringResource
    get() = when (this) {
        TutorialMessage.WELCOME -> Res.string.game_tutorial_welcome
        TutorialMessage.WATCH_GREEN -> Res.string.game_tutorial_watch_green
        TutorialMessage.GREEN_COMES_OUT -> Res.string.game_tutorial_green_comes_out
        TutorialMessage.GREEN_MOVES_ON -> Res.string.game_tutorial_green_moves_on
        TutorialMessage.GREEN_ROLLS_AGAIN -> Res.string.game_tutorial_green_rolls_again
        TutorialMessage.YOUR_TURN_OUT -> Res.string.game_tutorial_your_turn_out
        TutorialMessage.BRING_OUT -> Res.string.game_tutorial_bring_out
        TutorialMessage.SIX_ROLLS_AGAIN -> Res.string.game_tutorial_six_rolls_again
        TutorialMessage.MOVE_OUT_PAWN -> Res.string.game_tutorial_move_out_pawn
        TutorialMessage.CAPTURE_INTRO -> Res.string.game_tutorial_capture_intro
        TutorialMessage.CAPTURE -> Res.string.game_tutorial_capture
        TutorialMessage.CAPTURE_ROLLS_AGAIN -> Res.string.game_tutorial_capture_rolls_again
        TutorialMessage.CAPTURE_MOVE_ON -> Res.string.game_tutorial_capture_move_on
        TutorialMessage.HOME_INTRO -> Res.string.game_tutorial_home_intro
        TutorialMessage.HOME -> Res.string.game_tutorial_home
        TutorialMessage.HOME_ROLLS_AGAIN -> Res.string.game_tutorial_home_rolls_again
        TutorialMessage.HOME_MOVE_ON -> Res.string.game_tutorial_home_move_on
        TutorialMessage.WIN_INTRO -> Res.string.game_tutorial_win_intro
        TutorialMessage.TOO_MANY -> Res.string.game_tutorial_too_many
        TutorialMessage.WIN_ROLL -> Res.string.game_tutorial_win_roll
        TutorialMessage.WIN -> Res.string.game_tutorial_win
        TutorialMessage.PART_DONE -> Res.string.game_tutorial_part_done
    }
