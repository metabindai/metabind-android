package ai.metabind.feature.home.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.metabind.data.home.preview.MCPPreviewLink
import ai.metabind.data.home.room.RecentItem
import ai.metabind.data.home.room.RecentsRepository
import ai.metabind.ui.delegates.AnalyticsDelegate
import ai.metabind.ui.delegates.AnalyticsDelegateImpl
import ai.metabind.ui.navigation.NavigationConductor
import ai.metabind.ui.navigation.Screens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ScanLinkViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val navigationConductor: NavigationConductor,
    private val recentsRepository: RecentsRepository,
) : ViewModel(), AnalyticsDelegate by AnalyticsDelegateImpl(navigationName = "ScanLink") {
    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    fun openPreview(input: String) {
        if (_viewState.value.opening) return
        _viewState.value = ViewState(opening = true)
        viewModelScope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    val project = MCPPreviewLink.parse(input)
                    val safeUrl = if (project != null) {
                        project.previewUrl
                    } else MCPPreviewLink.contentUrl(input)
                    recentsRepository.insert(RecentItem(url = safeUrl,
                        name = project?.title, lastVisited = System.currentTimeMillis() / 1000))
                }
                navigationConductor.request(Screens.Detail(id))
                // Keep scanning disabled until this screen leaves composition.
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _viewState.value = ViewState(error = "Unable to open preview. Use a valid Metabind content or project link.")
            }
        }
    }

    fun clearError() { _viewState.value = ViewState() }
    data class ViewState(val opening: Boolean = false, val error: String? = null)
}
