package net.zodac.ludoplus.game

import kotlin.time.Clock

/** The wall-clock time now, in milliseconds since the epoch - what every stored timestamp is measured in. */
fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
