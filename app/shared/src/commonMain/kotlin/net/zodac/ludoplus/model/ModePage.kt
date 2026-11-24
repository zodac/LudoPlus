package net.zodac.ludoplus.model

/** A page of Statistics, picked from the drop-down at its top: one [Mode]'s games, or [AllModes]', every mode's together. */
sealed interface ModePage {
    /** What the page is stored as (the `statistics_choice` preference): the mode's id, or `all_modes`. */
    val stored: String

    /** The mode whose games it has, or null for every mode's. */
    val mode: GameMode?

    data class Mode(override val mode: GameMode) : ModePage {
        override val stored: String get() = mode.id
    }

    data object AllModes : ModePage {
        override val stored = "all_modes"
        override val mode: GameMode? = null
    }

    companion object {
        /** The default mode's page: where Statistics opens until another is picked. */
        val DEFAULT: ModePage = Mode(GameMode.DEFAULT)

        /** Every page, in the drop-down's order: each mode's, then [AllModes]. */
        val all: List<ModePage> = GameMode.entries.map(::Mode) + AllModes

        /** The page [stored] names; [DEFAULT] where nothing was picked yet or the mode is no longer known. */
        fun parse(stored: String?): ModePage = all.firstOrNull { it.stored == stored } ?: DEFAULT
    }
}
