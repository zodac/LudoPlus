package net.zodac.ludo.model

enum class PlayerType(override val id: String) : StoredId {
    HUMAN("HUMAN"),
    AI("AI"),
}
