package com.example.umaconsp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,

    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,

    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,

    background = Background,
    onBackground = OnBackground,

    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,

    outline = Outline,
    outlineVariant = OutlineVariant,

    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFB7B7FF),
    onPrimary = Color(0xFF19124D),
    primaryContainer = Color(0xFF2D2A6F),
    onPrimaryContainer = Color(0xFFEAE8FF),

    secondary = Color(0xFF63E6BE),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFC9FFF4),

    tertiary = Color(0xFFD6BCFA),
    onTertiary = Color(0xFF3B1364),
    tertiaryContainer = Color(0xFF5B21B6),
    onTertiaryContainer = Color(0xFFF4E8FF),

    background = DarkBackground,
    onBackground = DarkOnBackground,

    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,

    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,

    error = Color(0xFFF97066),
    onError = Color(0xFF4A0404),
    errorContainer = Color(0xFF7A271A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun UmaconspTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
