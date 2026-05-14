/*
 * KeyEntryViewModel.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.ui.screens

import ai.metabind.assistant.demo.data.ApiKeyRepository
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class KeyEntryViewModel @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository
) : ViewModel() {

    fun setApiKey(key: String) {
        apiKeyRepository.setApiKey(key)
    }
}
