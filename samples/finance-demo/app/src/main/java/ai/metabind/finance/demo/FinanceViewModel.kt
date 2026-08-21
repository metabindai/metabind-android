/*
 * FinanceViewModel.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo

import ai.metabind.ai.MetabindAssistant
import ai.metabind.finance.demo.data.ApiKeyRepository
import ai.metabind.finance.demo.ui.AnswerRouter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the assistant and the router built on it, so both outlive configuration
 * changes and a thread in flight isn't lost to a rotation.
 */
@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val apiKeyRepository: ApiKeyRepository,
) : ViewModel() {

    sealed interface State {
        /** Reading the stored key. Brief, but not instant — DataStore is off-thread. */
        data object Loading : State
        data object NeedsKey : State
        data class Ready(val router: AnswerRouter) : State
    }

    var state by mutableStateOf<State>(State.Loading)
        private set

    private var assistant: MetabindAssistant? = null
    private var router: AnswerRouter? = null

    init {
        viewModelScope.launch {
            val key = apiKeyRepository.initialKey()
            if (key.isBlank()) state = State.NeedsKey else start(key)
        }
    }

    /** Persist [key] and stand up an assistant on it. */
    fun start(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            apiKeyRepository.save(trimmed)
            release()
            // A Metabind API key authenticates against both the agent proxy and the
            // MCP server — one key, two endpoints. The proxy holds the upstream
            // model-provider credentials, so no LLM key ever reaches the app.
            val created = MetabindAssistant(
                apiKey = trimmed,
                orgId = BuildConfig.FINANCE_DEMO_ORG_ID,
                projectId = BuildConfig.FINANCE_DEMO_PROJECT_ID,
                agentHost = BuildConfig.FINANCE_DEMO_AGENT_HOST,
                mcpHost = BuildConfig.FINANCE_DEMO_MCP_HOST,
            )
            assistant = created
            // No chat, no navigation stack — the assistant's first answer is the
            // screen. See `HomeScreen`.
            router = AnswerRouter(created, viewModelScope).also { state = State.Ready(it) }
        }
    }

    /** Forgets the key and drops back to the entry screen. The reset is sticky. */
    fun resetApiKey() {
        viewModelScope.launch {
            apiKeyRepository.reset()
            release()
            state = State.NeedsKey
        }
    }

    private fun release() {
        router?.close()
        router = null
        assistant?.close()
        assistant = null
    }

    override fun onCleared() {
        release()
    }
}
