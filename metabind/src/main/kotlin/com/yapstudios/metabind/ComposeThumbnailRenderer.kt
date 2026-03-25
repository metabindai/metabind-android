package com.yapstudios.metabind

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.ViewGroup
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.yapstudios.bindjs.JsRuntime
import com.yapstudios.bindjs.composables.BindJSView
import com.yapstudios.bindjs.model.Component
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "ComposeThumbnailRenderer"

suspend fun createThumbnail(
    context: Context,
    jsRuntime: JsRuntime,
    component: Component,
): ImageBitmap {
    val thumbnailWidth = 1080
    val thumbnailHeight = 1920

    val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val surfaceTexture = SurfaceTexture(false)
    surfaceTexture.setDefaultBufferSize(thumbnailWidth, thumbnailHeight)
    val surface = Surface(surfaceTexture)
    val virtualDisplay = displayManager.createVirtualDisplay(
        "ThumbnailDisplay",
        thumbnailWidth, thumbnailHeight, context.resources.displayMetrics.densityDpi,
        surface,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
    )

    val owner = ComposeViewOwner()
    owner.onCreate()

    val presentation = Presentation(context, virtualDisplay.display)
    val composeView = ComposeView(presentation.context).apply {
        setContent {
                BindJSView(
                    jsRuntime,
                    component,
                    version = 0,
                    { }
                )
        }
    }
    presentation.setContentView(
        composeView,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )

    // Set lifecycle owners on the decor view so ComposeView can find them
    val decorView = presentation.window!!.decorView
    decorView.setViewTreeLifecycleOwner(owner)
    decorView.setViewTreeSavedStateRegistryOwner(owner)

    presentation.show()

    // Give Compose time to compose, measure, layout, and draw
    delay(300)

    // Use PixelCopy to capture the rendered content (supports hardware bitmaps)
    val bitmap = createBitmap(thumbnailWidth, thumbnailHeight, Bitmap.Config.ARGB_8888)
    suspendCancellableCoroutine { cont ->
        PixelCopy.request(
            presentation.window!!,
            bitmap,
            { result ->
                if (result != PixelCopy.SUCCESS) {
                    Log.e(TAG, "PixelCopy failed with result: $result")
                }
                cont.resumeWith(Result.success(Unit))
            },
            Handler(Looper.getMainLooper())
        )
    }

    // Clean up
    presentation.dismiss()
    virtualDisplay.release()
    surface.release()
    surfaceTexture.release()
    owner.onDestroy()

    return bitmap.asImageBitmap()
}

private class ComposeViewOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
