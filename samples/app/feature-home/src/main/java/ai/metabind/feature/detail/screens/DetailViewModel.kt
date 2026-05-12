package ai.metabind.feature.detail.screens

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Serializable
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val itemRepository: RecentsRepository,
    private val navigationConductor: NavigationConductor,
) : ViewModel(),
    AnalyticsDelegate by AnalyticsDelegateImpl(
        navigationName = "Detail"
    ),
    ViewStateProviderDelegate<DetailViewModel.ViewState> by ViewStateProviderDelegateImpl(
        ViewState.Loading,
        savedState
    ) {

    fun initialize(itemId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = itemRepository.getById(itemId) ?: return@launch
            updateState(ViewState.Success(contentId = item.token))
        }
    }

    fun onBackPressed() {
        navigationConductor.request(Screens.RecentsPop)
    }

    sealed class ViewState : Serializable {
        object Loading : ViewState()
        data class Success(
            val contentId: String,
        ) : ViewState()
    }
}
