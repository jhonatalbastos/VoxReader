package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.util.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0C4A6E),
    secondaryContainer = Color(0xFF075985),
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = AmberAccent,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1E40AF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Color(0xFFD97706),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569)
)

// Papel Livro: Aconchegante e quente como páginas de livro impresso
private val BookPaperColorScheme = lightColorScheme(
    primary = Color(0xFF78350F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF451A03),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDE68A),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFF92400E),
    background = Color(0xFFFAF6EE),
    surface = Color(0xFFF3EDE2),
    onBackground = Color(0xFF292524),
    onSurface = Color(0xFF292524),
    surfaceVariant = Color(0xFFE7DEC8),
    onSurfaceVariant = Color(0xFF57534E)
)

// Sépia: Tons dourados vintage e suaves para descanso visual
private val SepiaColorScheme = lightColorScheme(
    primary = Color(0xFF8D6E63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7CCC8),
    onPrimaryContainer = Color(0xFF3E2723),
    secondary = Color(0xFFA1887F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEBE9),
    onSecondaryContainer = Color(0xFF4E342E),
    tertiary = Color(0xFFBCAAA4),
    background = Color(0xFFFBF0D9),
    surface = Color(0xFFF5E6C8),
    onBackground = Color(0xFF433422),
    onSurface = Color(0xFF433422),
    surfaceVariant = Color(0xFFEAD4B0),
    onSurfaceVariant = Color(0xFF735C3E)
)

// AMOLED: Preto puro absoluto para economia de bateria e contraste extremo
private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = Color(0xFFF59E0B),
    background = Color(0xFF000000),
    surface = Color(0xFF0D0D11),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1A1A22),
    onSurfaceVariant = Color(0xFF94A3B8)
)

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme: ColorScheme = when (appTheme) {
        AppTheme.BOOK_PAPER -> BookPaperColorScheme
        AppTheme.SEPIA -> SepiaColorScheme
        AppTheme.LIGHT -> LightColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.AMOLED -> AmoledColorScheme
        AppTheme.SYSTEM -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else if (darkTheme) {
                DarkColorScheme
            } else {
                LightColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
