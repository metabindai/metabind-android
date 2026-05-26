/*
 * HomeScreen.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.ui.screens

import ai.metabind.assistant.MetabindAssistantView
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HomeScreen(
    onNavigateToKeyEntry: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    BackHandler {}
    MetabindAssistantView(assistant = viewModel.assistant)
}
