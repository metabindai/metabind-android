/*
 * MetabindViewModel.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.metabind.view

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.exception.CacheMissException
import ai.metabind.bindjs.DesignerComponent
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MetabindViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val jsRuntime: JsRuntime = JsRuntimeImpl.getInstance(application)
    private val componentRepository: ComponentRepository = ComponentRepository.get(application)
    private val jsCallMutex = Mutex()
    private val rerenderScheduled = AtomicBoolean(false)
    private val pendingDragData = AtomicReference<Pair<String, Array<Any>>?>(null)

    companion object {
        private const val TAG = "MetabindViewModel"
    }

    fun loadContent(contentId: String, enableSubscription: Boolean) {
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
                    val jsComponent = renderComponent(component.name, component.isContent)
                    val nextVersion =
                        (_uiState.value as? UiState.Success)?.componentVersion?.plus(1) ?: 1
                    _uiState.value = UiState.Success(
                        component = jsComponent,
                        componentName = component.name,
                        isContent = component.isContent,
                        componentVersion = nextVersion
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading component", e)
                    _uiState.value = UiState.Error
                }
            },
            onFailure = { e ->
                if (e is CacheMissException) {
                    Log.i(TAG, "Failed to load component from cache", e)
                } else {
                    Log.e(TAG, "Failed to load component from GraphQL", e)
                    _uiState.value = UiState.Error
                }
            }
        )
    }

    fun onUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnAppear -> callEventHandler(event.handlerId)
            is UiEvent.OnDisappear -> callEventHandler(event.handlerId)
            is UiEvent.OnChange -> callEventHandler(
                event.handlerId,
                arrayOf(event.oldValue ?: "", event.newValue ?: "")
            )
            is UiEvent.OnTap -> callEventHandler(event.handlerId)
            is UiEvent.OnLongPress -> callEventHandler(event.handlerId)
            is UiEvent.OnDrag -> callDragEventHandler(event.handlerId, arrayOf(event.state))
            is UiEvent.OnPickerTap -> callPickerSetter(event.setterId, event.tag)
            is UiEvent.OnNavigationTap -> onNavigationTap(event.handlerId)
            is UiEvent.OnSwitch -> callEventHandler(event.handlerId, arrayOf(event.checked))
            is UiEvent.OnChartSelection -> callEventHandler(event.handlerId, arrayOf(event.value))
        }
    }

    private fun onNavigationTap(handlerId: String) {
        (_uiState.value as? UiState.Success)?.let { state ->
            viewModelScope.launch(Dispatchers.IO) {
                jsRuntime.callForResultComponent(handlerId)?.let { component ->
                    _uiState.value = state.copy(
                        component = component,
                        componentVersion = state.componentVersion + 1
                    )
                }
            }
        }
    }

    private fun callPickerSetter(setterId: String, value: String) {
        (_uiState.value as? UiState.Success)?.let { state ->
            viewModelScope.launch(Dispatchers.IO) {
                Log.d(TAG, "Call callPickerSetter. $setterId $value")
                jsRuntime.callPickerSetter(setterId, value)
                val component = renderComponent(state.componentName, state.isContent)
                _uiState.value = state.copy(
                    component = component,
                    componentVersion = state.componentVersion + 1
                )
            }
        }
    }

    /** Optimized drag handler using latest-wins pattern. */
    private fun callDragEventHandler(handlerId: String, data: Array<Any>) {
        pendingDragData.set(handlerId to data)
        if (_uiState.value is UiState.Success) {
            viewModelScope.launch(Dispatchers.IO) {
                jsCallMutex.withLock {
                    val latestDrag = pendingDragData.getAndSet(null) ?: return@withLock
                    jsRuntime.callEventHandler(latestDrag.first, latestDrag.second)
                    scheduleRerender()
                }
            }
        }
    }

    /** Coalesce rerenders - skip if one is already scheduled. */
    private fun scheduleRerender() {
        if (!rerenderScheduled.compareAndSet(false, true)) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                yield()
                val current = _uiState.value as? UiState.Success ?: return@launch
                val component = renderComponent(current.componentName, current.isContent)
                _uiState.value = current.copy(
                    component = component,
                    componentVersion = current.componentVersion + 1
                )
            } catch (e: Exception) {
                Log.e(TAG, "Rerender failed", e)
            } finally {
                rerenderScheduled.set(false)
            }
        }
    }

    private fun callEventHandler(handlerId: String, data: Array<Any> = emptyArray()) {
        if (_uiState.value is UiState.Success) {
            viewModelScope.launch(Dispatchers.IO) {
                jsCallMutex.withLock {
                    Log.d(TAG, "Call eventHandler. $handlerId")
                    jsRuntime.callEventHandler(handlerId, data)
                    scheduleRerender()
                }
            }
        }
    }

    /** Re-render in response to a JS-driven (deferred) state change. */
    private suspend fun rerenderFromJs() {
        jsCallMutex.withLock {
            scheduleRerender()
        }
    }

    // Atomic willRender + component call (see JsRuntime.renderComponent): the
    // pair must not be split, or concurrent drag re-renders corrupt the shared
    // JS hook state and handlers stop firing.
    private suspend fun renderComponent(name: String, isContent: Boolean): BaseComponent<*> =
        if (isContent) {
            jsRuntime.renderComponent(name)
        } else {
            jsRuntime.renderComponentPreview(name, 0)
        }

    sealed class UiState {
        object Loading : UiState()
        data class Success(
            val component: BaseComponent<*>,
            val componentName: String,
            val isContent: Boolean,
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
