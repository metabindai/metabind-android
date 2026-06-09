/*
 * MainActivity.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo

import ai.metabind.assistant.demo.data.ApiKeyRepository
import ai.metabind.assistant.demo.navigation.AppNavigation
import ai.metabind.assistant.demo.navigation.HomeRoute
import ai.metabind.assistant.demo.navigation.KeyEntryRoute
import ai.metabind.assistant.demo.ui.theme.MetabindAssistantDemoTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var apiKeyRepository: ApiKeyRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MetabindAssistantDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Resolve the start route off the main thread; show a brief loader
                    // until DataStore reports whether a key is already stored.
                    val startRoute by produceState<NavKey?>(initialValue = null) {
                        value = if (apiKeyRepository.hasApiKey()) HomeRoute else KeyEntryRoute
                    }
                    when (val route = startRoute) {
                        null -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }

                        else -> AppNavigation(
                            startRoute = route,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
