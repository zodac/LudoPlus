package net.zodac.ludo.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.ludo.data.achievements.AchievementsState
import net.zodac.ludo.data.settings.DefaultStyleIds
import net.zodac.ludo.model.Achievement
import net.zodac.ludo.model.AchievementCategory

class StyleRegistryTest {

    private val registries = StyleCategory.entries.map { it.registry }

    private fun earned(vararg achievements: Achievement) = StyleAccess(AchievementsState(achievements.associateWith { 0L }))

    private fun group(registry: StyleRegistry, family: String): StyleGroup = registry.groups.single { it.name.key == "style_family_$family" }

    @Test
    fun `every id is its family's name then its variant's - as the Styles screen shows them`() {
        for (registry in registries) for (group in registry.groups) for (variant in group.variants) {
            val expected = group.name.key.removePrefix("style_family_") + "_" + variant.name.key.removePrefix("style_colour_")
            assertEquals(expected, variant.id)
        }
    }

    @Test
    fun `each category's default is the id a new player starts with - and an unknown id draws it`() {
        assertEquals(
            listOf(DefaultStyleIds.BOARD, DefaultStyleIds.PAWNS, DefaultStyleIds.DICE, DefaultStyleIds.TABLE_BACKGROUND),
            registries.map { it.defaultId },
        )
        val everything = earned(*Achievement.entries.toTypedArray())
        for (registry in registries) assertEquals(registry.defaultId, registry.unlockedId("no_such_style", everything))
    }

    @Test
    fun `the styles DiceFive locked behind its own achievements open with Ludo's`() {
        assertEquals(StyleUnlock.SpecificAchievement(Achievement.MIDNIGHT_RUN), group(DiceStyleRegistry, "starry").unlock)
        assertEquals(StyleUnlock.SpecificAchievement(Achievement.MIDNIGHT_RUN), group(TableBackgroundRegistry, "starry").unlock)
        assertEquals(StyleUnlock.SpecificAchievement(Achievement.TOO_MUCH_OF_A_GOOD_THING), group(DiceStyleRegistry, "devil").unlock)
        assertEquals(StyleUnlock.SpecificAchievement(Achievement.NUMBER_CRUNCHER), group(DiceStyleRegistry, "maths").unlock)
        assertEquals(StyleUnlock.AchievementCount(55), group(DiceStyleRegistry, "d20").unlock)
        assertEquals(StyleUnlock.AchievementCount(55), group(TableBackgroundRegistry, "floating_dice").unlock)
        assertEquals(listOf("d20_white", "d20_black", "d20_blue"), group(DiceStyleRegistry, "d20").variants.map { it.id })
        for (achievement in listOf(Achievement.MIDNIGHT_RUN, Achievement.TOO_MUCH_OF_A_GOOD_THING, Achievement.NUMBER_CRUNCHER)) {
            assertTrue(achievement.unlocksStyle, achievement.name)
        }
    }

    @Test
    fun `a style that goes with another of the same name is unlocked by the same thing`() {
        val unlocks = registries
            .flatMap { registry -> registry.groups.map { it.name.key to it.unlock } }
            .groupBy({ it.first }, { it.second })
        for ((family, locks) in unlocks) assertEquals(1, locks.distinct().size, "$family is unlocked by ${locks.distinct()}")
        // The ones that go together by name, so a new background can't quietly miss its die.
        for (family in listOf("stone", "neon", "meadow", "leather", "christmas", "glitter", "rgb", "ocean", "starry", "glitch")) {
            assertTrue(DiceStyleRegistry.groups.any { it.name.key == "style_family_$family" }, "no $family die")
            assertTrue(TableBackgroundRegistry.groups.any { it.name.key == "style_family_$family" }, "no $family background")
        }
    }

    @Test
    fun `the boards are the plan's eight families - each but Paper locked with the die of its name`() {
        assertEquals(
            listOf("classic", "wood", "ocean", "retro", "paper", "starry", "stone", "neon"),
            BoardStyleRegistry.groups.map { it.name.key.removePrefix("style_family_") },
        )
        assertEquals(
            listOf("classic_cream", "wood_oak", "wood_walnut", "ocean_lagoon", "ocean_deep", "retro_blue", "paper_pencil", "starry_midnight", "stone_slate", "stone_sandstone", "neon_night"),
            BoardStyleRegistry.ids,
        )
        for (family in listOf("wood", "ocean", "retro", "starry", "stone", "neon")) {
            assertEquals(group(DiceStyleRegistry, family).unlock, group(BoardStyleRegistry, family).unlock, family)
        }
        assertEquals(StyleUnlock.AchievementCount(50), group(BoardStyleRegistry, "paper").unlock)
    }

