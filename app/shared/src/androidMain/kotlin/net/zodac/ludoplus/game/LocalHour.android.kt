package net.zodac.ludoplus.game

import java.time.Instant
import java.time.ZoneId

internal actual fun localHourOfDay(epochMillis: Long): Int = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).hour
