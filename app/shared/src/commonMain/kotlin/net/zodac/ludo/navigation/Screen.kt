package net.zodac.ludo.navigation

import net.zodac.ludo.app.UnlockedStyle
import net.zodac.ludo.game.style.StyleCategory
import net.zodac.ludo.ui.style.catalog

/** Route constants for [LudoPlusNavHost]. */
object Screen {
    const val MENU = "menu"

    const val PLAY_GRAPH = "play"
    const val PLAY_SETUP = "play/setup"
    const val PLAY_GAME = "play/game"

    const val SCORES = "scores"
    const val STATISTICS = "statistics"
    const val ACHIEVEMENTS = "achievements"
    const val SETTINGS = "settings"

    /** The Graphics page, opened from its row in Settings. */
    const val GRAPHICS = "settings/graphics"

    const val STYLES = "styles"

    /** A Styles category's own page, opened from its field on the Styles page: "styles/dice_cup". */
    fun styleCategory(category: StyleCategory): String = "$STYLES/${category.name.lowercase()}"

    /**
     * Where a long-pressed styles banner naming [styles] goes: the one category's page they're all in (opened over the
     * Styles page, so Back returns there), or - styles of several categories - the Styles page, their fields flashed.
     */
    fun stylesBannerTarget(styles: List<UnlockedStyle>): String {
        val categories = styles.mapNotNull { style -> StyleCategory.entries.firstOrNull { it.catalog.noun.key == style.categoryNoun.key } }.distinct()
        return categories.singleOrNull()?.let(::styleCategory) ?: STYLES
    }

    const val RULES = "rules"
    const val TUTORIAL = "tutorial"
}
