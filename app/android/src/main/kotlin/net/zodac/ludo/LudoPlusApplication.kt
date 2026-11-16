package net.zodac.ludo

import android.app.Application
import net.zodac.ludo.device.CrashHandler

class LudoPlusApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The crash page's process shows an error and nothing else: none of the app is set up there, only the page's words.
        if (CrashHandler.isCrashProcess()) {
            CrashActivity.provideComposeResourcesContext(this)
            return
        }
        CrashHandler.install(this)
        Distribution.initialize(this)
    }
}
