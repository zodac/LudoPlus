package net.zodac.ludo.data.records

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One person's part in one finished game. The computer's players are never recorded. The Leaderboard and Statistics
 * are both read from these rows.
 */
@Entity(
    tableName = "results",
    indices = [
        // The Leaderboard: each mode's wins, fastest first.
        Index(name = "index_results_leaderboard", value = ["gameModeId", "playedUnmodified", "won", "turns", "captures", "endedAtEpochMillis"]),
        // Statistics: each player's games, in the order they were played.
        Index(name = "index_results_player", value = ["playerKey", "endedAtEpochMillis"]),
    ],
)
data class GameResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The name as it was typed. */
    val playerName: String,
    /** [playerName] as one player is known by, whatever its case ([net.zodac.ludo.model.playerNameKey]). */
    val playerKey: String,
    /** Whether this was the first seat's player (the device's own). */
    val isPrimaryPlayer: Boolean,
    /** The mode's id ([net.zodac.ludo.model.GameMode.id]). */
    val gameModeId: String,
    /** Whether the game ran under its mode's own rules, no modifier on: only these make the Leaderboard. */
    val playedUnmodified: Boolean = true,
    val playerCount: Int,
    /** When the game ended, by the wall clock. */
    val endedAtEpochMillis: Long,
    /** How long the whole game was on screen. */
    val playMillis: Long,
    /** Where they placed: 1 for the winner. */
    val place: Int,
    /** Whether they placed first. */
    val won: Boolean,
    /** How far into the game they were placed: brought every pawn home, or for the last, when the game ended. */
    val finishMillis: Long,
    /** How many turns they started. */
    val turns: Int,
    /** How many opponents' pawns they sent back to a yard. */
    val captures: Int,
    /** How many of their own pawns were sent back to their yard. */
    val timesCaptured: Int,
    /** How many of their pawns were home when the game ended. */
    val pawnsHome: Int,
)
