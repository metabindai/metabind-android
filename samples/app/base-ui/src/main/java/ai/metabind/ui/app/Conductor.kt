package ai.metabind.ui.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A lightweight wrapper around a MutableSharedFlow that adds some semantics for communicating
 * requests from one part of the app (typically the viewmodel layer) to another part
 * (typically the UI layer). This facilitates loose coupling and lets us safely handle single-
 * fire events in the Compose framework.
 */
abstract class Conductor<T>(val default: T) {

    private val _flow: MutableSharedFlow<T> = MutableSharedFlow(extraBufferCapacity = 1)
    val flow: SharedFlow<T> = _flow.asSharedFlow()

    fun request(value: T) {
        _flow.tryEmit(value)
    }

    fun clear() {
        _flow.tryEmit(default)
    }

}
