package com.yapstudios.metabind.view

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yapstudios.bindjs.DesignerComponent
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class MetabindViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val jsRuntime: JsRuntime = JsRuntimeImpl.getInstance(application)
    private val componentRepository: ComponentRepository = ComponentRepository.get(application)

    companion object {
        private const val TAG = "MetabindViewModel"
    }

    fun loadContent(contentId: String, enableSubscription: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            jsRuntime.awaitReady()
            if (enableSubscription) {
                launch {
                    componentRepository.subscribeToPreviewByToken(contentId).collect { }
                }

                // Watch the cache: emits cached value, then network, then reacts to cache writes from subscription
                componentRepository.watchPreviewByToken(contentId)
                    .flowOn(Dispatchers.IO)
                    .collect { result ->
                        updateComponent(result)
                    }
            } else {
                updateComponent(componentRepository.getPreviewByToken(contentId, true))
            }
        }
    }

    private suspend fun updateComponent(result: Result<PreviewComponent>) {
        result.fold(
            onSuccess = { component ->
                try {
                    jsRuntime.setComponents(component.toDesignerComponent())
                    val jsComponent = jsRuntime.callComponent(component.name)
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
            is UiEvent.OnPickerTap -> callPickerSetter(event.setterId, event.tag)
        }
    }

    private fun callPickerSetter(setterId: String, value: String) {
        (_uiState.value as? UiState.Success)?.let { state ->
            viewModelScope.launch(Dispatchers.IO) {
                Log.d(TAG, "Call callPickerSetter. $setterId $value")
                jsRuntime.callPickerSetter(setterId, value)
                jsRuntime.willRender()
                val component = jsRuntime.callComponent(state.componentName)
                _uiState.value = state.copy(
                    component = component,
                    componentVersion = state.componentVersion + 1
                )
            }
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

fun String.removeJsComments(): String {
    // Regex explanation:
    //   /\*[\s\S]*?\*/   -> Matches multi-line comments (/* ... */)
    //   |                -> OR
    //   ([^\\:]|^)\/\/.*$ -> Matches single-line comments (// ...) only if they are not part of a URL (e.g., "http://" or "https://")
    //   The (?:...) part uses a non-capturing group for the URL check, preventing it from being replaced.
    val regex = Regex("/\\*[\\s\\S]*?\\*/|([^\\:]|^)//.*$", setOf(RegexOption.MULTILINE))

    // Replace comments with the first capturing group ($1), which is the part before the single-line comment (if any),
    // ensuring the text is preserved and only the comment part is removed.
    return replace(regex, "$1")
}

fun String.prepareJavaScriptString(): String {
    val replaced = this
        .replace("props: ComponentProps", "props")
        .replace("children: Component[]", "children")

    return replaced.removeJsComments()
}

fun PreviewComponent.toDesignerComponent(): DesignerComponent {
    val component = DesignerComponent(
        name = name,
        content = content.prepareJavaScriptString(),
        dependencies = dependencies.map {
            DesignerComponent(
                name = it.key,
                content = it.value.prepareJavaScriptString()
            )
        }
    )
    return component
}
