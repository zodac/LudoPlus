package net.zodac.ludoplus.online

import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.Achievement.*
import net.zodac.ludoplus.model.AchievementCategory

/**
 * Each achievement's Play Games points, from its difficulty tier: entered in Play Console from the export
 * (`scripts/export-play-console.sh`), so this is their record. Every Easter egg is worth the minimum, Completionist 100,
 * and the other 99 share the rest of Play's 2,000 by tier. Published points can't change, so a new achievement needs
 * points freed up before it ships (`.claude/PLAY_GAMES.md`, "Achievements"). Proposed 2026-11-18, for the maintainer's
 * review before publishing; the Hard, Very Hard, and Extreme tiers raised 2026-11-23, when 11 achievements were removed,
 * and Very Hard lowered to 55 the same day for My Favourite Spot and We're All Friends Here; Off The Mark moved to
 * Trivial for Can't Wait To Get Going. The Hard, Very Hard, and Extreme tiers lowered, and Off The Mark back in Easy,
 * 2026-11-24, for those of the game modes and house rules; Hard back to 25 the same day, when eight were removed, and
 * Very Hard 50 and Extreme 90, and Revenge Is Sweet (then Payback) Medium, when four more were. Retuned 2026-11-25 from how
 * often each comes up in simulated games (`.claude/PLAY_GAMES.md`, "Achievements"), with Hard 30 and Very Hard 55.
 * Retuned again 2026-11-26, when the climbs on games, wins, pawns home, and squares were shortened: Hard 45, Very Hard 70,
 * and Extreme 80.
 */
internal enum class PlayPointsTier(val points: Int) {
    TRIVIAL(5),
    EASY(10),
    MEDIUM(15),
    HARD(45),
    VERY_HARD(70),
    EXTREME(80),

    /** Completionist alone: every other achievement. */
    ULTIMATE(100),
    EASTER_EGG(5),
}

internal val playPointsTiers: Map<Achievement, PlayPointsTier> = buildMap {
    fun tier(tier: PlayPointsTier, vararg achievements: Achievement) = achievements.forEach { put(it, tier) }

    tier(
        PlayPointsTier.TRIVIAL,
        THE_JOURNEY_BEGINS, LETS_FINISH_THIS, ONE_MORE_TIME, GOTCHA, HOME_SWEET_HOME, FIRST_VICTORY, WHO_MADE_THIS,
        I_ROBOT, WOODEN_SPOON, EARLY_BIRD, WHERE_WERE_GOING_WE_DONT_NEED_RULES, ALL_HANDS_ON_DECK, INCH_BY_INCH,
        STRAIGHT_OUT_THE_GATE, DRY_SPELL, WELCOME_PARTY, DEJA_VU,
    )
    tier(
        PlayPointsTier.EASY,
        WARMING_UP, GETTING_COMFORTABLE, REGULAR, PEST, HOMECOMING, BOTTOM_OF_THE_BARREL, INSULT_TO_INJURY, LOCKED_OUT,
        TOO_MUCH_OF_A_GOOD_THING, LEAPFROG, LOADED_DICE, OFF_THE_MARK, PAINT_THE_TOWN_RED, DOUBLE_OR_NOTHING,
        THROUGH_THE_LOOKING_GLASS, LAST_PAWN_STANDING, AGENT_OF_CHAOS, LIVING_TO_TELL_THE_TALE, THERE_AND_BACK_AGAIN,
        GROUNDHOG_DAY, NOBODY_IS_SAFE, WERE_ALL_FRIENDS_HERE, ONE_OF_EVERYTHING, PICKING_ON_YOU, NEVER_LEFT_HOME,
        SO_NEAR_YET_SO_FAR, TALL_POPPY, SIX_SHOOTER,
    )
    tier(
        PlayPointsTier.MEDIUM,
        CHAMPION, HALL_OF_FAMER, SEASONED, OLD_HAND, VISITING_THE_NEIGHBOURS, TWO_FOR_ONE, HAT_TRICK, PARTING_SHOT,
        DOUBLE_DELIVERY, FRONT_RUNNER,
        LATE_BLOOMER, LIFE_OF_THE_PARTY, PACIFIST, PHOTO_FINISH, UNTOUCHABLE, PIPPED_TO_THE_POST, SPOON_COLLECTOR,
        PUNCHING_BAG, SITTING_DUCKS, HOW_DO_YOU_DO_FELLOW_CPUS, MY_FAVOURITE_SPOT, TWO_BIRDS_TWO_STONES,
        STOP_PICKING_ON_ME, PAYBACK, BARELY_A_SCRATCH, EVERYTHING_EVERYWHERE_ALL_AT_ONCE, GRAND_TOUR, NOT_SO_FAST,
        BACK_TO_SQUARE_ONE, EXPRESS_DELIVERY, LAP_OF_HONOUR, ON_THE_WARPATH, SIX_PACK, WIRE_TO_WIRE, COLOUR_COORDINATED,
        BOUNCER,
    )
    tier(
        PlayPointsTier.HARD,
        LIFETIME_ACHIEVEMENT, LIVING_LEGEND, GLOBETROTTER, ULTRAMARATHON, WELL_ROLLED, COMEBACK_KID, NEVER_SAY_DIE,
        SPEED_DEMON, NOT_A_GHOST_OF_A_CHANCE,
        FULL_HOUSE, HOT_STREAK, CONQUEROR, MENACE,
    )
    tier(
        PlayPointsTier.VERY_HARD,
        CLEAN_SWEEP, COUNTING_UP, DEFEAT_FROM_THE_JAWS_OF_VICTORY, PUBLIC_ENEMY,
    )
    tier(
        PlayPointsTier.EXTREME,
        UNSTOPPABLE,
    )
    tier(PlayPointsTier.ULTIMATE, COMPLETIONIST)
    Achievement.entries.filter { it.category == AchievementCategory.EASTER_EGGS }.forEach { put(it, PlayPointsTier.EASTER_EGG) }
}
