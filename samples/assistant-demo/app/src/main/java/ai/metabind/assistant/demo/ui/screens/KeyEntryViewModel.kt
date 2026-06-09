/*
 * KeyEntryViewModel.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.ui.screens

import ai.metabind.assistant.demo.data.ApiKeyRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class KeyEntryViewModel @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository
) : ViewModel() {

    /**
     * Persists [key], then invokes [onSaved] once the write has completed so navigation
     * to Home only happens after the key is durably stored (HomeViewModel reads it back
     * from DataStore on construction).
     */
    fun saveApiKey(key: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            apiKeyRepository.setApiKey(key)
            onSaved()
        }
    }
}
