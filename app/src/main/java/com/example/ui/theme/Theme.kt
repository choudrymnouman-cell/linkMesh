package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ActionOrange,
    secondary = SafeTeal,
    tertiary = AlertRed,
    background = DarkSlateDeep,
    surface = DarkNavySurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextLightAccent,
    onSurface = TextLightAccent,
    surfaceVariant = SolidCardDark,
    onSurfaceVariant = TextSecondary,
    outline = HighDensityBorder
)

private val LightColorScheme = lightColorScheme(
    primary = ActionOrange,
    secondary = SafeTeal,
    tertiary = AlertRed,
    background = LightSlateBack,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE0E2E5),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFFC7C7C7)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to force our distinctive emergency orange look!
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
