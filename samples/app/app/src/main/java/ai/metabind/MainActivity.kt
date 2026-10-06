package ai.metabind

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import ai.metabind.data.home.preview.MCPPreviewLink
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import ai.metabind.ui.app.ComposeApp
import ai.metabind.ui.app.rememberSystemUiController
import ai.metabind.ui.navigation.NavigationConductor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var previewLink by mutableStateOf<String?>(null)

    @Inject
    lateinit var navigationConductor: NavigationConductor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewLink = savedInstanceState?.getString("previewLink") ?: previewFrom(intent)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val systemUiController = rememberSystemUiController()

            SideEffect {
                systemUiController.setStatusBarColor(
                    color = Color.Transparent,
                    darkIcons = false
                )
                systemUiController.setNavigationBarColor(color = Color.Transparent)
            }

            ComposeApp(
                navigationConductor = navigationConductor,
                previewLink = previewLink,
                onPreviewOpened = { previewLink = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        previewLink = previewFrom(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("previewLink", previewLink)
        super.onSaveInstanceState(outState)
    }

    private fun previewFrom(intent: Intent): String? {
        val input = intent.dataString ?: return null
        intent.data = null
        return runCatching { MCPPreviewLink.parse(input)?.previewUrl ?: MCPPreviewLink.contentUrl(input) }.getOrNull()
    }
}
