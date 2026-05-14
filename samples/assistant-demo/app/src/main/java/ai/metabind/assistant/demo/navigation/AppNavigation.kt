/*
 * AppNavigation.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.assistant.demo.navigation

import ai.metabind.assistant.demo.ui.screens.HomeScreen
import ai.metabind.assistant.demo.ui.screens.KeyEntryScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(KeyEntryRoute)

    val onBack: () -> Unit = remember(backStack) {
        { backStack.removeLastOrNull() }
    }

    val provider = remember {
        entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    onNavigateToKeyEntry = { backStack.add(KeyEntryRoute) }
                )
            }
            entry<KeyEntryRoute> {
                KeyEntryScreen(
                    onNavigateToHome = {
                        backStack.add(HomeRoute)
                        backStack.removeAt(backStack.size - 2)
                    }
                )
            }
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = onBack,
        entryProvider = provider
    )
}
