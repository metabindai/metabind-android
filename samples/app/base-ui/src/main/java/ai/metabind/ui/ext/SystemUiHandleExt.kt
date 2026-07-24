package ai.metabind.ui.ext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import ai.metabind.ui.app.SystemUiHandle

@Composable
fun SystemUiHandle.NavigationBarColor(color: Color) {
    SideEffect { setNavigationBarColor(color) }
}

@Composable
fun SystemUiHandle.NavigationBarColor(color: Color, darkIcons: Boolean) {
    SideEffect { setNavigationBarColor(color = color, darkIcons = darkIcons) }
}

@Composable
fun SystemUiHandle.StatusBarColor(color: Color) {
    SideEffect { setStatusBarColor(color) }
}

@Composable
fun SystemUiHandle.StatusBarColor(color: Color, darkIcons: Boolean) {
    SideEffect { setStatusBarColor(color = color, darkIcons = darkIcons) }
}

@Composable
fun SystemUiHandle.SystemBarsColor(color: Color) {
    SideEffect { setSystemBarsColor(color) }
}

@Composable
fun SystemUiHandle.SystemBarsColor(color: Color, darkIcons: Boolean) {
    SideEffect { setSystemBarsColor(color = color, darkIcons = darkIcons) }
}
