/*
 * HomeScreen.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.ui.screens

import ai.metabind.ai.MetabindAssistantView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HomeScreen(
    onNavigateToKeyEntry: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    BackHandler {}
    val assistant by viewModel.assistant.collectAsState()
    val current = assistant
    if (current != null) {
        MetabindAssistantView(assistant = current)
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
