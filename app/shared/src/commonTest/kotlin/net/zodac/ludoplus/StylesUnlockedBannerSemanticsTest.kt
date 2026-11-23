package net.zodac.ludoplus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import net.zodac.ludoplus.app.AchievementEvent
import net.zodac.ludoplus.app.AchievementEvents
import net.zodac.ludoplus.app.LocalAppContainer
import net.zodac.ludoplus.app.StylesEarnedBy
import net.zodac.ludoplus.app.UnlockedStyle
import net.zodac.ludoplus.game.style.styleRewards
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.ui.achievements.AchievementBannerHost
import net.zodac.ludoplus.ui.style.DiceStyles
import net.zodac.ludoplus.ui.style.TableBackgrounds
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/**
 * The "style unlocked" banner comes and goes by itself, so TalkBack has to be told it's there: one
 * polite live region naming every style it unlocked, with the tap that pauses it as an action.
 */
class StylesUnlockedBannerSemanticsTest : UiTest() {

    private val showcase = Showcase { compose }

    @Test
    fun `a styles banner is one polite live region with a pause action - naming one style - two - or two and how many more`() = uiTest {
        // Three styles: one polite live region naming every style, with the tap that pauses it as an action.
        showBannerHostAndEmit(listOf(DiceStyles.unlockedStyle("Frosted"), TableBackgrounds.unlockedStyle("Spotlight"), TableBackgrounds.unlockedStyle("Pinstripe")), StylesEarnedBy.Count(35))
        compose.onNode(hasContentDescription("Styles unlocked: Frosted die, Spotlight background, Pinstripe background. Earned 35 achievements"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assert(SemanticsMatcher("pauses on a tap") { it.config[SemanticsActions.OnClick].label == "Pause countdown" })
        // It names two and counts the rest.
        compose.onNodeWithText("Earned 35 achievements: 'Frosted' die, 'Spotlight' background and 1 more", useUnmergedTree = true).assertExists()

        // One style: Style Unlocked, naming it - and TalkBack hears it.
        showBannerHostAndEmit(listOf(DiceStyles.unlockedStyle("Frosted")), StylesEarnedBy.Count(23))
        compose.onNodeWithText("Style Unlocked", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Earned 23 achievements: the 'Frosted' die style", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Style unlocked: Frosted die. Earned 23 achievements")).assertExists()

        // Two styles: both named.
        showBannerHostAndEmit(listOf(DiceStyles.unlockedStyle("Frosted"), TableBackgrounds.unlockedStyle("Spotlight")), StylesEarnedBy.Count(25))
        compose.onNodeWithText("Styles Unlocked", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Earned 25 achievements: 'Frosted' die and 'Spotlight' background", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Styles unlocked: Frosted die, Spotlight background. Earned 25 achievements")).assertExists()
    }

    @Test
    fun `a style behind one achievement names the achievement`() = uiTest {
        showBannerHostAndEmit(Achievement.BIG_FAN.stylesUnlocked(), StylesEarnedBy.OneAchievement(Achievement.BIG_FAN))
        compose.onNodeWithText("Styles Unlocked", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Earned 'Big Fan': 'Glitch' die and background", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Styles unlocked: Glitch die, Glitch background. Earned Big Fan")).assertExists()

        showBannerHostAndEmit(Achievement.MIDNIGHT_RUN.stylesUnlocked(), StylesEarnedBy.OneAchievement(Achievement.MIDNIGHT_RUN))
        compose.onNodeWithText("Earned 'Midnight Run': 'Starry' board, die, and background", useUnmergedTree = true).assertExists()

        showBannerHostAndEmit(Achievement.COUNTING_UP.stylesUnlocked(), StylesEarnedBy.OneAchievement(Achievement.COUNTING_UP))
        compose.onNodeWithText("Style Unlocked", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Earned 'Counting Up': the 'Maths' die style", useUnmergedTree = true).assertExists()
        compose.onNode(hasContentDescription("Style unlocked: Maths die. Earned Counting Up")).assertExists()
    }

    private fun Achievement.stylesUnlocked(): List<UnlockedStyle> =
        styleRewards.map { UnlockedStyle(it.styleName, it.categoryNoun, it.hiddenColours) }

    /** A fresh banner host - one showing nothing yet - then the banner for [styles]. */
    private fun showBannerHostAndEmit(styles: List<UnlockedStyle>, earnedBy: StylesEarnedBy) {
        showcase.show {
            CompositionLocalProvider(LocalAppContainer provides inMemoryAppContainer()) {
                LudoPlusTheme {
                    AchievementBannerHost(isOnGameScreen = { false }, onAchievementSelected = {}, onStylesSelected = {}) { Box(Modifier.fillMaxSize()) }
                }
            }
        }
        AchievementEvents.emit(AchievementEvent.StylesUnlocked(styles, earnedBy))
        compose.waitForIdle()
    }
}
