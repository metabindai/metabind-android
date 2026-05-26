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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    apiKeyRepository: ApiKeyRepository,
) : ViewModel() {

    val assistant = MetabindAssistant(
        apiKey = apiKeyRepository.getApiKey() ?: "",
        orgId = BuildConfig.METABIND_ORG_ID,
        projectId = BuildConfig.METABIND_PROJECT_ID,
        agentHost = BuildConfig.METABIND_AGENT_HOST,
    )

    override fun onCleared() {
        assistant.close()
    }
}