    @Test
    fun `no count lock is beyond the 100 counted achievements - and the water set opens at 15`() {
        val counted = Achievement.entries.count { it.category != AchievementCategory.EASTER_EGGS }
        assertEquals(100, counted)
        for (registry in registries) for (group in registry.groups) {
            val unlock = group.unlock
            if (unlock is StyleUnlock.AchievementCount) assertTrue(unlock.count <= counted, "${group.name.key} needs ${unlock.count}")
        }
        assertEquals(StyleUnlock.AchievementCount(15), group(DiceStyleRegistry, "ocean").unlock)
        assertEquals(StyleUnlock.AchievementCount(15), group(TableBackgroundRegistry, "ocean").unlock)
        assertEquals(listOf("ocean_waves", "ocean_lagoon"), group(TableBackgroundRegistry, "ocean").variants.map { it.id })
    }

    @Test
    fun `only Classic is free`() {
        for (registry in registries) for (group in registry.groups) {
            val isClassic = group.name.key.contains("classic")
            assertEquals(isClassic, group.unlock == StyleUnlock.Free, "${group.name.key}'s unlock is ${group.unlock}")
        }
    }

    @Test
    fun `an Easter Egg opens its style - but never counts towards a count lock`() {
        val eggs = Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS }
        val allEggs = earned(*eggs.toTypedArray())
        assertEquals(0, allEggs.achievements.countedUnlocks)
        // Leather opens at 5 counted achievements: ten Easter Eggs don't open it.
        assertFalse(DiceStyleRegistry.isUnlocked("leather_tan", allEggs))
        val fiveCounted = earned(*Achievement.entries.filter { it.category != AchievementCategory.EASTER_EGGS }.take(5).toTypedArray())
        assertTrue(DiceStyleRegistry.isUnlocked("leather_tan", fiveCounted))

        assertTrue(Achievement.BIG_FAN.unlocksStyle)
        val everythingButBigFan = earned(*Achievement.entries.filter { it != Achievement.BIG_FAN }.toTypedArray())
        assertEquals(TableBackgroundRegistry.defaultId, TableBackgroundRegistry.unlockedId("glitch_screen", everythingButBigFan))
        assertEquals("glitch_screen", TableBackgroundRegistry.unlockedId("glitch_screen", earned(Achievement.BIG_FAN)))
    }

    @Test
    fun `the unlocked count counts every variant`() {
        val none = StyleAccess(AchievementsState())
        val all = earned(*Achievement.entries.toTypedArray())
        for (registry in registries) {
            val classic = registry.groups.single { it.unlock == StyleUnlock.Free }
            assertEquals(UnlockCount(classic.variants.size, registry.ids.size), registry.unlockCount(none), registry.noun.key)
            assertEquals(UnlockCount(registry.ids.size, registry.ids.size), registry.unlockCount(all), registry.noun.key)
        }
        // Midnight Run opens the Starry die and background: every colour of each counted unlocked.
        val midnight = earned(Achievement.MIDNIGHT_RUN)
        for (registry in listOf(DiceStyleRegistry, TableBackgroundRegistry)) {
            val starry = group(registry, "starry").variants.size
            assertEquals(registry.unlockCount(none) + UnlockCount(starry, 0), registry.unlockCount(midnight), registry.noun.key)
        }
    }

    @Test
    fun `owning every style meets every lock with nothing earned - so every pick is drawn`() {
        val owner = StyleAccess(AchievementsState(), ownsAllStyles = true)
        for (registry in registries) {
            for (group in registry.groups) assertTrue(group.unlock.isMet(owner), "${group.name.key} is locked for an owner")
            for (id in registry.ids) assertEquals(id, registry.unlockedId(id, owner))
            assertEquals(UnlockCount(registry.ids.size, registry.ids.size), registry.unlockCount(owner), registry.noun.key)
        }
        val locks = registries.flatMap { registry -> registry.groups.map { it.unlock } }
        assertTrue(locks.any { it is StyleUnlock.AchievementCount })
        assertTrue(locks.any { it is StyleUnlock.SpecificAchievement })
    }

    @Test
    fun `without the purchase the locks are the achievements' alone`() {
        val notOwned = StyleAccess(AchievementsState(), ownsAllStyles = false)
        assertEquals(StyleAccess(AchievementsState()), notOwned)
        for (registry in listOf(DiceStyleRegistry, TableBackgroundRegistry)) {
            assertTrue(registry.groups.any { !it.unlock.isMet(notOwned) }, registry.noun.key)
        }
        assertFalse(DiceStyleRegistry.isUnlocked("d20_white", notOwned))
    }
}
