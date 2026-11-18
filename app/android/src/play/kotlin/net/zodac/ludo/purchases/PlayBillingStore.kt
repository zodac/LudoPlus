package net.zodac.ludo.purchases

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryPurchasesAsync
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import net.zodac.ludo.BuildConfig
import net.zodac.ludo.PLAY_STORE_PACKAGE
import net.zodac.ludo.installerPackage

/**
 * [Store] on Google Play Billing, the only code that imports the Billing Library. One per process, so its state survives
 * a configuration change; each Activity attaches itself, as Play's purchase sheet needs one. Present only where
 * [usesPlayBilling]; everywhere else the app gets [NoStore].
 *
 * What the account owns is asked on connecting and on every resume, as a payment can complete, and a refund take
 * effect, while the app is closed. That ask is also what restores a purchase on a reinstall or a new phone. A failed
 * ask leaves [state] as it was. A purchase counts only once it is [Purchase.PurchaseState.PURCHASED], and is then
 * acknowledged (Play refunds one left unacknowledged for three days); one that wasn't is acknowledged on the next ask.
 * None of the calls throws: a failure is logged under [LOG_TAG] with Play's response code. Every call has a timeout,
 * bar the purchase sheet, which waits for the player.
 */
class PlayBillingStore private constructor(application: Application) : Store {

    private val mutableState = MutableStateFlow<StoreState>(StoreState.Loading)
    override val state: StateFlow<StoreState> = mutableState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activity: WeakReference<ComponentActivity>? = null

    /** Each product, with its price, once Play has sent it: fetched until every one has been. */
    private var productDetails: Map<Product, ProductDetails> = emptyMap()

    /** Every purchase Play has reported for the account, by product, as of the last ask or update. */
    private var owned: Map<Product, Purchase> = emptyMap()

    /**
     * What to show, one at a time, in order: purchases Play sent ([onPurchasesUpdated]), or null to ask Play what the
     * account owns. So an ask that was already under way never overwrites a purchase that arrived after it began.
     */
    private val updates = Channel<List<Purchase>?>(Channel.UNLIMITED)

    private val client = BillingClient.newBuilder(application)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        // A call that finds the connection lost reconnects first, so a lost connection needs nothing here.
        .enableAutoServiceReconnection()
        .build()

    init {
        scope.launch {
            for (purchases in updates) if (purchases == null) refresh() else show(owned + purchases.byProduct())
        }
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    when (result.responseCode) {
                        BillingResponseCode.OK -> requestRefresh()
                        // This Play Store or account can't buy at all (an old Play Store, a country without Play billing).
                        BillingResponseCode.BILLING_UNAVAILABLE -> {
                            failed("startConnection", result)
                            mutableState.value = StoreState.Unavailable
                        }
                        else -> failed("startConnection", result)
                    }
                }

