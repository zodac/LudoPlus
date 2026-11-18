package net.zodac.ludoplus

import java.util.TimeZone

internal actual fun setTimeZone(id: String): String = TimeZone.getDefault().id.also { TimeZone.setDefault(TimeZone.getTimeZone(id)) }
