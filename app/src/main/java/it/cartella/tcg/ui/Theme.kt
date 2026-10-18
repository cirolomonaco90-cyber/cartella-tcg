package it.cartella.tcg.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Gradiente di marca (header, schede in evidenza). Il testo sopra è sempre bianco. */
val BrandGradient: Brush = Brush.linearGradient(listOf(Color(0xFF3B4CCA), Color(0xFF8E44D6)))

/** Gradiente ambra per il valore stimato. Il testo sopra è scuro. */
val AmberGradient: Brush = Brush.linearGradient(listOf(Color(0xFFFFC53D), Color(0xFFFF9A2E)))
val OnAmber = Color(0xFF2A1700)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B4CCA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE0FF),
    onPrimaryContainer = Color(0xFF000F5C),
    secondary = Color(0xFF5B5D72),
    secondaryContainer = Color(0xFFE2E3F7),
    onSecondaryContainer = Color(0xFF181A2C),
    tertiary = Color(0xFFC77700),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF291800),
    background = Color(0xFFF1F0F8),
    surface = Color(0xFFF7F6FC),
    surfaceContainerLow = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF0A1A8C),
    primaryContainer = Color(0xFF2D3DB8),
    onPrimaryContainer = Color(0xFFDEE0FF),
    secondary = Color(0xFFC4C5DD),
    secondaryContainer = Color(0xFF434559),
    onSecondaryContainer = Color(0xFFE0E1F9),
    tertiary = Color(0xFFFFB95C),
    tertiaryContainer = Color(0xFF633F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF101118),
    surface = Color(0xFF14151C),
    surfaceContainerLow = Color(0xFF1B1C26),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun CartellaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
