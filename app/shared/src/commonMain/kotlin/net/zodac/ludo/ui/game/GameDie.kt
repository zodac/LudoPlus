package net.zodac.ludo.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.random.Random
import net.zodac.ludo.data.settings.MotionGroup
import net.zodac.ludo.ui.common.groupReduceMotion
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.game_die_cd
import net.zodac.ludo.resources.game_die_showing_cd
import net.zodac.ludo.resources.game_roll_action
import net.zodac.ludo.ui.common.stringResource
import net.zodac.ludo.ui.style.PipFace
import net.zodac.ludo.ui.theme.DicePipColor
import net.zodac.ludo.ui.theme.IvoryDiceBottom
import net.zodac.ludo.ui.theme.IvoryDiceTop
import net.zodac.ludo.ui.theme.Primary

/** How rounded the die's corners are, as a percentage of its size (DiceFive's bevelled die). */
private const val DIE_CORNER_PERCENT = 22

/** The tumble: how many faces flick past, how long each shows, and how far the die turns as it settles. */
private const val TUMBLE_FACES = 7
private const val TUMBLE_FACE_MILLIS = 60L
private const val TUMBLE_TURN_DEGREES = 300f

/**
 * The die: DiceFive's ivory bevelled die, showing [value] (blank before the first roll). While [canRoll] it is ringed
 * in violet and a tap rolls it ([onRoll]); TalkBack hears "Die showing 4", a button with the action "Roll".
 *
 * Each new [roll] (any value that changes with every roll) tumbles it through random faces, turning, onto [value],
 * unless the Die roll motion is off ([MotionGroup.DIE_ROLL]). TalkBack hears only the result.
 */
@Composable
fun GameDie(value: Int?, roll: Any?, canRoll: Boolean, onRoll: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(DIE_CORNER_PERCENT)
    val firstRoll = remember { roll }
    val reduce = groupReduceMotion(MotionGroup.DIE_ROLL)
    // The faces it flicks through, and how far through them it is: 1 is settled on [value].
    val faces = remember(roll) { List(TUMBLE_FACES) { Random.nextInt(1, 7) } }
    val tumble = remember(roll) { Animatable(if (roll == null || roll === firstRoll || reduce) 1f else 0f) }
    LaunchedEffect(roll) { tumble.animateTo(1f, tween((TUMBLE_FACES * TUMBLE_FACE_MILLIS).toInt(), easing = LinearEasing)) }
    val tumbleFace = if (tumble.value < 1f) faces[(tumble.value * TUMBLE_FACES).toInt().coerceAtMost(TUMBLE_FACES - 1)] else null
    val description = if (value == null) stringResource(Res.string.game_die_cd) else stringResource(Res.string.game_die_showing_cd, value)
    val rollLabel = stringResource(Res.string.game_roll_action)
    Box(
        modifier = modifier
            .clearAndSetSemantics {
                contentDescription = description
                if (canRoll) {
                    role = Role.Button
                    onClick(label = rollLabel) { onRoll(); true }
                }
            }
            .graphicsLayer { rotationZ = TUMBLE_TURN_DEGREES * (1f - tumble.value) * (1f - tumble.value) }
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(IvoryDiceTop, IvoryDiceBottom)))
            .border(if (canRoll) 3.dp else 1.dp, if (canRoll) Primary else IvoryDiceBottom, shape)
            .then(if (canRoll) Modifier.clickable(onClickLabel = rollLabel, role = Role.Button, onClick = onRoll) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val face = tumbleFace ?: value
        if (face != null) {
            PipFace(value = face, color = DicePipColor, modifier = Modifier.fillMaxSize().padding(8.dp), pipRadiusFraction = 0.11f)
        }
    }
}
