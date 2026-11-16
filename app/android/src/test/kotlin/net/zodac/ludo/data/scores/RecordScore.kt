package net.zodac.ludo.data.scores

import net.zodac.ludo.game.TieBreakStats
import net.zodac.ludo.game.nowEpochMillis
import net.zodac.ludo.model.GameMode

/** [ScoreRepository.recordGame] for a game with one human player. */
suspend fun ScoreRepository.recordScore(
    playerName: String,
    stats: TieBreakStats,
    won: Boolean?,
    isPrimaryPlayer: Boolean,
    gameMode: GameMode = GameMode.STANDARD,
    playedUnmodified: Boolean = true,
    timestampEpochMillis: Long = nowEpochMillis(),
) = recordGame(
    listOf(PlayerResult(playerName, stats, won, isPrimaryPlayer)),
    gameMode = gameMode,
    playedUnmodified = playedUnmodified,
    timestampEpochMillis = timestampEpochMillis,
)
