package net.zodac.ludo.model

/**
 * Selectable per CPU player on the setup screen. Picks the CPU's strategy, which comes in Phase 3 of
 * `.claude/IMPLEMENTATION.md`.
 */
enum class Difficulty(override val id: String) : StoredId {
    EASY("EASY"),
    MEDIUM("MEDIUM"),
    HARD("HARD"),
}
