package net.zodac.ludoplus.game.style

import net.zodac.ludoplus.data.settings.DefaultStyleIds
import net.zodac.ludoplus.resources.style_noun_board
import net.zodac.ludoplus.resources.style_noun_pawns
import net.zodac.ludoplus.resources.style_family_counter
import net.zodac.ludoplus.resources.style_family_cone
import net.zodac.ludoplus.resources.style_family_peg
import net.zodac.ludoplus.resources.style_family_glass
import net.zodac.ludoplus.resources.style_family_animal
import net.zodac.ludoplus.resources.style_colour_ridged
import net.zodac.ludoplus.resources.style_colour_painted
import net.zodac.ludoplus.resources.style_colour_stained
import net.zodac.ludoplus.resources.style_colour_clear
import net.zodac.ludoplus.resources.style_colour_cat
import net.zodac.ludoplus.resources.style_colour_duck
import net.zodac.ludoplus.resources.style_colour_cut
import net.zodac.ludoplus.resources.style_colour_cream
import net.zodac.ludoplus.resources.style_colour_gloss
import net.zodac.ludoplus.resources.style_variant_finish
import net.zodac.ludoplus.game.style.StyleUnlock.AchievementCount
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.style_colour_amber
import net.zodac.ludoplus.resources.style_colour_arabic
import net.zodac.ludoplus.resources.style_colour_arabic_green
import net.zodac.ludoplus.resources.style_colour_basalt
import net.zodac.ludoplus.resources.style_colour_baubles
import net.zodac.ludoplus.resources.style_colour_black
import net.zodac.ludoplus.resources.style_colour_black_marble
import net.zodac.ludoplus.resources.style_colour_blue
import net.zodac.ludoplus.resources.style_colour_blueprint
import net.zodac.ludoplus.resources.style_colour_bronze
import net.zodac.ludoplus.resources.style_colour_brown
import net.zodac.ludoplus.resources.style_colour_charcoal
import net.zodac.ludoplus.resources.style_colour_chinese
import net.zodac.ludoplus.resources.style_colour_chocolate
import net.zodac.ludoplus.resources.style_colour_crimson
import net.zodac.ludoplus.resources.style_colour_cyan
import net.zodac.ludoplus.resources.style_colour_deep
import net.zodac.ludoplus.resources.style_colour_digits
import net.zodac.ludoplus.resources.style_colour_emerald
import net.zodac.ludoplus.resources.style_colour_forestry
import net.zodac.ludoplus.resources.style_colour_gift
import net.zodac.ludoplus.resources.style_colour_gold
import net.zodac.ludoplus.resources.style_colour_granite
import net.zodac.ludoplus.resources.style_colour_green
import net.zodac.ludoplus.resources.style_colour_green_and_purple
import net.zodac.ludoplus.resources.style_colour_green_mono
import net.zodac.ludoplus.resources.style_colour_honey
import net.zodac.ludoplus.resources.style_colour_ice
import net.zodac.ludoplus.resources.style_colour_indigo
import net.zodac.ludoplus.resources.style_colour_irish
import net.zodac.ludoplus.resources.style_colour_ivory
import net.zodac.ludoplus.resources.style_colour_ivory_serif
import net.zodac.ludoplus.resources.style_colour_japanese
import net.zodac.ludoplus.resources.style_colour_japanese_black
import net.zodac.ludoplus.resources.style_colour_lagoon
import net.zodac.ludoplus.resources.style_colour_latin_american
import net.zodac.ludoplus.resources.style_colour_lava
import net.zodac.ludoplus.resources.style_colour_limestone
import net.zodac.ludoplus.resources.style_colour_manzu
import net.zodac.ludoplus.resources.style_colour_maple
import net.zodac.ludoplus.resources.style_colour_menu
import net.zodac.ludoplus.resources.style_colour_midnight
import net.zodac.ludoplus.resources.style_colour_mixed
import net.zodac.ludoplus.resources.style_colour_navy
import net.zodac.ludoplus.resources.style_colour_neon
import net.zodac.ludoplus.resources.style_colour_night
import net.zodac.ludoplus.resources.style_colour_oak
import net.zodac.ludoplus.resources.style_colour_orange_and_teal
import net.zodac.ludoplus.resources.style_colour_oxblood
import net.zodac.ludoplus.resources.style_colour_pencil
import net.zodac.ludoplus.resources.style_colour_pink
import net.zodac.ludoplus.resources.style_colour_pink_and_blue
import net.zodac.ludoplus.resources.style_colour_pinzu
import net.zodac.ludoplus.resources.style_colour_poker
import net.zodac.ludoplus.resources.style_colour_purple
import net.zodac.ludoplus.resources.style_colour_purple_script
import net.zodac.ludoplus.resources.style_colour_rainbow
import net.zodac.ludoplus.resources.style_colour_red
import net.zodac.ludoplus.resources.style_colour_red_block
import net.zodac.ludoplus.resources.style_colour_ripple
import net.zodac.ludoplus.resources.style_colour_roman
import net.zodac.ludoplus.resources.style_colour_roman_blue
import net.zodac.ludoplus.resources.style_colour_rose_gold
import net.zodac.ludoplus.resources.style_colour_sandstone
import net.zodac.ludoplus.resources.style_colour_screen
import net.zodac.ludoplus.resources.style_colour_silver
import net.zodac.ludoplus.resources.style_colour_slate
import net.zodac.ludoplus.resources.style_colour_snowcap
import net.zodac.ludoplus.resources.style_colour_soil
import net.zodac.ludoplus.resources.style_colour_sozu
import net.zodac.ludoplus.resources.style_colour_starlight
import net.zodac.ludoplus.resources.style_colour_strawberry
import net.zodac.ludoplus.resources.style_colour_swirl
import net.zodac.ludoplus.resources.style_colour_tan
import net.zodac.ludoplus.resources.style_colour_teal_plain
import net.zodac.ludoplus.resources.style_colour_tricolour
import net.zodac.ludoplus.resources.style_colour_vanilla
import net.zodac.ludoplus.resources.style_colour_walnut
import net.zodac.ludoplus.resources.style_colour_wave
import net.zodac.ludoplus.resources.style_colour_waves
import net.zodac.ludoplus.resources.style_colour_western
import net.zodac.ludoplus.resources.style_colour_white
import net.zodac.ludoplus.resources.style_colour_white_marble
import net.zodac.ludoplus.resources.style_colour_white_rainbow
import net.zodac.ludoplus.resources.style_colour_white_wave
import net.zodac.ludoplus.resources.style_colour_yellow
import net.zodac.ludoplus.resources.style_family_bestagon
import net.zodac.ludoplus.resources.style_family_bestagons
import net.zodac.ludoplus.resources.style_family_brick
import net.zodac.ludoplus.resources.style_family_cake
import net.zodac.ludoplus.resources.style_family_christmas
import net.zodac.ludoplus.resources.style_family_circuit
import net.zodac.ludoplus.resources.style_family_classic
import net.zodac.ludoplus.resources.style_family_d20
import net.zodac.ludoplus.resources.style_family_devil
import net.zodac.ludoplus.resources.style_family_egg
import net.zodac.ludoplus.resources.style_family_floating_dice
import net.zodac.ludoplus.resources.style_family_frosted
import net.zodac.ludoplus.resources.style_family_games
import net.zodac.ludoplus.resources.style_family_garden
import net.zodac.ludoplus.resources.style_family_gems
import net.zodac.ludoplus.resources.style_family_gingham
import net.zodac.ludoplus.resources.style_family_glitch
import net.zodac.ludoplus.resources.style_family_glitter
import net.zodac.ludoplus.resources.style_family_googly
import net.zodac.ludoplus.resources.style_family_lcd
import net.zodac.ludoplus.resources.style_family_leather
import net.zodac.ludoplus.resources.style_family_maths
import net.zodac.ludoplus.resources.style_family_meadow
import net.zodac.ludoplus.resources.style_family_metal
import net.zodac.ludoplus.resources.style_family_misprint
import net.zodac.ludoplus.resources.style_family_multicolour
import net.zodac.ludoplus.resources.style_family_neon
import net.zodac.ludoplus.resources.style_family_numeral
import net.zodac.ludoplus.resources.style_family_obsidian
import net.zodac.ludoplus.resources.style_family_ocean
import net.zodac.ludoplus.resources.style_family_pinstripe
import net.zodac.ludoplus.resources.style_family_paper
import net.zodac.ludoplus.resources.style_family_planks
import net.zodac.ludoplus.resources.style_family_retro
import net.zodac.ludoplus.resources.style_family_rgb
import net.zodac.ludoplus.resources.style_family_ribbon
import net.zodac.ludoplus.resources.style_family_spotlight
import net.zodac.ludoplus.resources.style_family_starry
import net.zodac.ludoplus.resources.style_family_stone
import net.zodac.ludoplus.resources.style_family_sunburst
import net.zodac.ludoplus.resources.style_family_tally
import net.zodac.ludoplus.resources.style_family_text
import net.zodac.ludoplus.resources.style_family_wireframe
import net.zodac.ludoplus.resources.style_family_wood
import net.zodac.ludoplus.resources.style_family_world
import net.zodac.ludoplus.resources.style_noun_background
import net.zodac.ludoplus.resources.style_noun_dice

