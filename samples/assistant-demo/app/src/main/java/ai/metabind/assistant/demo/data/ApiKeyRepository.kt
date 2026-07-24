/*
 * ApiKeyRepository.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "metabind_assistant")

/**
 * Persists the Metabind API key in Jetpack DataStore so it survives process death and
 * lets the app skip the key-entry screen on subsequent launches.
 *
 * All reads/writes are suspending — DataStore confines the actual disk IO to its own
 * background dispatcher, so nothing here touches the main thread.
 */
@Singleton
class ApiKeyRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dataStore = context.dataStore

    val apiKey: Flow<String?> = dataStore.data.map { prefs -> prefs[API_KEY] }

    suspend fun getApiKey(): String? = apiKey.first()

    suspend fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    suspend fun setApiKey(key: String) {
        dataStore.edit { prefs -> prefs[API_KEY] = key.trim() }
    }

    suspend fun clearApiKey() {
        dataStore.edit { prefs -> prefs.remove(API_KEY) }
    }

    companion object {
        private val API_KEY = stringPreferencesKey("metabind_api_key")
    }
}
