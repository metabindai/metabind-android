package ai.metabind.ui.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

class SystemUiHandleImpl(
    override val innerPadding: PaddingValues,
    private val systemUiController: SystemUiController,
) : SystemUiHandle {

    override val bottomPadding: Dp
        get() = innerPadding.calculateBottomPadding()

    override val topPadding: Dp
        get() = innerPadding.calculateTopPadding()

    override fun setNavigationBarColor(color: Color) {
        systemUiController.setNavigationBarColor(color = color)
    }

    override fun setNavigationBarColor(color: Color, darkIcons: Boolean) {
        systemUiController.setNavigationBarColor(color = color, darkIcons = darkIcons)
    }

    override fun setStatusBarColor(color: Color) {
        systemUiController.setStatusBarColor(color = color)
    }

    override fun setStatusBarColor(color: Color, darkIcons: Boolean) {
        systemUiController.setStatusBarColor(color = color, darkIcons = darkIcons)
    }

    override fun setSystemBarsColor(color: Color) {
        systemUiController.setSystemBarsColor(color = color)
    }

    override fun setSystemBarsColor(color: Color, darkIcons: Boolean) {
        systemUiController.setSystemBarsColor(color = color, darkIcons = darkIcons)
    }

    override fun withNewInnerPadding(innerPadding: PaddingValues): SystemUiHandle =
        SystemUiHandleImpl(
            innerPadding = innerPadding,
            systemUiController = systemUiController,
        )
}