/** The achievement count at which every style of the water set opens, in each category that has one. */
private const val WATER_SET_UNLOCK = 15

/** The count the D20 dice and the Floating Dice background open at: the one count no other style uses. */
private const val D20_UNLOCK = 55

/** The dice: every family, in the Styles screen's order, and what unlocks it. Their art is `ui.style.DiceStyles`. */
object DiceStyleRegistry : StyleRegistry(
    Res.string.style_noun_dice,
    listOf(
        StyleGroup(
            Res.string.style_family_classic,
            listOf(
                StyleVariant("classic_ivory", Res.string.style_colour_ivory),
                StyleVariant("classic_red", Res.string.style_colour_red),
                StyleVariant("classic_yellow", Res.string.style_colour_yellow),
                StyleVariant("classic_blue", Res.string.style_colour_blue),
                StyleVariant("classic_oak", Res.string.style_colour_oak),
            ),
        ),
        // Leather and Wood, like the board and backgrounds of the same names, open with them.
        StyleGroup(
            Res.string.style_family_leather,
            listOf(
                StyleVariant("leather_tan", Res.string.style_colour_tan),
                StyleVariant("leather_black", Res.string.style_colour_black),
                StyleVariant("leather_oxblood", Res.string.style_colour_oxblood),
            ),
            unlock = AchievementCount(5),
        ),
        StyleGroup(
            Res.string.style_family_wood,
            listOf(
                StyleVariant("wood_oak", Res.string.style_colour_oak),
                StyleVariant("wood_walnut", Res.string.style_colour_walnut),
                StyleVariant("wood_maple", Res.string.style_colour_maple),
            ),
            unlock = AchievementCount(10),
        ),
        // The water set, with the Ocean boards and backgrounds.
        StyleGroup(
            Res.string.style_family_ocean,
            listOf(
                StyleVariant("ocean_lagoon", Res.string.style_colour_lagoon),
                StyleVariant("ocean_deep", Res.string.style_colour_deep),
            ),
            unlock = AchievementCount(WATER_SET_UNLOCK),
        ),
        StyleGroup(
            Res.string.style_family_metal,
            listOf(
                StyleVariant("metal_gold", Res.string.style_colour_gold),
                StyleVariant("metal_silver", Res.string.style_colour_silver),
                StyleVariant("metal_bronze", Res.string.style_colour_bronze),
            ),
            unlock = AchievementCount(20),
        ),
        StyleGroup(
            Res.string.style_family_numeral,
            listOf(
                StyleVariant("numeral_digits", Res.string.style_colour_digits),
                StyleVariant("numeral_red", Res.string.style_colour_red),
                StyleVariant("numeral_blue", Res.string.style_colour_blue),
            ),
            unlock = AchievementCount(20),
        ),
        StyleGroup(
            Res.string.style_family_world,
            listOf(
                StyleVariant("world_roman", Res.string.style_colour_roman),
                StyleVariant("world_roman_blue", Res.string.style_colour_roman_blue),
                StyleVariant("world_arabic_green", Res.string.style_colour_arabic_green),
                StyleVariant("world_arabic", Res.string.style_colour_arabic),
                StyleVariant("world_japanese", Res.string.style_colour_japanese),
                StyleVariant("world_japanese_black", Res.string.style_colour_japanese_black),
            ),
            unlock = AchievementCount(25),
        ),
        StyleGroup(
            Res.string.style_family_frosted,
            listOf(
                StyleVariant("frosted_ice", Res.string.style_colour_ice),
                StyleVariant("frosted_white", Res.string.style_colour_white),
            ),
            unlock = AchievementCount(30),
        ),
        StyleGroup(
            Res.string.style_family_text,
            listOf(
                StyleVariant("text_ivory_serif", Res.string.style_colour_ivory_serif),
                StyleVariant("text_green_mono", Res.string.style_colour_green_mono),
                StyleVariant("text_purple_script", Res.string.style_colour_purple_script),
                StyleVariant("text_red_block", Res.string.style_colour_red_block),
                StyleVariant("text_teal_plain", Res.string.style_colour_teal_plain),
            ),
            unlock = AchievementCount(30),
        ),
        StyleGroup(
            Res.string.style_family_misprint,
            listOf(
                StyleVariant("misprint_pencil", Res.string.style_colour_pencil),
                StyleVariant("misprint_blueprint", Res.string.style_colour_blueprint),
            ),
            unlock = AchievementCount(35),
        ),
        StyleGroup(
            Res.string.style_family_obsidian,
            listOf(
                StyleVariant("obsidian_lava", Res.string.style_colour_lava),
                StyleVariant("obsidian_blue", Res.string.style_colour_blue),
            ),
            unlock = AchievementCount(35),
        ),
        StyleGroup(
            Res.string.style_family_multicolour,
            listOf(
                StyleVariant("multicolour_tricolour", Res.string.style_colour_tricolour),
                StyleVariant("multicolour_rainbow", Res.string.style_colour_rainbow),
                StyleVariant("multicolour_irish", Res.string.style_colour_irish),
            ),
            unlock = AchievementCount(40),
        ),
        StyleGroup(
            Res.string.style_family_games,
            listOf(
                StyleVariant("games_poker", Res.string.style_colour_poker),
                StyleVariant("games_pinzu", Res.string.style_colour_pinzu),
                StyleVariant("games_manzu", Res.string.style_colour_manzu),
                StyleVariant("games_sozu", Res.string.style_colour_sozu),
            ),
            unlock = AchievementCount(45),
        ),
        StyleGroup(
            Res.string.style_family_retro,
            listOf(
                StyleVariant("retro_green", Res.string.style_colour_green),
                StyleVariant("retro_amber", Res.string.style_colour_amber),
                StyleVariant("retro_blue", Res.string.style_colour_blue),
                StyleVariant("retro_red", Res.string.style_colour_red),
            ),
            unlock = AchievementCount(45),
        ),
        StyleGroup(
            Res.string.style_family_tally,
            listOf(
                StyleVariant("tally_western", Res.string.style_colour_western),
                StyleVariant("tally_chinese", Res.string.style_colour_chinese),
                StyleVariant("tally_latin_american", Res.string.style_colour_latin_american),
                StyleVariant("tally_forestry", Res.string.style_colour_forestry),
            ),
            unlock = AchievementCount(50),
        ),
        StyleGroup(
            Res.string.style_family_egg,
            listOf(
                StyleVariant("egg_white", Res.string.style_colour_white),
                StyleVariant("egg_brown", Res.string.style_colour_brown),
                StyleVariant("egg_gold", Res.string.style_colour_gold),
            ),
            unlock = AchievementCount(50),
        ),
        StyleGroup(
            Res.string.style_family_cake,
            listOf(
                StyleVariant("cake_strawberry", Res.string.style_colour_strawberry),
                StyleVariant("cake_vanilla", Res.string.style_colour_vanilla),
                StyleVariant("cake_chocolate", Res.string.style_colour_chocolate),
            ),
            unlock = AchievementCount(60),
        ),
        StyleGroup(
            Res.string.style_family_stone,
            listOf(
                StyleVariant("stone_granite", Res.string.style_colour_granite),
                StyleVariant("stone_slate", Res.string.style_colour_slate),
                StyleVariant("stone_sandstone", Res.string.style_colour_sandstone),
                StyleVariant("stone_limestone", Res.string.style_colour_limestone),
                StyleVariant("stone_basalt", Res.string.style_colour_basalt),
                StyleVariant("stone_white_marble", Res.string.style_colour_white_marble),
                StyleVariant("stone_black_marble", Res.string.style_colour_black_marble),
            ),
            unlock = AchievementCount(65),
        ),
        StyleGroup(
            Res.string.style_family_gems,
            listOf(
                StyleVariant("gems_ivory", Res.string.style_colour_ivory),
                StyleVariant("gems_black", Res.string.style_colour_black),
            ),
            unlock = AchievementCount(65),
        ),
        StyleGroup(
            Res.string.style_family_lcd,
            listOf(
                StyleVariant("lcd_neon", Res.string.style_colour_neon),
                StyleVariant("lcd_white", Res.string.style_colour_white),
            ),
            unlock = AchievementCount(70),
        ),
        // Opens with the Christmas background.
        StyleGroup(
            Res.string.style_family_christmas,
            listOf(
                StyleVariant("christmas_snowcap", Res.string.style_colour_snowcap),
                StyleVariant("christmas_gift", Res.string.style_colour_gift),
                StyleVariant("christmas_baubles", Res.string.style_colour_baubles),
            ),
            unlock = AchievementCount(70),
        ),
        StyleGroup(
            Res.string.style_family_meadow,
            listOf(
                StyleVariant("meadow_green", Res.string.style_colour_green),
            ),
            unlock = AchievementCount(75),
        ),
        StyleGroup(
            Res.string.style_family_ribbon,
            listOf(
                StyleVariant("ribbon_white", Res.string.style_colour_white),
                StyleVariant("ribbon_black", Res.string.style_colour_black),
            ),
            unlock = AchievementCount(80),
        ),
        StyleGroup(
            Res.string.style_family_bestagon,
            listOf(
                StyleVariant("bestagon_honey", Res.string.style_colour_honey),
                StyleVariant("bestagon_slate", Res.string.style_colour_slate),
            ),
            unlock = AchievementCount(85),
        ),
        StyleGroup(
            Res.string.style_family_glitter,
            listOf(
                StyleVariant("glitter_mixed", Res.string.style_colour_mixed),
            ),
            unlock = AchievementCount(90),
        ),
        StyleGroup(
            Res.string.style_family_garden,
            listOf(
                StyleVariant("garden_soil", Res.string.style_colour_soil),
            ),
            unlock = AchievementCount(95),
        ),
        StyleGroup(
            Res.string.style_family_neon,
            listOf(
                StyleVariant("neon_night", Res.string.style_colour_night),
            ),
            unlock = AchievementCount(100),
        ),
        StyleGroup(
            Res.string.style_family_rgb,
            listOf(
                StyleVariant("rgb_rainbow", Res.string.style_colour_rainbow),
                StyleVariant("rgb_wave", Res.string.style_colour_wave),
                StyleVariant("rgb_white_rainbow", Res.string.style_colour_white_rainbow),
                StyleVariant("rgb_white_wave", Res.string.style_colour_white_wave),
            ),
            unlock = AchievementCount(100),
        ),
        StyleGroup(
            Res.string.style_family_d20,
            listOf(
                StyleVariant("d20_white", Res.string.style_colour_white),
                StyleVariant("d20_black", Res.string.style_colour_black),
                StyleVariant("d20_blue", Res.string.style_colour_blue),
            ),
            unlock = AchievementCount(D20_UNLOCK),
        ),
        StyleGroup(
            Res.string.style_family_googly,
            listOf(
                StyleVariant("googly_ivory", Res.string.style_colour_ivory),
                StyleVariant("googly_black", Res.string.style_colour_black),
                StyleVariant("googly_blue", Res.string.style_colour_blue),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.COMPLETIONIST),
        ),
        StyleGroup(
            Res.string.style_family_glitch,
            listOf(
                StyleVariant("glitch_black", Res.string.style_colour_black),
                StyleVariant("glitch_white", Res.string.style_colour_white),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.BIG_FAN),
        ),
        StyleGroup(
            Res.string.style_family_maths,
            listOf(
                StyleVariant("maths_white", Res.string.style_colour_white),
                StyleVariant("maths_black", Res.string.style_colour_black),
                StyleVariant("maths_green", Res.string.style_colour_green),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.COUNTING_UP),
        ),
        StyleGroup(
            Res.string.style_family_wireframe,
            listOf(
                StyleVariant("wireframe_cyan", Res.string.style_colour_cyan),
                StyleVariant("wireframe_green", Res.string.style_colour_green),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.ARE_YOU_A_DEVELOPER),
        ),
        StyleGroup(
            Res.string.style_family_devil,
            listOf(
                StyleVariant("devil_crimson", Res.string.style_colour_crimson),
                StyleVariant("devil_black", Res.string.style_colour_black),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.TOO_MUCH_OF_A_GOOD_THING),
        ),
        // To go with the Starry board and background, and earned with them.
        StyleGroup(
            Res.string.style_family_starry,
            listOf(
                StyleVariant("starry_midnight", Res.string.style_colour_midnight),
                StyleVariant("starry_silver", Res.string.style_colour_silver),
                StyleVariant("starry_purple", Res.string.style_colour_purple),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.MIDNIGHT_RUN),
        ),
    ),
)



