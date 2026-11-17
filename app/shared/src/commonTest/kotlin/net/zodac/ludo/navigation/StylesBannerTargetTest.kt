package net.zodac.ludo.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludo.app.UnlockedStyle
import net.zodac.ludo.ui.style.TableBackgrounds
import net.zodac.ludo.ui.style.DiceStyles

/** Where a long-pressed styles banner goes: the one category's page its styles are in, or the Styles page. */
class StylesBannerTargetTest {

    private val die = UnlockedStyle(DiceStyles.families[1].name, DiceStyles.noun)
    private val otherDie = UnlockedStyle(DiceStyles.families[2].name, DiceStyles.noun)
    private val background = UnlockedStyle(TableBackgrounds.families[1].name, TableBackgrounds.noun)

    @Test
    fun `styles all of one category open its page - of several, the Styles page`() {
        assertEquals("styles/dice", Screen.stylesBannerTarget(listOf(die)))
        assertEquals("styles/dice", Screen.stylesBannerTarget(listOf(die, otherDie)))
        assertEquals("styles/background", Screen.stylesBannerTarget(listOf(background)))
        assertEquals("styles", Screen.stylesBannerTarget(listOf(die, background)))
        assertEquals("styles", Screen.stylesBannerTarget(emptyList()))
    }
}
