/*
 * PreviewViewModel.kt.
 *
 * © 2026 Yap Studios LLC
 */
package com.yapstudios.metabind.view

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yapstudios.bindjs.JsRuntime
import com.yapstudios.bindjs.JsRuntimeImpl
import com.yapstudios.bindjs.composables.UiEvent
import com.yapstudios.bindjs.model.Component
import com.yapstudios.metabind.ComponentRepository
import com.yapstudios.metabind.PreviewComponent
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
            updateComponent(componentRepository.getPreviewByToken(contentId, true))
        }
    }

    private suspend fun updateComponent(result: Result<PreviewComponent>) {
        result.fold(
            onSuccess = { component ->
                try {
                    jsRuntime.setComponents(component.toDesignerComponent())
                    val jsComponent = jsRuntime.callComponentThumbnail(component.name)
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
            is UiEvent.OnDrag -> callEventHandler(event.handlerId)
            else -> {}
        }
    }

    private fun callEventHandler(handlerId: String) {
        (_uiState.value as? UiState.Success)?.let { state ->
            viewModelScope.launch(Dispatchers.IO) {
                Log.d(TAG, "Call eventHandler. $handlerId")
                jsRuntime.callEventHandler(handlerId)
                jsRuntime.willRender()
                val component = jsRuntime.callComponent(state.componentName)
                _uiState.value = state.copy(
                    component = component,
                    componentVersion = state.componentVersion + 1
                )
            }
        }
    }

    sealed class UiState {
        object Loading : UiState()
        data class Success(
            val component: Component,
            val componentName: String,
            val componentVersion: Int = 1,
        ) : UiState()

        object Error : UiState()
    }
}

