package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = LightPurple,
    onPrimary = ActivePurpleText,
    primaryContainer = ElegantPurple,
    onPrimaryContainer = DarkText,
    secondary = LightPurple,
    onSecondary = ActivePurpleText,
    tertiary = LightPurple,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkBg,
    onSurface = DarkText,
    surfaceVariant = KeypadBg,
    onSurfaceVariant = SecondaryText,
    outline = KeypadBg
  )

private val LightColorScheme = DarkColorScheme // Elegant Dark is enforced to fit the design intent

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for "Elegant Dark" visual identity
  dynamicColor: Boolean = false, // Set to false to prevent device theme from overriding the customized colors
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
