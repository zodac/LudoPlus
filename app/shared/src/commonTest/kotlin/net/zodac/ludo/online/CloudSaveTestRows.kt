package net.zodac.ludo.online

import net.zodac.ludo.data.records.GameResult
import net.zodac.ludo.model.playerNameKey

/** A game record as a merge carries it: [uuid] padded to a whole one with zeros, won by [name] at [at]. */
internal fun cloudRecord(uuid: String, at: Long, name: String = "Sam", turns: Int = 40, won: Boolean = true) = GameResult(
    uuid = uuid.padEnd(32, '0'),
    playerName = name,
    playerKey = playerNameKey(name),
    isPrimaryPlayer = true,
    gameModeId = "classic",
    playerCount = 4,
    endedAtEpochMillis = at,
    playMillis = 600_000,
    place = if (won) 1 else 2,
    won = won,
    finishMillis = 500_000,
    turns = turns,
    captures = 3,
    timesCaptured = 1,
    pawnsHome = if (won) 4 else 2,
)
