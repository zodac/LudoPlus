package net.zodac.ludoplus.ui.common

import androidx.compose.foundation.border
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider

/** What [AppTooltip] shows on request - see [rememberAppTooltipState]; wraps Material's experimental state. */
@OptIn(ExperimentalMaterial3Api::class)
class AppTooltipState internal constructor(internal val state: TooltipState) {
    /** Whether the tooltip is up. */
    val isVisible: Boolean get() = state.isVisible

    /** Shows the tooltip, until it times out - or, for a persistent one, until it's dismissed. */
    suspend fun show() = state.show()

    /** Hides the tooltip. */
    fun dismiss() = state.dismiss()

    /** Shows the tooltip if it's hidden, hides it if it's showing. */
    suspend fun toggle() = if (state.isVisible) state.dismiss() else state.show()
}

/** A state for [AppTooltip]; with [persistent] its tooltip stays until it's dismissed, rather than timing out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppTooltipState(persistent: Boolean = false): AppTooltipState {
    val state = rememberTooltipState(isPersistent = persistent)
    return remember(state) { AppTooltipState(state) }
}

/**
 * The one tooltip every screen uses - a plain tooltip over [content] - so its look, placement and
 * behaviour are changed here and nowhere else. Don't reach for `TooltipBox` directly. [body] is what
 * it shows: usually a [message] of text (the overloads below), or a layout of its own (the board's
 * totals, a two-column table).
 *
 * By default a long press shows it ([enabled] false switches that off). Pass [state] to show it from
 * something else too, such as a tap: `state.show()` in a coroutine. Give the content a matching action
 * for a screen reader - a tooltip isn't announced, so it can't be the only way to reach what it says.
 * With [focusable] false, a tap outside it reaches what's under it (as well as closing it) instead of only closing it.
 * [wide] is for a layout too big for a plain tooltip: up to a rich tooltip's width, and kept clear of the screen's edges.
 */
// rememberPlainTooltipPositionProvider is deprecated in favour of rememberTooltipPositionProvider,
// which doesn't exist yet in material3 1.4.0 - it arrives with the 1.5.0 line. Swap it over then.
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTooltip(
    body: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: AppTooltipState = rememberAppTooltipState(),
    enabled: Boolean = true,
    focusable: Boolean = true,
    wide: Boolean = false,
    content: @Composable () -> Unit,
) {
    val margin = with(LocalDensity.current) { WIDE_TOOLTIP_MARGIN.roundToPx() }
    val shape = if (wide) MaterialTheme.shapes.medium else MaterialTheme.shapes.small
    TooltipBox(
        positionProvider = if (wide) remember(margin) { WideTooltipPosition(margin) } else TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            // Raised off the dark page by a lighter container, a gold outline and a shadow - branded
            // (gold is the app's one accent) yet plainly not part of the content it floats over. The
            // default inverse-surface tooltip is a stark light-grey slab on this dark theme.
            PlainTooltip(
                modifier = Modifier.border(TOOLTIP_OUTLINE, MaterialTheme.colorScheme.primary, shape),
                maxWidth = if (wide) TooltipDefaults.richTooltipMaxWidth else TooltipDefaults.plainTooltipMaxWidth,
                shape = shape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = TOOLTIP_ELEVATION,
            ) {
                body()
            }
        },
        state = state.state,
        modifier = modifier,
        focusable = focusable,
        enableUserInput = enabled,
        content = content,
    )
}

/** [AppTooltip] for a [message] of text - what nearly every tooltip is. */
@Composable
fun AppTooltip(
    message: AnnotatedString,
    modifier: Modifier = Modifier,
    state: AppTooltipState = rememberAppTooltipState(),
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) = AppTooltip(
    body = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
    modifier = modifier,
    state = state,
    enabled = enabled,
    content = content,
)

/** [AppTooltip] for a plain-text [message]. */
@Composable
fun AppTooltip(
    message: String,
    modifier: Modifier = Modifier,
    state: AppTooltipState = rememberAppTooltipState(),
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) = AppTooltip(AnnotatedString(message), modifier, state, enabled, content)

/** Centred on its anchor, above it where there's room or else below, and [margin] pixels clear of it and of the window's edges. */
private class WideTooltipPosition(private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.center.x - popupContentSize.width / 2
        val above = anchorBounds.top - margin - popupContentSize.height
        val y = if (above >= margin) above else anchorBounds.bottom + margin
        return IntOffset(x.within(windowSize.width - popupContentSize.width), y.within(windowSize.height - popupContentSize.height))
    }

    private fun Int.within(room: Int): Int {
        val least = margin.coerceAtMost(maxOf(room, 0))
        return coerceIn(least, maxOf(room - margin, least))
    }
}

private val WIDE_TOOLTIP_MARGIN = 8.dp
private val TOOLTIP_OUTLINE = 1.dp
private val TOOLTIP_ELEVATION = 6.dp

/** A tooltip message in [parseInlineMarkup]'s markup, with `` `backticked` `` text in bold gold. */
@Composable
fun tooltipMarkup(text: String): AnnotatedString {
    val gold = MaterialTheme.colorScheme.primary
    return remember(text, gold) {
        parseInlineMarkup(text, codeStyle = SpanStyle(fontWeight = FontWeight.Bold, color = gold))
    }
}

