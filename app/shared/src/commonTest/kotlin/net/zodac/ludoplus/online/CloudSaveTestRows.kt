package net.zodac.ludoplus.online

import net.zodac.ludoplus.data.records.GameResult
import net.zodac.ludoplus.model.playerNameKey

/** A game record as a merge carries it, won by [name] at [at]: its uuid is [label]'s bytes in hex, padded with zeros. */
internal fun cloudRecord(label: String, at: Long, name: String = "Sam", turns: Int = 40, won: Boolean = true) = GameResult(
    uuid = label.encodeToByteArray().also { require(it.size <= 16) { "A label is 16 bytes at most" } }
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }.padEnd(32, '0'),
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
    squares = if (won) 21 else 0,
    squaresMoved = if (won) 310 else null,
)

/** The label [cloudRecord] made this record's uuid from. */
internal val GameResult.uuidLabel: String
    get() = uuid.chunked(2).map { it.toInt(16).toByte() }.toByteArray().decodeToString().trimEnd('\u0000')
