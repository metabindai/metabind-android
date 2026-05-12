package ai.metabind.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.navigation.navigation
import ai.metabind.feature.detail.screens.DetailScreen
import ai.metabind.feature.detail.screens.DetailViewModel
import ai.metabind.feature.home.screens.ScanLinkScreen
import ai.metabind.feature.home.screens.ScanLinkViewModel
import ai.metabind.feature.recents.screens.RecentsScreen
import ai.metabind.feature.recents.screens.RecentsViewModel
import ai.metabind.ui.navigation.Screens.MainGraph
import ai.metabind.ui.navigation.Screens.Recents
import ai.metabind.ui.navigation.Screens.ScanLink

/**
 * This function sets up screen composables (and their view models) as possible navigation
 * destinations.  The data we use to set up the screens and reference them as destinations
 * is defined in Screens.kt.
 *
 * Note that it is possible (and sometimes necessary) for the same screen composables to
 * appear in more than one nav graph.  Since all routes must be unique you will need to define
 * separate screens in Screens.kt for each nav graph it needs to appear in, but you
 * can call out the same composables and view models for different screens when invoking
 * viewModelComposable below.
 *
 * By default, screens are drawn full-bleed from edge to edge, which means content can display
 * behind the top status bar and the bottom nav bar. If this isn't desired, wrap your Screen in
 * an OpaqueScreen to constraint its bounds to exclude system areas.
 *
 */
internal fun NavGraphBuilder.composeNavScreens(
    lifecycle: Lifecycle,
    environment: Map<String, Any>,
) {
    val uriPrefix = "ai.metabind://app/"

    /**
     * The MainGraph contains the app's main functionality.  It will be at the root of the
     * navigation graph any time the user is logged in.
     */

    navigation(
        startDestination = Recents.route,
        route = MainGraph().route
    ) {
        viewModelComposable<RecentsViewModel>(
            route = Recents.route,
            deepLinks = listOf(navDeepLink { uriPattern = uriPrefix + Recents.route }),
            environment = environment
        ) { viewModel, _ ->
            viewModel.register(lifecycle)
            RecentsScreen(
                viewModel = viewModel,
            )
        }

        viewModelComposable<ScanLinkViewModel>(
            route = ScanLink.route,
            deepLinks = listOf(navDeepLink { uriPattern = uriPrefix + ScanLink.route })
        ) { viewModel, _ ->
            viewModel.register(lifecycle)
            ScanLinkScreen(
                viewModel = viewModel,
            )
        }

        viewModelComposable<DetailViewModel>(
            route = "detail/{itemId}",
            arguments = listOf(
                navArgument("itemId") {
                    type = NavType.LongType
                }),
            deepLinks = listOf(navDeepLink { uriPattern = uriPrefix + "detail/{itemId}" })
        ) { viewModel, backStackEntry ->
            val itemId = backStackEntry.arguments?.getLong("itemId") ?: 0
            viewModel.register(lifecycle)
            LaunchedEffect(itemId) {
                viewModel.initialize(itemId)
            }
            DetailScreen(
                viewModel = viewModel,
            )
        }
    }
}

/**
 * Binds a ViewStateViewModel to a given Navigation-scoped Compose screen.
 */
private inline fun <reified T : ViewModel> NavGraphBuilder.viewModelComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    environment: Map<String, Any> = emptyMap(),
    noinline content: @Composable (T, NavBackStackEntry) -> Unit,
) {
    composable(route, arguments, deepLinks) { backStackEntry ->
        val viewModel = hiltViewModel<T>()
        content(viewModel, backStackEntry)
    }
}
