package net.zodac.ludoplus.data.records

import net.zodac.ludoplus.model.GameMode

/** The [Statistics] read now, of [playerCount] and [mode] (null: of all). */
suspend fun GameRecordRepository.playerStatistics(playerCount: Int? = null, mode: GameMode? = null): List<PlayerStatistics> = statistics().players(playerCount, mode)
