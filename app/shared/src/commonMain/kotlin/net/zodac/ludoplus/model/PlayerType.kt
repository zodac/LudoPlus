package net.zodac.ludoplus.model

enum class PlayerType(override val id: String) : StoredId {
    /** A person at this phone. */
    HUMAN("HUMAN"),
    AI("AI"),

    /**
     * A person at another phone nearby, in a Nearby game (`.claude/NETPLAY.md`): on the host's New Game, an open seat a
     * joining phone takes; in the game, the host waits for that phone's moves, as it waits for a tap.
     */
    NEARBY("NEARBY"),
}
