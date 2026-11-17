package net.zodac.ludo.data.settings

/**
 * The id of each Styles category's default - what [SettingsRepository] reads before a pick is saved. The default
 * styles themselves (each catalog's first, in `ui.style`) take their ids from here, so the two can't disagree.
 * Stored ids: permanent once released.
 */
object DefaultStyleIds {
    const val DICE = "classic_ivory"
    const val TABLE_BACKGROUND = "classic_navy"
    const val BOARD = "classic_cream"
    const val PAWNS = "classic_gloss"
}
