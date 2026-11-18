package net.zodac.ludo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import net.zodac.ludo.ads.AchievementBannersShowing
import net.zodac.ludo.ads.Ads
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.platform.SilentPlatformServices
import net.zodac.ludo.purchases.Entitlement
import net.zodac.ludo.ui.LudoPlusApp
import org.robolectric.annotation.Config

/** An ad network that's ready, drawing a tagged box as its banner and noting whether it was last asked to take touches. */
private class FakeAds : Ads {
    override val ready: StateFlow<Boolean> = MutableStateFlow(true)
    var clickable: Boolean? = null

    @Composable
    override fun Banner(clickable: Boolean, modifier: Modifier) {
        this.clickable = clickable
        Box(modifier.height(50.dp).testTag(AD))
    }

    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)

    override fun showPrivacyOptions() = Unit
}

private const val AD = "ad"

/** The ad banner: at the foot of every page but the game's and the tutorial's, gone for a buyer, and untouchable under an achievement banner. */
@Config(qualifiers = "w411dp-h891dp")
class AdBannerTest : UiTest() {

    @AfterTest
    fun noBanner() {
        AchievementBannersShowing.state.value = false
    }

    private fun show(ads: Ads, container: AppContainer = inMemoryAppContainer()) {
        compose.setContent { LudoPlusApp(container = container, platform = SilentPlatformServices, ads = ads) }
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText("Rules")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun adShown(): Boolean = compose.onAllNodes(hasTestTag(AD)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `the menu and pages show it - the tutorial doesn't`() = uiTest {
        show(FakeAds())
        assertEquals(true, adShown())
        compose.onNodeWithText("Rules").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasText("Play the Tutorial")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(true, adShown())
        compose.onNodeWithText("Play the Tutorial").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10_000) { !adShown() }
    }

    @Test
    fun `with ads removed there is none`() = uiTest {
        val container = inMemoryAppContainer()
        runBlocking { container.purchasesRepository.set(Entitlement.NO_ADS, true) }
        show(FakeAds(), container)
        compose.waitForIdle()
        assertEquals(false, adShown())
    }

    @Test
    fun `it takes no touch while an achievement banner is over it`() = uiTest {
        val ads = FakeAds()
        show(ads)
        assertEquals(true, ads.clickable)
        AchievementBannersShowing.state.value = true
        compose.waitForIdle()
        assertEquals(false, ads.clickable)
    }
}
