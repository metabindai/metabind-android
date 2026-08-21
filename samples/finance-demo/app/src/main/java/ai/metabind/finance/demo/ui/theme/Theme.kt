/*
 * Theme.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp

/**
 * The app's one bit of brand. Everything else leans on system-neutral surfaces so
 * the rendered MCP components stay the loudest thing on screen.
 */
val Accent = Color(0xFF218C6B)

/**
 * Surfaces the app names by role rather than by Material token.
 *
 * The iOS original is built on `systemGroupedBackground` / `secondarySystemGroupedBackground`,
 * where a card is *lighter* than the page in light mode and the relationship inverts in
 * dark mode. Material's tonal tokens don't invert that way, so the two palettes are
 * spelled out instead of derived — that keeps a card reading as a raised cell in both
 * schemes rather than vanishing into the page in one of them.
 */
data class FinancePalette(
    /** Behind everything — iOS `systemGroupedBackground`. */
    val page: Color,
    /** A card's own fill — iOS `secondarySystemGroupedBackground`. */
    val card: Color,
    /** What the ask bar's scrim fades into — iOS `systemBackground`. */
    val chrome: Color,
    /** Hairline borders. */
    val separator: Color,
    /** Unselected chips, cancel buttons — iOS `.fill.tertiary`. */
    val fill: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Translucent fill standing in for liquid glass. */
    val glass: Color,
    val isDark: Boolean,
)

private val LightPalette = FinancePalette(
    page = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    chrome = Color(0xFFFFFFFF),
    separator = Color(0x66C6C6C8),
    fill = Color(0x1F767680),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0x993C3C43),
    glass = Color(0xD9FFFFFF),
    isDark = false,
)

private val DarkPalette = FinancePalette(
    page = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    chrome = Color(0xFF000000),
    separator = Color(0x66545458),
    fill = Color(0x3D767680),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0x99EBEBF5),
    glass = Color(0xCC2C2C2E),
    isDark = true,
)

private val LocalFinancePalette = staticCompositionLocalOf { LightPalette }

/** The palette for the current scheme. */
val palette: FinancePalette
    @Composable @ReadOnlyComposable get() = LocalFinancePalette.current

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = LightPalette.page,
    onBackground = LightPalette.textPrimary,
    surface = LightPalette.card,
    onSurface = LightPalette.textPrimary,
    onSurfaceVariant = Color(0xFF5B5B60),
    outlineVariant = Color(0xFFD6D6DA),
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = DarkPalette.page,
    onBackground = DarkPalette.textPrimary,
    surface = DarkPalette.card,
    onSurface = DarkPalette.textPrimary,
    onSurfaceVariant = Color(0xFFA3A3A8),
    outlineVariant = Color(0xFF3A3A3C),
)

@Composable
fun MetabindFinanceDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Deliberately not `dynamicColorScheme`: the accent is the app's only brand mark
    // and a wallpaper-derived one would fight the rendered cards for attention.
    CompositionLocalProvider(LocalFinancePalette provides if (darkTheme) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography(),
            content = content,
        )
    }
}

/**
 * Stands in for iOS 26's liquid glass: a translucent fill under a hairline border,
 * so a pill reads as sitting *over* the scrolling content rather than in it.
 *
 * Android has no backdrop-sampling equivalent that works below API 31, so this is
 * a flat translucency rather than a real blur — the shape, the border and the tint
 * are what carry the look.
 */
@Composable
fun Modifier.glass(shape: Shape, tint: Color? = null): Modifier {
    val colors = palette
    return this
        .clip(shape)
        .background(tint ?: colors.glass)
        .border(0.5.dp, colors.separator, shape)
}
