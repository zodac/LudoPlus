package net.zodac.ludo.purchases

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * What the store last said this device's player has ([Entitlement]), in a file of its own
 * ([net.zodac.ludo.data.PreferencesFile.PURCHASES]) so no reset button touches it. Never in the cloud save: the store
 * account owns the purchases, and may not be the online game service's account.
 */
class PurchasesRepository(private val dataStore: DataStore<Preferences>) {

    /** Whether the player has [entitlement]: false until the store first says so. */
    fun has(entitlement: Entitlement): Flow<Boolean> = dataStore.data.map { it[key(entitlement)] ?: false }.distinctUntilChanged()

    suspend fun set(entitlement: Entitlement, has: Boolean) {
        dataStore.edit { it[key(entitlement)] = has }
    }

    /**
     * Keeps each entitlement to what [store] says, for as long as it's collected: set once a product granting it is owned,
     * cleared once the store answers that none is (a refund, a pending payment, another store account). A store that
     * hasn't answered, or isn't there, changes nothing.
     */
    suspend fun follow(store: Store) = coroutineScope {
        for (entitlement in Entitlement.entries) {
            launch { store.state.mapNotNull { it.has(entitlement) }.distinctUntilChanged().collect { set(entitlement, it) } }
        }
    }

    private fun key(entitlement: Entitlement) = booleanPreferencesKey(entitlement.id)
}
