package net.zodac.ludoplus.model

/** Selectable per CPU player on the setup screen. Picks the CPU's strategy (`game/CpuStrategy.kt`). */
enum class Difficulty(override val id: String) : StoredId {
    EASY("EASY"),
    MEDIUM("MEDIUM"),
    HARD("HARD"),
}
