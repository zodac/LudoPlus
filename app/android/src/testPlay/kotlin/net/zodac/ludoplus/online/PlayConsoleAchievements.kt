package net.zodac.ludoplus.online

import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.Achievement.*
import net.zodac.ludoplus.model.AchievementCategory

/**
 * Each achievement's Play Games points, from its difficulty tier: entered in Play Console from the export
 * (`scripts/export-play-console.sh`), so this is their record. Every Easter egg is worth the minimum, Completionist 100,
 * and the other 99 share the rest of Play's 2,000 by tier. Published points can't change, so a new achievement needs
 * points freed up before it ships (`.claude/PLAY_GAMES.md`, "Achievements"). Proposed 2026-11-18, for the maintainer's
 * review before publishing.
 */
internal enum class PlayPointsTier(val points: Int) {
    TRIVIAL(5),
    EASY(10),
    MEDIUM(15),
    HARD(20),
    VERY_HARD(45),
    EXTREME(70),

    /** Completionist alone: every other achievement. */
    ULTIMATE(100),
    EASTER_EGG(5),
}

internal val playPointsTiers: Map<Achievement, PlayPointsTier> = buildMap {
    fun tier(tier: PlayPointsTier, vararg achievements: Achievement) = achievements.forEach { put(it, tier) }

    tier(
        PlayPointsTier.TRIVIAL,
        THE_JOURNEY_BEGINS, LETS_FINISH_THIS, ONE_MORE_TIME, GOTCHA, HOME_SWEET_HOME, FIRST_VICTORY, TRAINING_WHEELS,
        BACK_TO_SCHOOL, READ_THE_MANUAL, WHO_MADE_THIS, NUMBER_CRUNCHER, I_ROBOT, WOODEN_SPOON, EARLY_BIRD,
    )
    tier(
        PlayPointsTier.EASY,
        WARMING_UP, GETTING_COMFORTABLE, ON_A_ROLL, PEST, HOMECOMING, OFF_THE_MARK, BROKEN_RECORD, WELCOME_PARTY,
        STRAIGHT_OUT_THE_GATE, NOT_SO_FAST, PAYBACK, ALL_HANDS_ON_DECK, LAST_HUMAN_STANDING, BACK_TO_SQUARE_ONE,
        BOTTOM_OF_THE_BARREL, DRY_SPELL, INSULT_TO_INJURY, LOCKED_OUT, TOO_MUCH_OF_A_GOOD_THING, INCH_BY_INCH, SAFETY_FIRST,
        LEAPFROG, DEJA_VU, BUDDY_SYSTEM,
    )
    tier(
        PlayPointsTier.MEDIUM,
        CHAMPION, HOT_STREAK, REGULAR, SIX_SHOOTER, FULL_SPREAD, HOT_HAND, TWO_FOR_ONE, HAT_TRICK, TALL_POPPY, PARTING_SHOT,
        PICKING_ON_YOU, NOBODY_IS_SAFE, DOUBLE_DELIVERY, FULL_HOUSE, FREQUENT_TRAVELLER, FRONT_RUNNER, LATE_BLOOMER,
        LIFE_OF_THE_PARTY, PACIFIST, PHOTO_FINISH, UNTOUCHABLE, NEVER_LEFT_HOME, GROUNDHOG_DAY, LOSING_STREAK,
        PIPPED_TO_THE_POST, SCENIC_ROUTE, SPOON_COLLECTOR, SO_NEAR_YET_SO_FAR, PUNCHING_BAG, SITTING_DUCKS,
        HOW_DO_YOU_DO_FELLOW_CPUS,
    )
    tier(
        PlayPointsTier.HARD,
        HALL_OF_FAMER, WELL_ROLLED, CENTURION, COUNTING_UP, SIX_PACK, ON_THE_WARPATH, BOUNCER, EXPRESS_DELIVERY,
        LAP_OF_HONOUR, MARATHON, CLEAN_SWEEP, COMEBACK_KID, NEVER_SAY_DIE, WIRE_TO_WIRE, DEFEAT_FROM_THE_JAWS_OF_VICTORY,
        SIXTH_SENSE, SPEED_DEMON,
    )
    tier(PlayPointsTier.VERY_HARD, UNSTOPPABLE, LIVING_LEGEND, OLD_HAND, MENACE, GLOBETROTTER, CONQUEROR, LIGHTNING_FAST)
    tier(PlayPointsTier.EXTREME, PART_OF_THE_FURNITURE, DIE_HARD, IMMORTAL, LIFETIME_ACHIEVEMENT, PUBLIC_ENEMY, ULTRAMARATHON)
    tier(PlayPointsTier.ULTIMATE, COMPLETIONIST)
    Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS }.forEach { put(it, PlayPointsTier.EASTER_EGG) }
}
