package net.zodac.ludoplus.ui.achievements

import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.ui.icons.Symbols
import net.zodac.ludoplus.ui.theme.AchievementDeveloperCyan
import net.zodac.ludoplus.ui.theme.AchievementBadgeViolet
import net.zodac.ludoplus.ui.theme.AchievementClockAmber
import net.zodac.ludoplus.ui.theme.AchievementHeartRed
import net.zodac.ludoplus.ui.theme.AchievementInkBlue
import net.zodac.ludoplus.ui.theme.AchievementMusicGold
import net.zodac.ludoplus.ui.theme.AchievementRobotSilver
import net.zodac.ludoplus.ui.theme.AchievementSidewaysTeal

/**
 * The glyph an unlocked [Achievement] shows on the Achievements page and its banner: its own, never a stock trophy, and
 * no two the same (`AchievementIconsTest`). Mostly Material Symbols ([Symbols]); where nothing there fits, one drawn
 * for this app (`DrawnAchievementIcons.kt`). The page shows [LOCKED_ACHIEVEMENT_ICON] until it's earned, so none of
 * these is a spoiler.
 *
 * A `when` over the enum, not a field on [Achievement], so the model stays free of Compose. `@Composable` for the
 * icons built with [remember].
 */
val Achievement.icon: ImageVector
    @Composable
    get() = when (this) {
        // ---- Milestones ---------------------------------------------------------------------
        Achievement.THE_JOURNEY_BEGINS -> Symbols.RocketLaunch
        Achievement.LETS_FINISH_THIS -> Symbols.History
        Achievement.ONE_MORE_TIME -> Symbols.Replay
        Achievement.WARMING_UP -> Symbols.Whatshot
        Achievement.GETTING_COMFORTABLE -> Symbols.Numbers
        Achievement.CHAMPION -> Symbols.SocialLeaderboard
        // Climbing, not a flame: Hot Streak has the flame.
        Achievement.ON_A_ROLL -> Symbols.KeyboardDoubleArrowUp
        Achievement.HOT_STREAK -> Symbols.LocalFireDepartment
        Achievement.REGULAR -> Symbols.EventRepeat
        Achievement.HALL_OF_FAMER -> Symbols.EmojiEvents
        Achievement.CENTURION -> Symbols.MilitaryTech
        Achievement.WELL_ROLLED -> Symbols.Casino
        // A crown for ten wins in a row: a flame would be too close to On A Roll's.
        Achievement.UNSTOPPABLE -> Symbols.Crown
        Achievement.LIVING_LEGEND -> Symbols.WorkspacePremium
        Achievement.OLD_HAND -> Symbols.Elderly
        Achievement.PART_OF_THE_FURNITURE -> Symbols.Chair
        Achievement.DIE_HARD -> Symbols.FitnessCenter
        Achievement.IMMORTAL -> Symbols.AllInclusive
        Achievement.LIFETIME_ACHIEVEMENT -> Symbols.HistoryEdu

        // ---- The Die ------------------------------------------------------------------------
        Achievement.OFF_THE_MARK -> Symbols.Start
        Achievement.SIX_SHOOTER -> Symbols.Filter6
        Achievement.BROKEN_RECORD -> Symbols.Album
        Achievement.FULL_SPREAD -> Symbols.FormatListNumbered
        Achievement.HOT_HAND -> Symbols.BackHand
        Achievement.SIX_PACK -> Symbols.SportsBar
        // Steps up, one number at a time.
        Achievement.COUNTING_UP -> Symbols.Stairs2
        Achievement.SIXTH_SENSE -> Symbols.Visibility

        // ---- Captures -----------------------------------------------------------------------
        Achievement.GOTCHA -> Symbols.Target
        Achievement.PEST -> Symbols.PestControl
        // Two stacked, for two pawns with one move.
        Achievement.TWO_FOR_ONE -> Symbols.FilterNone
        Achievement.WELCOME_PARTY -> Symbols.Celebration
        Achievement.STRAIGHT_OUT_THE_GATE -> Symbols.DoorOpen
        Achievement.HAT_TRICK -> Symbols.Looks3
        // Justice done, on the very next turn.
        Achievement.PAYBACK -> Symbols.Gavel
        Achievement.NOT_SO_FAST -> Symbols.Block
        // Cut down to size.
        Achievement.TALL_POPPY -> Symbols.ContentCut
        Achievement.PARTING_SHOT -> Symbols.NearMe
        Achievement.PICKING_ON_YOU -> Symbols.PersonRemove
        // The whole world: every opponent.
        Achievement.NOBODY_IS_SAFE -> Symbols.Public
        Achievement.ON_THE_WARPATH -> Symbols.Swords
        // Shown the door.
        Achievement.BOUNCER -> Symbols.Logout
        Achievement.MENACE -> Symbols.Dangerous
        Achievement.PUBLIC_ENEMY -> Symbols.Skull

        // ---- Racing -------------------------------------------------------------------------
        Achievement.HOME_SWEET_HOME -> Symbols.House
        Achievement.HOMECOMING -> Symbols.Cabin
        Achievement.EARLY_BIRD -> Symbols.FlutterDash
        Achievement.ALL_HANDS_ON_DECK -> Symbols.WavingHand
        Achievement.DOUBLE_DELIVERY -> Symbols.LocalShipping
        Achievement.EXPRESS_DELIVERY -> Symbols.Rocket
        // A runner on a lap of the board.
        Achievement.LAP_OF_HONOUR -> Symbols.Sprint
        Achievement.FULL_HOUSE -> Symbols.HolidayVillage
        Achievement.FREQUENT_TRAVELLER -> Symbols.HomeWork
        Achievement.MARATHON -> Symbols.DirectionsRun
        Achievement.GLOBETROTTER -> Symbols.TravelExplore
        Achievement.ULTRAMARATHON -> Symbols.Hiking

        // ---- Winning ------------------------------------------------------------------------
        Achievement.CLEAN_SWEEP -> Symbols.DeleteSweep
        Achievement.COMEBACK_KID -> Symbols.TrendingUp
        Achievement.CONQUEROR -> Symbols.Castle
        Achievement.FIRST_VICTORY -> Symbols.Flag
        Achievement.FRONT_RUNNER -> Symbols.LooksOne
        Achievement.HOW_DO_YOU_DO_FELLOW_CPUS -> Symbols.Memory
        Achievement.LAST_HUMAN_STANDING -> Symbols.EmojiPeople
        Achievement.LATE_BLOOMER -> Symbols.LocalFlorist
        Achievement.LIFE_OF_THE_PARTY -> Symbols.Diversity3
        Achievement.LIGHTNING_FAST -> Symbols.FlashOn
        Achievement.NEVER_SAY_DIE -> Symbols.Healing
        Achievement.PACIFIST -> Symbols.ThumbUp
        Achievement.PHOTO_FINISH -> Symbols.PhotoCamera
        Achievement.SPEED_DEMON -> Symbols.Bolt
        Achievement.TRAINING_WHEELS -> Symbols.PedalBike
        // Locked away, out of reach: never captured.
        Achievement.UNTOUCHABLE -> Symbols.Lock
        Achievement.WIRE_TO_WIRE -> Symbols.Timeline

        // ---- Misfortune ---------------------------------------------------------------------
        Achievement.BACK_TO_SQUARE_ONE -> Symbols.RestartAlt
        Achievement.BOTTOM_OF_THE_BARREL -> Symbols.KeyboardDoubleArrowDown
        // Drawn for this app: a trophy cracked in two - the win that broke at the last.
        Achievement.DEFEAT_FROM_THE_JAWS_OF_VICTORY -> CRACKED_TROPHY_ICON
        Achievement.DRY_SPELL -> Symbols.WbSunny
        Achievement.GROUNDHOG_DAY -> Symbols.Update
        Achievement.I_ROBOT -> Symbols.Android
        Achievement.INSULT_TO_INJURY -> Symbols.PersonalInjury
        Achievement.LOCKED_OUT -> Symbols.HourglassEmpty
        Achievement.LOSING_STREAK -> Symbols.ThumbDown
        Achievement.NEVER_LEFT_HOME -> Symbols.Cottage
        Achievement.PIPPED_TO_THE_POST -> Symbols.Timer
        Achievement.PUNCHING_BAG -> Symbols.SentimentDissatisfied
        Achievement.SCENIC_ROUTE -> Symbols.Landscape
        Achievement.SITTING_DUCKS -> Symbols.Cancel
        Achievement.SPOON_COLLECTOR -> Symbols.Restaurant
        Achievement.TOO_MUCH_OF_A_GOOD_THING -> Symbols.Looks6
        Achievement.WOODEN_SPOON -> Symbols.TrendingDown

        // ---- Miscellaneous (hidden) ---------------------------------------------------------
        Achievement.BACK_TO_SCHOOL -> Symbols.School
        Achievement.BUDDY_SYSTEM -> Symbols.Groups
        Achievement.DEJA_VU -> Symbols.Repeat
        Achievement.INCH_BY_INCH -> Symbols.SlowMotionVideo
        Achievement.LEAPFROG -> Symbols.Redo
        Achievement.NUMBER_CRUNCHER -> Symbols.BarChart
        Achievement.READ_THE_MANUAL -> Symbols.MenuBook
        Achievement.SAFETY_FIRST -> Symbols.HealthAndSafety
        Achievement.SO_NEAR_YET_SO_FAR -> Symbols.SentimentNeutral
        Achievement.WHO_MADE_THIS -> Symbols.Info

        // ---- Collection ---------------------------------------------------------------------
        // Drawn for this app: the trophy in crystal, for the last of them all.
        Achievement.COMPLETIONIST -> CRYSTAL_TROPHY_ICON

        // ---- Easter Eggs --------------------------------------------------------------------
        // Every icon in this category is a fixed colour rather than the ambient tint - see [iconTintOrUnspecified].
        Achievement.ALLITERATION -> Symbols.Abc
        Achievement.ARE_YOU_A_DEVELOPER -> Symbols.DeveloperMode
        Achievement.BIG_FAN -> Symbols.Favorite
        Achievement.I_AM_NOT_A_ROBOT -> Symbols.SmartToy
        // A crescent moon and two stars, for the small hours it was earned in.
        Achievement.MIDNIGHT_RUN -> rememberMidnightIcon()
        Achievement.MUSICAL_CHAIRS -> Symbols.MusicNote
        Achievement.SIDEWAYS -> Symbols.ScreenRotation
        Achievement.TIME_FLIES -> Symbols.Schedule
        Achievement.WHATS_IN_A_NAME -> Symbols.Signature
        Achievement.WHOS_LUDO -> Symbols.Badge
    }