/** The table backgrounds: every family, in the Styles screen's order, and what unlocks it. Their art is `ui.style.TableBackgrounds`. */
object TableBackgroundRegistry : StyleRegistry(
    Res.string.style_noun_background,
    listOf(
        StyleGroup(
            Res.string.style_family_classic,
            listOf(
                StyleVariant("classic_navy", Res.string.style_colour_navy),
                StyleVariant("classic_red", Res.string.style_colour_red),
                StyleVariant("classic_brown", Res.string.style_colour_brown),
            ),
        ),
        StyleGroup(
            Res.string.style_family_leather,
            listOf(
                StyleVariant("leather_tan", Res.string.style_colour_tan),
                StyleVariant("leather_oxblood", Res.string.style_colour_oxblood),
                StyleVariant("leather_black", Res.string.style_colour_black),
            ),
            unlock = AchievementCount(5),
        ),
        StyleGroup(
            Res.string.style_family_planks,
            listOf(
                StyleVariant("planks_oak", Res.string.style_colour_oak),
                StyleVariant("planks_walnut", Res.string.style_colour_walnut),
            ),
            unlock = AchievementCount(10),
        ),
        StyleGroup(
            Res.string.style_family_ocean,
            listOf(
                StyleVariant("ocean_waves", Res.string.style_colour_waves),
                StyleVariant("ocean_lagoon", Res.string.style_colour_lagoon),
            ),
            unlock = AchievementCount(WATER_SET_UNLOCK),
        ),
        StyleGroup(
            Res.string.style_family_spotlight,
            listOf(
                StyleVariant("spotlight_purple", Res.string.style_colour_purple),
                StyleVariant("spotlight_navy", Res.string.style_colour_navy),
                StyleVariant("spotlight_green", Res.string.style_colour_green),
            ),
            unlock = AchievementCount(30),
        ),
        StyleGroup(
            Res.string.style_family_pinstripe,
            listOf(
                StyleVariant("pinstripe_charcoal", Res.string.style_colour_charcoal),
                StyleVariant("pinstripe_navy", Res.string.style_colour_navy),
            ),
            unlock = AchievementCount(35),
        ),
        StyleGroup(
            Res.string.style_family_brick,
            listOf(
                StyleVariant("brick_neon", Res.string.style_colour_neon),
            ),
            unlock = AchievementCount(40),
        ),
        StyleGroup(
            Res.string.style_family_sunburst,
            listOf(
                StyleVariant("sunburst_crimson", Res.string.style_colour_crimson),
                StyleVariant("sunburst_amber", Res.string.style_colour_amber),
            ),
            unlock = AchievementCount(45),
        ),
        StyleGroup(
            Res.string.style_family_starry,
            listOf(
                StyleVariant("starry_midnight", Res.string.style_colour_midnight),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.MIDNIGHT_RUN),
        ),
        StyleGroup(
            Res.string.style_family_gingham,
            listOf(
                StyleVariant("gingham_red", Res.string.style_colour_red),
                StyleVariant("gingham_blue", Res.string.style_colour_blue),
                StyleVariant("gingham_green", Res.string.style_colour_green),
            ),
            unlock = AchievementCount(60),
        ),
        StyleGroup(
            Res.string.style_family_stone,
            listOf(
                StyleVariant("stone_slate", Res.string.style_colour_slate),
                StyleVariant("stone_granite", Res.string.style_colour_granite),
                StyleVariant("stone_black_marble", Res.string.style_colour_black_marble),
            ),
            unlock = AchievementCount(65),
        ),
        StyleGroup(
            Res.string.style_family_christmas,
            listOf(
                StyleVariant("christmas_green", Res.string.style_colour_green),
                StyleVariant("christmas_red", Res.string.style_colour_red),
            ),
            unlock = AchievementCount(70),
        ),
        StyleGroup(
            Res.string.style_family_meadow,
            listOf(
                StyleVariant("meadow_green", Res.string.style_colour_green),
            ),
            unlock = AchievementCount(75),
        ),
        StyleGroup(
            Res.string.style_family_circuit,
            listOf(
                StyleVariant("circuit_green", Res.string.style_colour_green),
                StyleVariant("circuit_black", Res.string.style_colour_black),
            ),
            unlock = AchievementCount(80),
        ),
        StyleGroup(
            Res.string.style_family_bestagons,
            listOf(
                StyleVariant("bestagons_honey", Res.string.style_colour_honey),
                StyleVariant("bestagons_charcoal", Res.string.style_colour_charcoal),
                StyleVariant("bestagons_indigo", Res.string.style_colour_indigo),
            ),
            unlock = AchievementCount(85),
        ),
        StyleGroup(
            Res.string.style_family_glitter,
            listOf(
                StyleVariant("glitter_gold", Res.string.style_colour_gold),
                StyleVariant("glitter_silver", Res.string.style_colour_silver),
                StyleVariant("glitter_rose_gold", Res.string.style_colour_rose_gold),
                StyleVariant("glitter_pink", Res.string.style_colour_pink),
                StyleVariant("glitter_purple", Res.string.style_colour_purple),
                StyleVariant("glitter_blue", Res.string.style_colour_blue),
                StyleVariant("glitter_emerald", Res.string.style_colour_emerald),
            ),
            unlock = AchievementCount(90),
        ),
        StyleGroup(
            Res.string.style_family_neon,
            listOf(
                StyleVariant("neon_pink_and_blue", Res.string.style_colour_pink_and_blue),
                StyleVariant("neon_green_and_purple", Res.string.style_colour_green_and_purple),
                StyleVariant("neon_orange_and_teal", Res.string.style_colour_orange_and_teal),
            ),
            unlock = AchievementCount(100),
        ),
        StyleGroup(
            Res.string.style_family_rgb,
            listOf(
                StyleVariant("rgb_rainbow", Res.string.style_colour_rainbow),
                StyleVariant("rgb_wave", Res.string.style_colour_wave),
                StyleVariant("rgb_ripple", Res.string.style_colour_ripple),
                StyleVariant("rgb_swirl", Res.string.style_colour_swirl),
                StyleVariant("rgb_starlight", Res.string.style_colour_starlight),
            ),
            unlock = AchievementCount(100),
        ),
        StyleGroup(
            Res.string.style_family_glitch,
            listOf(
                StyleVariant("glitch_screen", Res.string.style_colour_screen),
            ),
            unlock = StyleUnlock.SpecificAchievement(Achievement.BIG_FAN),
        ),
        StyleGroup(
            Res.string.style_family_floating_dice,
            listOf(
                StyleVariant("floating_dice_menu", Res.string.style_colour_menu),
            ),
            unlock = AchievementCount(D20_UNLOCK),
        ),
    ),
)

