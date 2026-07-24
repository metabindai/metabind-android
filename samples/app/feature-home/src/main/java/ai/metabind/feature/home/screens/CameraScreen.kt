package ai.metabind.feature.home.screens

import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
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
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                // Configure barcode scanning options for supported formats
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(
                        Barcode.FORMAT_ALL_FORMATS,
                    )
                    .build()

                // Initialize the barcode scanner client with the configured options
                val barcodeScanner = BarcodeScanning.getClient(options)

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

                // Bind the camera controller to the lifecycle owner
                cameraController.bindToLifecycle(lifecycleOwner)

                // Set the camera controller for the PreviewView
                this.controller = cameraController
            }
        }
    )
}
