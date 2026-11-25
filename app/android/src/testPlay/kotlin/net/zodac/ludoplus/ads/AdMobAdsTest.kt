package net.zodac.ludoplus.ads

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.ads.AdSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** AdMob's banner slot: the page is laid out around it from the first frame, so it's its full size before AdMob has started. */
@RunWith(AndroidJUnit4::class)
class AdMobAdsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `the slot has the banner's height before AdMob starts`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val height = AdSize.getLargeAnchoredAdaptiveBannerAdSize(application, 411).height
        assertTrue("The banner's height is $height", height >= 50)

        val ads = AdMobAds(application)
        assertTrue(ads.available)
        compose.setContent { ads.Banner(clickable = true, modifier = Modifier.fillMaxWidth().testTag("slot")) }
        compose.onNodeWithTag("slot").assertHeightIsEqualTo(height.dp)
    }
}
