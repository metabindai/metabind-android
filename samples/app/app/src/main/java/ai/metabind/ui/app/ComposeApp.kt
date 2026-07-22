package ai.metabind.ui.app

import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ai.metabind.SystemServiceUtil.isTalkBackEnabled
import ai.metabind.ui.navigation.NavigationConductor
import ai.metabind.ui.navigation.Screens
import ai.metabind.ui.theme.AppTheme
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

@Composable
internal fun ComposeApp(
    navigationConductor: NavigationConductor,
) {
    val viewModel = hiltViewModel<AppViewModel>()

    AppTheme {
        val navController = rememberNavController()
        val lifecycleOwner = LocalLifecycleOwner.current
        val environment = buildEnvironment()

        // Observe our navigation flow to see if the app wants to move to a new screen. This might
        // be triggered by the result of a networking call or some other action not directly
        // initiated by the user.
        LaunchedEffect("navigation") {
            navigationConductor.flow.onEach { command ->
                appNavigation(command, navController)
            }.launchIn(this)
        }

        Scaffold { padding ->
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            BackHandler(enabled = true) {
                if (currentRoute != Screens.Recents.route) {
                    navController.navigate(Screens.Recents.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screens.MainGraph().destination,
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    composeNavScreens(
                        lifecycle = lifecycleOwner.lifecycle,
                        environment = environment,
                    )
                }
            }
        }
    }
}


@Composable
private fun buildEnvironment(
    customValues: Map<String, Any> = emptyMap(),
): Map<String, Any> {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val context: Context = LocalContext.current

    return mapOf(
        "displayScale" to density.density,
        "geometry" to mapOf(
            "size" to mapOf(
                "width" to configuration.screenWidthDp,
                "height" to configuration.screenHeightDp
            ),
            "safeAreaInsets" to mapOf(
                "top" to WindowInsets.systemBars.getTop(density),
                "bottom" to WindowInsets.systemBars.getBottom(density),
                "left" to WindowInsets.systemBars.getLeft(
                    density,
                    LocalLayoutDirection.current
                ),
                "right" to WindowInsets.systemBars.getRight(
                    density,
                    LocalLayoutDirection.current
                )
            )
        ),
        "screen" to mapOf(
            "width" to configuration.screenWidthDp,
            "height" to configuration.screenHeightDp,
            "safeAreaInsets" to mapOf(
                "top" to WindowInsets.systemBars.getTop(density),
                "bottom" to WindowInsets.systemBars.getBottom(density),
                "left" to WindowInsets.systemBars.getLeft(
                    density,
                    LocalLayoutDirection.current
                ),
                "right" to WindowInsets.systemBars.getRight(
                    density,
                    LocalLayoutDirection.current
                )
            )
        ),
        "locale" to Locale.getDefault().toString(),
        "timeZone" to TimeZone.getDefault().toString(),
        "colorScheme" to when (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> "dark"
            else -> "light"
        },
        "layoutDirection" to when (LocalLayoutDirection.current) {
            androidx.compose.ui.unit.LayoutDirection.Rtl -> "rtl"
            else -> "ltr"
        },
        "accessibility" to mapOf(
            "talkBackEnabled" to isTalkBackEnabled(context),
        ),
        "now" to Instant.now().toString()
    ).plus(customValues)
}
