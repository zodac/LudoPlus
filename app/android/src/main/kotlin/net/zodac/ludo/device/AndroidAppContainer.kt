package net.zodac.ludo.device

import android.content.Context
import net.zodac.ludo.BuildConfig
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.app.BuildInfo

/**
 * The process-wide [AppContainer] on Android - built on first use, from the application context.
 * One per process is also what DataStore needs: it refuses a second instance on the same file.
 */
object AndroidAppContainer {

    @Volatile
    private var instance: AppContainer? = null

    fun get(context: Context): AppContainer = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }

    private fun create(appContext: Context): AppContainer = AppContainer.create(
        // One file per PreferencesFile, in filesDir/datastore.
        dataDir = appContext.filesDir.resolve("datastore").absolutePath,
        buildInfo = BuildInfo(versionName = BuildConfig.VERSION_NAME, isDebug = BuildConfig.DEBUG),
    )
}
