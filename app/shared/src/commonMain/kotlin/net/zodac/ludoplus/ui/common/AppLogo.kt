package net.zodac.ludoplus.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.app_name
import net.zodac.ludoplus.resources.sora
import net.zodac.ludoplus.ui.theme.color
import org.jetbrains.compose.resources.Font

/**
 * The brand typeface: Sora (`composeResources/font/sora.ttf`), used on the logo wordmark here, on
 * [ScreenScaffold]'s page titles and every dialog title, and on the game's own marks: the scorecard
 * tiles' labels and Hit List targets, the corner badges (see `SegmentBadge`), the rolls left by the
 * cup, the scores on the player tabs and Game Over, the Leaderboard's mode-card titles, an
 * unlock banner's title, the labels (not the numbers) in the board's totals tooltip, and the
 * game mode and modifier names and values - on their picker pages and in the New Game fields that
 * open them - and the New Game screen's player count and You / User / CPU labels. This is a deliberate, narrow departure from stock M3 type ([UI.md]'s "no
 * typography overrides" rule is about the type *scale*, not a call site): these are brand marks,
 * not body text, so they earn their own face the same way the game board earns its own palette.
 *
 * Never for a player's name or anything else a player types: the font only covers ASCII and
 * Latin-1, so a name in any other script (or with an emoji) would come out in two faces.
 *
 * Not for a column of numbers either (the Leaderboard's scores, Statistics, the totals tooltip's
 * values): Sora's default digits are proportional - a "1" is narrower than a "0" - so right-aligned
 * numbers in it don't line up digit for digit. The system font's digits are all one width. Sora
 * does carry tabular digits (`fontFeatureSettings = "tnum"`) if a column ever has to be in it.
 *
 * The upstream font ships as a variable font (a 100-800 weight axis); since this app only ever
 * uses the bold instance, `sora.ttf` here is a static weight-700 instance produced with
 * `fontTools.varLib.instancer`, then subset with `fontTools.subset` (`layout_features=['*']`,
 * `name_IDs=['*']`) to U+0020-007E and U+00A0-00FF - printable ASCII plus Latin-1, for an
 * achievement title like "Déjà Vu" and the Hit List's "·" any-place mark. Variable-axis and
 * unused-script data was most of the original file's size. It is the only weight there is, so a
 * Sora call site asks for [FontWeight.Bold] - a lighter or heavier one would get Bold anyway.
 */
internal val SoraFontFamily: FontFamily
    @Composable
    get() = FontFamily(Font(Res.font.sora, weight = FontWeight.Bold))

/** The title style of a page's top bar: [SoraFontFamily], bold, `titleLarge` size. Its gold comes from the bar's colours. */
internal val pageTitleStyle: TextStyle
    @Composable
    get() = MaterialTheme.typography.titleLarge.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)

/** The title style of a dialog: [SoraFontFamily], bold, `headlineSmall` size, in the gold `primary`. */
internal val dialogTitleStyle: TextStyle
    @Composable
    get() = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )

/** The style of a category header on the Achievements and Styles pages, and its count: [SoraFontFamily], bold, `titleSmall` size, as written. */
internal val categoryHeaderStyle: TextStyle
    @Composable
    get() = MaterialTheme.typography.titleSmall.copy(fontFamily = SoraFontFamily, fontWeight = FontWeight.Bold)

/** Whether `sora.ttf` has a glyph for this character - printable ASCII or Latin-1, as cut (see [SoraFontFamily]). */
internal fun Char.isInSoraFont(): Boolean = this in ' '..'~' || this in '\u00A0'..'\u00FF'

/**
 * The app's mark on the main menu: a pawn disc in each of the classic board's four colours, in its seat order, over the
 * wordmark.
 * A placeholder until the brand art is made (the launcher icon's pawn, Phase 0 of `.claude/IMPLEMENTATION.md`). The
 * discs are decoration, so a screen reader hears only the name, as a heading.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            for (colour in PlayerColour.entries.take(BoardGeometry.CLASSIC.seats)) {
                ColourDisc(colour = colour.color, size = 36.dp, ring = MaterialTheme.colorScheme.surface)
            }
        }
        Text(
            text = stringResource(Res.string.app_name),
            modifier = Modifier.padding(top = 16.dp).semantics { heading() },
            style = MaterialTheme.typography.displayMedium,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}
