package net.zodac.ludoplus.ui.style

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlin.coroutines.CoroutineContext
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.ui.common.LocalPageCover
import net.zodac.ludoplus.ui.common.PageCover

private const val ART = "art"

/** Costly art is painted where the app says, never by the frame that shows it, and the page cover waits for it. */
class PaintedSurfaceTest : UiTest() {

    /** Holds what it's handed until [run], as a thread that hasn't got to it yet. */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val held = ArrayDeque<Runnable>()
        val waiting: Int get() = held.size

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            held += block
        }

        fun run() {
            while (held.isNotEmpty()) held.removeFirst().run()
        }
    }

    private val background = HeldDispatcher()
    private val cover = PageCover(CoroutineScope(Dispatchers.Unconfined))

    @BeforeTest
    fun forget() = releaseCachedSurfaces()

    @Composable
    private fun Art(colour: Color, state: SurfaceState = rememberSurfaceState()) =
        PaintedSurface(colour, Modifier.size(20.dp).testTag(ART), state) { drawRect(colour) }

    /** Shows [content] with surfaces painted on [background], under [cover]. */
    private fun show(content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalSurfacePainting provides background, LocalPageCover provides cover, content = content)
    }

    private fun middle(): Color = compose.onNodeWithTag(ART).captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }

    @Test
    fun `the frame that shows it doesn't paint it - the cover is held until it's painted`() = uiTest {
        val before = surfacesPaintedForTest()
        lateinit var state: SurfaceState
        show {
            state = rememberSurfaceState()
            Art(Color.Red, state)
        }
        compose.waitForIdle()
        middle()
        assertEquals(before, surfacesPaintedForTest())
        assertEquals(1, background.waiting)
        assertTrue(cover.held)
        assertFalse(state.painted)

        background.run()
        compose.waitForIdle()
        assertEquals(Color.Red, middle())
        assertEquals(before + 1, surfacesPaintedForTest())
        assertFalse(cover.held)
        assertTrue(state.painted)
    }

    @Test
    fun `other art in its place shows the last until it's painted`() = uiTest {
        var colour by mutableStateOf(Color.Red)
        show { Art(colour) }
        compose.waitForIdle()
        background.run()
        compose.waitForIdle()

        colour = Color.Blue
        compose.waitForIdle()
        assertEquals(Color.Red, middle())
        background.run()
        compose.waitForIdle()
        assertEquals(Color.Blue, middle())
    }

    @Test
    fun `one that goes before it's painted is painted and kept - and shown at once the next time`() = uiTest {
        var shown by mutableStateOf(true)
        show { if (shown) Art(Color.Red) }
        compose.waitForIdle()
        shown = false
        compose.waitForIdle()
        background.run()

        val before = surfacesPaintedForTest()
        shown = true
        compose.waitForIdle()
        assertEquals(Color.Red, middle())
        assertEquals(0, background.waiting)
        assertEquals(before + 1, surfacesPaintedForTest())
        assertFalse(cover.held)
    }

    @Test
    fun `two of the same art are painted once`() = uiTest {
        val before = surfacesPaintedForTest()
        show {
            Art(Color.Red)
            PaintedSurface(Color.Red, Modifier.size(20.dp)) { drawRect(Color.Red) }
        }
        compose.waitForIdle()
        background.run()
        compose.waitForIdle()
        assertEquals(before + 1, surfacesPaintedForTest())
        assertEquals(Color.Red, middle())
        assertFalse(cover.held)
    }

    @Test
    fun `nowhere to paint it and it's there on its first frame`() = uiTest {
        lateinit var state: SurfaceState
        compose.setContent {
            state = rememberSurfaceState()
            Art(Color.Red, state)
        }
        assertTrue(state.painted)
        assertEquals(Color.Red, middle())
    }
}
