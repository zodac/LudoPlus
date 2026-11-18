package net.zodac.ludoplus.ui.achievements

import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludoplus.UiTest
import net.zodac.ludoplus.model.Achievement

/** Every achievement draws an icon of its own: no two the same, and none the locked rows' question mark. */
class AchievementIconsTest : UiTest() {

    @Test
    fun `no two achievements draw the same icon - and none draws the locked rows' question mark`() = uiTest {
        val icons = mutableMapOf<Achievement, ImageVector>()
        compose.setContent { Achievement.entries.forEach { icons[it] = it.icon } }
        compose.waitForIdle()
        val shared = icons.entries.groupBy({ it.value }, { it.key }).values.filter { it.size > 1 }
        assertEquals(emptyList(), shared)
        assertEquals(emptyList(), icons.filterValues { it == LOCKED_ACHIEVEMENT_ICON }.keys.toList())
    }
}
