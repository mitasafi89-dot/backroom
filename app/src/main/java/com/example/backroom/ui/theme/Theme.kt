package com.example.backroom.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════
// MATERIAL 3 COLOR SCHEMES
// ═══════════════════════════════════════════════════════════════

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Accent,
    onTertiary = OnAccent,
    tertiaryContainer = AccentContainer,
    onTertiaryContainer = OnAccentContainer,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = Error,
    errorContainer = ErrorContainer,
    onError = Color.White,
    onErrorContainer = OnErrorContainer,
    scrim = ScrimLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,           // Lighter in dark mode for contrast
    onPrimary = OnPrimaryContainer,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryContainer,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = AccentLight,
    onTertiary = OnAccent,
    tertiaryContainer = AccentContainerDark,
    onTertiaryContainer = Color(0xFFFFF3DC),
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = Color(0xFFFF8A80),        // Softer red for dark mode
    errorContainer = ErrorContainerDark,
    onError = OnErrorContainer,
    onErrorContainer = OnErrorContainerDark,
    scrim = ScrimDark
)

// ═══════════════════════════════════════════════════════════════
// SHAPE SYSTEM — Border Radius Tokens
// ═══════════════════════════════════════════════════════════════
//
// xs:  4dp  — badges, chips
// sm:  8dp  — input fields, small cards
// md: 12dp  — cards, dialogs
// lg: 16dp  — large cards, bottom sheets
// xl: 24dp  — FABs, prominent containers
// full: 50% — circles, pills
// ═══════════════════════════════════════════════════════════════

val BackroomShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// ═══════════════════════════════════════════════════════════════
// SPACING & ELEVATION TOKENS (via CompositionLocal)
// ═══════════════════════════════════════════════════════════════

@Immutable
data class BackroomSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val xxxl: Dp = 48.dp
)

@Immutable
data class BackroomElevation(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 3.dp,
    val level3: Dp = 6.dp,
    val level4: Dp = 8.dp
)

val LocalBackroomSpacing = staticCompositionLocalOf { BackroomSpacing() }
val LocalBackroomElevation = staticCompositionLocalOf { BackroomElevation() }

// ═══════════════════════════════════════════════════════════════
// THEME COMPOSABLE
// ═══════════════════════════════════════════════════════════════

@Suppress("DEPRECATION")
@Composable
fun BackroomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalBackroomSpacing provides BackroomSpacing(),
        LocalBackroomElevation provides BackroomElevation()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = BackroomShapes,
            content = content
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// CONVENIENCE EXTENSIONS
// ═══════════════════════════════════════════════════════════════

object BackroomTheme {
    val spacing: BackroomSpacing
        @Composable get() = LocalBackroomSpacing.current

    val elevation: BackroomElevation
        @Composable get() = LocalBackroomElevation.current
}
