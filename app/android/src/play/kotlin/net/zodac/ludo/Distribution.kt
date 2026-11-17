package net.zodac.ludo

import android.app.Application
import androidx.activity.ComponentActivity
import net.zodac.ludo.platform.ProgressFiles

/**
 * The play flavour's own part of the app (.claude/DESIGN.md, "Distribution"). Google Play Games and Play Billing come
 * back in their phases of .claude/IMPLEMENTATION.md.
 */
internal object Distribution {

    fun initialize(application: Application) = Unit

    /** None: nothing from a file may reach Play Games, so the play app has no progress files. */
    fun progressFiles(activity: ComponentActivity): ProgressFiles? = null
}
