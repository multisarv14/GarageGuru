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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = DeepIndigo,
    secondary = BrandIndigoDark,
    tertiary = EmeraldGreen,
    background = DarkSlate,
    surface = DarkSlate,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = DarkSlate,
    onBackground = SwissWhite,
    onSurface = SwissWhite
  )

private val LightColorScheme =
  lightColorScheme(
    primary = DeepIndigo,
    secondary = BrandIndigoDark,
    tertiary = EmeraldGreen,
    background = SwissWhite,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkSlate,
    onSurface = DarkSlate,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Always light mode as requested
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = LightColorScheme
  val view = androidx.compose.ui.platform.LocalView.current
  if (!view.isInEditMode) {
    val window = (view.context as? android.app.Activity)?.window
    if (window != null) {
      window.statusBarColor = android.graphics.Color.WHITE
      androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
    }
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
