package net.zodac.ludoplus.ui.style

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.coroutines.CoroutineContext
import kotlin.math.ceil
import net.zodac.ludoplus.ui.common.HoldPageCover

/**
 * Where a [PaintedSurface] is painted: off the main thread, in the app. Null, as in a test of one page, paints it as it's
 * first drawn, so it's there on its first frame.
 */
val LocalSurfacePainting = staticCompositionLocalOf<CoroutineContext?> { null }

/** Whether a [PaintedSurface]'s art is there yet. */
@Stable
internal class SurfaceState(painted: Boolean) {
    var painted by mutableStateOf(painted)
}

/** A [SurfaceState] for a [PaintedSurface] that something else waits for. */
@Composable
internal fun rememberSurfaceState(): SurfaceState {
    val painting = LocalSurfacePainting.current
    return remember { SurfaceState(painted = painting == null) }
}

/**
 * Static, costly art: [paint]ed once for [key] at the size it's laid out at, into the images [drawCachedSurface] keeps,
 * then stamped. Painted on [LocalSurfacePainting], so no frame waits for it, and the page cover is held up until it's
 * there ([HoldPageCover]). It's painted to the end and kept even if this has gone by then, so a warm-up's paints count.
 * While other art takes its place (a board changing part-way through a game), the last is shown, stretched to fit.
 *
 * [key] must say everything [paint] depends on besides the size and density, and [paint] may only draw, from what never
 * changes: it runs off the main thread.
 */
@Composable
internal fun PaintedSurface(key: Any, modifier: Modifier = Modifier, state: SurfaceState = rememberSurfaceState(), paint: DrawScope.() -> Unit) {
    val painting = LocalSurfacePainting.current
    val density = LocalDensity.current
    val surface = SurfaceKey(key, density.density)
    if (painting == null) {
        Canvas(modifier) { drawCachedSurface(surface, paint) }
        return
    }
    val direction = LocalLayoutDirection.current
    var size by remember { mutableStateOf(IntSize.Zero) }
    var shown by remember { mutableStateOf<ShownSurface?>(null) }
    val wanted = SizedSurface(surface, size.width, size.height)
    LaunchedEffect(wanted) {
        if (wanted.width > 0 && wanted.height > 0) shown = ShownSurface(wanted, paintedSurface(wanted, painting, density, direction, paint))
    }
    val ready = shown?.surface == wanted
    SideEffect { state.painted = ready }
    if (!ready) HoldPageCover()
    Canvas(modifier.onSizeChanged { size = it }) {
        val room = IntSize(ceil(this.size.width).toInt(), ceil(this.size.height).toInt())
        val current = shown
        // Kept since it was last shown, it's there on the first frame, before the effect above has run.
        val image = if (current?.surface == wanted) current.image else keptSurface(SizedSurface(surface, room.width, room.height)) ?: current?.image
        if (image != null) drawImage(image, dstOffset = IntOffset.Zero, dstSize = room)
    }
}

private data class SurfaceKey(val key: Any, val density: Float)

private class ShownSurface(val surface: SizedSurface, val image: ImageBitmap)
