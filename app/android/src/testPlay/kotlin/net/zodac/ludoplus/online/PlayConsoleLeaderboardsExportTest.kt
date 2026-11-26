package net.zodac.ludoplus.online

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.ui.achievements.icon
import net.zodac.ludoplus.ui.achievements.iconTintOrUnspecified
import net.zodac.ludoplus.ui.theme.LudoPlusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** One Play leaderboard: [mode]'s best wins with [playerCount] players, by its ranking. */
private data class Board(val mode: GameMode, val playerCount: Int) {
    val key: String get() = mode.onlineLeaderboardKey(playerCount)
}

/** Every board, in Play's list order: each mode in New Game's order, its player counts in order. */
private val BOARDS = GameMode.entries.flatMap { mode -> (BoardGeometry.MIN_PLAYERS..BoardGeometry.MAX_PLAYERS).map { Board(mode, it) } }

/**
 * The achievement whose icon marks the mode's boards, so the two always match: the mode's own win, or for a mode with none
 * (Classic), First Victory.
 */
private val GameMode.leaderboardAchievement: Achievement
    get() = Achievement.entries.singleOrNull { it.isModeWin && it.mode == this } ?: Achievement.FIRST_VICTORY

/**
 * Play Console's side of the leaderboards, one per mode and player count ([playLeaderboardKeys]), which are made there
 * by hand (it has no import for them): the rules each keeps to there, checked on every run, and (with
 * `-PexportPlayConsole`, which `scripts/export-play-console.sh` passes) the sheet to make them from and their icons,
 * written to `leaderboards/` in the gitignored `assets/play-console/` folder.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w600dp-h900dp-mdpi")
class PlayConsoleLeaderboardsExportTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the boards are the ones the app sends - each name within Play's limit`() {
        assertEquals(playLeaderboardKeys, BOARDS.map { it.key })
        val strings = stringsOf(Locale.ENGLISH)
        for (board in BOARDS) assertTrue(board.key, name(board, strings).length <= MAX_NAME_LENGTH)
        assertEquals("Classic, 4 players", name(Board(GameMode.CLASSIC, 4), strings))
    }

    @Test
    fun `every board has an icon of its own`() {
        var shown by mutableStateOf(BOARDS.first())
        compose.setContent { IconTile(shown, CHECK_SIZE) }
        val drawn = BOARDS.associateWith { board ->
            shown = board
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag(ICON_TAG).captureToImage().asAndroidBitmap()
            IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }.toList()
        }
        val sameIcon = drawn.entries.groupBy({ it.value }, { it.key.key }).values.filter { it.size > 1 }
        assertEquals("Boards sharing an icon", emptyList<List<String>>(), sameIcon)
    }

    /**
     * A board's Play icon: its mode's win achievement's icon over its player count in a circle ("4p"), on the achievements' tile,
     * [size] square.
     */
    @Composable
    private fun IconTile(board: Board, size: Int) {
        LudoPlusTheme {
            val colours = MaterialTheme.colorScheme
            Box(Modifier.size(size.dp).background(colours.primaryContainer).testTag(ICON_TAG), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val achievement = board.mode.leaderboardAchievement
                    Icon(
                        imageVector = achievement.icon,
                        contentDescription = null,
                        tint = achievement.iconTintOrUnspecified(colours.onPrimaryContainer),
                        modifier = Modifier.size((size * GLYPH_SIZE / ICON_SIZE).dp),
                    )
                    Spacer(Modifier.size((size * GAP_SIZE / ICON_SIZE).dp))
                    Box(
                        Modifier.size((size * CIRCLE_SIZE / ICON_SIZE).dp).background(colours.onPrimaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${board.playerCount}p", // i18n: Play Console art, English alone
                            color = colours.primaryContainer,
                            fontWeight = FontWeight.Bold,
                            fontSize = (size * COUNT_SIZE / ICON_SIZE).sp,
                            lineHeight = (size * COUNT_SIZE / ICON_SIZE).sp,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `export the sheet and icons for Play Console`() {
        assumeTrue("Only with -PexportPlayConsole", exportingPlayConsole)
        val dir = playConsoleDir().resolve("leaderboards")
        val icons = dir.resolve("leaderboard-icons").apply { deleteRecursively(); mkdirs() }
        writeSheet(dir.resolve(timestamped("leaderboards.csv")))

        var shown by mutableStateOf(BOARDS.first())
        compose.setContent { IconTile(shown, ICON_SIZE) }
        for (board in BOARDS) {
            shown = board
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag(ICON_TAG).captureToImage().asAndroidBitmap()
            assertEquals(ICON_SIZE, bitmap.width)
            icons.resolve("${board.key}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /**
     * One row per leaderboard, in Play's list order, with everything Play Console asks for: a whole number, by its mode's
     * ranking (the turns of a win, smaller is better, from 1 up; or the squares travelled or held, larger is better, from
     * 0 up), with tamper protection on.
     */
    private fun writeSheet(file: File) {
        val text = LOCALES.associateWith(::stringsOf)
        val header = listOf("list_order", "key", "format", "ordering", "lower_limit", "upper_limit", "tamper_protection", "icon") +
            LOCALES.map { "name_${it.code}" }
        val rows = BOARDS.mapIndexed { index, board ->
            listOf(
                (index + 1).toString(),
                board.key,
                "Numeric, 0 decimal places",
                if (board.mode.ranking.largerIsBetter) "Larger is better" else "Smaller is better",
                if (board.mode.ranking.largerIsBetter) "0" else "1",
                "",
                "On",
                "leaderboard-icons/${board.key}.png",
            ) + LOCALES.map { name(board, text.getValue(it)) }
        }
        file.writeText(csvLines(listOf(header) + rows))
    }

    /** The board's name on Play: its mode's, and its player count ("Classic, 4 players"). Play Console text, not the app's. */
    private fun name(board: Board, strings: Map<String, String>): String =
        "${strings.getValue(board.mode.displayName.key)}, ${board.playerCount} players" // i18n: Play Console text, English alone

    private companion object {
        const val MAX_NAME_LENGTH = 100

        /** Play's icons are 512px square: the icon, and the count in a circle under it, on the achievements' tile. */
        const val ICON_SIZE = 512
        const val GLYPH_SIZE = 232
        const val GAP_SIZE = 20
        const val CIRCLE_SIZE = 176
        const val COUNT_SIZE = 92

        /** Big enough to tell any two glyphs apart. */
        const val CHECK_SIZE = 64
        const val ICON_TAG = "leaderboard-icon"
    }
}
