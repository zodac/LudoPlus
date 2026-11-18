package net.zodac.ludoplus.purchases

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.zodac.ludoplus.data.InMemoryPreferences

private fun ready(vararg owned: Pair<Product, ProductState>) =
    StoreState.Ready(Product.entries.associateWith { ProductState.ForSale("£2.99") } + owned)

/** What the device keeps of the store's answers: the entitlements the style locks and the ad banner read. */
@OptIn(ExperimentalCoroutinesApi::class)
class PurchasesRepositoryTest {

    @Test
    fun `nothing is had until the store says so`() = runTest {
        val repository = PurchasesRepository(InMemoryPreferences())
        val store = FakeStore(StoreState.Loading)
        follow(repository, store)
        assertFalse(repository.has(Entitlement.ALL_STYLES).first())
        store.state.value = ready()
        runCurrent()
        assertFalse(repository.has(Entitlement.ALL_STYLES).first())
        assertFalse(repository.has(Entitlement.NO_ADS).first())
    }

    @Test
    fun `each purchase grants its own - the combined one both`() = runTest {
        val repository = PurchasesRepository(InMemoryPreferences())
        val store = FakeStore(ready())
        follow(repository, store)
        store.state.value = ready(Product.UNLOCK_ALL_STYLES to ProductState.Owned)
        runCurrent()
        assertTrue(repository.has(Entitlement.ALL_STYLES).first())
        assertFalse(repository.has(Entitlement.NO_ADS).first())

        store.state.value = ready(Product.REMOVE_ADS to ProductState.Owned)
        runCurrent()
        assertFalse(repository.has(Entitlement.ALL_STYLES).first())
        assertTrue(repository.has(Entitlement.NO_ADS).first())

        store.state.value = ready(Product.UNLOCK_ALL_STYLES_AND_REMOVE_ADS to ProductState.Owned)
        runCurrent()
        assertTrue(repository.has(Entitlement.ALL_STYLES).first())
        assertTrue(repository.has(Entitlement.NO_ADS).first())
    }

    @Test
    fun `a refund clears it - a store that hasn't answered or isn't there keeps it`() = runTest {
        val repository = PurchasesRepository(InMemoryPreferences()).apply { set(Entitlement.NO_ADS, true) }
        val store = FakeStore(StoreState.Loading)
        follow(repository, store)
        store.state.value = StoreState.Unavailable
        runCurrent()
        assertTrue(repository.has(Entitlement.NO_ADS).first())
        store.state.value = ready()
        runCurrent()
        assertFalse(repository.has(Entitlement.NO_ADS).first())
    }

    @Test
    fun `a pending payment never grants it`() = runTest {
        val repository = PurchasesRepository(InMemoryPreferences())
        val store = FakeStore(ready())
        follow(repository, store)
        store.state.value = ready(Product.UNLOCK_ALL_STYLES to ProductState.Pending)
        runCurrent()
        assertFalse(repository.has(Entitlement.ALL_STYLES).first())
        store.state.value = ready(Product.UNLOCK_ALL_STYLES to ProductState.Owned)
        runCurrent()
        assertTrue(repository.has(Entitlement.ALL_STYLES).first())
    }

    @Test
    fun `only an answer from the store says what is had`() {
        assertEquals(true, ready(Product.UNLOCK_ALL_STYLES_AND_REMOVE_ADS to ProductState.Owned).has(Entitlement.NO_ADS))
        assertEquals(false, ready(Product.REMOVE_ADS to ProductState.Pending).has(Entitlement.NO_ADS))
        assertNull(StoreState.Loading.has(Entitlement.NO_ADS))
        assertNull(StoreState.Unavailable.has(Entitlement.ALL_STYLES))
    }

    @Test
    fun `the combined product is offered only while neither is owned`() {
        assertEquals(Product.UNLOCK_ALL_STYLES_AND_REMOVE_ADS, ready().productFor(Entitlement.ALL_STYLES, alsoWanted = true))
        assertEquals(Product.UNLOCK_ALL_STYLES, ready().productFor(Entitlement.ALL_STYLES, alsoWanted = false))
        assertEquals(Product.REMOVE_ADS, ready(Product.UNLOCK_ALL_STYLES to ProductState.Owned).productFor(Entitlement.NO_ADS, alsoWanted = true))
        assertEquals(Product.UNLOCK_ALL_STYLES_AND_REMOVE_ADS, ready().productFor(Entitlement.NO_ADS, alsoWanted = true))
    }

    @Test
    fun `stored ids and product ids are permanent`() {
        assertEquals(listOf("owns_all_styles", "no_ads"), Entitlement.entries.map { it.id })
        assertEquals(listOf("unlock_all_styles", "remove_ads", "unlock_all_styles_and_remove_ads"), Product.entries.map { it.id })
    }

    @Test
    fun `no store sells nothing`() {
        assertEquals(StoreState.Unavailable, NoStore.state.value)
    }

    /** [repository] follows [store] for the rest of the test, having taken its current state. */
    private fun TestScope.follow(repository: PurchasesRepository, store: Store) {
        backgroundScope.launch { repository.follow(store) }
        runCurrent()
    }

    private class FakeStore(initial: StoreState) : Store {
        override val state = MutableStateFlow(initial)

        override fun buy(product: Product) = true
    }
}
