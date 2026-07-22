/*
 * HomeViewModel.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.ui.screens

import ai.metabind.assistant.MetabindAssistant
import ai.metabind.assistant.demo.BuildConfig
import ai.metabind.assistant.demo.data.ApiKeyRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    apiKeyRepository: ApiKeyRepository,
) : ViewModel() {

    private val _assistant = MutableStateFlow<MetabindAssistant?>(null)
    val assistant: StateFlow<MetabindAssistant?> = _assistant.asStateFlow()

    init {
        viewModelScope.launch {
            _assistant.value = MetabindAssistant(
                apiKey = apiKeyRepository.getApiKey() ?: "",
                orgId = BuildConfig.METABIND_ORG_ID,
                projectId = BuildConfig.METABIND_PROJECT_ID,
                agentHost = BuildConfig.METABIND_AGENT_HOST,
            )
        }
    }

    override fun onCleared() {
        _assistant.value?.close()
    }
}
