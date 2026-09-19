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
  primary = DarkPrimary,
  onPrimary = Color(0xFF003731),
  primaryContainer = DarkPrimaryContainer,
  onPrimaryContainer = Color(0xFF7CF7E3),
  secondary = MedicalBlue,
  background = DarkBackground,
  surface = DarkSurface,
  surfaceVariant = DarkSurfaceVariant,
  onBackground = Color(0xFFF1F5F9),
  onSurface = Color(0xFFF1F5F9)
)

private val LightColorScheme = lightColorScheme(
  primary = PharmacyTealPrimary,
  onPrimary = Color.White,
  primaryContainer = PharmacyTealContainer,
  onPrimaryContainer = PharmacyOnTealContainer,
  secondary = MedicalBlue,
  onSecondary = Color.White,
  secondaryContainer = MedicalBlueContainer,
  tertiary = PharmacyTealSecondary,
  background = PharmacyBackground,
  onBackground = Color(0xFF0F172A),
  surface = PharmacySurface,
  onSurface = Color(0xFF0F172A),
  surfaceVariant = PharmacySurfaceVariant,
  onSurfaceVariant = Color(0xFF475569),
  outline = PharmacyOutline
)

@Composable
fun SKPharmacyTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Use our pharmacy branded palette by default
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

// Alias for backwards compatibility
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) = SKPharmacyTheme(darkTheme, dynamicColor, content)
