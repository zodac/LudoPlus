package net.zodac.ludoplus.ui.achievements

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.ui.icons.Symbols
import net.zodac.ludoplus.ui.theme.BoardInk
import net.zodac.ludoplus.ui.theme.CrystalTrophyCyan
import net.zodac.ludoplus.ui.theme.CrystalTrophyIce
import net.zodac.ludoplus.ui.theme.CrystalTrophyViolet
import net.zodac.ludoplus.ui.theme.CrystalTrophyWhite
import net.zodac.ludoplus.ui.theme.GooglyEyePupil
import net.zodac.ludoplus.ui.theme.GooglyEyeRim
import net.zodac.ludoplus.ui.theme.GooglyEyeWhite
import net.zodac.ludoplus.ui.theme.PortalColours
import net.zodac.ludoplus.ui.theme.color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

/** An icon on a 24-unit grid, of one [path] whose overlaps are cut out. */
private fun cutOutIcon(name: String, path: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addPath(addPathNodes(path), fill = INK, pathFillType = PathFillType.EvenOdd)
        .build()

/**
 * A side-profile sports car and the trail of fire it leaves: an original, generic silhouette of a low
 * wedge with a fastback rear, no badge or logo, for the film the title Where We're Going, We Don't Need Rules quotes. Its
 * subpaths: the body (both wheel arches cut into its underside), the window, the wheels, then the fire.
 */
internal val TIME_MACHINE_CAR_ICON: ImageVector by lazy {
    cutOutIcon(
        "TimeMachineCar",
        "M3.8,16.2 L3.8,13.2 L5.2,12.5 L10.8,10.2 L14.2,10.2 L17,12.5 L23.5,13.5 L23.5,15.6 L22.9,16.2 L21.8,16.2 A2.4,2.4 0,0 0,17 16.2 L9.8,16.2 " +
            "A2.4,2.4 0,0 0,5 16.2 Z M11.1,10.9 L13.9,10.9 L15.6,12.3 L9.4,12.3 Z M5.5,16.4 A1.9,1.9 0,1 0,9.3 16.4 A1.9,1.9 0,1 0,5.5 16.4 Z " +
            "M17.5,16.4 A1.9,1.9 0,1 0,21.3 16.4 A1.9,1.9 0,1 0,17.5 16.4 Z M5.6,18.3 L0.2,18.3 L0.2,17.6 Q0.6,16.4 0.4,15.2 Q1.9,16.3 1.9,17.4 " +
            "Q2.5,16.4 2.3,15.8 Q3.6,16.6 3.7,17.5 Q4.2,17.5 4.3,17 Q4.9,17.6 5.6,17.7 Z",
    )
}

/** Two dice, corner to corner, showing the same three. For Loaded Dice, the same number again and again. */
internal val LOADED_DICE_ICON: ImageVector by lazy {
    fun pip(x: Float, y: Float) = "M${x - DIE_PIP},${y}a$DIE_PIP,$DIE_PIP 0 1,0 ${2 * DIE_PIP},0a$DIE_PIP,$DIE_PIP 0 1,0 ${-2 * DIE_PIP},0Z"
    fun die(x: Float, y: Float) = roundSquare(x, y, DIE_SIDE, corner = 2f) + listOf(0.24f, 0.5f, 0.76f).joinToString("") { pip(x + DIE_SIDE * it, y + DIE_SIDE * it) }
    cutOutIcon("LoadedDice", die(0.5f, 0.5f) + die(12.3f, 12.3f))
}

private const val DIE_SIDE = 11.2f
private const val DIE_PIP = 1.35f

/** A ghost as Haunted House's are, its eyes and mouth cut out. For Living To Tell The Tale. */
internal val GHOST_ICON: ImageVector by lazy {
    cutOutIcon(
        "Ghost",
        "M4.5,21.5 L4.5,10 C4.5,0 19.5,0 19.5,10 L19.5,21.5 Q17,16.8 14.5,21.5 Q12,16.8 9.5,21.5 Q7,16.8 4.5,21.5 Z " +
            "M7.6,9.6 A1.5,2 0,1 0,10.6 9.6 A1.5,2 0,1 0,7.6 9.6 Z M13.4,9.6 A1.5,2 0,1 0,16.4 9.6 A1.5,2 0,1 0,13.4 9.6 Z M10.6,14.4 A1.4,1.2 0,1 0,13.4 14.4 A1.4,1.2 0,1 0,10.6 14.4 Z",
    )
}

/** One die showing its 6, the pips cut out. For Six Pack, 30 6s in one game. */
internal val SIX_DIE_ICON: ImageVector by lazy { cutOutIcon("SixDie", sixFace(2f, 2f, 20f)) }

/** Three dice in a triangle, each showing 6, the pips cut out. For Too Much Of A Good Thing, three 6s and the turn lost. */
internal val THREE_SIXES_ICON: ImageVector by lazy {
    cutOutIcon("ThreeSixes", sixFace(6.6f, 0.8f, 10.8f) + sixFace(0.8f, 12.4f, 10.8f) + sixFace(12.4f, 12.4f, 10.8f))
}

/** A die [side] across from ([x], [y]) showing 6, its pips to be cut out. */
private fun sixFace(x: Float, y: Float, side: Float): String =
    roundSquare(x, y, side, corner = side * 0.2f) +
        listOf(0.3f, 0.7f).flatMap { across -> listOf(0.24f, 0.5f, 0.76f).map { down -> circle(x + side * across, y + side * down, side * 0.09f) } }
            .joinToString("")

/**
 * Four of Material Symbols' chess pawns in single file, the front one well ahead of the rest. For One By One, each pawn
 * brought home before the next leaves the yard.
 */
internal val SINGLE_FILE_ICON: ImageVector by lazy {
    val pawn = Symbols.ChessPawn.pathNodes()
    ImageVector.Builder(name = "SingleFile", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        for (x in listOf(-1.2f, 3.4f, 8f, 15.4f)) {
            addGroup(translationX = x, translationY = 8.5f, scaleX = PAWN_SCALE, scaleY = PAWN_SCALE)
            addGroup(translationY = 960f)
            addPath(pawn, fill = INK)
            clearGroup()
            clearGroup()
        }
    }.build()
}

private const val PAWN_SCALE = 0.0105f

/**
 * One die showing 2, its pips cut out, over two of Material Symbols' chess pawns knocked over on their sides, heads
 * together. For Two Birds, Two Stones, a capture with each die of one roll.
 */
internal val DIE_AND_TWO_PAWNS_ICON: ImageVector by lazy {
    val pawn = Symbols.ChessPawn.pathNodes()
    ImageVector.Builder(name = "DieAndTwoPawns", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        addPath(
            addPathNodes(roundSquare(7.2f, 0.6f, 9.6f, corner = 2f) + circle(9.8f, 3.2f, 1.05f) + circle(14.2f, 7.6f, 1.05f)),
            fill = INK,
            pathFillType = PathFillType.EvenOdd,
        )
        for ((x, turn) in listOf(-2.2f to 90f, 12.2f to -90f)) {
            val half = 480f * TIPPED_PAWN_SCALE
            addGroup(rotate = turn, pivotX = x + half, pivotY = TIPPED_PAWN_TOP + half)
            addGroup(translationX = x, translationY = TIPPED_PAWN_TOP, scaleX = TIPPED_PAWN_SCALE, scaleY = TIPPED_PAWN_SCALE)
            addGroup(translationY = 960f)
            addPath(pawn, fill = INK)
            clearGroup()
            clearGroup()
            clearGroup()
        }
    }.build()
}

private const val TIPPED_PAWN_SCALE = 0.0145f
private const val TIPPED_PAWN_TOP = 10.2f

/** Every path's data in this icon, whatever group it sits in. */
private fun ImageVector.pathNodes(): List<PathNode> {
    fun VectorGroup.nodes(): List<PathNode> = flatMap { node ->
        when (node) {
            is VectorGroup -> node.nodes()
            is VectorPath -> node.pathData
        }
    }
    return root.nodes()
}

/**
 * A generic caped hero, hands on hips and a star cut into the chest, the cape flaring out either side. For One Of
 * Everything, every power-up taken in one game.
 */
internal val CAPED_HERO_ICON: ImageVector by lazy {
    val star = (0 until 10).joinToString("", "M", "Z") { i ->
        val r = if (i % 2 == 0) 1.6f else 0.65f
        val angle = (-90.0 + i * 36) * PI / 180
        "${12 + r * cos(angle).toFloat()},${11 + r * sin(angle).toFloat()}" + if (i < 9) "L" else ""
    }
    val figure = "M8.6,7.6L15.4,7.6C16.2,7.6 16.7,8.1 17,8.8L18.4,12L14.7,14.2L15,22.4L12.9,22.4L12,16.6L11.1,22.4L9,22.4L9.3,14.2" +
        "L5.6,12L7,8.8C7.3,8.1 7.8,7.6 8.6,7.6Z" + "M14.6,10.2L16.2,11.7L14.6,12.6Z" + "M9.4,10.2L7.8,11.7L9.4,12.6Z" + circle(12f, 4.2f, 2.6f) + star
    val cape = "M6.4,13.4L8.5,15L8.1,21.2L3.4,21.8Z" + "M17.6,13.4L15.5,15L15.9,21.2L20.6,21.8Z"
    cutOutIcon("CapedHero", figure + cape)
}

/** A wooden spoon on the diagonal, its bowl at the top right. For Wooden Spoon, last place. */
internal val WOODEN_SPOON_ICON: ImageVector by lazy {
    ImageVector.Builder(name = "WoodenSpoon", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .addGroup(rotate = 45f, pivotX = 12f, pivotY = 12f)
        .addPath(
            addPathNodes(
                "M12,1.2C15.2,1.2 16.6,4 16.6,6.6C16.6,9.4 14.8,11.2 13.2,11.8L13.3,21.4A1.3,1.3 0,0 1,10.7 21.4L10.8,11.8" +
                    "C9.2,11.2 7.4,9.4 7.4,6.6C7.4,4 8.8,1.2 12,1.2Z",
            ),
            fill = INK,
        )
        .clearGroup()
        .build()
}

/** Two ducks side by side, facing left, each eye cut out. For Sitting Ducks, two pawns captured with one move. */
internal val DUCKS_ICON: ImageVector by lazy {
    fun duck(x: Float, y: Float) =
        "M${x + 0.4f},${y + 8.6f}L${x + 2.7f},${y + 7.5f}A2.45,2.45 0,1 1,${x + 7.2f} ${y + 9.2f}L${x + 7.6f},${y + 11f}" +
            "C${x + 8.6f},${y + 11.1f} ${x + 10.2f},${y + 10.6f} ${x + 11.4f},${y + 9.2f}C${x + 11.9f},${y + 11f} ${x + 11.6f},${y + 13.5f} ${x + 10.6f},${y + 15f}" +
            "C${x + 9.6f},${y + 16.3f} ${x + 8f},${y + 16.8f} ${x + 6.4f},${y + 16.8f}C${x + 4.4f},${y + 16.8f} ${x + 3.2f},${y + 14.8f} ${x + 3.4f},${y + 12.6f}" +
            "C${x + 3.5f},${y + 11.6f} ${x + 3.9f},${y + 10.8f} ${x + 3.4f},${y + 9.7f}L${x + 2.8f},${y + 9.4f}Z" + circle(x + 5.3f, y + 7.4f, 0.6f)
    cutOutIcon("Ducks", duck(0f, 1.5f) + duck(11.8f, 1.5f))
}

/**
 * Two dice thrown side by side, tilted apart, showing 5 and 3, the pips cut out. For Double Or Nothing, the win in Two
 * Dice, and Two Dice's leaderboards.
 */
internal val TWO_DICE_ICON: ImageVector by lazy {
    val five = listOf(0.27f to 0.27f, 0.73f to 0.27f, 0.5f to 0.5f, 0.27f to 0.73f, 0.73f to 0.73f)
    val three = listOf(0.27f to 0.73f, 0.5f to 0.5f, 0.73f to 0.27f)
    ImageVector.Builder(name = "TwoDice", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        for ((centre, turn, pips) in listOf(Triple(Offset(6.1f, 15.3f), -15f, five), Triple(Offset(17.9f, 8.7f), 12f, three))) {
            val x = centre.x - THROWN_DIE_SIDE / 2
            val y = centre.y - THROWN_DIE_SIDE / 2
            val face = roundSquare(x, y, THROWN_DIE_SIDE, corner = THROWN_DIE_SIDE * 0.2f) +
                pips.joinToString("") { (across, down) -> circle(x + THROWN_DIE_SIDE * across, y + THROWN_DIE_SIDE * down, THROWN_DIE_SIDE * 0.1f) }
            addGroup(rotate = turn, pivotX = centre.x, pivotY = centre.y)
            addPath(addPathNodes(face), fill = INK, pathFillType = PathFillType.EvenOdd)
            clearGroup()
        }
    }.build()
}

private const val THROWN_DIE_SIDE = 9.8f

private fun circle(cx: Float, cy: Float, r: Float): String = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0Z"

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

/** An icon on a 24-unit grid of squares [side] across with rounded corners, each at its top left in its player's colour. */
private fun squaresIcon(name: String, side: Float, corner: Float, squares: List<Pair<Offset, PlayerColour>>): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        for ((at, colour) in squares) addPath(addPathNodes(roundSquare(at.x, at.y, side, corner)), fill = SolidColor(colour.color))
    }.build()

private fun roundSquare(x: Float, y: Float, side: Float, corner: Float): String {
    val edge = side - 2 * corner
    return "M${x + corner},${y}h${edge}a$corner,$corner 0 0,1 $corner,${corner}v${edge}a$corner,$corner 0 0,1 ${-corner},${corner}" +
        "h${-edge}a$corner,$corner 0 0,1 ${-corner},${-corner}v${-edge}a$corner,$corner 0 0,1 $corner,${-corner}Z"
}

/** Nine squares, all red but a blue and a yellow one. For Paint The Town Red, the win in Colour Rush. */
internal val RED_SQUARES_ICON: ImageVector by lazy {
    val others = mapOf(2 to PlayerColour.BLUE, 6 to PlayerColour.YELLOW)
    squaresIcon("RedSquares", side = 6.6f, corner = 1f, (0..8).map { Offset(1.5f + it % 3 * 7.2f, 1.5f + it / 3 * 7.2f) to (others[it] ?: PlayerColour.RED) })
}

/** A corner of the track, its eight squares red, round four of the other players'. For Colour Coordinated, eight squares in a row. */
internal val TRACK_CORNER_ICON: ImageVector by lazy {
    val row = listOf(0 to 4, 0 to 3, 0 to 2, 0 to 1, 0 to 0, 1 to 0, 2 to 0, 3 to 0).map { it to PlayerColour.RED }
    val others = listOf((2 to 2) to PlayerColour.GREEN, (3 to 2) to PlayerColour.YELLOW, (2 to 3) to PlayerColour.BLUE, (3 to 3) to PlayerColour.GREEN)
    squaresIcon("TrackCorner", side = 4.05f, corner = 0.7f, (row + others).map { (at, colour) -> Offset(3.15f + at.first * 4.55f, 0.87f + at.second * 4.55f) to colour })
}

/**
 * A googly eye: a white eye with a dark rim, its black pupil slid down to the lower right with a glint in it. For
 * Everything Everywhere All At Once, the win with every house rule piled on.
 */
internal val GOOGLY_EYE_ICON: ImageVector by lazy {
    ImageVector.Builder(name = "GooglyEye", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        addPath(addPathNodes(circle(12f, 12f, 10f)), fill = SolidColor(GooglyEyeWhite), stroke = SolidColor(GooglyEyeRim), strokeLineWidth = 1.1f)
        addPath(addPathNodes(circle(15.2f, 15.4f, 4.2f)), fill = SolidColor(GooglyEyePupil))
        addPath(addPathNodes(circle(16.6f, 13.6f, 1.1f)), fill = SolidColor(GooglyEyeWhite))
    }.build()
}

/**
 * A portal as Portals mode draws its first pair's: a violet disc darkening to its middle, ringed in ink and white, three
 * white arms swirling out from an ink middle with a white triangle in it. For Through The Looking Glass, the win in
 * Portals, and Portals' leaderboards.
 */
internal val PORTAL_ICON: ImageVector by lazy {
    val tone = PortalColours[0]
    val centre = Offset(12f, 12f)
    val triangle = (0..<3).joinToString("L", "M", "Z") { corner ->
        val angle = -PI / 2 + 2 * PI * corner / 3
        "${12f + cos(angle).toFloat() * 2.5f},${12.3f + sin(angle).toFloat() * 2.5f}"
    }
    ImageVector.Builder(name = "Portal", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
        addPath(addPathNodes(circle(12f, 12f, 11.6f)), fill = SolidColor(Color.White))
        addPath(addPathNodes(circle(12f, 12f, 10.8f)), fill = SolidColor(BoardInk))
        addPath(addPathNodes(circle(12f, 12f, 10f)), fill = Brush.radialGradient(listOf(lerp(tone, Color.Black, 0.75f), tone), centre, 10f))
        addPath(addPathNodes((0..<PORTAL_ARMS).joinToString("") { arm -> swirlArm(arm * 2 * PI / PORTAL_ARMS) }), fill = SolidColor(Color.White))
        addPath(addPathNodes(circle(12f, 12f, 5f)), fill = SolidColor(BoardInk))
        addPath(addPathNodes(triangle), fill = SolidColor(Color.White))
    }.build()
}

private const val PORTAL_ARMS = 3
private const val PORTAL_ARM_STEPS = 16

/** One arm of [PORTAL_ICON]'s swirl from angle [start], out from the middle as it turns, thinning to its tip. */
private fun swirlArm(start: Double): String {
    fun point(along: Float, side: Float): String {
        val angle = start + along * 2.2
        val radius = 4.6f + 4.4f * along + side * (1.5f - 0.9f * along) / 2
        return "${12f + cos(angle).toFloat() * radius},${12f + sin(angle).toFloat() * radius}"
    }
    val steps = (0..PORTAL_ARM_STEPS).map { it / PORTAL_ARM_STEPS.toFloat() }
    return "M" + (steps.map { point(it, 1f) } + steps.reversed().map { point(it, -1f) }).joinToString("L") + "Z"
}
