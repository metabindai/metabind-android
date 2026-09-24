package ai.metabind.feature.home.screens

import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun CameraScreen(
    onBarcodeRecognized: (String) -> Unit,
) {
    val localContext = LocalContext.current
    val cameraController = remember {
        LifecycleCameraController(localContext)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val barcodeScanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    DisposableEffect(cameraController, lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            barcodeScanner.close()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                // Set up the image analysis analyzer for barcode detection
                cameraController.setImageAnalysisAnalyzer(
                    ContextCompat.getMainExecutor(ctx), // Use the main executor
                    MlKitAnalyzer(
                        listOf(barcodeScanner), // Pass the barcode scanner
                        ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, // Use view-referenced coordinates
                        ContextCompat.getMainExecutor(ctx) // Use the main executor
                    ) { result: MlKitAnalyzer.Result? ->
                        // Process the barcode scanning results
                        val barcodeResults = result?.getValue(barcodeScanner)
                        if (!barcodeResults.isNullOrEmpty()) {
                            barcodeResults.first().rawValue?.let {
                                onBarcodeRecognized(it)
                            }
                        }
                    }
                )

                // Set the camera controller for the PreviewView
                this.controller = cameraController
            }
        }
    )
}
