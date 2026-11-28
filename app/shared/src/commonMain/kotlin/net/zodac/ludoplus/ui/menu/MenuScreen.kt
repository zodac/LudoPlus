package net.zodac.ludoplus.ui.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.menu_continue
import net.zodac.ludoplus.resources.menu_achievements
import net.zodac.ludoplus.resources.menu_leaderboard
import net.zodac.ludoplus.resources.menu_new_game
import net.zodac.ludoplus.resources.menu_play
import net.zodac.ludoplus.resources.menu_rules
import net.zodac.ludoplus.resources.menu_settings
import net.zodac.ludoplus.resources.menu_statistics
import net.zodac.ludoplus.resources.menu_styles
import net.zodac.ludoplus.ui.common.AppLogo
import net.zodac.ludoplus.ui.common.BrandBackdrop
import net.zodac.ludoplus.ui.common.LocalLandscapePage
import net.zodac.ludoplus.ui.common.LocalPageContentHeight
import net.zodac.ludoplus.ui.common.MAIN_ACTION_HEIGHT
import net.zodac.ludoplus.ui.common.PAGE_VERTICAL_PADDING
import net.zodac.ludoplus.ui.common.PageColumn
import net.zodac.ludoplus.ui.common.WholeWordsText
import net.zodac.ludoplus.ui.common.isLargeFont
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.style.GameVisualTheme
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/**
 * The least a menu button stands, and a fingertip's target: [MenuLayout] gives it [MAIN_ACTION_HEIGHT] where the page has
 * the height. Still a stock `Button` in every respect but its height - shape, colour roles, ripple, state layers and
 * typography all come from the theme.
 */
private val MENU_BUTTON_MIN_HEIGHT = 48.dp

/**
 * The split Play button's dividing line: the page showing through between its two halves, the same
 * 2dp M3's connected button groups leave between their buttons.
 */
private val PLAY_SPLIT_GAP = 2.dp

// Each half keeps the pill's rounded ends on its outer side and meets the other along a straight edge.
private val NEW_GAME_HALF_SHAPE = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
private val CONTINUE_HALF_SHAPE = RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)

