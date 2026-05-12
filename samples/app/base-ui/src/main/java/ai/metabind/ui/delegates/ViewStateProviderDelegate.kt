package ai.metabind.ui.delegates

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.Serializable

/**
 * Defines expected behavior for a screen that needs to load data. Implementers can provide any
 * ViewState they like. Will generally be consumed by a Compose Screen to populate its body.
 */
interface ViewStateProviderDelegate<T : Serializable> {
    fun updateState(newState: T)
    val viewState: StateFlow<T>
}

class ViewStateProviderDelegateImpl<T : Serializable>(
    initialState: T,
    private val savedState: SavedStateHandle,
) : ViewStateProviderDelegate<T> {
    private val savedStateKey = "STATE"

    /**
     * Describes the current state of this screen.
     */
    private val _viewState: MutableStateFlow<T> = MutableStateFlow(initialState)
    override val viewState: StateFlow<T> = _viewState

    init {
        if (savedState.contains(savedStateKey)) {
            savedState.get<T>(savedStateKey)?.also { restoredState ->
                Timber.d("Restoring saved state: $restoredState")
                updateState(restoredState)
            }
        }
    }

    /**
     * Update the state.
     * All state changes should be sent through this function.
     */
    override fun updateState(newState: T) {
        _viewState.value = newState
        savedState.set(savedStateKey, newState)
    }

}
