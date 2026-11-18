package net.zodac.ludo

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zodac.ludo.ads.AdMobAds
import net.zodac.ludo.data.PreferencesFile
import net.zodac.ludo.data.records.AppDatabase
import net.zodac.ludo.online.PlayGamesServices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.xmlpull.v1.XmlPullParser

/** The Play app: Google Play Games, and no progress files, signed in or not - nothing from a file can reach its boards. */
@RunWith(AndroidJUnit4::class)
class PlayFlavourTest {

    @Test
    fun `it is the Play app`() {
        assertEquals("net.zodac.ludo", ApplicationProvider.getApplicationContext<Context>().packageName)
    }

    @Test
    fun `it carries AdMob's app id - Google's test one in a local build - and may use the network for ads`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val metaData = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData
        assertTrue(metaData.getString("com.google.android.gms.ads.APPLICATION_ID").orEmpty().matches(Regex("ca-app-pub-[0-9]+~[0-9]+")))
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("android.permission.INTERNET" in permissions)
    }

    @Test
    fun `a debug build only ever asks for Google's test banner`() {
        if (BuildConfig.DEBUG) assertEquals("ca-app-pub-3940256099942544/9214589741", AdMobAds.bannerId)
    }

    @Test
    fun `it has no progress files`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).create().get()
        assertNull(Distribution.progressFiles(activity))
    }

    @Test
    fun `backups and device transfers carry everything but Play Games' sign-in at launch - from Android 12`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(R.xml.data_extraction_rules, applicationManifestResource(context, "dataExtractionRules"))

        val playGames = "sharedpref:${PlayGamesServices.PREFERENCES_FILE}.xml"
        assertEquals(
            listOf("cloud-backup/exclude $playGames", "device-transfer/exclude $playGames"),
            rules(context, R.xml.data_extraction_rules),
        )
    }

    @Test
    fun `backups up to Android 11 carry only the settings - adb can edit and restore one there`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(R.xml.backup_rules, applicationManifestResource(context, "fullBackupContent"))

        val database = listOf("", "-journal", "-shm", "-wal").map { "database:${AppDatabase.FILE_NAME}$it" }
        val preferences = PreferencesFile.entries.filter { it != PreferencesFile.SETTINGS }.map { "file:datastore/${it.fileName}" }
        assertEquals(
            (listOf("sharedpref:${PlayGamesServices.PREFERENCES_FILE}.xml") + database + preferences).map { "full-backup-content/exclude $it" },
            rules(context, R.xml.backup_rules),
        )
    }

    /** Each include or exclude in the rules file [id], as "parent/tag domain:path". */
    private fun rules(context: Context, id: Int): List<String> {
        val parser = context.resources.getXml(id)
        val found = mutableListOf<String>()
        var parent = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (parser.name == "include" || parser.name == "exclude") {
                val domain = parser.getAttributeValue(null, "domain")
                val path = parser.getAttributeValue(null, "path")
                found += "$parent/${parser.name} $domain:$path"
            } else {
                parent = parser.name
            }
        }
        return found
    }
}