/**
 * [tint] as given, except for the fixed-colour icons: every Easter Egg means a colour of its own, not whatever
 * container holds it (Big Fan's heart is always [AchievementHeartRed]; [Color.Unspecified] shows a drawn icon's own
 * fills), and so does Completionist's crystal.
 */
fun Achievement.iconTintOrUnspecified(tint: Color): Color = when (this) {
    Achievement.COMPLETIONIST -> Color.Unspecified
    Achievement.ALLITERATION -> AchievementInkBlue
    Achievement.ARE_YOU_A_DEVELOPER -> AchievementDeveloperCyan
    Achievement.BIG_FAN -> AchievementHeartRed
    Achievement.MIDNIGHT_RUN -> Color.Unspecified
    Achievement.I_AM_NOT_A_ROBOT -> AchievementRobotSilver
    Achievement.MUSICAL_CHAIRS -> AchievementMusicGold
    Achievement.SIDEWAYS -> AchievementSidewaysTeal
    Achievement.TIME_FLIES -> AchievementClockAmber
    Achievement.WHATS_IN_A_NAME -> AchievementInkBlue
    Achievement.WHOS_LUDO -> AchievementBadgeViolet
    else -> tint
}

/** What every locked row shows in place of its icon, so none gives itself away. */
val LOCKED_ACHIEVEMENT_ICON: ImageVector = Symbols.QuestionMark

