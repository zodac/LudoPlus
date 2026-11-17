package net.zodac.ludo.ui.style

import net.zodac.ludo.game.style.StyleCategory

/** This category's art: its registry's groups, each drawn. */
val StyleCategory.catalog: StyleCatalog<*>
    get() = when (this) {
        StyleCategory.BOARD -> BoardStyles
        StyleCategory.PAWNS -> PawnStyles
        StyleCategory.DICE -> DiceStyles
        StyleCategory.BACKGROUND -> TableBackgrounds
    }
