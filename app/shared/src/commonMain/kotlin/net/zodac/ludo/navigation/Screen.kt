package net.zodac.ludo.navigation

/** Route constants for [LudoPlusNavHost]. */
object Screen {
    const val MENU = "menu"

    const val PLAY_GRAPH = "play"
    const val PLAY_SETUP = "play/setup"
    const val PLAY_GAME = "play/game"

    const val SETTINGS = "settings"

    /** The Graphics page, opened from its row in Settings. */
    const val GRAPHICS = "settings/graphics"

    const val RULES = "rules"

    /** The guided tutorial: a game screen of its own, opened from Rules' How to Play page or New Game's link. */
    const val TUTORIAL = "tutorial"

    const val LEADERBOARD = "leaderboard"

    const val STATISTICS = "statistics"
}
