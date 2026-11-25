package net.zodac.ludoplus.ads

import android.app.Application
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.zodac.ludoplus.BuildConfig

/**
 * [Ads] on Google Mobile Ads (AdMob), with Google's consent form (the User Messaging Platform): the only code that
 * imports either. One per process; each Activity attaches itself, as the consent form needs one.
 *
 * On each attach the consent information is brought up to date and, where the law asks for it (the UK and the EU) and
 * the player hasn't answered yet, the form is shown. Only once [ConsentInformation.canRequestAds] does the SDK start and
 * the banner's slot get its ad. Nothing throws: a failure is logged under [LOG_TAG] and leaves the slot empty. See
 * `.claude/ADS.md`.
 */
class AdMobAds @VisibleForTesting internal constructor(private val application: Application) : Ads {

    override val available: Boolean = true

    /** Whether the SDK has started, which it does only once ads may be asked for. */
    private val ready = MutableStateFlow(false)

    private val mutablePrivacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = mutablePrivacyOptionsRequired.asStateFlow()

    private var activity: WeakReference<ComponentActivity>? = null
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(application)
    private val started = AtomicBoolean(false)

    private fun attach(activity: ComponentActivity) {
        this.activity = WeakReference(activity)
        // The window's decor is installed here, on the main thread, before UMP is called: UMP 4.0.0 reads the decor view
        // from its own thread, and while onCreate hasn't installed it yet, the two threads race to build it. One run
        // threw ("Window couldn't find content container view"); another left the window on an empty decor, a black
        // screen with the app's content never shown.
        activity.window.decorView
        // A choice made in an earlier session counts at once; the update below may then ask again.
        update()
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    error?.let { Log.w(LOG_TAG, "Consent form: ${it.errorCode} ${it.message}") }
                    update()
                }
            },
            { error ->
                Log.w(LOG_TAG, "Consent update: ${error.errorCode} ${error.message}")
                update()
            },
        )
    }

    /** Whether to offer the consent choices again, and the SDK started (once) when ads may be asked for. */
    private fun update() {
        mutablePrivacyOptionsRequired.value =
            consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (!consent.canRequestAds() || !started.compareAndSet(false, true)) return
        // Off the main thread, as Google advises: it reads the device and starts the mediation adapters.
        Thread {
            MobileAds.initialize(application) { ready.value = true }
        }.start()
    }

    override fun showPrivacyOptions() {
        val activity = activity?.get() ?: return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            error?.let { Log.w(LOG_TAG, "Privacy options: ${it.errorCode} ${it.message}") }
            update()
        }
    }

    /**
     * An anchored adaptive banner, as wide as there is room and as tall as AdMob's banner for that width, on the page's
     * surface until it loads. Its size is worked out from the screen alone, so the slot has it before the SDK starts; the
     * ad view itself is made only once [ready]. A new width (the screen turning) is a new banner. While not [clickable],
     * a layer over it takes every touch, so none reaches the ad.
     */
    @Composable
    override fun Banner(clickable: Boolean, modifier: Modifier) {
        val adsReady by ready.collectAsStateWithLifecycle()
        BoxWithConstraints(modifier) {
            val context = LocalContext.current
            val width = maxWidth.value.toInt()
            val size = remember(width) { AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, width) }
            Box(Modifier.fillMaxWidth().height(size.height.dp).background(MaterialTheme.colorScheme.surface)) {
                if (adsReady) {
                    key(size) {
                        AndroidView(
                            factory = { viewContext ->
                                AdView(viewContext).apply {
                                    setAdSize(size)
                                    adUnitId = bannerId
                                    loadAd(AdRequest.Builder().build())
                                }
                            },
                            onRelease = { it.destroy() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (!clickable) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                        },
                    )
                }
            }
        }
    }

    companion object {

        /** Why a call failed, in logcat: `adb logcat -s LudoPlusAds`. */
        const val LOG_TAG = "LudoPlusAds"

        /** Google's test banner, which a debug build always shows: real ads are never shown to (or tapped by) a developer. */
        private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/9214589741"

        internal val bannerId: String get() = if (BuildConfig.DEBUG) TEST_BANNER_ID else BuildConfig.ADMOB_BANNER_ID

        @Volatile
        private var instance: AdMobAds? = null

        /** The ads for [activity] to provide, attached to it, where there are any: a device with Google Play services. */
        fun attach(activity: ComponentActivity): Ads {
            if (!usesAds(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) == ConnectionResult.SUCCESS)) return NoAds
            val ads = instance ?: AdMobAds(activity.application).also { instance = it }
            ads.attach(activity)
            return ads
        }

        /** AdMob needs Google Play services; any build of the play app with them shows ads (a debug build, test ones). */
        internal fun usesAds(playServicesAvailable: Boolean): Boolean = playServicesAvailable
    }
}
