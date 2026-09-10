package com.romurbex.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RomurbexColorScheme = darkColorScheme(
    primary = Rust,
    onPrimary = ConcreteBlack,
    secondary = Moss,
    onSecondary = ConcreteBlack,
    background = ConcreteBlack,
    onBackground = Concrete,
    surface = ConcreteSurface,
    onSurface = Concrete,
    surfaceVariant = ConcreteSurfaceHigh,
    onSurfaceVariant = ConcreteDim,
    error = DangerRed,
)

@Composable
fun RomurbexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = RomurbexColorScheme,
        typography = RomurbexTypography,
        content = content,
    )
}
