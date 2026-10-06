package ai.metabind.ui.app

import androidx.lifecycle.ViewModel
import ai.metabind.ui.navigation.NavigationConductor
import ai.metabind.data.home.room.RecentsRepository
import ai.metabind.data.home.room.RecentItem
import ai.metabind.data.home.preview.MCPPreviewLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class AppViewModel @Inject constructor(
    private val navigationConductor: NavigationConductor,
    private val recents: RecentsRepository,
) : ViewModel() {
    suspend fun importPreview(link: String): Long = withContext(Dispatchers.IO) {
        val project = MCPPreviewLink.parse(link)
        recents.insert(RecentItem(url = project?.previewUrl ?: MCPPreviewLink.contentUrl(link),
            name = project?.title, lastVisited = System.currentTimeMillis() / 1000))
    }
}
