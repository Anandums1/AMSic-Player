package com.anandu.musicplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.core.view.WindowCompat

// ── Colour palette ─────────────────────────────────────────────────────
private val Purple80  = Color(0xFFCFBCFF)
private val Purple40  = Color(0xFF7B61FF)
private val Teal80    = Color(0xFF80F0D0)
private val Teal40    = Color(0xFF00C9A7)
private val Surface0  = Color(0xFF0F0F14)
private val Surface1  = Color(0xFF1A1A24)
private val Surface2  = Color(0xFF25253A)
private val OnSurface = Color(0xFFE6E1F0)

private val DarkColors = darkColorScheme(
    primary            = Purple80,
    onPrimary          = Color(0xFF1C0056),
    primaryContainer   = Purple40,
    secondary          = Teal80,
    onSecondary        = Color(0xFF003830),
    secondaryContainer = Teal40,
    background         = Surface0,
    surface            = Surface1,
    surfaceVariant     = Surface2,
    onBackground       = OnSurface,
    onSurface          = OnSurface,
    onSurfaceVariant   = Color(0xFFC4C0CC),
)

private val LightColors = lightColorScheme(
    primary            = Purple40,
    primaryContainer   = Color(0xFFEDE7FF),
    secondary          = Teal40,
    secondaryContainer = Color(0xFFB8F5E3),
)

// ── Typography ─────────────────────────────────────────────────────────
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = emptyList(),   // works on-device without cert pinning
)

private val outfitFont = GoogleFont("Outfit")

private val OutfitFamily = FontFamily(
    Font(googleFont = outfitFont, fontProvider = fontProvider, weight = FontWeight.Light),
    Font(googleFont = outfitFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = outfitFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = outfitFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = outfitFont, fontProvider = fontProvider, weight = FontWeight.Bold),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge  = displayLarge.copy(fontFamily = OutfitFamily),
        displayMedium = displayMedium.copy(fontFamily = OutfitFamily),
        displaySmall  = displaySmall.copy(fontFamily = OutfitFamily),
        headlineLarge = headlineLarge.copy(fontFamily = OutfitFamily),
        headlineMedium = headlineMedium.copy(fontFamily = OutfitFamily),
        headlineSmall = headlineSmall.copy(fontFamily = OutfitFamily),
        titleLarge    = titleLarge.copy(fontFamily = OutfitFamily),
        titleMedium   = titleMedium.copy(fontFamily = OutfitFamily),
        titleSmall    = titleSmall.copy(fontFamily = OutfitFamily),
        bodyLarge     = bodyLarge.copy(fontFamily = OutfitFamily),
        bodyMedium    = bodyMedium.copy(fontFamily = OutfitFamily),
        bodySmall     = bodySmall.copy(fontFamily = OutfitFamily),
        labelLarge    = labelLarge.copy(fontFamily = OutfitFamily),
        labelMedium   = labelMedium.copy(fontFamily = OutfitFamily),
        labelSmall    = labelSmall.copy(fontFamily = OutfitFamily),
    )
}

// ── Theme ──────────────────────────────────────────────────────────────
@Composable
fun AMSicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content,
    )
}
