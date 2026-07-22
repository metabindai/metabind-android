package ai.metabind.ui.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Simple stub [SystemUiHandle] implementation, designed specifically for easy and convenient use
 * with either tests or the Android Studio preview window.
 */
object SystemUiHandleStub : SystemUiHandle {

    override val bottomPadding: Dp = 0.dp

    override val topPadding: Dp = 0.dp

    override val innerPadding: PaddingValues = PaddingValues()

    override fun setNavigationBarColor(color: Color) = Unit

    override fun setNavigationBarColor(color: Color, darkIcons: Boolean) = Unit

    override fun setStatusBarColor(color: Color) = Unit

    override fun setStatusBarColor(color: Color, darkIcons: Boolean) = Unit

    override fun setSystemBarsColor(color: Color) = Unit

    override fun setSystemBarsColor(color: Color, darkIcons: Boolean) = Unit

    override fun withNewInnerPadding(innerPadding: PaddingValues): SystemUiHandle = this
}
