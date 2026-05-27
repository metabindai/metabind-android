/*
 * PreviewViewModel.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.metabind.view

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.metabind.bindjs.JsRuntime
import ai.metabind.bindjs.JsRuntimeImpl
import ai.metabind.bindjs.composables.UiEvent
import ai.metabind.bindjs.model.BaseComponent
import ai.metabind.metabind.ComponentRepository
import ai.metabind.metabind.PreviewComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PreviewViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val jsRuntime: JsRuntime = JsRuntimeImpl.getInstance(application)
    private val componentRepository: ComponentRepository = ComponentRepository.get(application)

    companion object {
        private const val TAG = "PreviewViewModel"
    }

    fun loadContent(contentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            jsRuntime.awaitReady()
            // JS state changes that land *after* an event handler returns —
            // e.g. a swipe's settle/reshuffle scheduled via setTimeout, or a
            // resolved host.toolCall(...) hydration — signal a rerender through
            // this listener. Without it only the synchronous state set during
            // an event handler is rendered, so deferred updates silently stall.
            jsRuntime.setOnRerenderRequested {
                viewModelScope.launch(Dispatchers.IO) { rerenderFromJs() }
            }
            updateComponent(componentRepository.getPreviewByToken(contentId, true))
        }
    }

    private suspend fun updateComponent(result: Result<PreviewComponent>) {
        result.fold(
            onSuccess = { component ->
                try {
                    jsRuntime.setComponents(component.toDesignerComponent())
                    val jsComponent = jsRuntime.callComponentThumbnail(component.name, component.isContent)
                    val nextVersion =
                        (_uiState.value as? UiState.Success)?.componentVersion?.plus(1) ?: 1
                    _uiState.value = UiState.Success(
                        component = jsComponent,
                        componentName = component.name,
                        componentVersion = nextVersion
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading component", e)
                    _uiState.value = UiState.Error
                }
            },
            onFailure = { e ->
                Log.e(TAG, "Error loading component", e)
                _uiState.value = UiState.Error
            }
        )
    }

    fun onUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnAppear -> callEventHandler(event.handlerId)
            is UiEvent.OnDisappear -> callEventHandler(event.handlerId)
            is UiEvent.OnTap -> callEventHandler(event.handlerId)
            is UiEvent.OnLongPress -> callEventHandler(event.handlerId)
            is UiEvent.OnDrag -> callEventHandler(event.handlerId, arrayOf(event.state))
            else -> {}
        }
    }

    private fun callEventHandler(handlerId: String, data: Array<Any> = emptyArray()) {
        (_uiState.value as? UiState.Success)?.let { state ->
            viewModelScope.launch(Dispatchers.IO) {
                Log.d(TAG, "Call eventHandler. $handlerId")
                jsRuntime.callEventHandler(handlerId, data)
                jsRuntime.willRender()
                val component = jsRuntime.callComponent(state.componentName)
                _uiState.value = state.copy(
                    component = component,
                    componentVersion = state.componentVersion + 1
                )
            }
        }
    }

    /** Re-render in response to a JS-driven (deferred) state change. */
    private suspend fun rerenderFromJs() {
        val state = _uiState.value as? UiState.Success ?: return
        try {
            jsRuntime.willRender()
            val component = jsRuntime.callComponent(state.componentName)
            _uiState.value = state.copy(
                component = component,
                componentVersion = state.componentVersion + 1
            )
        } catch (e: Exception) {
            Log.e(TAG, "rerender failed", e)
        }
    }

    sealed class UiState {
        object Loading : UiState()
        data class Success(
            val component: BaseComponent<*>,
            val componentName: String,
            val componentVersion: Int = 1,
        ) : UiState()

        object Error : UiState()
    }
}

