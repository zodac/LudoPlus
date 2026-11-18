package net.zodac.ludoplus.ui.style

import androidx.compose.runtime.Composable
import net.zodac.ludoplus.data.settings.SavedStyles
import net.zodac.ludoplus.game.style.StyleReward
import net.zodac.ludoplus.game.style.access
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.styles_reward_hidden_colours
import net.zodac.ludoplus.resources.styles_reward_style
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.stringResource

/** The table art a game is drawn in for these picks: each one, or its default while it's locked. */
val SavedStyles.visualTheme: GameVisualTheme
    get() = GameVisualTheme(
        board = BoardStyles.unlockedById(boardStyleId, access),
        pawns = PawnStyles.unlockedById(pawnStyleId, access),
        diceStyle = DiceStyles.unlockedById(diceStyleId, access),
        background = TableBackgrounds.unlockedById(tableBackgroundId, access),
    )

/** What [this] reward is called in a sentence, in the player's language. */
@Composable
fun StyleReward.description(): String {
    val name = stringResource(styleName)
    val noun = stringResource(categoryNoun)
    return if (hiddenColours == 0) {
        stringResource(Res.string.styles_reward_style, name, noun)
    } else {
        pluralStringResource(Res.plurals.styles_reward_hidden_colours, hiddenColours, name, noun)
    }
}
