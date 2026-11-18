package net.zodac.ludoplus.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.ludoplus.data.achievements.AchievementsState
import net.zodac.ludoplus.data.settings.DefaultStyleIds
import net.zodac.ludoplus.data.settings.SavedStyles
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCategory

class StyleCategoryTest {

    private fun picks(dice: String = DefaultStyleIds.DICE, achievements: AchievementsState = AchievementsState(), ownsAllStyles: Boolean = false) =
        SavedStyles(DefaultStyleIds.BOARD, DefaultStyleIds.PAWNS, dice, DefaultStyleIds.TABLE_BACKGROUND, achievements, ownsAllStyles = ownsAllStyles)

    @Test
    fun `the categories are the registries in the Styles screen's order - each reading its own pick`() {
        assertEquals(listOf(BoardStyleRegistry, PawnStyleRegistry, DiceStyleRegistry, TableBackgroundRegistry), StyleCategory.entries.map { it.registry })
        val picks = SavedStyles("board", "pawns", "dice", "background", AchievementsState())
        assertEquals(listOf("board", "pawns", "dice", "background"), StyleCategory.entries.map { it.pickIn(picks) })
    }

    @Test
    fun `a table drawn in anything but the defaults - a locked or unknown pick drawing its default`() {
        assertFalse(picks().hasNonDefaultStyle)
        assertFalse(picks(dice = "no_such_style").hasNonDefaultStyle)
        // Leather opens at 5 achievements: picked but locked, it's drawn as the default.
        assertFalse(picks(dice = "leather_tan").hasNonDefaultStyle)
        val five = AchievementsState(Achievement.entries.filter { it.category != AchievementCategory.EASTER_EGGS }.take(5).associateWith { 0L })
        assertTrue(picks(dice = "leather_tan", achievements = five).hasNonDefaultStyle)
        assertTrue(picks(dice = "leather_tan", ownsAllStyles = true).hasNonDefaultStyle)
    }
}
