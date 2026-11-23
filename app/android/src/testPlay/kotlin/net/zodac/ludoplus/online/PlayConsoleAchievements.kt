package net.zodac.ludoplus.online

import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.Achievement.*
import net.zodac.ludoplus.model.AchievementCategory

/**
 * Each achievement's Play Games points, from its difficulty tier: entered in Play Console from the export
 * (`scripts/export-play-console.sh`), so this is their record. Every Easter egg is worth the minimum, Completionist 100,
 * and the other 102 share the rest of Play's 2,000 by tier. Published points can't change, so a new achievement needs
 * points freed up before it ships (`.claude/PLAY_GAMES.md`, "Achievements"). Proposed 2026-11-18, for the maintainer's
 * review before publishing; the Hard, Very Hard, and Extreme tiers raised 2026-11-23, when 11 achievements were removed,
 * and Very Hard lowered to 55 the same day for My Favourite Spot and We're All Friends Here; Off The Mark moved to
 * Trivial for Can't Wait To Get Going. The Hard, Very Hard, and Extreme tiers lowered, and Off The Mark back in Easy,
 * 2026-11-24, for those of the game modes and house rules; Hard back to 25 the same day, when eight were removed.
 */
internal enum class PlayPointsTier(val points: Int) {
    TRIVIAL(5),
    EASY(10),
    MEDIUM(15),
    HARD(25),
    VERY_HARD(40),
    EXTREME(75),

    /** Completionist alone: every other achievement. */
    ULTIMATE(100),
    EASTER_EGG(5),
}

internal val playPointsTiers: Map<Achievement, PlayPointsTier> = buildMap {
    fun tier(tier: PlayPointsTier, vararg achievements: Achievement) = achievements.forEach { put(it, tier) }

    tier(
        PlayPointsTier.TRIVIAL,
        THE_JOURNEY_BEGINS, LETS_FINISH_THIS, ONE_MORE_TIME, GOTCHA, HOME_SWEET_HOME, FIRST_VICTORY,
        WHO_MADE_THIS, I_ROBOT, WOODEN_SPOON, EARLY_BIRD, WHERE_WERE_GOING_WE_DONT_NEED_RULES,
    )
    tier(
        PlayPointsTier.EASY,
        WARMING_UP, GETTING_COMFORTABLE, ON_A_ROLL, PEST, HOMECOMING, WELCOME_PARTY,
        STRAIGHT_OUT_THE_GATE, NOT_SO_FAST, PAYBACK, ALL_HANDS_ON_DECK, BACK_TO_SQUARE_ONE, BOTTOM_OF_THE_BARREL, DRY_SPELL,
        INSULT_TO_INJURY, LOCKED_OUT, TOO_MUCH_OF_A_GOOD_THING, INCH_BY_INCH, LEAPFROG, DEJA_VU, BUDDY_SYSTEM, OFF_THE_MARK,
        QUICK_ON_THE_DRAW, DOUBLE_OR_NOTHING, THROUGH_THE_LOOKING_GLASS, LAST_PAWN_STANDING, AGENT_OF_CHAOS,
        LIVING_TO_TELL_THE_TALE, THERE_AND_BACK_AGAIN,
    )
    tier(
        PlayPointsTier.MEDIUM,
        CHAMPION, HOT_STREAK, REGULAR, SIX_SHOOTER, FULL_SPREAD, HOT_HAND, TWO_FOR_ONE, HAT_TRICK, TALL_POPPY, PARTING_SHOT,
        PICKING_ON_YOU, NOBODY_IS_SAFE, DOUBLE_DELIVERY, FULL_HOUSE, FRONT_RUNNER, LATE_BLOOMER,
        LIFE_OF_THE_PARTY, PACIFIST, PHOTO_FINISH, UNTOUCHABLE, NEVER_LEFT_HOME, GROUNDHOG_DAY,
        PIPPED_TO_THE_POST, SPOON_COLLECTOR, SO_NEAR_YET_SO_FAR, PUNCHING_BAG, SITTING_DUCKS,
        HOW_DO_YOU_DO_FELLOW_CPUS, MY_FAVOURITE_SPOT, WERE_ALL_FRIENDS_HERE, TWO_BIRDS_TWO_STONES, ONE_OF_EVERYTHING,
        BARELY_A_SCRATCH, EVERYTHING_EVERYWHERE_ALL_AT_ONCE, GRAND_TOUR,
    )
    tier(
        PlayPointsTier.HARD,
        HALL_OF_FAMER, WELL_ROLLED, CENTURION, COUNTING_UP, SIX_PACK, ON_THE_WARPATH, BOUNCER, EXPRESS_DELIVERY,
        LAP_OF_HONOUR, CLEAN_SWEEP, COMEBACK_KID, NEVER_SAY_DIE, WIRE_TO_WIRE, DEFEAT_FROM_THE_JAWS_OF_VICTORY,
        SIXTH_SENSE, SPEED_DEMON, BLINK_AND_YOULL_MISS_IT, NOT_A_GHOST_OF_A_CHANCE,
    )
    tier(PlayPointsTier.VERY_HARD, UNSTOPPABLE, LIVING_LEGEND, OLD_HAND, MENACE, GLOBETROTTER, CONQUEROR)
    tier(PlayPointsTier.EXTREME, DIE_HARD, LIFETIME_ACHIEVEMENT, PUBLIC_ENEMY, ULTRAMARATHON)
    tier(PlayPointsTier.ULTIMATE, COMPLETIONIST)
    Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS }.forEach { put(it, PlayPointsTier.EASTER_EGG) }
}