@Composable
fun MenuScreen(
    hasInProgressGame: Boolean?,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onAchievements: () -> Unit,
    onLeaderboard: () -> Unit,
    onStatistics: () -> Unit,
    onStyles: () -> Unit,
    onRules: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    logoTheme: GameVisualTheme? = GameVisualTheme(),
    debugBuild: Boolean = false,
    onLogoPawnTapped: () -> Unit = {},
) {
    // The menu alone has its backdrop drifting; every other screen keeps it still.
    BrandBackdrop(modifier = modifier, driftingDice = true) {
        // No vertical padding of the page's own: the margins are among what MenuLayout tightens.
        PageColumn(horizontalPadding = 28.dp, wide = true, verticalPadding = 0.dp) {
            // Held invisible (still taking its space) until the saved-game check has answered, so the
            // buttons arrive together rather than Play flashing before it splits, or a gap popping in.
            val ready = hasInProgressGame != null
            val stackAlpha by animateFloatAsState(
                targetValue = if (ready) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "menuButtonsAlpha", // i18n: not translated - an animation label, not shown
            )
            val held = Modifier
                .alpha(stackAlpha)
                .then(if (ready) Modifier else Modifier.pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
            val destinations = listOf(
                stringResource(Res.string.menu_achievements) to onAchievements,
                stringResource(Res.string.menu_leaderboard) to onLeaderboard,
                stringResource(Res.string.menu_statistics) to onStatistics,
                stringResource(Res.string.menu_styles) to onStyles,
                stringResource(Res.string.menu_rules) to onRules,
                stringResource(Res.string.menu_settings) to onSettings,
            )
            // Destinations a row: one, under each other, or two side by side in landscape, where seven in a column don't fit
            // the height.
            val landscape = LocalLandscapePage.current
            val perRow = if (landscape) 2 else 1
            MenuLayout(
                pageHeight = LocalPageContentHeight.current,
                landscape = landscape,
                logo = { AppLogo(logoTheme, debugMark = debugBuild, onPawnTapped = onLogoPawnTapped) },
                rows = {
                    // One filled button for the primary action and tonal buttons for the rest: M3's
                    // emphasis hierarchy, which also stops five identical slabs competing for the eye.
                    Box(modifier = held, propagateMinConstraints = true) {
                        PlayButton(hasInProgressGame = hasInProgressGame == true, onContinue = onContinue, onNewGame = onNewGame)
                    }
                    for (row in destinations.chunked(perRow)) {
                        // Height set by the taller button, so a label that wraps at a large font doesn't leave its neighbour short.
                        Row(modifier = held.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(MENU_BUTTON_GAP)) {
                            for ((label, onClick) in row) {
                                MenuDestinationButton(label = label, onClick = onClick, modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                },
            )
        }
    }
}

/** The gap between the menu's buttons, where the page has the height for it. */
private val MENU_BUTTON_GAP = 12.dp

/** The gap between two rows of buttons on a page short of height. */
private val MENU_BUTTON_MIN_GAP = 8.dp

/** The gap between the logo and the buttons under it, where the page has the height for it. */
private val LOGO_GAP = 48.dp

/** The gap between the logo and the buttons under it on a page short of height. */
private val LOGO_MIN_GAP = 20.dp

/** The margin above and below the menu on a page short of height. */
private val MENU_MIN_VERTICAL_PADDING = 8.dp

/** The gap between the logo and the buttons beside it, in landscape. */
private val PANE_GAP = 24.dp

/** The least the logo is drawn at, of its own size, on a page short of height. */
private const val LOGO_MIN_SCALE = 0.6f

/** How much of the height left over goes above the menu: the logo sits in the lit upper third, and the buttons just below the middle. */
private const val SPARE_HEIGHT_ABOVE = 0.22f / (0.22f + 0.38f)

/** How many steps each of [MenuLayout]'s two tightenings is taken in. */
private const val FIT_STEPS = 50

/**
 * The menu's [logo] over its [rows] of buttons, or beside them in [landscape], fitted to [pageHeight]. Where it's too tall
 * at full size, it tightens only as far as it must: first its spacing (the margins, the gap under the logo, and the gaps
 * between the buttons), then, with that at its least, the buttons' height (to [MENU_BUTTON_MIN_HEIGHT]) and the logo's
 * size (to [LOGO_MIN_SCALE]). Taller than [pageHeight] even so, it keeps those least sizes and the page scrolls. A button
 * is never shorter than its label needs, and text in one is never shrunk.
 */
@Composable
private fun MenuLayout(
    pageHeight: Dp,
    landscape: Boolean,
    logo: @Composable () -> Unit,
    rows: @Composable () -> Unit,
) {
    Layout(contents = listOf(logo, rows), modifier = Modifier.fillMaxWidth()) { (logoMeasurables, rowMeasurables), constraints ->
        val width = constraints.maxWidth
        val buttonWidth = if (landscape) (width - PANE_GAP.roundToPx()) / 2 else width
        val logoPlaceable = logoMeasurables.single().measure(Constraints(maxWidth = buttonWidth))
        val needed = rowMeasurables.map { it.minIntrinsicHeight(buttonWidth) }

        val room = pageHeight.roundToPx()
        // Worked out in the pixels it's placed in, so a fit is a fit to the pixel.
        val sizes = { spacing: Float, size: Float ->
            val buttonHeight = lerp(MAIN_ACTION_HEIGHT, MENU_BUTTON_MIN_HEIGHT, size).roundToPx()
            val padding = lerp(PAGE_VERTICAL_PADDING, MENU_MIN_VERTICAL_PADDING, spacing).roundToPx()
            MenuSizes(
                padding = padding,
                logoGap = lerp(LOGO_GAP, LOGO_MIN_GAP, spacing).roundToPx(),
                buttonGap = lerp(MENU_BUTTON_GAP, MENU_BUTTON_MIN_GAP, spacing).roundToPx(),
                rowHeights = needed.map { maxOf(it, buttonHeight) },
                // Beside the buttons, it has the whole height to itself.
                logoScale = if (landscape) {
                    ((room - padding * 2f) / logoPlaceable.height).coerceIn(LOGO_MIN_SCALE, 1f)
                } else {
                    lerp(1f, LOGO_MIN_SCALE, size)
                },
                fullLogoHeight = logoPlaceable.height,
                landscape = landscape,
            )
        }
        val fitted = (0..FIT_STEPS * 2).asSequence()
            .map { step -> sizes((step.toFloat() / FIT_STEPS).coerceAtMost(1f), (step.toFloat() / FIT_STEPS - 1f).coerceAtLeast(0f)) }
            .let { candidates -> candidates.firstOrNull { it.height <= room } ?: candidates.last() }
        val height = maxOf(room, fitted.height)
        val rowPlaceables = rowMeasurables.mapIndexed { row, measurable -> measurable.measure(Constraints.fixed(buttonWidth, fitted.rowHeights[row])) }

        layout(width, height) {
            val spare = height - fitted.height
            // Side by side, each is centred in the height; otherwise the logo leads, with more of the spare height below.
            val logoTop = if (landscape) (height - fitted.logoHeight) / 2 else fitted.padding + (spare * SPARE_HEIGHT_ABOVE).roundToInt()
            val logoLeft = (buttonWidth - logoPlaceable.width) / 2
            if (fitted.logoScale == 1f) {
                logoPlaceable.placeRelative(logoLeft, logoTop)
            } else {
                // Drawn smaller rather than laid out again: its art, its wordmark, and the pawn's touch go together.
                logoPlaceable.placeRelativeWithLayer(logoLeft, logoTop) {
                    scaleX = fitted.logoScale
                    scaleY = fitted.logoScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
            }
            var top = if (landscape) (height - fitted.buttonsHeight) / 2 else logoTop + fitted.logoHeight + fitted.logoGap
            for (row in rowPlaceables) {
                row.placeRelative(width - buttonWidth, top)
                top += row.height + fitted.buttonGap
            }
        }
    }
}

/** [MenuLayout]'s sizes at one tightening, in pixels, and the height they come to. */
private class MenuSizes(
    val padding: Int,
    val logoGap: Int,
    val buttonGap: Int,
    val rowHeights: List<Int>,
    val logoScale: Float,
    fullLogoHeight: Int,
    landscape: Boolean,
) {
    val logoHeight = (fullLogoHeight * logoScale).roundToInt()
    val buttonsHeight = rowHeights.sum() + buttonGap * (rowHeights.size - 1)
    val height = padding * 2 + if (landscape) maxOf(logoHeight, buttonsHeight) else logoHeight + logoGap + buttonsHeight
}

/**
 * The menu's primary action. With no saved game it's a single Play button; with one it's split down
 * the middle into New Game and Continue - the same width, height and pill outline, so nothing else on
 * the menu moves, and no dialog to ask which. Each half is its own stock `Button`, so each has its own
 * ripple, state layer and touch target, clipped to its own shape. Both stay filled: neither is
 * destructive (New Game only opens the setup form - the saved game is replaced only once a new one
 * actually starts), so neither needs to be played down against the other.
 */
@Composable
fun PlayButton(hasInProgressGame: Boolean, onContinue: () -> Unit, onNewGame: () -> Unit) {
    if (!hasInProgressGame) {
        Button(
            onClick = onNewGame,
            modifier = Modifier.fillMaxWidth().heightIn(min = MENU_BUTTON_MIN_HEIGHT),
        ) {
            Text(text = stringResource(Res.string.menu_play), style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    // Height set by the taller half, so a label that wraps at a large font doesn't leave the other short.
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(PLAY_SPLIT_GAP),
    ) {
        Button(
            onClick = onNewGame,
            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = MENU_BUTTON_MIN_HEIGHT),
            shape = NEW_GAME_HALF_SHAPE,
        ) {
            PlayHalfLabel(stringResource(Res.string.menu_new_game))
        }
        Button(
            onClick = onContinue,
            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = MENU_BUTTON_MIN_HEIGHT),
            shape = CONTINUE_HALF_SHAPE,
        ) {
            PlayHalfLabel(stringResource(Res.string.menu_continue))
        }
    }
}

/** A half of the split Play button's label. Half a button's width is what a large font can outgrow by a whole word. */
@Composable
private fun PlayHalfLabel(text: String) {
    if (LocalDensity.current.isLargeFont) {
        WholeWordsText(text = text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    } else {
        Text(text = text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MenuDestinationButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = MENU_BUTTON_MIN_HEIGHT),
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true)
@Composable
private fun MenuScreenPreview() {
    LudoPlusTheme {
        MenuScreen(
            hasInProgressGame = false,
            onContinue = {},
            onNewGame = {},
            onAchievements = {},
            onLeaderboard = {},
            onStatistics = {},
            onStyles = {},
            onRules = {},
            onSettings = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MenuScreenInProgressPreview() {
    LudoPlusTheme {
        MenuScreen(
            hasInProgressGame = true,
            onContinue = {},
            onNewGame = {},
            onAchievements = {},
            onLeaderboard = {},
            onStatistics = {},
            onStyles = {},
            onRules = {},
            onSettings = {},
        )
    }
}