/** The boards: every family, in the Styles screen's order, and what unlocks it. Their art is `ui.style.BoardStyles`. */
object BoardStyleRegistry : StyleRegistry(
    Res.string.style_noun_board,
    listOf(
        StyleGroup(
            Res.string.style_family_classic,
            listOf(StyleVariant(DefaultStyleIds.BOARD, Res.string.style_colour_cream)),
        ),
        // Each family but Paper shares its lock with the die and background of the same name.
        StyleGroup(
            Res.string.style_family_wood,
            listOf(
                StyleVariant("wood_oak", Res.string.style_colour_oak),
                StyleVariant("wood_walnut", Res.string.style_colour_walnut),
            ),
            unlock = AchievementCount(10),
        ),
        StyleGroup(
            Res.string.style_family_ocean,
            listOf(
                StyleVariant("ocean_lagoon", Res.string.style_colour_lagoon),
                StyleVariant("ocean_deep", Res.string.style_colour_deep),
            ),
            unlock = AchievementCount(WATER_SET_UNLOCK),
        ),
        StyleGroup(
            Res.string.style_family_retro,
            listOf(StyleVariant("retro_blue", Res.string.style_colour_blue)),
            unlock = AchievementCount(45),
        ),
        StyleGroup(
            Res.string.style_family_paper,
            listOf(StyleVariant("paper_pencil", Res.string.style_colour_pencil)),
            unlock = AchievementCount(50),
        ),
        StyleGroup(
            Res.string.style_family_starry,
            listOf(StyleVariant("starry_midnight", Res.string.style_colour_midnight)),
            unlock = StyleUnlock.SpecificAchievement(Achievement.MIDNIGHT_RUN),
        ),
        StyleGroup(
            Res.string.style_family_stone,
            listOf(
                StyleVariant("stone_slate", Res.string.style_colour_slate),
                StyleVariant("stone_sandstone", Res.string.style_colour_sandstone),
            ),
            unlock = AchievementCount(65),
        ),
        StyleGroup(
            Res.string.style_family_neon,
            listOf(StyleVariant("neon_night", Res.string.style_colour_night)),
            unlock = AchievementCount(100),
        ),
    ),
)

