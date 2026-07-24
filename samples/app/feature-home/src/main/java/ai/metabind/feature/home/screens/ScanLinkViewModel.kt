package ai.metabind.feature.home.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.metabind.data.home.room.RecentItem
import ai.metabind.data.home.room.RecentsRepository
import ai.metabind.ui.delegates.AnalyticsDelegate
import ai.metabind.ui.delegates.AnalyticsDelegateImpl
import ai.metabind.ui.delegates.ViewStateProviderDelegate
import ai.metabind.ui.delegates.ViewStateProviderDelegateImpl
import ai.metabind.ui.navigation.NavigationConductor
import ai.metabind.ui.navigation.Screens
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Serializable
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class ScanLinkViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val navigationConductor: NavigationConductor,
    private val recentsRepository: RecentsRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel(),
    AnalyticsDelegate by AnalyticsDelegateImpl(
        navigationName = "ScanLink"
    ),
    ViewStateProviderDelegate<ScanLinkViewModel.ViewState> by ViewStateProviderDelegateImpl(
        ViewState(),
        savedState
    ) {

    fun checkPasteOnStart() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val itemCount = clipboard.primaryClip?.itemCount ?: 0
        if (itemCount > 0) {
            val maybeUrl = clipboard.primaryClip?.getItemAt(0)?.text.toString()
            try {
                val url = URL(maybeUrl)
                val host = url.host?.lowercase()
                val path = url.path?.lowercase()
                if (host != null && path != null &&
                    host.endsWith("metabind.ai") &&
                    path.startsWith("/preview/") &&
                    path.length > "/preview/".length
                ) {
                    updateState(
                        viewState.value.copy(
                            showPasteAlert = true,
                            url = url.toString(),
                        )
                    )
                    return
                }
            } catch (_: Exception) {
                Timber.d("String in clipboard is not a valid URL")
            }
        }
    }

    fun onDismissPasteAlert() {
        updateState(
            viewState.value.copy(
                showPasteAlert = false,
                url = null
            )
        )
    }

    fun onConfirmPasteAlert() {
        viewModelScope.launch(Dispatchers.IO) {
            viewState.value.url?.let { url ->
                val recentItem = RecentItem(
                    url = url, lastVisited = LocalDateTime.now().toEpochSecond(
                        ZoneOffset.UTC
                    )
                )
                val insertedId = recentsRepository.insert(recentItem)
                navigationConductor.request(Screens.Detail(itemId = insertedId))
            }
            onDismissPasteAlert()
        }
    }

    fun onBarcodeRecognized(value: String) {
        try {
            val url = URL(value)
            viewModelScope.launch(Dispatchers.IO) {
                val recentItem = RecentItem(
                    url = value, lastVisited = LocalDateTime.now().toEpochSecond(
                        ZoneOffset.UTC
                    )
                )
                val insertedId = recentsRepository.insert(recentItem)
                navigationConductor.request(Screens.Detail(itemId = insertedId))
            }
        } catch (_: Exception) {
            Timber.d("QRCode is not a valid URL")
        }
    }

    data class ViewState(
        val value: String? = null,
        val showPasteAlert: Boolean = false,
        val url: String? = null,
    ) : Serializable
}
