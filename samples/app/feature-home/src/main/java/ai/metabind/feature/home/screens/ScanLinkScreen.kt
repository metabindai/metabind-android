@file:OptIn(ExperimentalPermissionsApi::class)

package ai.metabind.feature.home.screens

import android.Manifest
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import ai.metabind.ui.theme.AppTheme

@Composable
fun ScanLinkScreen(
    viewModel: ScanLinkViewModel,
) {
    val viewState = viewModel.viewState.collectAsState().value
    ScanLinkContent(
        viewState = viewState,
        viewModel::checkPasteOnStart,
        viewModel::onDismissPasteAlert,
        viewModel::onConfirmPasteAlert,
        viewModel::onBarcodeRecognized
    )
}

@Composable
fun ScanLinkContent(
    viewState: ScanLinkViewModel.ViewState,
    checkPasteOnStart: () -> Unit,
    onDismissPasteAlert: () -> Unit,
    onConfirmPasteAlert: () -> Unit,
    onBarcodeRecognized: (String) -> Unit,
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status.isGranted) {
        LaunchedEffect(Unit) {
            checkPasteOnStart()
        }
        Box(modifier = Modifier.fillMaxSize()) {
            CameraScreen(onBarcodeRecognized)
            viewState.value?.let {
                Text(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    text = it,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W600),
                    color = Color.White
                )
            }
            if (viewState.showPasteAlert) {
                AlertDialog(
                    onDismissRequest = onDismissPasteAlert,
                    title = {
                        Text(
                            "Metabind would like to paste from clipboard",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = onConfirmPasteAlert
                        ) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = onDismissPasteAlert
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    } else if (cameraPermissionState.status.shouldShowRationale) {
        Text("Camera Permission permanently denied")
    } else {
        SideEffect {
            cameraPermissionState.run { launchPermissionRequest() }
        }
    }
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PreviewScanLink() {
    AppTheme {
        ScanLinkContent(ScanLinkViewModel.ViewState(), {}, {}, {}, {})
    }
}
