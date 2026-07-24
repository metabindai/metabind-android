package ai.metabind.ui.app

import androidx.navigation.NavHostController

internal fun NavHostController.navigateAndReplaceStartRoute(newStartRoot: String) {
    popBackStack(graph.startDestinationId, true)
    graph.setStartDestination(newStartRoot)
    navigate(newStartRoot)
}
