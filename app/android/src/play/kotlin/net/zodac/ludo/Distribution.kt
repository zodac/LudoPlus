package net.zodac.ludo

import android.app.Application
import androidx.activity.ComponentActivity
import net.zodac.ludo.online.GameServices
import net.zodac.ludo.online.PlayGamesServices
import net.zodac.ludo.platform.ProgressFiles

/**
 * The play flavour's own part of the app, for Google Play: Google Play Games, and no progress files. Play Billing comes
 * back in Phase 11 of .claude/IMPLEMENTATION.md.
 */
internal object Distribution {

    /** Only sets Play Games up: it starts once the player has signed in from Settings. */
    fun initialize(application: Application) = PlayGamesServices.initialize(application)

    fun gameServices(activity: ComponentActivity): GameServices = PlayGamesServices.attach(activity)

    /** None: a file's progress could otherwise reach Play Games' achievements and leaderboards. */
    fun progressFiles(activity: ComponentActivity): ProgressFiles? = null
}
