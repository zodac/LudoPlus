package net.zodac.ludo.ui.style

import net.zodac.ludo.game.style.StyleCategory

/** This category's art: its registry's groups, each drawn. */
val StyleCategory.catalog: StyleCatalog<*>
    get() = when (this) {
        StyleCategory.DICE -> DiceStyles
        StyleCategory.DICE_CUP -> DiceCupStyles
        StyleCategory.MAT -> DiceMats
        StyleCategory.BACKGROUND -> TableBackgrounds
        StyleCategory.FRAME -> ScoreFrames
        StyleCategory.BOX -> ScoreBoxes
    }
