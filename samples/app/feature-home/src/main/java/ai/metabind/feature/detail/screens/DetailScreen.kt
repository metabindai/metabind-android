@file:OptIn(ExperimentalPermissionsApi::class)

package ai.metabind.feature.detail.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import ai.metabind.metabind.view.MetabindView

@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
) {
    val viewState = viewModel.viewState.collectAsState().value

    BackHandler(enabled = true) {
        viewModel.onBackPressed()
    }

    DetailContent(
        viewState = viewState,
        onClose = { viewModel.onBackPressed() },
    )
}

@Composable
fun DetailContent(
    viewState: DetailViewModel.ViewState,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (viewState) {
                is DetailViewModel.ViewState.Loading -> LoadingState()
                is DetailViewModel.ViewState.Success -> LoadedState(contentId = viewState.contentId)
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .systemBarsPadding()
                .padding(start = 12.dp)
                .size(32.dp)
                .clip(CircleShape)
                .align(Alignment.TopStart),
            colors = IconButtonDefaults.outlinedIconButtonColors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        ) {
            Icon(Icons.Default.Close, null)
        }
    }
}

@Composable
fun LoadedState(contentId: String) {
    MetabindView(contentId = contentId, enableSubscription = true)
}

@Composable
private fun BoxScope.LoadingState() {
    CircularProgressIndicator(
        modifier = Modifier
            .width(32.dp)
            .align(Alignment.Center),
    )
}
