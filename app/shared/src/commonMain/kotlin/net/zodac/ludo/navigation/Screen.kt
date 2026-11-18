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

    /** The host's Nearby lobby, in the play graph, between New Game and the board. */
    const val PLAY_LOBBY = "play/lobby"

    /** Joining a Nearby game: the graph, finding a game, and its board, which share one view model. */
    const val JOIN_GRAPH = "join"
    const val JOIN_FIND = "join/find"
    const val JOIN_GAME = "join/game"

    const val SETTINGS = "settings"

    /** The Graphics page, opened from its row in Settings. */
    const val GRAPHICS = "settings/graphics"

    const val RULES = "rules"

    /** The guided tutorial: a game screen of its own, opened from Rules' How to Play page or New Game's link. */
    const val TUTORIAL = "tutorial"

    const val LEADERBOARD = "leaderboard"

    const val STATISTICS = "statistics"

    const val ACHIEVEMENTS = "achievements"

    const val STYLES = "styles"

    /** A Styles category's own page, opened from its field on the Styles page: "styles/board". */
    fun styleCategory(category: StyleCategory): String = "$STYLES/${category.name.lowercase()}"

    /**
     * Where a long-pressed styles banner naming [styles] goes: the one category's page they're all in (opened over the
     * Styles page, so Back returns there), or - styles of several categories - the Styles page, their fields flashed.
     */
    fun stylesBannerTarget(styles: List<UnlockedStyle>): String {
        val categories = styles.mapNotNull { style -> StyleCategory.entries.firstOrNull { it.catalog.noun.key == style.categoryNoun.key } }.distinct()
        return categories.singleOrNull()?.let(::styleCategory) ?: STYLES
    }
}
