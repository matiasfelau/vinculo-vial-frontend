package com.vinculovial.app.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * La identidad de ViVi es una única paleta clara (ver wireframe); no hay variante oscura
 * definida por diseño, así que el esquema se fija aunque el sistema esté en modo oscuro.
 */
private val VvColorScheme = lightColorScheme(
    primary = VvNavy,
    onPrimary = Color.White,
    primaryContainer = VvNavyDark,
    onPrimaryContainer = Color.White,
    secondary = VvGreen,
    onSecondary = Color.White,
    secondaryContainer = VvMint,
    onSecondaryContainer = VvInk,
    tertiary = VvOrange,
    onTertiary = Color.White,
    background = VvCream,
    onBackground = VvInk,
    surface = VvCream,
    onSurface = VvInk,
    surfaceVariant = VvMint,
    onSurfaceVariant = VvMuted,
    outline = VvMintDark,
    error = VvOrange,
    onError = Color.White,
)

@Composable
fun VinculoVialTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = VvColorScheme,
        typography = VvTypography,
        content = content,
    )
}
