package net.zodac.ludoplus.model

/**
 * A player's own game mode (Phase 9 of `.claude/IMPLEMENTATION.md`): a built-in [base] mode with [houseRules] on,
 * saved under [name] and picked on New Game. It always has a house rule (without one it would be its base), so its
 * games, as any with house rules, stay off the Leaderboard and out of Statistics, and earn only the house rules' own achievements.
 *
 * [id] is its key in the settings, kept when it's renamed; [name] is unique among the player's, whatever its case.
 */
data class CustomMode(val id: String, val name: String, val base: GameMode, val houseRules: Set<HouseRule>) {
    init {
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) { "A custom mode's name is 1 to $MAX_NAME_LENGTH characters" }
        require(houseRules.isNotEmpty()) { "A custom mode has a house rule" }
    }

    /** The rules its games follow: its base's, with its house rules on. */
    val rules: GameRules get() = base.rules.withHouseRules(houseRules)

    companion object {
        /** The longest name a custom mode takes. */
        const val MAX_NAME_LENGTH = 24

        /** A new mode's id: one more than the highest of [existing]'s. */
        fun nextId(existing: List<CustomMode>): String =
            "custom_${(existing.mapNotNull { it.id.removePrefix("custom_").toIntOrNull() }.maxOrNull() ?: 0) + 1}"

        /** Whether [name] would clash with one of [existing]'s, whatever its case, other than [except]'s own. */
        fun nameTaken(name: String, existing: List<CustomMode>, except: String? = null): Boolean =
            existing.any { it.id != except && it.name.trim().equals(name.trim(), ignoreCase = true) }
    }
}
