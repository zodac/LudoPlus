package net.zodac.ludo.ui.theme

import androidx.compose.ui.graphics.Color
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.PlayerColour

// ---------------------------------------------------------------------------------------------
// Material 3 colour roles.
//
// These are tonal-palette values, not hand-picked hexes: each palette holds one hue at a fixed
// chroma across the standard M3 tones (0-100, where the number is CIELAB lightness), and every
// role below is one tone from one palette, following M3's own dark-scheme role->tone mapping. That's
// what makes the contrast pairs (primary/onPrimary, surface/onSurface, ...) reliable rather than
// eyeballed.
//
// Generated (Google's material-color-utilities, HCT) from three brand seeds:
//   primary   #9C6BFF - violet: no player's colour, so "press this" never reads as "your turn"
//   secondary #1E2A4A - the navy of the board's surround
//   tertiary  #F1E4C3 - the ivory of the board's plain squares
// Neutrals are the navy held at low chroma (6, and 12 for the variants), so greys read slightly
// cool rather than dead.
//
// To re-generate after a seed change, use the same tonal-palette maths (or Material Theme Builder)
// - do not nudge individual values by hand, or the contrast guarantees go with it.
// ---------------------------------------------------------------------------------------------

val Primary = Color(0xFFD2BBFF)
val OnPrimary = Color(0xFF3E008E)
val PrimaryContainer = Color(0xFF581AB8)
val OnPrimaryContainer = Color(0xFFEADDFF)
val Secondary = Color(0xFFBAC5EE)
val OnSecondary = Color(0xFF232F50)
val SecondaryContainer = Color(0xFF3A4667)
val OnSecondaryContainer = Color(0xFFDAE2FF)
val Tertiary = Color(0xFFD2C6A6)
val OnTertiary = Color(0xFF37301A)
val TertiaryContainer = Color(0xFF4E462E)
val OnTertiaryContainer = Color(0xFFEFE2C1)
val Background = Color(0xFF121318)
val OnBackground = Color(0xFFE3E2E9)
val Surface = Color(0xFF121318)
val OnSurface = Color(0xFFE3E2E9)
val SurfaceVariant = Color(0xFF434654)
val OnSurfaceVariant = Color(0xFFC3C6D6)
val SurfaceDim = Color(0xFF121318)
val SurfaceBright = Color(0xFF38393F)
val SurfaceContainerLowest = Color(0xFF0D0E13)
val SurfaceContainerLow = Color(0xFF1A1B21)
val SurfaceContainer = Color(0xFF1E1F25)
val SurfaceContainerHigh = Color(0xFF292A2F)
val SurfaceContainerHighest = Color(0xFF33343A)
val Outline = Color(0xFF8D909F)
val OutlineVariant = Color(0xFF434654)
val InverseSurface = Color(0xFFE3E2E9)
val InverseOnSurface = Color(0xFF2F3036)
val InversePrimary = Color(0xFF703CD1)

// M3's baseline error palette, used as-is: there's no reason to brand "something went wrong".
val Error = Color(0xFFF2B8B5)
val OnError = Color(0xFF601410)
val ErrorContainer = Color(0xFF8C1D18)
val OnErrorContainer = Color(0xFFF9DEDC)

/**
 * What a [PlayerColour] looks like - the one place a player's colour is turned into a [Color], so the setup screen's
 * swatch, the board's yard and home column, and anything else marking that player all change together. Each is a
 * light, mid-chroma tone of the classic board's colour that reads on the dark page; keep a new one that way and clear
 * of the others (and of [Primary]).
 */
val PlayerColour.color: Color
    get() = when (this) {
        PlayerColour.RED -> Color(0xFFF2675F)
        PlayerColour.GREEN -> Color(0xFF5CE38F)
        PlayerColour.YELLOW -> Color(0xFFFFD54F)
        PlayerColour.BLUE -> Color(0xFF6C9CFF)
    }

/**
 * Each [GameMode]'s own colour: the one place a mode is turned into a [Color], so anything marking a mode shows the
 * same one. Classic, the main mode, takes the brand violet ([Primary]). A new mode needs one (the `when` won't compile
 * without it): a light tone that reads on the dark page, clear of the others and of red (`error`).
 */
val GameMode.color: Color
    get() = when (this) {
        GameMode.CLASSIC -> Primary
    }

// ---------------------------------------------------------------------------------------------
// The game table: the board, pawns, and die. Not colour roles (UI.md, "Two visual systems"); the
// players' colours come from PlayerColour.color.
// ---------------------------------------------------------------------------------------------

/** The board's plain squares, and the inside of each yard: the brand's ivory seed. */
val BoardIvory = Color(0xFFF1E4C3)

/** The lines between squares, the star squares' stars, and a pawn's outline: the brand's navy seed. */
val BoardInk = Color(0xFF1E2A4A)

/** A yard, home column, or start square with nobody sitting there (fewer than four players). */
val BoardEmptySeat = Color(0xFFB9B2A0)

