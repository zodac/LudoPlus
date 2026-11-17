package net.zodac.ludo.ui.achievements

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import net.zodac.ludo.model.Achievement
import net.zodac.ludo.ui.icons.Symbols
import net.zodac.ludo.ui.theme.AchievementDeveloperCyan
import net.zodac.ludo.ui.theme.AchievementHeartRed

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
        Achievement.GETTING_COMFORTABLE -> Symbols.Numbers
        Achievement.ON_A_ROLL -> Symbols.ModeHeat
        Achievement.REGULAR -> Symbols.EventRepeat
        Achievement.HALL_OF_FAMER -> Symbols.EmojiEvents
        Achievement.CENTURION -> Symbols.MilitaryTech
        Achievement.WELL_ROLLED -> Symbols.Casino
        // A crown for ten wins in a row: a flame would be too close to On A Roll's.
        Achievement.UNSTOPPABLE -> Symbols.Crown
        Achievement.LIVING_LEGEND -> Symbols.WorkspacePremium

        // ---- Captures -----------------------------------------------------------------------
        Achievement.GOTCHA -> Symbols.Target
        // Two stacked, for two pawns with one move.
        Achievement.TWO_FOR_ONE -> Symbols.FilterNone
        Achievement.HAT_TRICK -> Symbols.Looks3
        // Justice done, on the very next turn.
        Achievement.PAYBACK -> Symbols.Gavel
        Achievement.NOT_SO_FAST -> Symbols.Block
        // The whole world: every opponent.
        Achievement.NOBODY_IS_SAFE -> Symbols.Public
        // Shown the door.
        Achievement.BOUNCER -> Symbols.Logout
        Achievement.PUBLIC_ENEMY -> Symbols.Skull

        // ---- Racing -------------------------------------------------------------------------
        Achievement.HOME_SWEET_HOME -> Symbols.House
        Achievement.ALL_HANDS_ON_DECK -> Symbols.WavingHand
        // A runner on a lap of the board.
        Achievement.LAP_OF_HONOUR -> Symbols.Sprint
        Achievement.FREQUENT_TRAVELLER -> Symbols.HomeWork
        Achievement.GLOBETROTTER -> Symbols.TravelExplore

        // ---- Winning ------------------------------------------------------------------------
        Achievement.CLEAN_SWEEP -> Symbols.DeleteSweep
        Achievement.COMEBACK_KID -> Symbols.TrendingUp
        Achievement.FIRST_VICTORY -> Symbols.Flag
        Achievement.HOW_DO_YOU_DO_FELLOW_CPUS -> Symbols.Memory
        Achievement.LAST_HUMAN_STANDING -> Symbols.EmojiPeople
        Achievement.PACIFIST -> Symbols.ThumbUp
        Achievement.PHOTO_FINISH -> Symbols.PhotoCamera
        Achievement.SPEED_DEMON -> Symbols.Bolt
        // Locked away, out of reach: never captured.
        Achievement.UNTOUCHABLE -> Symbols.Lock

        // ---- Misfortune ---------------------------------------------------------------------
        Achievement.BACK_TO_SQUARE_ONE -> Symbols.RestartAlt
        // Drawn for this app: a trophy cracked in two - the win that broke at the last.
        Achievement.DEFEAT_FROM_THE_JAWS_OF_VICTORY -> CRACKED_TROPHY_ICON
        Achievement.I_ROBOT -> Symbols.Android
        Achievement.LOCKED_OUT -> Symbols.HourglassEmpty
        Achievement.PIPPED_TO_THE_POST -> Symbols.Timer
        Achievement.PUNCHING_BAG -> Symbols.SentimentDissatisfied
        Achievement.SITTING_DUCKS -> Symbols.Cancel
        Achievement.TOO_MUCH_OF_A_GOOD_THING -> Symbols.Looks6
        Achievement.WOODEN_SPOON -> Symbols.TrendingDown

        // ---- Miscellaneous (hidden) ---------------------------------------------------------
        Achievement.BUDDY_SYSTEM -> Symbols.Groups
        Achievement.DEJA_VU -> Symbols.Repeat
        Achievement.INCH_BY_INCH -> Symbols.SlowMotionVideo
        Achievement.SO_NEAR_YET_SO_FAR -> Symbols.SentimentNeutral
        Achievement.WHO_MADE_THIS -> Symbols.Info

        // ---- Collection ---------------------------------------------------------------------
        // Drawn for this app: the trophy in crystal, for the last of them all.
        Achievement.COMPLETIONIST -> CRYSTAL_TROPHY_ICON

        // ---- Easter Eggs --------------------------------------------------------------------
        // Every icon in this category is a fixed colour rather than the ambient tint - see [iconTintOrUnspecified].
        Achievement.ARE_YOU_A_DEVELOPER -> Symbols.DeveloperMode
        Achievement.BIG_FAN -> Symbols.Favorite
        // A crescent moon and two stars, for the small hours it was earned in.
        Achievement.MIDNIGHT_RUN -> rememberMidnightIcon()
    }

/**
 * [tint] as given, except for the fixed-colour icons: every Easter Egg means a colour of its own, not whatever
 * container holds it (Big Fan's heart is always [AchievementHeartRed]; [Color.Unspecified] shows a drawn icon's own
 * fills), and so does Completionist's crystal.
 */
fun Achievement.iconTintOrUnspecified(tint: Color): Color = when (this) {
    Achievement.COMPLETIONIST -> Color.Unspecified
    Achievement.ARE_YOU_A_DEVELOPER -> AchievementDeveloperCyan
    Achievement.BIG_FAN -> AchievementHeartRed
    Achievement.MIDNIGHT_RUN -> Color.Unspecified
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
