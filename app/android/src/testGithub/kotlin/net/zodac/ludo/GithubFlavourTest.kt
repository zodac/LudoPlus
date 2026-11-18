package net.zodac.ludo

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.ludo.ads.NoAds
import net.zodac.ludo.online.NoGameServices
import net.zodac.ludo.purchases.NoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * The github APK: an app of its own, beside the Play one rather than in its place, with progress files and no Google
 * Play Games, Play Billing, or ads anywhere in it: no SDK, no manifest entries, no service, nothing for sale, no network.
 */
@RunWith(AndroidJUnit4::class)
class GithubFlavourTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `it is its own app - not the Play one`() {
        assertEquals("net.zodac.ludo.github", context.packageName)
    }

    @Test
    fun `a debug build is Ludo Plus Debug on the launcher`() {
        val label = context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        assertEquals(if (BuildConfig.DEBUG) "Ludo Plus Debug" else "Ludo Plus", label)
    }

    @Test
    fun `there is no Play Games SDK in it`() {
        for (name in listOf("com.google.android.gms.games.PlayGames", "net.zodac.ludo.online.PlayGamesServices")) {
            assertFalse(name, runCatching { Class.forName(name) }.isSuccess)
        }
    }

    @Test
    fun `its manifest has none of Play Games' or Sidekick's entries`() {
        val metaData = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        for (key in listOf("com.google.android.gms.games.APP_ID", "com.google.android.finsky.deku.OVERLAY_CONFIG")) {
            assertFalse(key, metaData?.containsKey(key) == true)
        }
        val providers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
        assertFalse(providers.any { it.name.contains("PlayGames") })
    }

    @Test
    fun `there is no Billing Library in it - and no billing permission`() {
        for (name in listOf("com.android.billingclient.api.BillingClient", "net.zodac.ludo.purchases.PlayBillingStore")) {
            assertFalse(name, runCatching { Class.forName(name) }.isSuccess)
        }
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("com.android.vending.BILLING"))
    }

    @Test
    fun `there is no ads SDK in it - no ad id - and no network`() {
        for (name in listOf("com.google.android.gms.ads.MobileAds", "com.google.android.ump.UserMessagingPlatform", "net.zodac.ludo.ads.AdMobAds")) {
            assertFalse(name, runCatching { Class.forName(name) }.isSuccess)
        }
        val metaData = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        assertFalse(metaData?.containsKey("com.google.android.gms.ads.APPLICATION_ID") == true)
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        for (permission in listOf("android.permission.INTERNET", "com.google.android.gms.permission.AD_ID")) assertFalse(permission, permission in permissions)
    }

    @Test
    fun `it has progress files - sealed with a 32-byte key - and no online service, store, or ads`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().get()
        assertSame(NoGameServices, Distribution.gameServices(activity))
        assertSame(NoStore, Distribution.store(activity))
        assertSame(NoAds, Distribution.ads(activity))
        assertEquals(32, Distribution.progressFiles(activity).key.size)
    }

    @Test
    fun `its backups carry everything - it has no Play Games choice to leave out`() {
        assertEquals(0, applicationManifestResource(context, "dataExtractionRules"))
        assertEquals(0, applicationManifestResource(context, "fullBackupContent"))
    }
}
