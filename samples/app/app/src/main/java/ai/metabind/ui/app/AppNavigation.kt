package ai.metabind.ui.app

import androidx.navigation.NavController
import ai.metabind.ui.navigation.NavigationCommand
import ai.metabind.ui.navigation.Screens
import timber.log.Timber

internal fun appNavigation(
        navigationCommand: NavigationCommand,
        navController: NavController,
) {
    val destination = navigationCommand.destination
    Timber.d("Navigation: Processing navigation request for $destination")
    val currentDestination = navController.currentDestination
    if (destination == Screens.Back.destination) {
        navController.navigateUp()
    } else if (destination.isNotBlank() && currentDestination?.route != destination) {
        if (navigationCommand.popTo != null) {
            navController.navigate(navigationCommand.route) {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                }
            }
        } else {
            navController.navigate(destination) {
                navigationCommand.popTo?.let { popDestination ->
                    popUpTo(popDestination.destination) {
                        // In our flows we always want to remove the pop destination from the stack.
                        inclusive = true
                        // We only pop when switching nav graphs, so we never want to preserve the
                        // current stack of the graph we're switching away from.
                        saveState = false
                    }
                }
                // Prevent launching the same graph or screen on itself.
                launchSingleTop = true
            }
        }
    }
}
