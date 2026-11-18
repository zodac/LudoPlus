package net.zodac.ludoplus.purchases

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shared game needs from a platform's app store (Google Play Billing on Android) for its purchases
 * ([Product]), and nothing that names it. Each platform implements it once; [NoStore] is the version for previews,
 * tests, and builds with no store. See `.claude/PURCHASES.md`.
 *
 * The store is never the source of truth while playing: [PurchasesRepository] keeps what it last said, and the game
 * reads only that. No call throws; a failure leaves [state] as it was.
 */
interface Store {

    val state: StateFlow<StoreState>

    /**
     * Opens the store's own purchase sheet for [product], while it's [ProductState.ForSale]; otherwise does nothing.
     * False if the sheet should have opened but couldn't, which the player is told, as the store shows nothing itself.
     */
    fun buy(product: Product): Boolean
}

/** What a purchase gives: each one's own permanent [id], the key [PurchasesRepository] keeps it under. */
enum class Entitlement(val id: String) {
    /** Every style and colour, without earning them. */
    ALL_STYLES("owns_all_styles"),

    /** No ad banner. */
    NO_ADS("no_ads"),
}

/**
 * The store's products, each a one-time purchase never consumed, so bought once per store account. [id] is the store's
 * product id: permanent once created in the store, even if deleted there.
 */
enum class Product(val id: String, val grants: Set<Entitlement>) {
    UNLOCK_ALL_STYLES("unlock_all_styles", setOf(Entitlement.ALL_STYLES)),
    REMOVE_ADS("remove_ads", setOf(Entitlement.NO_ADS)),

    /** Both of the others in one. */
    UNLOCK_ALL_STYLES_AND_REMOVE_ADS("unlock_all_styles_and_remove_ads", setOf(Entitlement.ALL_STYLES, Entitlement.NO_ADS)),
    ;

    companion object {
        fun byId(id: String): Product? = entries.firstOrNull { it.id == id }
    }
}

/** What the store says about the player's store account. */
sealed interface StoreState {

    /** There is no store on this device (another distribution, no Play Store, a preview). Nothing is offered. */
    data object Unavailable : StoreState

    /** The store hasn't answered yet. */
    data object Loading : StoreState

    /** The store has answered: each product's state. */
    data class Ready(val products: Map<Product, ProductState>) : StoreState {
        fun of(product: Product): ProductState = products[product] ?: ProductState.ForSale(null)
    }
}

/** One product on the player's store account. */
sealed interface ProductState {

    /** Not owned. [price] is the store's own localised price, or null while it couldn't be fetched. */
    data class ForSale(val price: String?) : ProductState

    /** Bought, but not paid yet (a cash or slow payment method): nothing is granted until it is [Owned]. */
    data object Pending : ProductState

    data object Owned : ProductState
}

/**
 * Whether this state says the account has [entitlement]: true where a product granting it is [ProductState.Owned],
 * false where the store answered that none is, and null where it hasn't answered, which changes nothing.
 */
fun StoreState.has(entitlement: Entitlement): Boolean? = when (this) {
    is StoreState.Ready -> products.any { (product, state) -> state == ProductState.Owned && entitlement in product.grants }
    StoreState.Unavailable, StoreState.Loading -> null
}

/**
 * The product to offer for [entitlement], alone or (with [alsoWanted]) with the other one too, given what the account
 * already has: the combined product only while neither is owned, as it would sell an owned one again.
 */
fun StoreState.Ready.productFor(entitlement: Entitlement, alsoWanted: Boolean): Product {
    val other = Entitlement.entries.single { it != entitlement }
    val single = Product.entries.single { it.grants == setOf(entitlement) }
    return if (alsoWanted && has(other) == false) Product.UNLOCK_ALL_STYLES_AND_REMOVE_ADS else single
}

val LocalStore = staticCompositionLocalOf<Store> { NoStore }
