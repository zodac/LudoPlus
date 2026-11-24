package net.zodac.ludoplus

import android.content.ComponentCallbacks2
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.ludoplus.ui.style.DiceStyles
import net.zodac.ludoplus.ui.style.surfacesPaintedForTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Short of memory in the background, the app lets its painted surfaces go: not merely on leaving the screen. */
@RunWith(AndroidJUnit4::class)
class TrimMemoryTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `painted surfaces are kept while the app is only hidden and let go when memory runs short in the background`() {
        val application: LudoPlusApplication = ApplicationProvider.getApplicationContext()
        // Granite's texture is painted once and stamped after.
        val stone = DiceStyles.byId("stone_granite")
        var drawing by mutableIntStateOf(0)
        compose.setContent { key(drawing) { stone.Die(value = 3, modifier = Modifier.size(64.dp)) } }

        /** How many surfaces drawing the die afresh paints: none while they're kept. */
        fun painted(): Int {
            val before = surfacesPaintedForTest()
            compose.runOnIdle { drawing++ }
            compose.onRoot().captureToImage()
            return surfacesPaintedForTest() - before
        }
        compose.onRoot().captureToImage()
        assertEquals("kept from one drawing to the next", 0, painted())

        application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals("kept while hidden", 0, painted())

        application.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        assertTrue("let go, and painted again", painted() > 0)
    }
}