/**
 * The pawns: every family, in the Styles screen's order, and what unlocks it. A pawn always takes its player's colour,
 * so a family's variants are finishes, not colours. Their art is `ui.style.PawnStyles`.
 */
object PawnStyleRegistry : StyleRegistry(
    Res.string.style_noun_pawns,
    listOf(
        StyleGroup(
            Res.string.style_family_classic,
            listOf(StyleVariant(DefaultStyleIds.PAWNS, Res.string.style_colour_gloss)),
        ),
        StyleGroup(
            Res.string.style_family_counter,
            listOf(StyleVariant("counter_ridged", Res.string.style_colour_ridged)),
            unlock = AchievementCount(5),
        ),
        StyleGroup(
            Res.string.style_family_cone,
            listOf(StyleVariant("cone_painted", Res.string.style_colour_painted)),
            unlock = AchievementCount(20),
        ),
        StyleGroup(
            Res.string.style_family_peg,
            listOf(StyleVariant("peg_stained", Res.string.style_colour_stained)),
            unlock = AchievementCount(25),
        ),
        StyleGroup(
            Res.string.style_family_glass,
            listOf(StyleVariant("glass_clear", Res.string.style_colour_clear)),
            unlock = AchievementCount(30),
        ),
        StyleGroup(
            Res.string.style_family_animal,
            listOf(
                StyleVariant("animal_cat", Res.string.style_colour_cat),
                StyleVariant("animal_duck", Res.string.style_colour_duck),
            ),
            unlock = AchievementCount(40),
        ),
        // With the Gems die, and opened with it.
        StyleGroup(
            Res.string.style_family_gems,
            listOf(StyleVariant("gems_cut", Res.string.style_colour_cut)),
            unlock = AchievementCount(65),
        ),
    ),
    variantNoun = Res.string.style_variant_finish,
)
