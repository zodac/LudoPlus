package net.zodac.ludoplus.ui.game

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.common_cpu_cd
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.icons.Symbols

/**
 * The little processor chip shown before a CPU player's name, wherever players are listed (the
 * in-game header, the results page), so a computer seat reads as one at a glance. Sized by the
 * caller to match the name's text beside it, and tinted like that text unless told otherwise.
 */
@Composable
fun CpuPlayerIcon(size: Dp, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    Icon(
        imageVector = Symbols.Memory,
        contentDescription = stringResource(Res.string.common_cpu_cd),
        tint = tint,
        modifier = modifier.size(size),
    )
}
