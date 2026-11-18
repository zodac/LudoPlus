package net.zodac.ludo

import android.app.Application
import androidx.activity.ComponentActivity
import net.zodac.ludo.ads.AdMobAds
import net.zodac.ludo.ads.Ads
import net.zodac.ludo.online.GameServices
import net.zodac.ludo.online.PlayGamesServices
import net.zodac.ludo.platform.ProgressFiles
import net.zodac.ludo.purchases.PlayBillingStore
import net.zodac.ludo.purchases.Store

/**
 * The play flavour's own part of the app, for Google Play: Google Play Games, Play Billing, AdMob's banner, and no
 * progress files.
 */
internal object Distribution {

    /** Only sets Play Games up: it starts once the player has signed in from Settings. */
    fun initialize(application: Application) = PlayGamesServices.initialize(application)

    fun gameServices(activity: ComponentActivity): GameServices = PlayGamesServices.attach(activity)

    fun store(activity: ComponentActivity): Store = PlayBillingStore.attach(activity)

    fun ads(activity: ComponentActivity): Ads = AdMobAds.attach(activity)

    /** None: a file's progress could otherwise reach Play Games' achievements and leaderboards. */
    fun progressFiles(activity: ComponentActivity): ProgressFiles? = null
}
