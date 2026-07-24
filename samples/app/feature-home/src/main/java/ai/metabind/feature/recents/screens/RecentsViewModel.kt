package ai.metabind.feature.recents.screens

import android.content.Context
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
import ai.metabind.metabind.ComponentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Serializable
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class RecentsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val navigationConductor: NavigationConductor,
    private val recentsRepository: RecentsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel(),
    AnalyticsDelegate by AnalyticsDelegateImpl(
        navigationName = "RecentsViewModel"
    ),
    ViewStateProviderDelegate<RecentsViewModel.ViewState> by ViewStateProviderDelegateImpl(
        ViewState(),
        savedState
    ) {

    private val componentRepository by lazy { ComponentRepository.Companion.get(appContext) }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            recentsRepository.allRecents.collect { recents ->
                val existingItems = viewState.value.recents?.associateBy { it.id } ?: emptyMap()
                updateState(
                    viewState.value.copy(recents = recents.map { recent ->
                        existingItems[recent.uid] ?: RecentItemViewState(
                            id = recent.uid,
                            token = recent.token,
                            name = recent.name
                        )
                    })
                )
                recents.filter { it.name == null }.forEach { recent ->
                    launch { fetchAndSaveName(recent.uid, recent.token) }
                }
            }
        }
    }

    private suspend fun fetchAndSaveName(itemId: Long, token: String) {
        try {
            val result = componentRepository.getPreviewByToken(token, false)
            result.fold(
                onSuccess = { component ->
                    val name = component.name
                    recentsRepository.updateName(itemId, name)
                    val currentRecents = viewState.value.recents ?: return
                    updateState(
                        viewState.value.copy(recents = currentRecents.map { item ->
                            if (item.id == itemId) item.copy(name = name) else item
                        })
                    )
                },
                onFailure = { e ->
                    Timber.e(e, "Error fetching the component")
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Error fetching the component")
        }
    }

    fun onResume() {
        refresh()
    }

    private fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            recentsRepository.load()
        }
    }

    fun onRemove(itemId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            recentsRepository.delete(itemId)
            updateState(
                viewState.value.copy(recents = viewState.value.recents?.filter { it.id != itemId })
            )
        }
    }

    fun onItemClicked(itemId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            recentsRepository.updateLastVisited(
                itemId,
                LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            )
        }
        navigationConductor.request(Screens.Detail(itemId))
    }

    fun onScanClicked() {
        navigationConductor.request(Screens.ScanLink)
    }

    data class ViewState(
        val recents: List<RecentItemViewState>? = null,
    ) : Serializable

    data class RecentItemViewState(
        val id: Long,
        val token: String,
        val isLoading: Boolean = true,
        val error: Boolean = false,
        val name: String? = null,
        val errorMsg: String? = null,
    ) : Serializable
}
