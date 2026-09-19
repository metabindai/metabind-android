package ai.metabind.feature.detail.screens

import ai.metabind.ai.MetabindAssistant
import ai.metabind.ai.MetabindAgentProvider
import ai.metabind.data.home.preview.MCPPreviewLink
import ai.metabind.data.home.preview.PreviewOAuth
import android.content.Intent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.metabind.data.home.room.RecentsRepository
import ai.metabind.ui.delegates.AnalyticsDelegate
import ai.metabind.ui.delegates.AnalyticsDelegateImpl
import ai.metabind.ui.delegates.ViewStateProviderDelegate
import ai.metabind.ui.delegates.ViewStateProviderDelegateImpl
import ai.metabind.ui.navigation.NavigationConductor
import ai.metabind.ui.navigation.Screens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Serializable
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val itemRepository: RecentsRepository,
    private val oauth: PreviewOAuth,
    private val navigationConductor: NavigationConductor,
) : ViewModel(),
    AnalyticsDelegate by AnalyticsDelegateImpl(
        navigationName = "Detail"
    ),
    ViewStateProviderDelegate<DetailViewModel.ViewState> by ViewStateProviderDelegateImpl(
        ViewState.Loading,
        savedState
    ) {

    var assistant: MetabindAssistant? = null
        private set
    private var currentItem: Long? = null
    private var project: MCPPreviewLink? = null
    private var opening: Job? = null
    private val browserIntents = Channel<Intent>(Channel.BUFFERED)
    val signInIntents = browserIntents.receiveAsFlow()

    fun initialize(itemId: Long) {
        if (currentItem == itemId && viewState.value !is ViewState.Error) return
        currentItem = itemId
        updateState(ViewState.Loading)
        opening?.cancel()
        opening = viewModelScope.launch {
            try {
                val item = withContext(Dispatchers.IO) { itemRepository.getById(itemId) }
                    ?: error("Missing preview")
                val project = MCPPreviewLink.parse(item.url)
                if (project == null) {
                    updateState(ViewState.Success(contentId = item.token))
                } else {
                    this@DetailViewModel.project = project
                    if (project.isDraft && !oauth.hasSession(project)) {
                        updateState(ViewState.SignIn(project.title))
                    } else openProject(project)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: PreviewOAuth.SignInRequired) {
                updateState(ViewState.SignIn(project?.title ?: "MCP Project"))
            } catch (_: Exception) {
                assistant?.close()
                assistant = null
                updateState(ViewState.Error)
            }
        }
    }

    private suspend fun openProject(project: MCPPreviewLink) {
        assistant?.close()
        val tokenProvider: (suspend () -> String)? = if (project.isDraft) {
            {
                try { oauth.accessToken(project) }
                catch (error: PreviewOAuth.SignInRequired) {
                    viewModelScope.launch {
                        assistant?.close()
                        assistant = null
                        updateState(ViewState.SignIn(project.title))
                    }
                    throw error
                }
            }
        } else null
        val chat = MetabindAssistant(orgId = project.organizationId,
            projectId = project.projectId, mcpHost = project.mcpHost, draft = project.isDraft,
            agentHost = if (project.isDevelopment) MetabindAgentProvider.DEVELOPMENT_HOST else MetabindAgentProvider.PRODUCTION_HOST,
            accessTokenProvider = tokenProvider)
        assistant = chat
        chat.awaitReady()
        updateState(ViewState.Project(project.title, project.isDevelopment, project.isDraft))
    }

    fun signIn() {
        val project = project ?: return
        if ((viewState.value as? ViewState.SignIn)?.busy == true) return
        updateState(ViewState.SignIn(project.title, busy = true))
        viewModelScope.launch {
            try { browserIntents.send(oauth.authorizationIntent(project)) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { signInFailed(project) }
        }
    }

    fun finishSignIn(data: Intent?) {
        viewModelScope.launch {
            opening?.join()
            val project = project ?: return@launch
            updateState(ViewState.Loading)
            try {
                oauth.finish(project, data)
                openProject(project)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { signInFailed(project) }
        }
    }

    fun browserUnavailable() { project?.let(::signInFailed) }

    private fun signInFailed(project: MCPPreviewLink) {
        assistant?.close()
        assistant = null
        updateState(ViewState.SignIn(project.title, error = "Sign-in was not completed. Use an account with access to this project and try again."))
    }

    fun disconnect() {
        val project = project ?: return
        assistant?.close()
        assistant = null
        viewModelScope.launch {
            oauth.disconnect(project)
            updateState(ViewState.SignIn(project.title))
        }
    }

    fun retry() { currentItem?.let(::initialize) }

    override fun onCleared() {
        assistant?.close()
        super.onCleared()
    }

    fun onBackPressed() {
        navigationConductor.request(Screens.RecentsPop)
    }

    sealed class ViewState : Serializable {
        object Loading : ViewState()
        object Error : ViewState()
        data class Project(val title: String, val development: Boolean, val draft: Boolean) : ViewState()
        data class SignIn(val title: String, val busy: Boolean = false, val error: String? = null) : ViewState()
        data class Success(
            val contentId: String,
        ) : ViewState()
    }
}
