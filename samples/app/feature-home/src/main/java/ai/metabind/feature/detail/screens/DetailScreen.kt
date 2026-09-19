@file:OptIn(ExperimentalPermissionsApi::class)

package ai.metabind.feature.detail.screens

import ai.metabind.ai.MetabindAssistant
import ai.metabind.ai.MetabindAssistantView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.finishSignIn(it.data)
    }
    LaunchedEffect(viewModel) {
        viewModel.signInIntents.collect { intent ->
            try { signIn.launch(intent) } catch (_: Exception) { viewModel.browserUnavailable() }
        }
    }

    BackHandler(enabled = true) {
        viewModel.onBackPressed()
    }

    DetailContent(
        viewState = viewState,
        onClose = { viewModel.onBackPressed() },
        assistant = viewModel.assistant,
        onRetry = viewModel::retry,
        onSignIn = viewModel::signIn,
        onDisconnect = viewModel::disconnect,
    )
}

@Composable
fun DetailContent(
    viewState: DetailViewModel.ViewState,
    onClose: () -> Unit,
    assistant: MetabindAssistant? = null,
    onRetry: () -> Unit = {},
    onSignIn: () -> Unit = {},
    onDisconnect: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (viewState) {
                is DetailViewModel.ViewState.Loading -> LoadingState()
                is DetailViewModel.ViewState.Success -> LoadedState(contentId = viewState.contentId)
                is DetailViewModel.ViewState.Project -> assistant?.let { ProjectContent(it, viewState, onDisconnect) }
                is DetailViewModel.ViewState.SignIn -> Column(Modifier.padding(32.dp)) {
                    Text(viewState.title, style = MaterialTheme.typography.titleLarge)
                    Text("Sign in to preview drafts")
                    Text(viewState.error ?: "Use your Metabind account. You need permission to access this project.")
                    TextButton(onClick = onSignIn, enabled = !viewState.busy) {
                        Text(if (viewState.busy) "Signing in…" else "Sign in to Metabind")
                    }
                }
                is DetailViewModel.ViewState.Error -> Column(Modifier.padding(32.dp)) {
                    Text("Unable to open preview. Check your connection and project access, or import a new preview QR.")
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
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
            Icon(Icons.Default.Close, "Close Preview")
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


@Composable
private fun ProjectContent(assistant: MetabindAssistant, project: DetailViewModel.ViewState.Project, onDisconnect: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var refreshUnavailable by remember(assistant) { mutableStateOf(false) }
    LaunchedEffect(assistant, lifecycle) {
        if (!project.draft) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(3_000)
                try {
                    assistant.refreshPreviewResources()
                    refreshUnavailable = false
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    refreshUnavailable = true
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
            Text(project.title, style = MaterialTheme.typography.titleMedium)
            Text("${if (project.draft) "Saved drafts" else "Published"} · ${if (project.development) "Development" else "Production"}", style = MaterialTheme.typography.labelSmall)
            if (project.draft) TextButton(onClick = onDisconnect) { Text("Disconnect this project") }
        }
        if (refreshUnavailable) Text("Saved edit refresh is temporarily unavailable.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelSmall)
        MetabindAssistantView(assistant, Modifier.weight(1f))
    }
}
