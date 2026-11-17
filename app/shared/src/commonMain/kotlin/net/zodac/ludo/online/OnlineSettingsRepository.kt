package net.zodac.ludo.online

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * What this device has done with the online service, in a file of its own ([net.zodac.ludo.data.PreferencesFile.ONLINE]),
 * never in the cloud save: which account it last synced with, how far each account's leaderboards have been sent wins,
 * and when each account's cloud save last matched it.
 */
class OnlineSettingsRepository(private val dataStore: DataStore<Preferences>) {

    /**
     * The account ([OnlineAccount.accountKey], a hash) this device last synced with, or null if it never has. A
     * different one signing in replaces the device's synced progress with its own, so accounts' data never mix.
     */
    val syncedAccount: Flow<String?> = dataStore.data.map { it[SYNCED_ACCOUNT_KEY] }

    suspend fun setSyncedAccount(accountKey: String) {
        dataStore.edit { it[SYNCED_ACCOUNT_KEY] = accountKey }
    }

    /**
     * When the newest game considered for [accountKey]'s leaderboard [leaderboardKey] ended
     * ([net.zodac.ludo.data.records.GameResult.endedAtEpochMillis]), or null if that account has had none from this
     * device. Kept per account, so each one gets this device's best wins once, whichever signed in last.
     */
    suspend fun winsPostedThrough(accountKey: String, leaderboardKey: String): Long? =
        dataStore.data.first()[winsPostedKey(accountKey, leaderboardKey)]

    suspend fun setWinsPostedThrough(accountKey: String, leaderboardKey: String, epochMillis: Long) {
        dataStore.edit { it[winsPostedKey(accountKey, leaderboardKey)] = epochMillis }
    }

    /** When [accountKey]'s cloud save last matched this device ("Saved"), or null if it never has. */
    fun cloudSavedAt(accountKey: String): Flow<Long?> = dataStore.data.map { it[cloudSavedKey(accountKey)] }

    suspend fun setCloudSavedAt(accountKey: String, epochMillis: Long) {
        dataStore.edit { it[cloudSavedKey(accountKey)] = epochMillis }
    }

    private companion object {
        fun cloudSavedKey(accountKey: String) = longPreferencesKey("cloud_saved:$accountKey")

        fun winsPostedKey(accountKey: String, leaderboardKey: String) = longPreferencesKey("wins_posted:$accountKey:$leaderboardKey")

        val SYNCED_ACCOUNT_KEY = stringPreferencesKey("synced_account")
    }
}
