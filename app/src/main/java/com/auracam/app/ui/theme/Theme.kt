package com.auracam.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = StudioWhite,
    secondary = PremiumGold,
    background = ObsidianBlack,
    surface = CardBlack,
    onPrimary = ObsidianBlack,
    onSecondary = ObsidianBlack,
    onBackground = StudioWhite,
    onSurface = StudioWhite
)

@Composable
fun AuraCamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
