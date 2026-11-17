package net.zodac.ludo

import android.app.Application
import androidx.activity.ComponentActivity
import net.zodac.ludo.device.AndroidProgressFiles
import net.zodac.ludo.platform.ProgressFiles

/**
 * The github flavour's own part of the app (.claude/DESIGN.md, "Distribution"): progress files, and no online game
 * service.
 */
internal object Distribution {

    fun initialize(application: Application) = Unit

    /** [activity]'s progress files: registered as it's created, before it starts. */
    fun progressFiles(activity: ComponentActivity): ProgressFiles = AndroidProgressFiles.attach(activity)
}
