package net.zodac.ludoplus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.runBlocking
import net.zodac.ludoplus.ads.AchievementBannersShowing
import net.zodac.ludoplus.ads.Ads
import net.zodac.ludoplus.ads.NoAds
import net.zodac.ludoplus.app.AppContainer
import net.zodac.ludoplus.data.records.GameRecordRepository
import net.zodac.ludoplus.data.records.InMemoryGameResultDao
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.purchases.Entitlement
import net.zodac.ludoplus.purchases.PurchasesRepository
import net.zodac.ludoplus.ui.LudoPlusApp
import org.robolectric.annotation.Config

/** An ad network, drawing a tagged box as its banner's slot and noting whether it was last asked to take touches. */
private class FakeAds : Ads {
    override val available: Boolean = true
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

/** Purchases that can't be read until [answer]. */
private class SlowPreferences : DataStore<Preferences> {
    private val read = MutableStateFlow<Preferences?>(null)
    override val data: Flow<Preferences> = read.filterNotNull()

    fun answer(preferences: Preferences) {
        read.value = preferences
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(read.value ?: emptyPreferences())
}

/** The records of a device where a person has finished a game. */
private fun played() = GameRecordRepository(InMemoryGameResultDao(finishedGame = true))

/**
 * The ad banner: its slot at the foot of every page but the game's and the tutorial's from the first frame, once a
 * person has finished a game, gone for a buyer, and untouchable under an achievement banner.
 */
@Config(qualifiers = "w411dp-h891dp")
class AdBannerTest : UiTest() {

    @AfterTest
    fun noBanner() {
        AchievementBannersShowing.state.value = false
    }

    private fun show(ads: Ads, container: AppContainer = inMemoryAppContainer(records = played())) {
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
    fun `before a first game is finished there is none - it comes with the first`() = uiTest {
        val records = GameRecordRepository(InMemoryGameResultDao())
        val container = inMemoryAppContainer(records = records)
        show(FakeAds(), container)
        compose.waitUntil(timeoutMillis = 10_000) { container.gameFinished.value == false }
        compose.waitForIdle()
        assertEquals(false, adShown())

        runBlocking { records.mergeCloud(played().cloudRecords()) }
        compose.waitUntil(timeoutMillis = 10_000) { adShown() }
    }

    @Test
    fun `with ads removed there is none`() = uiTest {
        val container = inMemoryAppContainer(records = played())
        runBlocking { container.purchasesRepository.set(Entitlement.NO_ADS, true) }
        show(FakeAds(), container)
        compose.waitUntil(timeoutMillis = 10_000) { container.adsRemoved.value == true }
        compose.waitForIdle()
        assertEquals(false, adShown())
    }

    @Test
    fun `without an ad network there is none`() = uiTest {
        show(NoAds)
        compose.waitForIdle()
        assertEquals(false, adShown())
    }

    @Test
    fun `the menu's first frame has its slot - the menu never moves for it`() = uiTest {
        val container = inMemoryAppContainer(records = played())
        compose.waitUntil(timeoutMillis = 10_000) { container.adsRemoved.value != null && container.gameFinished.value != null }
        compose.mainClock.autoAdvance = false
        compose.setContent { LudoPlusApp(container = container, platform = SilentPlatformServices, ads = FakeAds()) }
        assertEquals(true, adShown())
        val first = compose.onNodeWithText("Rules").getBoundsInRoot()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(first, compose.onNodeWithText("Rules").getBoundsInRoot())
    }

    @Test
    fun `nothing is drawn until the purchases have been read - then the slot comes with the page`() = uiTest {
        val purchases = SlowPreferences()
        show(FakeAds(), inMemoryAppContainer(records = played(), purchases = PurchasesRepository(purchases)))
        assertEquals(1, coloursDrawn())
        assertEquals(false, adShown())

        purchases.answer(emptyPreferences())
        compose.waitUntil(timeoutMillis = 10_000) { adShown() }
        assertTrue(coloursDrawn() > 1)
    }

    @Test
    fun `a buyer's page is drawn once the purchases have been read - and no slot ever comes`() = uiTest {
        val purchases = SlowPreferences()
        show(FakeAds(), inMemoryAppContainer(records = played(), purchases = PurchasesRepository(purchases)))
        assertEquals(1, coloursDrawn())

        purchases.answer(preferencesOf(booleanPreferencesKey(Entitlement.NO_ADS.id) to true))
        compose.waitUntil(timeoutMillis = 10_000) { coloursDrawn() > 1 }
        assertEquals(false, adShown())
    }

    /** How many colours the screen shows: one, the page's surface, while nothing is drawn over it. */
    private fun coloursDrawn(): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return buildSet { for (x in 0 until pixels.width step 4) for (y in 0 until pixels.height step 4) add(pixels[x, y]) }.size
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
