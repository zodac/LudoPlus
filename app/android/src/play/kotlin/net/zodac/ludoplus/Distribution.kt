package net.zodac.ludoplus

import android.app.Application
import androidx.activity.ComponentActivity
import net.zodac.ludoplus.ads.AdMobAds
import net.zodac.ludoplus.ads.Ads
import net.zodac.ludoplus.netplay.Nearby
import net.zodac.ludoplus.netplay.NearbyConnections
import net.zodac.ludoplus.online.GameServices
import net.zodac.ludoplus.online.PlayGamesServices
import net.zodac.ludoplus.platform.ProgressFiles
import net.zodac.ludoplus.purchases.PlayBillingStore
import net.zodac.ludoplus.purchases.Store

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

    fun nearby(activity: ComponentActivity): Nearby = NearbyConnections.attach(activity)

    /** None: a file's progress could otherwise reach Play Games' achievements and leaderboards. */
    fun progressFiles(activity: ComponentActivity): ProgressFiles? = null
}