/** The ivory die, light at its top-left to shaded at its bottom-right (DiceFive's classic die). */
val IvoryDiceTop = Color(0xFFFFFCF3)
val IvoryDiceBottom = Color(0xFFD9C9A3)

/** The die's pips. */
val DicePipColor = Color(0xFF2B2118)

/** The results page's trophy and fireworks. */
val GoldAccent = Color(0xFFFFCC55)
val GoldAccentDim = Color(0xFFB8862A)
val CupRimGold = Color(0xFFC79A4B)

/** The Leaderboard's second and third places; the first is [GoldAccent]. */
val LeaderboardSilver = Color(0xFFC9D1DC)
val LeaderboardBronze = Color(0xFFD79A6A)

// Achievement icons in fixed colours (ui/achievements/AchievementIcons.kt): M3 has no role for "this heart is red".
/** Big Fan's heart. */
val AchievementHeartRed = Color(0xFFE53935)

/** Are You A Developer?'s icon: Android's developer cyan. */
val AchievementDeveloperCyan = Color(0xFF3FE6FF)

/** I Am Not A Robot's toy robot: brushed silver. */
val AchievementRobotSilver = Color(0xFFC9CED6)

/** Musical Chairs' note: brass gold. */
val AchievementMusicGold = Color(0xFFF2C14E)

/** Who's Ludo?'s name badge: the brand's own violet seed. */
val AchievementBadgeViolet = Color(0xFF9C6BFF)

/** Alliteration's letters and What's In A Name?'s signature: fountain-pen ink blue. */
val AchievementInkBlue = Color(0xFF6FA8FF)

/** Sideways' turning phone: teal. */
val AchievementSidewaysTeal = Color(0xFF4FD1C5)

/** Time Flies' clock: amber. */
val AchievementClockAmber = Color(0xFFFFB74D)

/** The Completionist trophy's crystal, top left to bottom right: white, ice, cyan, violet. */
val CrystalTrophyWhite = Color(0xFFFFFFFF)
val CrystalTrophyIce = Color(0xFFCDEFFF)
val CrystalTrophyCyan = Color(0xFF8EC5EC)
val CrystalTrophyViolet = Color(0xFF6F7FD8)

// Styles: the die and background art brought back from DiceFive (5423b6c), for ui.style. Deliberately not colour roles:
// the table is a rendered object, not app chrome. The navy felt is the default background.
val FeltNavyGlow = Color(0xFF2A5590)
val FeltNavyTop = Color(0xFF1C3D66)
val FeltNavyBottom = Color(0xFF0B1E3A)
// The Classic oak die and brown background: honey-oak with off-white pips (mid-tone enough that the gold held ring
// still stands off the face), and dark stained wood.
val BarrelDiceTop = Color(0xFFC8894A)
val BarrelDiceBottom = Color(0xFF7A4A22)
val BarrelDicePipColor = Color(0xFFFFF6E8)
val BarrelBackgroundTop = Color(0xFF3A2314)
val BarrelBackgroundBottom = Color(0xFF1C1009)
val FireBackgroundTop = Color(0xFF7A130D)
val FireBackgroundBottom = Color(0xFF3D0805)
// The Classic die's red, yellow, and blue (DieColourPalette): DiceFive's Tricolour dice. "Swatch" is the colour's dot
// on the Styles page.
val TricolourRedDiceTop = Color(0xFFEF5350)
val TricolourRedDiceBottom = Color(0xFF9A1B1B)
val TricolourRedPipColor = Color(0xFFFFF4EF)
// The yellow die's face and its own held ring have swapped shades from every other die: its face
// is drawn in GoldAccent/GoldAccentDim (the colour every OTHER die's held ring uses) and its own
// held ring uses what used to be its face colour instead - see TricolourYellowHeldRing and
// DieColourPalette.heldRing. The original same-hue pairing made a held yellow die's ring vanish
// against its own face.
val TricolourYellowDiceTop = GoldAccent
val TricolourYellowDiceBottom = GoldAccentDim
val TricolourYellowPipColor = Color(0xFF3A2A05)
val TricolourYellowHeldRing = Color(0xFFFFE66B)
val TricolourBlueDiceTop = Color(0xFF5C9CEB)
val TricolourBlueDiceBottom = Color(0xFF173E8C)
val TricolourBluePipColor = Color(0xFFF1F6FF)
val TricolourRedSwatch = Color(0xFFE53935)
val TricolourYellowSwatch = Color(0xFFFDD835)
val TricolourBlueSwatch = Color(0xFF1E88E5)
val IrishWhiteDiceBottom = Color(0xFFD8D8D8)
val IrishWhitePipColor = Color(0xFF262626)
val IrishGreenSwatch = Color(0xFF169B62)
val IrishWhiteSwatch = Color(0xFFFFFFFF)
val IrishOrangeSwatch = Color(0xFFFF883E)
