package net.zodac.ludo.model

/**
 * A setup-time player slot, as configured on [net.zodac.ludo.ui.setup.GameSetupScreen]
 * before a game starts.
 */
data class PlayerConfig(
    val slot: Int,
    val type: PlayerType,
    val name: String,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val colour: PlayerColour = PlayerColour.defaultFor(slot - 1),
)
