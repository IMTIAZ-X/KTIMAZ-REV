package com.imtiaz.ktimazstudio.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = background_dark,
    onBackground = text_dark,
    surface = background_dark,
    onSurface = text_dark,
    surfaceVariant = disassembly_bytes_color, // Use for minor elements
    onSurfaceVariant = disassembly_address_color, // Use for subtle text
    primaryContainer = Purple500, // For backgrounds of primary elements
    onPrimaryContainer = disassembly_mnemonic_color, // For text on primary container
    secondaryContainer = Color(0xFF424242), // For highlighted items (e.g., bookmarks)
    tertiaryContainer = disassembly_branch_target_color, // For distinct elements
    tertiary = disassembly_comment_color // For comments
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = background_light,
    onBackground = text_light,
    surface = background_light,
    onSurface = text_light,
    surfaceVariant = Color.LightGray,
    onSurfaceVariant = Color.DarkGray,
    primaryContainer = Purple200,
    onPrimaryContainer = Color.Black,
    secondaryContainer = Color(0xFFE0E0E0),
    tertiaryContainer = Color.Red,
    tertiary = Color.DarkGreen
)

@Composable
fun MobileARMDisassemblerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}