/*
 * ThumbnailViewModel.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.metabind.view

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ai.metabind.bindjs.JsRuntime
import ai.metabind.bindjs.JsRuntimeImpl
import ai.metabind.metabind.ComponentRepository
import ai.metabind.metabind.createThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThumbnailViewModel(
    context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    val jsRuntime: JsRuntime = JsRuntimeImpl.getInstance(context)
    private val componentRepository: ComponentRepository = ComponentRepository.get(context)

    companion object {
        private const val TAG = "ThumbnailViewModel"

        fun factory(context: Context): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ThumbnailViewModel(context.applicationContext)
                }
            }
    }

    fun loadContent(contentId: String, context: Context) {
        Log.d(TAG, "Loading content for $contentId")
        viewModelScope.launch(Dispatchers.IO) {
            componentRepository.getPreviewByToken(contentId).fold(
                onSuccess = { component ->
                    jsRuntime.awaitReady()
                    val jsComponent = try {
                        jsRuntime.setComponents(component.toDesignerComponent())
                        jsRuntime.callComponentThumbnail(component.name, component.isContent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading component", e)
                        null
                    }

                    if (jsComponent != null) {
                        val bitmap = withContext(Dispatchers.Main) {
                            createThumbnail(context, jsRuntime, jsComponent)
                        }
                        _uiState.value = UiState.Success(
                            bitmap = bitmap,
                            isContent = component.isContent
                        )
                    } else {
                        _uiState.value = UiState.Error
                    }
                },
                onFailure = {
                    _uiState.value = UiState.Error
                }
            )
        }
    }

    sealed class UiState {
        object Loading : UiState()
        data class Success(
            val bitmap: ImageBitmap,
            val isContent: Boolean,
        ) : UiState()

        object Error : UiState()
    }
}
