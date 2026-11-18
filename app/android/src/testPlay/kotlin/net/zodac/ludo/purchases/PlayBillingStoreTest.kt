package net.zodac.ludo.purchases

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import net.zodac.ludo.purchases.PlayBillingStore.Companion.storeState
import net.zodac.ludo.purchases.PlayBillingStore.Companion.byProduct
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** Which builds and devices get Play Billing, and what Play's purchases mean for the app. */
@RunWith(AndroidJUnit4::class)
class PlayBillingStoreTest {

    @Test
    fun `a release build uses Play Billing only when installed from Google Play`() {
        assertTrue(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = false, installer = "com.android.vending"))
        assertFalse(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = false, installer = null))
        assertFalse(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = false, installer = "com.google.android.packageinstaller"))
    }

    @Test
    fun `a debug build uses Play Billing however it was installed - for a license tester`() {
        assertTrue(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = true, installer = null))
        assertTrue(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = true, installer = "com.example.files"))
        assertTrue(PlayBillingStore.usesPlayBilling(playStoreInstalled = true, isDebugBuild = true, installer = "com.android.vending"))
    }

    @Test
    fun `no build uses Play Billing without the Play Store`() {
        assertFalse(PlayBillingStore.usesPlayBilling(playStoreInstalled = false, isDebugBuild = true, installer = "com.android.vending"))
        assertFalse(PlayBillingStore.usesPlayBilling(playStoreInstalled = false, isDebugBuild = false, installer = "com.android.vending"))
    }

    @Test
    fun `a device without the Play Store gets no store - so nothing is for sale`() {
        // Robolectric has no Play Store, as the emulator and a de-Googled phone haven't.
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val store = PlayBillingStore.attach(activity)
        assertSame(NoStore, store)
        assertEquals(StoreState.Unavailable, store.state.value)
    }

    @Test
    fun `the app asks for the billing permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(permissions.contains("com.android.vending.BILLING"))
    }

    @Test
    fun `opening the purchase sheet needs a word to the player unless it opened or the product is already owned`() {
        assertTrue(PlayBillingStore.sheetAnswered(BillingClient.BillingResponseCode.OK))
        assertTrue(PlayBillingStore.sheetAnswered(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED))
        assertFalse(PlayBillingStore.sheetAnswered(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE))
        assertFalse(PlayBillingStore.sheetAnswered(BillingClient.BillingResponseCode.BILLING_UNAVAILABLE))
        assertFalse(PlayBillingStore.sheetAnswered(BillingClient.BillingResponseCode.ERROR))
    }

    @Test
    fun `only a paid purchase is owned - a pending one is waiting - and the rest are for sale at Play's price`() {
        val prices = mapOf(Product.UNLOCK_ALL_STYLES to "£2.99", Product.REMOVE_ADS to "£1.99")
        val state = storeState(
            mapOf(Product.UNLOCK_ALL_STYLES to purchase(PURCHASED), Product.REMOVE_ADS to purchase(PENDING, product = Product.REMOVE_ADS.id)),
            prices,
        ) as StoreState.Ready
        assertEquals(ProductState.Owned, state.of(Product.UNLOCK_ALL_STYLES))
        assertEquals(ProductState.Pending, state.of(Product.REMOVE_ADS))
        assertEquals(ProductState.ForSale(null), state.of(Product.UNLOCK_ALL_STYLES_AND_REMOVE_ADS))
        assertEquals(ProductState.ForSale("£1.99"), (storeState(emptyMap(), prices) as StoreState.Ready).of(Product.REMOVE_ADS))
    }

    @Test
    fun `each product's purchase counts for it alone - a paid one before a pending one - another app's product for none`() {
        val other = purchase(PURCHASED, product = "some_other_product")
        assertEquals(emptyMap<Product, Purchase>(), listOf(other).byProduct())
        assertEquals(emptyMap<Product, Purchase>(), emptyList<Purchase>().byProduct())
        val pending = purchase(PENDING, token = "pending")
        val paid = purchase(PURCHASED, token = "paid")
        val ads = purchase(PURCHASED, product = Product.REMOVE_ADS.id, token = "ads")
        val byProduct = listOf(other, pending, paid, ads).byProduct()
        assertEquals("paid", byProduct[Product.UNLOCK_ALL_STYLES]?.purchaseToken)
        assertEquals("ads", byProduct[Product.REMOVE_ADS]?.purchaseToken)
        assertEquals("pending", listOf(other, pending).byProduct()[Product.UNLOCK_ALL_STYLES]?.purchaseToken)
    }

    @Test
    fun `with a licence key only a purchase it signed counts - one signed by another key, or changed since, doesn't`() {
        val play = keyPair()
        val signed = purchase(PURCHASED, signedBy = play)
        assertSame(signed, listOf(signed).byProduct(licenceKey = play.licenceKey)[Product.UNLOCK_ALL_STYLES])
        assertEquals(emptyMap<Product, Purchase>(), listOf(purchase(PURCHASED, signedBy = keyPair())).byProduct(licenceKey = play.licenceKey))
        assertEquals(emptyMap<Product, Purchase>(), listOf(Purchase(signed.originalJson.replace("\"token\"", "\"forged\""), signed.signature)).byProduct(licenceKey = play.licenceKey))
        assertEquals(emptyMap<Product, Purchase>(), listOf(purchase(PURCHASED)).byProduct(licenceKey = play.licenceKey))
    }

    @Test
    fun `a licence key that can't be read counts no purchase - and with none, every purchase counts`() {
        val signed = purchase(PURCHASED, signedBy = keyPair())
        assertEquals(emptyMap<Product, Purchase>(), listOf(signed).byProduct(licenceKey = "not a key"))
        assertSame(signed, listOf(signed).byProduct(licenceKey = "")[Product.UNLOCK_ALL_STYLES])
    }

    private val KeyPair.licenceKey: String get() = Base64.getEncoder().encodeToString(public.encoded)

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    /** A purchase as Play sends it: its JSON's purchaseState is 0 once paid and 4 while pending - signed by [signedBy], if given. */
    private fun purchase(state: Int, product: String = Product.UNLOCK_ALL_STYLES.id, token: String = "token", signedBy: KeyPair? = null): Purchase {
        val json = JSONObject()
            .put("orderId", "GPA.0000-0000-0000-00000")
            .put("packageName", "net.zodac.ludo")
            .put("productId", product)
            .put("purchaseTime", 1_790_000_000_000L)
            .put("purchaseState", state)
            .put("purchaseToken", token)
            .put("acknowledged", false)
            .toString()
        val signature = signedBy?.let { pair ->
            Signature.getInstance("SHA1withRSA").run {
                initSign(pair.private)
                update(json.toByteArray(Charsets.UTF_8))
                Base64.getEncoder().encodeToString(sign())
            }
        }
        return Purchase(json, signature ?: "signature")
    }

    private companion object {
        const val PURCHASED = 0
        const val PENDING = 4
    }
}
