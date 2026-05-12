package ai.metabind.ui.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Interface that allows for easy and convenient access and customization of some aspects of the
 * system UI.
 */
interface SystemUiHandle {

    /**
     * System's bottom padding dimension, this can be understood to be the height of the navigation
     * bar.
     */
    val bottomPadding: Dp

    /**
     * System's top padding dimension, this can be understood to be the height of the status bar.
     */
    val topPadding: Dp

    /** All of the system's padding dimensions, represented as a single [PaddingValues] instance. */
    val innerPadding: PaddingValues

    /**
     * Sets the color of the navigation bar, while allowing the system to automatically determine if
     * dark or light icons should be used.
     */
    fun setNavigationBarColor(color: Color)

    /** Sets the color of the navigation bar and whether dark or light icons should be used. */
    fun setNavigationBarColor(color: Color, darkIcons: Boolean)

    /**
     * Sets the color of the status bar, while allowing the system to automatically determine if
     * dark or light icons should be used.
     */
    fun setStatusBarColor(color: Color)

    /** Sets the color of the status bar and whether dark or light icons should be used. */
    fun setStatusBarColor(color: Color, darkIcons: Boolean)

    /**
     * Sets the color of both the status bar and navigation bar, while allowing the system to
     * automatically determine if dark or light icons should be used.
     */
    fun setSystemBarsColor(color: Color)

    /**
     * Sets the color of both the status bar and navigation bar, and also whether dark or light
     * icons should be used.
     */
    fun setSystemBarsColor(color: Color, darkIcons: Boolean)

    /**
     * Returns a new [SystemUiHandle] instance designed to accommodate the given [PaddingValues].
     * Generally intended for use cases of nested Scaffold composables.
     */
    fun withNewInnerPadding(innerPadding: PaddingValues): SystemUiHandle
}

// ported from https://github.com/google/accompanist/tree/main/systemuicontroller
interface SystemUiController {
    fun setStatusBarColor(
        color: Color,
        darkIcons: Boolean = color.luminance() > 0.5f,
        transformColorForLightContent: (Color) -> Color = BlackScrimmed
    )

    /**
     * Set the navigation bar color.
     *
     * @param color The **desired** [Color] to set. This may require modification if running on an
     *   API level that only supports white navigation bar icons. Additionally this will be ignored
     *   and [Color.Transparent] will be used on API 29+ where gesture navigation is preferred or
     *   the system UI automatically applies background protection in other navigation modes.
     * @param darkIcons Whether dark navigation bar icons would be preferable.
     * @param navigationBarContrastEnforced Whether the system should ensure that the navigation bar
     *   has enough contrast when a fully transparent background is requested. Only supported on API
     *   29+.
     * @param transformColorForLightContent A lambda which will be invoked to transform [color] if
     *   dark icons were requested but are not available. Defaults to applying a black scrim.
     */
    fun setNavigationBarColor(
        color: Color,
        darkIcons: Boolean = color.luminance() > 0.5f,
        navigationBarContrastEnforced: Boolean = true,
        transformColorForLightContent: (Color) -> Color = BlackScrimmed
    )

    /**
     * Set the status and navigation bars to [color].
     *
     * @see setStatusBarColor
     * @see setNavigationBarColor
     */
    fun setSystemBarsColor(
        color: Color,
        darkIcons: Boolean = color.luminance() > 0.5f,
        isNavigationBarContrastEnforced: Boolean = true,
        transformColorForLightContent: (Color) -> Color = BlackScrimmed
    ) {
        setStatusBarColor(color, darkIcons, transformColorForLightContent)
        setNavigationBarColor(
            color, darkIcons, isNavigationBarContrastEnforced, transformColorForLightContent)
    }
}

/**
 * Remembers a [SystemUiController] for the given [window].
 *
 * If no [window] is provided, an attempt to find the correct [Window] is made.
 *
 * First, if the [LocalView]'s parent is a [DialogWindowProvider], then that dialog's [Window] will
 * be used.
 *
 * Second, we attempt to find [Window] for the [Activity] containing the [LocalView].
 *
 * If none of these are found (such as may happen in a preview), then the functionality of the
 * returned [SystemUiController] will be degraded, but won't throw an exception.
 */
@Composable
fun rememberSystemUiController(
    window: Window? = findWindow(),
): SystemUiController {
    val view = LocalView.current
    return remember(view, window) { AndroidSystemUiController(view, window) }
}

/**
 * A helper class for setting the navigation and status bar colors for a [View], gracefully
 * degrading behavior based upon API level.
 *
 * Typically you would use [rememberSystemUiController] to remember an instance of this.
 */
internal class AndroidSystemUiController(private val view: View, private val window: Window?) :
    SystemUiController {
    private val windowInsetsController = window?.let { WindowCompat.getInsetsController(it, view) }

    override fun setStatusBarColor(
        color: Color,
        darkIcons: Boolean,
        transformColorForLightContent: (Color) -> Color
    ) {
        statusBarDarkContentEnabled = darkIcons

        window?.statusBarColor =
            when {
                darkIcons && windowInsetsController?.isAppearanceLightStatusBars != true -> {
                    // If we're set to use dark icons, but our windowInsetsController call didn't
                    // succeed (usually due to API level), we instead transform the color to
                    // maintain contrast
                    transformColorForLightContent(color)
                }
                else -> color
            }.toArgb()
    }

    override fun setNavigationBarColor(
        color: Color,
        darkIcons: Boolean,
        navigationBarContrastEnforced: Boolean,
        transformColorForLightContent: (Color) -> Color
    ) {
        navigationBarDarkContentEnabled = darkIcons
        isNavigationBarContrastEnforced = navigationBarContrastEnforced

        window?.navigationBarColor =
            when {
                darkIcons && windowInsetsController?.isAppearanceLightNavigationBars != true -> {
                    // If we're set to use dark icons, but our windowInsetsController call didn't
                    // succeed (usually due to API level), we instead transform the color to
                    // maintain contrast
                    transformColorForLightContent(color)
                }
                else -> color
            }.toArgb()
    }

    private var statusBarDarkContentEnabled: Boolean
        get() = windowInsetsController?.isAppearanceLightStatusBars == true
        set(value) {
            windowInsetsController?.isAppearanceLightStatusBars = value
        }

    private var navigationBarDarkContentEnabled: Boolean
        get() = windowInsetsController?.isAppearanceLightNavigationBars == true
        set(value) {
            windowInsetsController?.isAppearanceLightNavigationBars = value
        }

    private var isNavigationBarContrastEnforced: Boolean
        get() = Build.VERSION.SDK_INT >= 29 && window?.isNavigationBarContrastEnforced == true
        set(value) {
            if (Build.VERSION.SDK_INT >= 29) {
                window?.isNavigationBarContrastEnforced = value
            }
        }
}

@Composable
private fun findWindow(): Window? =
    (LocalView.current.parent as? DialogWindowProvider)?.window
        ?: LocalView.current.context.findWindow()

private tailrec fun Context.findWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.findWindow()
        else -> null
    }

private val BlackScrim = Color(0f, 0f, 0f, 0.3f) // 30% opaque black
private val BlackScrimmed: (Color) -> Color = { original -> BlackScrim.compositeOver(original) }
