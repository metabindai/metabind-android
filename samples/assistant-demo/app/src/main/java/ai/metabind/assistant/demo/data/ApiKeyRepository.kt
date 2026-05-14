/*
 * ApiKeyRepository.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.data

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyRepository @Inject constructor() {
    private var apiKey: String? = null

    fun getApiKey(): String? = apiKey

    fun setApiKey(key: String) {
        apiKey = key.trim()
    }
}