                override fun onBillingServiceDisconnected() = Unit
            },
        )
    }

    private fun attach(activity: ComponentActivity) {
        this.activity = WeakReference(activity)
        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> requestRefresh()
                    Lifecycle.Event.ON_DESTROY -> if (this.activity?.get() === activity) this.activity = null
                    else -> Unit
                }
            },
        )
    }

    override fun buy(product: Product): Boolean {
        val state = mutableState.value as? StoreState.Ready ?: return true
        if (state.of(product) !is ProductState.ForSale) return true
        val activity = activity?.get() ?: return false
        val details = productDetails[product] ?: return false
        val offer = details.oneTimePurchaseOfferDetails ?: return false
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { offer.offerToken?.let(::setOfferToken) }
            .build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(params)).build())
        when (result.responseCode) {
            BillingResponseCode.OK -> Unit
            // Bought on another device since this one last asked.
            BillingResponseCode.ITEM_ALREADY_OWNED -> requestRefresh()
            else -> failed("launchBillingFlow", result)
        }
        return sheetAnswered(result.responseCode)
    }

    /** A purchase made in Play's sheet, or a pending one completing, while the app is running. */
    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> purchases?.let { updates.trySend(it) }
            BillingResponseCode.USER_CANCELED -> Unit
            BillingResponseCode.ITEM_ALREADY_OWNED -> requestRefresh()
            else -> failed("onPurchasesUpdated", result)
        }
    }

    private fun requestRefresh() {
        updates.trySend(null)
    }

    private suspend fun refresh() {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        val purchases = withTimeoutOrNull(TIMEOUT_MILLIS) { client.queryPurchasesAsync(params) }
            ?: return failed("queryPurchases timed out")
        if (purchases.billingResult.responseCode != BillingResponseCode.OK) return failed("queryPurchases", purchases.billingResult)
        show(purchases.purchasesList.byProduct())
    }

    /** Shows what [purchases] mean for the account, and acknowledges each once it's paid for. */
    private suspend fun show(purchases: Map<Product, Purchase>) {
        owned = purchases
        if (Product.entries.any { it !in productDetails }) productDetails = productDetails + queryProductDetails()
        mutableState.value = storeState(purchases, productDetails.mapValues { (_, details) -> details.oneTimePurchaseOfferDetails?.formattedPrice })
        for (purchase in purchases.values.distinct()) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) acknowledge(purchase)
        }
    }

    private suspend fun acknowledge(purchase: Purchase) {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
        val result = withTimeoutOrNull(TIMEOUT_MILLIS) { client.acknowledgePurchase(params) }
            ?: return failed("acknowledgePurchase timed out")
        if (result.responseCode != BillingResponseCode.OK) failed("acknowledgePurchase", result)
    }

    /** The products Play sent, with their prices; each it didn't send has its reason logged (not yet active, say). */
    private suspend fun queryProductDetails(): Map<Product, ProductDetails> {
        val products = Product.entries.map {
            QueryProductDetailsParams.Product.newBuilder().setProductId(it.id).setProductType(ProductType.INAPP).build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
        // Through the listener rather than billing-ktx's queryProductDetails, which drops why a product wasn't sent.
        val (result, details) = withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Pair<BillingResult, QueryProductDetailsResult>> { continuation ->
                client.queryProductDetailsAsync(params) { result, details -> continuation.resume(result to details) }
            }
        } ?: return emptyMap<Product, ProductDetails>().also { failed("queryProductDetails timed out") }
        if (result.responseCode != BillingResponseCode.OK) return emptyMap<Product, ProductDetails>().also { failed("queryProductDetails", result) }
        details.unfetchedProductList.forEach { Log.w(LOG_TAG, "queryProductDetails: ${it.productId} not sent, status ${it.statusCode}") }
        return details.productDetailsList.mapNotNull { detail -> Product.byId(detail.productId)?.let { it to detail } }.toMap()
    }

    private fun failed(message: String, result: BillingResult? = null) {
        Log.w(LOG_TAG, if (result == null) message else "$message failed: response ${result.responseCode} (${result.debugMessage})")
    }

    companion object {

        /** Why a call failed, in logcat: `adb logcat -s LudoPlusPurchases`. */
        const val LOG_TAG = "LudoPlusPurchases"

        private const val TIMEOUT_MILLIS = 15_000L

        /** The intent the Billing Library binds the Play Store's billing service with (its manifest's `<queries>`). */
        private const val BILLING_SERVICE_ACTION = "com.android.vending.billing.InAppBillingService.BIND"

        @Volatile
        private var instance: PlayBillingStore? = null

        @Volatile
        private var enabled: Boolean? = null

        /**
         * The store for [activity] to provide, attached to it, where Play Billing is used: a device with the Play Store,
         * running a debug build or a release build installed from Google Play. Anywhere else, [NoStore].
         */
        fun attach(activity: ComponentActivity): Store {
            val enabled = enabled ?: isEnabled(activity).also { enabled = it }
            if (!enabled) return NoStore
            val store = instance ?: PlayBillingStore(activity.application).also { instance = it }
            store.attach(activity)
            return store
        }

        private fun isEnabled(context: Context): Boolean = usesPlayBilling(
            playStoreInstalled = hasPlayStoreBilling(context),
            isDebugBuild = BuildConfig.DEBUG,
            installer = installerPackage(context),
        )

        /**
         * Play Billing needs the Play Store. A release build uses it only when installed from Google Play: a sideloaded
         * one can't buy (Play checks the signature). A debug build always does, so a license tester can buy with it.
         */
        internal fun usesPlayBilling(playStoreInstalled: Boolean, isDebugBuild: Boolean, installer: String?): Boolean =
            playStoreInstalled && (isDebugBuild || installer == PLAY_STORE_PACKAGE)

        private fun hasPlayStoreBilling(context: Context): Boolean = runCatching {
            val intent = Intent(BILLING_SERVICE_ACTION).setPackage(PLAY_STORE_PACKAGE)
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                // The only way to ask before Android 13, which deprecated it for the ResolveInfoFlags one above.
                @Suppress("DEPRECATION")
                packageManager.queryIntentServices(intent, 0)
            }.isNotEmpty()
        }.getOrDefault(false)

        /** Whether [responseCode] from opening the purchase sheet needs no word to the player: it opened, or the product is already owned. */
        internal fun sheetAnswered(responseCode: Int): Boolean = responseCode == BillingResponseCode.OK || responseCode == BillingResponseCode.ITEM_ALREADY_OWNED

        /** The account's [purchases], by product, as a [StoreState]: each product not bought offered at its [prices] (null: unknown). */
        internal fun storeState(purchases: Map<Product, Purchase>, prices: Map<Product, String?>): StoreState = StoreState.Ready(
            Product.entries.associateWith { product ->
                when (purchases[product]?.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> ProductState.Owned
                    Purchase.PurchaseState.PENDING -> ProductState.Pending
                    else -> ProductState.ForSale(prices[product])
                }
            },
        )

        /**
         * The account's purchase of each product among these, a paid one first. With a [licenceKey], only a purchase it
         * signed counts ([PurchaseSignature]); with none, every one does.
         */
        internal fun List<Purchase>.byProduct(licenceKey: String = BuildConfig.PLAY_LICENCE_KEY): Map<Product, Purchase> {
            val signed = filter { licenceKey.isEmpty() || PurchaseSignature.isValid(licenceKey, it.originalJson, it.signature) }
            return Product.entries.mapNotNull { product ->
                val ours = signed.filter { product.id in it.products }
                (ours.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED } ?: ours.firstOrNull())?.let { product to it }
            }.toMap()
        }
    }
}
