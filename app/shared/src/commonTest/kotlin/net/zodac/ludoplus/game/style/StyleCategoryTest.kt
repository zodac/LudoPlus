package net.zodac.ludoplus.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludoplus.data.achievements.AchievementsState
import net.zodac.ludoplus.data.settings.SavedStyles

class StyleCategoryTest {

    @Test
    fun `the categories are the registries in the Styles screen's order - each reading its own pick`() {
        assertEquals(listOf(BoardStyleRegistry, PawnStyleRegistry, DiceStyleRegistry, TableBackgroundRegistry), StyleCategory.entries.map { it.registry })
        val picks = SavedStyles("board", "pawns", "dice", "background", AchievementsState())
        assertEquals(listOf("board", "pawns", "dice", "background"), StyleCategory.entries.map { it.pickIn(picks) })
    }
}
