package net.zodac.ludo

import android.app.Application
import androidx.activity.ComponentActivity
import net.zodac.ludo.ads.Ads
import net.zodac.ludo.ads.NoAds
import net.zodac.ludo.device.AndroidProgressFiles
import net.zodac.ludo.online.GameServices
import net.zodac.ludo.online.NoGameServices
import net.zodac.ludo.platform.ProgressFiles
import net.zodac.ludo.purchases.NoStore
import net.zodac.ludo.purchases.Store

/**
 * The github flavour's own part of the app (.claude/DESIGN.md, "Distribution"): progress files, and no online game
 * service, store, or ads.
 */
internal object Distribution {

    fun initialize(application: Application) = Unit

    /** None: the github app has no online service. */
    fun gameServices(activity: ComponentActivity): GameServices = NoGameServices

    /** None: the github app sells nothing. */
    fun store(activity: ComponentActivity): Store = NoStore

    /** None: the github app has no ads, and no network. */
    fun ads(activity: ComponentActivity): Ads = NoAds

    /** [activity]'s progress files: registered as it's created, before it starts. */
    fun progressFiles(activity: ComponentActivity): ProgressFiles = AndroidProgressFiles.attach(activity)
}
