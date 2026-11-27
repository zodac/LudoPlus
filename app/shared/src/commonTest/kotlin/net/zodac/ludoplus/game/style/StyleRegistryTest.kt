package net.zodac.ludoplus.game.style

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.ludoplus.data.achievements.AchievementsState
import net.zodac.ludoplus.data.settings.DefaultStyleIds
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCategory

class StyleRegistryTest {

    private val registries = StyleCategory.entries.map { it.registry }

    private fun earned(vararg achievements: Achievement) = StyleAccess(AchievementsState(achievements.associateWith { 0L }))

    private fun group(registry: StyleRegistry, family: String): StyleGroup = registry.groups.single { it.name.key == "style_family_$family" }

    @Test
    fun `every id is its family's name then its variant's - as the Styles screen shows them`() {
        for (registry in registries) for (group in registry.groups) for (variant in group.variants) {
            val name = group.name.key.removePrefix("style_family_") + "_" + variant.name.key.removePrefix("style_colour_")
            assertEquals(RENAMED[name] ?: name, variant.id)
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
        assertEquals(StyleUnlock.SpecificAchievement(Achievement.COUNTING_UP), group(DiceStyleRegistry, "maths").unlock)
        assertEquals(StyleUnlock.AchievementCount(55), group(DiceStyleRegistry, "d20").unlock)
        assertEquals(StyleUnlock.AchievementCount(55), group(TableBackgroundRegistry, "floating_dice").unlock)
        assertEquals(listOf("d20_white", "d20_black", "d20_blue"), group(DiceStyleRegistry, "d20").variants.map { it.id })
        for (achievement in listOf(Achievement.MIDNIGHT_RUN, Achievement.TOO_MUCH_OF_A_GOOD_THING, Achievement.COUNTING_UP)) {
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
    fun `the boards are seventeen families - each with a die or background of its name locked with it`() {
        assertEquals(
            listOf(
                "classic", "wood", "ocean", "comic", "patchwork", "candy", "obsidian", "disco", "retro", "paper", "starry", "groovy", "stone", "meadow",
                "circuit", "bestagons", "neon",
            ),
            BoardStyleRegistry.groups.map { it.name.key.removePrefix("style_family_") },
        )
        assertEquals(
            listOf(
                "classic_cream", "wood_oak", "wood_walnut", "ocean_lagoon", "ocean_deep", "comic_pop", "comic_noir", "patchwork_cotton", "patchwork_denim",
                "candy_bubblegum", "candy_mint", "obsidian_lava", "obsidian_blue", "disco_night", "retro_blue", "paper_pencil", "paper_blueprint",
                "starry_midnight", "groovy_amber", "groovy_purple", "stone_slate", "stone_sandstone", "meadow_green", "circuit_green", "circuit_black",
                "bestagons_honey", "bestagons_charcoal", "neon_night",
            ),
            BoardStyleRegistry.ids,
        )
        for (family in listOf("wood", "ocean", "obsidian", "retro", "starry", "stone", "meadow", "neon")) {
            assertEquals(group(DiceStyleRegistry, family).unlock, group(BoardStyleRegistry, family).unlock, family)
        }
        for (family in listOf("circuit", "bestagons")) {
            assertEquals(group(TableBackgroundRegistry, family).unlock, group(BoardStyleRegistry, family).unlock, family)
        }
        val ownLocks = mapOf("comic" to 20, "patchwork" to 25, "candy" to 30, "disco" to 40, "paper" to 50, "groovy" to 60)
        for ((family, count) in ownLocks) assertEquals(StyleUnlock.AchievementCount(count), group(BoardStyleRegistry, family).unlock, family)
    }

    @Test
    fun `the pawns are eighteen families - each with a die of its name locked with it`() {
        assertEquals(
            listOf(
                "classic", "counter", "chess", "shapes", "cone", "token", "glass", "monster", "animal", "retro", "toadstool", "cake", "gems", "space",
                "robot", "neon", "googly", "devil",
            ),
            PawnStyleRegistry.groups.map { it.name.key.removePrefix("style_family_") },
        )
        assertEquals(
            listOf(
                "classic_gloss", "counter_ridged", "chess_rook", "chess_knight", "chess_queen", "shapes_star", "shapes_heart", "shapes_cube",
                "cone_painted", "peg_stained", "glass_clear", "monster_cyclops", "monster_horned", "animal_cat", "animal_duck", "animal_frog",
                "animal_penguin", "retro_pixel", "toadstool_spotted", "cake_cupcake", "gems_cut", "space_rocket", "space_saucer", "robot_tin",
                "neon_night", "googly_blob", "devil_imp",
            ),
            PawnStyleRegistry.ids,
        )
        for (family in listOf("retro", "cake", "gems", "neon", "googly", "devil")) {
            assertEquals(group(DiceStyleRegistry, family).unlock, group(PawnStyleRegistry, family).unlock, family)
        }
        val ownLocks = mapOf(
            "counter" to 5, "chess" to 10, "shapes" to 20, "cone" to 20, "token" to 25, "glass" to 30, "monster" to 35, "animal" to 40, "toadstool" to 50,
            "space" to 70, "robot" to 80,
        )
        for ((family, count) in ownLocks) assertEquals(StyleUnlock.AchievementCount(count), group(PawnStyleRegistry, family).unlock, family)
    }

    @Test
    fun `every count lock is within the achievements, Easter Eggs included - and the water set opens at 15`() {
        val counts = registries.flatMap { it.groups }.mapNotNull { (it.unlock as? StyleUnlock.AchievementCount)?.count }
        assertEquals(100, counts.max())
        assertTrue(counts.all { it <= Achievement.entries.size }, "${Achievement.entries.size} achievements")
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
    fun `an Easter Egg opens its style - and counts towards a count lock like any other`() {
        val eggs = Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS }
        val fourEggs = earned(*eggs.take(4).toTypedArray())
        assertEquals(4, fourEggs.achievements.unlockedCount)
        // Leather opens at 5 achievements: four Easter Eggs don't open it, and a fifth does.
        assertFalse(DiceStyleRegistry.isUnlocked("leather_tan", fourEggs))
        assertTrue(DiceStyleRegistry.isUnlocked("leather_tan", earned(*eggs.take(5).toTypedArray())))
        // Every achievement, Easter Eggs included, opens the 100 locks: Neon.
        assertFalse(DiceStyleRegistry.isUnlocked("neon_night", earned(*Achievement.entries.take(99).toTypedArray())))
        assertTrue(DiceStyleRegistry.isUnlocked("neon_night", earned(*Achievement.entries.take(100).toTypedArray())))

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

    private companion object {
        /** Styles renamed after release, by the id their name would give: they keep the id they shipped with. */
        val RENAMED = mapOf("token_plastic" to "peg_stained")
    }
}