/** A pale gold crescent moon with two white stars beside it, each its own fixed fill - see [Achievement.MIDNIGHT_RUN]. */
@Composable
private fun rememberMidnightIcon(): ImageVector = remember {
    ImageVector.Builder(name = "Midnight", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        // The crescent: round the outside of a circle about (11, 12), back along the inside of one about (15, 9).
        addPath(addPathNodes("M10.04,4.06A8,8 0 1,0 18.36,15.14A7,7 0 0,1 10.04,4.06Z"), fill = SolidColor(MidnightIconMoon))
        // Two four-pointed stars.
        for ((cx, cy, r) in listOf(Triple(18.6f, 4.4f, 2.6f), Triple(21f, 10.4f, 1.7f))) {
            val waist = r * 0.3f
            addPath(
                addPathNodes("M$cx,${cy - r}L${cx + waist},${cy - waist}L${cx + r},${cy}L${cx + waist},${cy + waist}L$cx,${cy + r}L${cx - waist},${cy + waist}L${cx - r},${cy}L${cx - waist},${cy - waist}Z"),
                fill = SolidColor(MidnightIconStar),
            )
        }
    }.build()
}

private val MidnightIconMoon = Color(0xFFFFE08A)
private val MidnightIconStar = Color(0xFFFFFFFF)

/**
 * The star badge on the corner of an achievement's icon square (row and unlock banner) when earning it also
 * unlocks a style ([unlocksStyle][net.zodac.ludoplus.ui.style.unlocksStyle]) - a cue that there's
 * something extra waiting on the Styles screen. Drawn over the icon's corner rather than beside the text, so it takes
 * no text width and can't make a title or description wrap. Decorative: what it says is spoken by the
 * row's action ("Show which style this unlocks") and the banner's announcement ("Unlocks a style").
 */
@Composable
fun BoxScope.StyleRewardStar(tint: Color) {
    Icon(
        imageVector = Symbols.Star,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.align(Alignment.TopEnd).offset(x = STAR_BADGE_OFFSET, y = -STAR_BADGE_OFFSET).size(STAR_BADGE_SIZE),
    )
}

private val STAR_BADGE_SIZE = 16.dp
private val STAR_BADGE_OFFSET = 6.dp
