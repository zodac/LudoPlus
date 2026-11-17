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
