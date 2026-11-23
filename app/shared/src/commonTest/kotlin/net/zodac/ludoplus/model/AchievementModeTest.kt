package net.zodac.ludoplus.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.ludoplus.game.GameFeats
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.model.Achievement.*

/** Which achievements each game mode, and a game with house rules, can earn (`.claude/GAME_MODES_AUDIT.md`). */
class AchievementModeTest {

    private fun game(mode: GameMode, houseRules: Set<HouseRule> = emptySet()) = GameState.new(mode, 4, rules = mode.rules.withHouseRules(houseRules))

    @Test
    fun `every mode but Classic has exactly one win of its own and one feat - and Classic neither`() {
        for (mode in GameMode.entries) {
            val own = Achievement.entries.filter { it.mode == mode }
            assertEquals(if (mode == GameMode.CLASSIC) emptyList() else listOf(true, false), own.map { it.isModeWin }, "$mode: $own")
        }
        assertTrue(Achievement.entries.filter { it.isModeWin }.all { it.mode != null })
    }

    @Test
    fun `an achievement of one mode is earnable only in it - one of none in any`() {
        assertEquals(listOf(GameMode.CHAOS), GameMode.entries.filter(AGENT_OF_CHAOS::isEarnableIn))
        assertEquals(GameMode.entries, GameMode.entries.filter(FIRST_VICTORY::isEarnableIn))
        assertEquals(GameMode.entries - GameMode.CLASSIC, GameMode.entries.filter(WHERE_WERE_GOING_WE_DONT_NEED_RULES::isEarnableIn))
    }

    @Test
    fun `the achievements kept out of a mode they'd be free in or measure something else in`() {
        val kept = Achievement.entries.filter { it.notEarnableIn.isNotEmpty() }.associateWith { it.notEarnableIn }
        val quick = setOf(
            COMEBACK_KID, DEFEAT_FROM_THE_JAWS_OF_VICTORY, FULL_HOUSE, MY_FAVOURITE_SPOT, ONE_BY_ONE, PACIFIST, SPEED_DEMON, THE_GREAT_ESCAPE, UNTOUCHABLE,
        )
        val twoDice = setOf(BOTTOM_OF_THE_BARREL, COUNTING_UP, DRY_SPELL, EXPRESS_DELIVERY, FULL_SPREAD, HOT_HAND)
        val survival = setOf(
            ALL_HANDS_ON_DECK, BUDDY_SYSTEM, CLEAN_SWEEP, HAT_TRICK, INSULT_TO_INJURY, LEAPFROG, SITTING_DUCKS, TWO_FOR_ONE, WIRE_TO_WIRE,
        )
        val guards = mapOf(
            GameMode.CLASSIC to emptySet(),
            GameMode.QUICK to quick,
            GameMode.TWO_DICE to twoDice,
            GameMode.PORTALS to setOf(EXPRESS_DELIVERY),
            GameMode.SURVIVAL to survival,
            GameMode.CHAOS to emptySet(),
            GameMode.HAUNTED_HOUSE to emptySet(),
        )
        for (mode in GameMode.entries) assertEquals(guards.getValue(mode), kept.filterValues { mode in it }.keys, "$mode")
    }

    @Test
    fun `the achievements a mode's rules rule out`() {
        val impossible = GameMode.entries.associateWith { mode -> Achievement.entries.filter { mode in it.impossibleIn }.toSet() }
        assertEquals(setOf(TOO_MUCH_OF_A_GOOD_THING), impossible[GameMode.TWO_DICE])
        // Nothing comes home or out of a yard, there's no last square, and a pawn is only captured once.
        assertEquals(
            setOf(
                NOT_SO_FAST, TALL_POPPY, PARTING_SHOT, HOME_SWEET_HOME, HOMECOMING, EARLY_BIRD, DOUBLE_DELIVERY, EXPRESS_DELIVERY, LAP_OF_HONOUR, FULL_HOUSE,
                GLOBETROTTER, COMEBACK_KID, FRONT_RUNNER, LATE_BLOOMER, NEVER_SAY_DIE, PHOTO_FINISH, BACK_TO_SQUARE_ONE,
                DEFEAT_FROM_THE_JAWS_OF_VICTORY, GROUNDHOG_DAY, LOCKED_OUT, NEVER_LEFT_HOME, PIPPED_TO_THE_POST, PUNCHING_BAG, DEJA_VU, INCH_BY_INCH,
                SO_NEAR_YET_SO_FAR, ONE_BY_ONE, THE_GREAT_ESCAPE,
            ),
            impossible[GameMode.SURVIVAL],
        )
        assertEquals(setOf(EVERYTHING_EVERYWHERE_ALL_AT_ONCE, WHERE_WERE_GOING_WE_DONT_NEED_RULES), impossible[GameMode.CLASSIC])
        for (mode in listOf(GameMode.QUICK, GameMode.PORTALS, GameMode.CHAOS, GameMode.HAUNTED_HOUSE)) assertEquals(emptySet(), impossible[mode], "$mode")
    }

    @Test
    fun `a limited achievement lists the modes left - neither those it's kept out of nor the impossible ones - and one only ruled out lists none`() {
        assertEquals(GameMode.entries - GameMode.QUICK, UNTOUCHABLE.limitedModes)
        assertEquals(listOf(GameMode.CLASSIC, GameMode.QUICK, GameMode.CHAOS, GameMode.HAUNTED_HOUSE), EXPRESS_DELIVERY.limitedModes)
        assertEquals(listOf(GameMode.CHAOS), AGENT_OF_CHAOS.limitedModes)
        assertEquals(emptyList(), HOME_SWEET_HOME.limitedModes)
        assertFalse(HOME_SWEET_HOME.isEarnableIn(GameMode.SURVIVAL))
        assertEquals(emptyList(), FIRST_VICTORY.limitedModes)
    }

    @Test
    fun `with house rules on a game earns only the house rules' own - and starting another mode - and moves no counter`() {
        assertEquals(
            setOf(EVERYTHING_EVERYWHERE_ALL_AT_ONCE, WHERE_WERE_GOING_WE_DONT_NEED_RULES),
            Achievement.entries.filter { it.earnableWithHouseRules }.toSet(),
        )
        val feats = GameFeats(setOf(FIRST_VICTORY, AGENT_OF_CHAOS, EVERYTHING_EVERYWHERE_ALL_AT_ONCE, UNTOUCHABLE), mapOf(AchievementCounter.GAMES_WON to 1), streak = true)
        assertEquals(GameFeats(setOf(EVERYTHING_EVERYWHERE_ALL_AT_ONCE)), feats.earnableIn(game(GameMode.CHAOS, setOf(HouseRule.BLOCKADES))))
        assertEquals(feats, feats.earnableIn(game(GameMode.CHAOS)))
        assertEquals(setOf(FIRST_VICTORY, EVERYTHING_EVERYWHERE_ALL_AT_ONCE), feats.earnableIn(game(GameMode.QUICK)).achievements)
    }
}
