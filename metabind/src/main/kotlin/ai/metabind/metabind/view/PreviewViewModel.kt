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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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

    private val jsCallMutex = Mutex()
    private val rerenderScheduled = AtomicBoolean(false)
    private val pendingDragData = AtomicReference<Pair<String, Array<Any>>?>(null)

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
            is UiEvent.OnDrag -> callDragEventHandler(event.handlerId, arrayOf(event.state))
            else -> {}
        }
    }

    private fun callEventHandler(handlerId: String, data: Array<Any> = emptyArray()) {
        (_uiState.value as? UiState.Success)?.let { state ->
            viewModelScope.launch(Dispatchers.IO) {
                jsCallMutex.withLock {
                    Log.d(TAG, "Call eventHandler. $handlerId")
                    jsRuntime.callEventHandler(handlerId, data)
                    scheduleRerender(state)
                }
            }
        }
    }

    /** Coalesce rerenders. */
    private suspend fun scheduleRerender(state: UiState.Success) {
        if (!rerenderScheduled.compareAndSet(false, true)) {
            Log.d(TAG, "RERENDER_COALESCED")
            return // Already scheduled, coalesce
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.yield() // Allow more events to coalesce
                // Re-read current state to avoid overwriting newer updates
                val current = _uiState.value as? UiState.Success ?: return@launch
                val startTime = System.currentTimeMillis()
                jsRuntime.willRender()
                val component = jsRuntime.callComponent(current.componentName)
                _uiState.value = current.copy(
                    component = component,
                    componentVersion = current.componentVersion + 1
                )
                Log.d(TAG, "RERENDER_COMPLETE took=${System.currentTimeMillis() - startTime}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Rerender failed", e)
            } finally {
                rerenderScheduled.set(false)
            }
        }
    }

    /** Optimized drag handler using latest-wins pattern. */
    private fun callDragEventHandler(handlerId: String, data: Array<Any>) {
        pendingDragData.set(handlerId to data)
        (_uiState.value as? UiState.Success)?.let { state ->
            viewModelScope.launch(Dispatchers.IO) {
                jsCallMutex.withLock {
                    val latestDrag = pendingDragData.getAndSet(null) ?: return@withLock
                    val startTime = System.currentTimeMillis()
                    jsRuntime.callEventHandler(latestDrag.first, latestDrag.second)
                    Log.d(TAG, "EVENT_JS_DONE handler=drag took=${System.currentTimeMillis() - startTime}ms")
                    scheduleRerender(state)
                }
            }
        }
    }

    /** Re-render in response to a JS-driven (deferred) state change. */
    private suspend fun rerenderFromJs() {
        val state = _uiState.value as? UiState.Success ?: return
        jsCallMutex.withLock {
            scheduleRerender(state)
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

