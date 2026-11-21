package com.nusa.tunnel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AuroraScheme = darkColorScheme(
    primary = Spruce,
    onPrimary = Aurora0,
    primaryContainer = SpruceDeep,
    onPrimaryContainer = Mint,
    secondary = Lime,
    onSecondary = Aurora0,
    tertiary = Mint,
    background = Aurora0,
    onBackground = Frost,
    surface = Aurora1,
    onSurface = Frost,
    surfaceVariant = Aurora2,
    onSurfaceVariant = FrostDim,
    outline = GlassStroke,
    error = Danger,
    onError = Aurora0,
)

@Composable
fun NusaTunnelTheme(content: @Composable () -> Unit) {
    // NusaTunnel dirancang dark aurora — dipakai di kedua mode sistem
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = AuroraScheme,
        typography = NusaTypography,
        content = content,
    )
}
