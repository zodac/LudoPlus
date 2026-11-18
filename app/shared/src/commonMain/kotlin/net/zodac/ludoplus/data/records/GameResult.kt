package net.zodac.ludoplus.data.records

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

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
        // A merge's rows: one row a uuid, on every device.
        Index(name = "index_results_uuid", value = ["uuid"], unique = true),
    ],
)
data class GameResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * The row's id on every device, as 32 lower-case hex digits, random: what a merge of two devices' records (the cloud
     * save, a progress file) tells rows apart by. [id] is this device's alone.
     */
    @ColumnInfo(defaultValue = "") val uuid: String = Uuid.random().toHexString(),
    /** The name as it was typed. */
    val playerName: String,
    /** [playerName] as one player is known by, whatever its case ([net.zodac.ludoplus.model.playerNameKey]). */
    val playerKey: String,
    /** Whether this was the first seat's player (the device's own). */
    val isPrimaryPlayer: Boolean,
    /** The mode's id ([net.zodac.ludoplus.model.GameMode.id]). */
    val gameModeId: String,
    /** Whether the game ran under its mode's own rules, without teams or house rules: only these make the Leaderboard. */
    val playedUnmodified: Boolean = true,
    /** Whether any house rule was on: such a game is in neither the Leaderboard nor Statistics. */
    @ColumnInfo(defaultValue = "0") val houseRules: Boolean = false,
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
