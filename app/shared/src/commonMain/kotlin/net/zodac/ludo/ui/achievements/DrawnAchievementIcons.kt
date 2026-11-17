package net.zodac.ludo.ui.achievements

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import net.zodac.ludo.ui.theme.CrystalTrophyCyan
import net.zodac.ludo.ui.theme.CrystalTrophyIce
import net.zodac.ludo.ui.theme.CrystalTrophyViolet
import net.zodac.ludo.ui.theme.CrystalTrophyWhite

// Achievement icons drawn for this app where Material Symbols has nothing that fits. Each is one colour, as a
// Symbol is - the row or banner tints it like any other - with any detail inside cut out of the shape rather than
// drawn in another colour; the few in fixed colours are together at the end. Shown at 22dp, so nothing finer than
// about a unit of the 24 grid survives.

private val INK = SolidColor(Color.Black)

// Material Symbols' trophy (emoji_events), on its own 960-unit grid, its y running from -960 at the top to 0.
private const val SYMBOLS_TROPHY =
    "M280-528v-152h-80v40q0 38 22 68.5t58 43.5Zm400 0q36-13 58-43.5t22-68.5v-40h-80v152ZM440-200v-124q-49-11-87.5-41.5T296-442q-75-9-125.5-65.5T120-640v-40q0-33 23.5-56.5T200-760h80q0-33 23.5-56.5T360-840h240q33 0 56.5 23.5T680-760h80q33 0 56.5 23.5T840-680v40q0 76-50.5 132.5T664-442q-18 46-56.5 76.5T520-324v124h120q17 0 28.5 11.5T680-160q0 17-11.5 28.5T640-120H320q-17 0-28.5-11.5T280-160q0-17 11.5-28.5T320-200h120Z"

/** An icon on Material Symbols' 960-unit grid: [build] draws with y from -960 (top) to 0, as Symbols' path data does. */
private fun symbolsGridIcon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 960f, viewportHeight = 960f)
        .addGroup(translationY = 960f)
        .apply(build)
        .clearGroup()
        .build()

/**
 * Material Symbols' trophy with a jagged crack down the cup from its rim, cut out of it. For Defeat From The Jaws Of
 * Victory, the lead that broke before the end.
 */
internal val CRACKED_TROPHY_ICON: ImageVector by lazy {
    symbolsGridIcon("CrackedTrophy") {
        val crack = "M467,-840L520,-760L430,-650L540,-530L455,-420L515,-420L600,-530L490,-650L580,-760L527,-840Z"
        addPath(addPathNodes(SYMBOLS_TROPHY + crack), fill = INK, pathFillType = PathFillType.EvenOdd)
    }
}

// ---- Fixed colours --------------------------------------------------------------------------------------------------
// Shown untinted (Achievement.iconTintOrUnspecified gives them Color.Unspecified).

/**
 * Material Symbols' trophy in crystal: white at the top left through ice and cyan to violet at the bottom right. For
 * Completionist, every other achievement unlocked.
 */
internal val CRYSTAL_TROPHY_ICON: ImageVector by lazy {
    symbolsGridIcon("CrystalTrophy") {
        val crystal = Brush.linearGradient(
            0f to CrystalTrophyWhite,
            0.35f to CrystalTrophyIce,
            0.65f to CrystalTrophyCyan,
            1f to CrystalTrophyViolet,
            start = Offset(120f, -840f),
            end = Offset(840f, -120f),
        )
        addPath(addPathNodes(SYMBOLS_TROPHY), fill = crystal)
    }
}
