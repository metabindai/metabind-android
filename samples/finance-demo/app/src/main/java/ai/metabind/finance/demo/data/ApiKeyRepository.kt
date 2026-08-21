/*
 * ApiKeyRepository.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo.data

import ai.metabind.finance.demo.BuildConfig
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "metabind_finance")

/**
 * Persists the Metabind API key so the app skips the key-entry screen on later
 * launches.
 *
 * All reads and writes suspend — DataStore confines the actual disk IO to its own
 * background dispatcher, so nothing here touches the main thread.
 *
 * The iOS original stores the key in the Keychain, which survives app deletion.
 * DataStore does not, so uninstalling is a reset here — and unlike Keychain there
 * is no hardware-backed protection. That is deliberate for a sample; a production
 * app should issue short-lived per-user credentials from a backend rather than
 * persisting a shared key at all.
 */
@Singleton
class ApiKeyRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.dataStore

    /**
     * Stored key if there is one, else the key configured into the build — unless
     * the user has explicitly reset, in which case they get nothing and the entry
     * screen.
     */
    suspend fun initialKey(): String {
        val prefs = dataStore.data.first()
        prefs[API_KEY]?.takeIf { it.isNotBlank() }?.let { return it }
        // Sticky, so a relaunch doesn't quietly reinstate the configured demo key.
        // Without it the reset would appear to work and then undo itself.
        if (prefs[WAS_RESET] == true) return ""
        return BuildConfig.FINANCE_DEMO_API_KEY.trim()
    }

    suspend fun save(key: String) {
        dataStore.edit { prefs ->
            prefs[API_KEY] = key.trim()
            prefs[WAS_RESET] = false
        }
    }

    suspend fun reset() {
        dataStore.edit { prefs ->
            prefs.remove(API_KEY)
            prefs[WAS_RESET] = true
        }
    }

    private companion object {
        val API_KEY = stringPreferencesKey("metabind_api_key")
        val WAS_RESET = booleanPreferencesKey("metabind_api_key_was_reset")
    }
}
