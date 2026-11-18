package net.zodac.ludoplus.ui.game

import net.zodac.ludoplus.ui.style.dieShadow
import net.zodac.ludoplus.ui.style.LocalDieTumbleMillis
import net.zodac.ludoplus.ui.style.LocalGameVisualTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random
import net.zodac.ludoplus.data.settings.MotionGroup
import net.zodac.ludoplus.ui.common.groupReduceMotion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.game_die_cd
import net.zodac.ludoplus.resources.game_die_select_action
import net.zodac.ludoplus.resources.game_die_showing_cd
import net.zodac.ludoplus.resources.game_roll_action
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.theme.Primary

/** The violet ring round the die while it can be rolled, and the room kept for it round the die either way. */
private val ROLLABLE_RING_WIDTH = 3.dp
private val RING_ROOM = 5.dp

/** The tumble ([DIE_TUMBLE_MILLIS] long): how many faces flick past, and how far the die turns as it settles. */
private const val TUMBLE_FACES = 7
private const val TUMBLE_TURN_DEGREES = 300f

/**
 * What a long press on the game's die does, in a debug build's game (superuser mode, `GameViewModel.onDieLongPressed`):
 * null everywhere else, and then the die has no long press at all.
 */
val LocalDieLongPress = compositionLocalOf<(() -> Unit)?> { null }

/**
 * The die, in the player's die style ([LocalGameVisualTheme]), showing [value] (blank before the first roll: the style's
 * body colour, no face). While [canRoll] it is ringed in violet and a tap rolls it ([onRoll]); TalkBack hears "Die
 * showing 4", a button with the action "Roll".
 *
 * Each new [roll] (any value that changes with every roll) tumbles it through random faces, turning, onto [value],
 * unless the Die roll motion is off ([MotionGroup.DIE_ROLL]). A style that tumbles itself (the D20, the Egg) turns
 * through its own solid instead, from the tumble's clock ([LocalDieTumbleMillis]). TalkBack hears only the result.
 *
 * One of two dice ([TwoDice]) says which it is in its [description], and is faded once [used]. While [onSelect] is set
 * it is a button choosing the die the next move uses, ringed in violet once [selected].
 */
@Composable
fun GameDie(
    value: Int?,
    roll: Any?,
    canRoll: Boolean,
    onRoll: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    used: Boolean = false,
    selected: Boolean = false,
    onSelect: (() -> Unit)? = null,
) {
    val style = LocalGameVisualTheme.current.diceStyle
    val firstRoll = remember { roll }
    val reduce = groupReduceMotion(MotionGroup.DIE_ROLL)
    // The faces it flicks through, and how far through them it is: 1 is settled on [value].
    val faces = remember(roll) { List(TUMBLE_FACES) { Random.nextInt(1, 7) } }
    val tumble = remember(roll) { Animatable(if (roll == null || roll === firstRoll || reduce) 1f else 0f) }
    LaunchedEffect(roll) { tumble.animateTo(1f, tween(DIE_TUMBLE_MILLIS, easing = LinearEasing)) }
    val tumbling = tumble.value < 1f
    val tumbleFace = if (tumbling && !style.tumblesItself) faces[(tumble.value * TUMBLE_FACES).toInt().coerceAtMost(TUMBLE_FACES - 1)] else null
    val spoken = description ?: if (value == null) stringResource(Res.string.game_die_cd) else stringResource(Res.string.game_die_showing_cd, value)
    val rollLabel = stringResource(Res.string.game_roll_action)
    val selectLabel = stringResource(Res.string.game_die_select_action)
    // A tap rolls while it can; otherwise, where it can, it picks this die.
    val tap = when {
        canRoll -> onRoll
        onSelect != null -> onSelect
        else -> null
    }
    val tapLabel = if (canRoll) rollLabel else selectLabel
    val longPress = LocalDieLongPress.current
    val dieShape = RoundedCornerShape(style.cornerPercent)
    val ringShape = RoundedCornerShape(style.cornerPercent + RING_CORNER_EXTRA_PERCENT)
    Box(
        modifier = modifier
            .clearAndSetSemantics {
                contentDescription = spoken
                if (tap != null) {
                    role = Role.Button
                    onClick(label = tapLabel) { tap(); true }
                }
            }
            .then(if (canRoll || selected) Modifier.border(ROLLABLE_RING_WIDTH, Primary, ringShape) else Modifier)
            .clip(ringShape)
            .then(
                when {
                    // Debug builds only: a tap still rolls (or picks) when it may.
                    longPress != null -> Modifier.combinedClickable(onClick = { tap?.invoke() }, onLongClick = longPress)
                    tap != null -> Modifier.clickable(onClickLabel = tapLabel, role = Role.Button, onClick = tap)
                    else -> Modifier
                },
            )
            .graphicsLayer { alpha = if (used) USED_DIE_ALPHA else 1f }
            .padding(RING_ROOM),
        contentAlignment = Alignment.Center,
    ) {
        val turn = if (style.tumblesItself) 0f else TUMBLE_TURN_DEGREES * (1f - tumble.value) * (1f - tumble.value)
        val dieModifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = turn }
        val face = tumbleFace ?: value
        if (face == null) {
            Box(modifier = dieModifier.dieShadow(dieShape).clip(dieShape).background(style.bodyColor))
        } else {
            CompositionLocalProvider(LocalDieTumbleMillis provides if (tumbling && style.tumblesItself) tumble.value * DIE_TUMBLE_MILLIS else null) {
                style.Die(value = face, held = false, modifier = dieModifier)
            }
        }
    }
}

/** How faint a die already moved is, of two. */
private const val USED_DIE_ALPHA = 0.35f

/** How much rounder the violet ring's corners are than the die's, as it sits a little way out from them. */
private const val RING_CORNER_EXTRA_PERCENT = 4
