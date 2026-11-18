package net.zodac.ludo.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared app needs from a platform's ad network (Google Mobile Ads on Android) for its one ad, a banner at the
 * foot of the screen, and nothing that names it. Each platform implements it once; [NoAds] is the version for previews,
 * tests, and builds with none (the github app). See `.claude/ADS.md`.
 */
interface Ads {

    /**
     * Whether an ad can be shown: the network is set up, and the player has answered its consent form where the law
     * asks for one. False until then, and for good on a device or build with none.
     */
    val ready: StateFlow<Boolean>

    /**
     * The banner, as wide as [modifier] allows and as tall as the network's banner for that width. [clickable] false,
     * touches on it go nowhere: while an achievement banner is over it, so a tap meant for that can't open an ad.
     */
    @Composable
    fun Banner(clickable: Boolean, modifier: Modifier)

    /** Whether the law asks for a way back to the consent choices (Settings' Ad privacy row). */
    val privacyOptionsRequired: StateFlow<Boolean>

    /** Opens the network's own consent form again, for the player to change their choices. */
    fun showPrivacyOptions()
}

/** No ads: previews, tests, the github app, and a device without the network. */
object NoAds : Ads {
    override val ready: StateFlow<Boolean> = MutableStateFlow(false)

    @Composable
    override fun Banner(clickable: Boolean, modifier: Modifier) = Unit

    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)

    override fun showPrivacyOptions() = Unit
}

val LocalAds = staticCompositionLocalOf<Ads> { NoAds }

/**
 * Whether an achievement banner is on screen, set by `AchievementBannerHost`: the ad banner under it takes no touch
 * meanwhile. Main thread only.
 */
object AchievementBannersShowing {
    val state = mutableStateOf(false)
}
