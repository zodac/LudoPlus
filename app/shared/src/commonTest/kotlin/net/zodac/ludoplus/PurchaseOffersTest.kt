package net.zodac.ludoplus

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import net.zodac.ludoplus.app.LocalAppContainer
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.platform.SilentPlatformServices
import net.zodac.ludoplus.purchases.LocalStore
import net.zodac.ludoplus.purchases.Product
import net.zodac.ludoplus.purchases.ProductState
import net.zodac.ludoplus.purchases.Store
import net.zodac.ludoplus.purchases.StoreState
import net.zodac.ludoplus.ui.settings.SettingsScreen
import net.zodac.ludoplus.ui.settings.settingsViewModel
import net.zodac.ludoplus.ui.styles.StylesScreen
import net.zodac.ludoplus.ui.theme.LudoPlusTheme

/** A store in [initial], noting each product it was asked to sell. */
private class OfferStore(initial: StoreState) : Store {
    override val state = MutableStateFlow(initial)
    val bought = mutableListOf<Product>()
    override fun buy(product: Product): Boolean = true.also { bought += product }
}

private fun ready(vararg owned: Pair<Product, ProductState>) =
    StoreState.Ready(Product.entries.associateWith { ProductState.ForSale("£2.99") } + owned)

/** The two offers: Unlock All on the Styles page, Remove ads in Settings, each with a switch for the other where it's for sale. */
class PurchaseOffersTest : UiTest() {

    private fun showStyles(store: Store) {
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides SilentPlatformServices,
                LocalStore provides store,
            ) { LudoPlusTheme { StylesScreen(stylesViewModel(), onBack = {}, onOpenCategory = {}) } }
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Styles")).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun showSettings(store: Store) {
        compose.setContent {
            CompositionLocalProvider(
                LocalAppContainer provides inMemoryAppContainer(),
                LocalPlatformServices provides SilentPlatformServices,
                LocalStore provides store,
            ) { LudoPlusTheme { SettingsScreen(settingsViewModel(), onBack = {}) } }
        }
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodes(hasText("Sound effects")).fetchSemanticsNodes().isNotEmpty() }
    }

    private val unlockAll = hasContentDescription("Unlock all styles") and hasClickAction()
    private val removeAds = hasText("Remove ads") and hasClickAction()

    @Test
    fun `Unlock All buys the styles alone - or with ads removed too - after its dialog`() = uiTest(TestScreen(411, 891)) {
        val store = OfferStore(ready())
        showStyles(store)
        compose.onNode(unlockAll).performClick()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf(Product.UNLOCK_ALL_STYLES), store.bought)

        compose.onNode(unlockAll).performClick()
        // TalkBack: "Remove ads too, switch, off".
        compose.onNode(isToggleable() and hasText("Remove ads too")).performClick()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf(Product.UNLOCK_ALL_STYLES, Product.UNLOCK_ALL_STYLES_AND_REMOVE_ADS), store.bought)
    }

    @Test
    fun `with ads already removed - Unlock All offers no switch`() = uiTest(TestScreen(411, 891)) {
        val store = OfferStore(ready(Product.REMOVE_ADS to ProductState.Owned))
        showStyles(store)
        compose.onNode(unlockAll).performClick()
        compose.onNode(hasText("Remove ads too")).assertDoesNotExist()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf(Product.UNLOCK_ALL_STYLES), store.bought)
    }

    @Test
    fun `a pending payment says so - and can't be bought again`() = uiTest(TestScreen(411, 891)) {
        showStyles(OfferStore(ready(Product.UNLOCK_ALL_STYLES to ProductState.Pending)))
        compose.onNode(unlockAll).assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Payment pending"))
    }

    @Test
    fun `owned - or with no store - there's no Unlock All`() = uiTest(TestScreen(411, 891)) {
        showStyles(OfferStore(ready(Product.UNLOCK_ALL_STYLES_AND_REMOVE_ADS to ProductState.Owned)))
        compose.onNode(hasContentDescription("Unlock all styles")).assertDoesNotExist()
    }

    @Test
    fun `Remove ads in Settings buys ad removal - or every style too - its row a button naming its action`() = uiTest(TestScreen(411, 891)) {
        val store = OfferStore(ready())
        showSettings(store)
        compose.onNode(removeAds).assert(SemanticsMatcher("double-tap to remove ads") { it.config[SemanticsActions.OnClick].label == "Remove ads" })
        compose.onNode(removeAds).performClick()
        compose.onNode(isToggleable() and hasText("Unlock all styles too")).performClick()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf(Product.UNLOCK_ALL_STYLES_AND_REMOVE_ADS), store.bought)
    }

    @Test
    fun `with ads removed - or no store - Settings offers nothing`() = uiTest(TestScreen(411, 891)) {
        showSettings(OfferStore(ready(Product.REMOVE_ADS to ProductState.Owned)))
        compose.onNode(removeAds).assertDoesNotExist()
    }
}
