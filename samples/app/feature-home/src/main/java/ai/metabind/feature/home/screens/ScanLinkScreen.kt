@file:OptIn(com.google.accompanist.permissions.ExperimentalPermissionsApi::class)
package ai.metabind.feature.home.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@Composable
fun ScanLinkScreen(viewModel: ScanLinkViewModel) {
    val state by viewModel.viewState.collectAsState()
    val context = LocalContext.current
    val emulator = Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("emulator") ||
        Build.MODEL.contains("Emulator") || Build.MODEL.contains("sdk_gphone") ||
        Build.HARDWARE in setOf("goldfish", "ranchu")
    val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    var scan by remember { mutableStateOf(!emulator && hasCamera) }
    // Never save a credential-bearing link in SavedStateHandle or saved instance state.
    var input by remember { mutableStateOf("") }
    val permission = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(scan) { if (scan && !permission.status.isGranted) permission.launchPermissionRequest() }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (scan) "Scan Preview QR" else "Open Preview Link", style = MaterialTheme.typography.headlineMedium)
        if (scan && permission.status.isGranted) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!state.opening && state.error == null) CameraScreen(viewModel::openPreview)
            }
        } else {
            if (scan) Text("Allow camera access to scan, or paste a preview link below.")
            OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Preview URL") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                enabled = !state.opening)
            Button(onClick = { val value = input; input = ""; viewModel.openPreview(value) },
                enabled = input.isNotBlank() && !state.opening) { Text("Open Preview") }
            Spacer(Modifier.weight(1f))
        }
        state.error?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = viewModel::clearError) { Text("Try Again") }
        }
        if (state.opening) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        if (scan) TextButton(onClick = { scan = false }) { Text("Paste Preview Link") }
        else if (!emulator && hasCamera) TextButton(onClick = { input = ""; scan = true }) { Text("Scan QR Code") }
    }
}
