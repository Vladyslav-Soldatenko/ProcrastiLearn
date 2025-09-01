@file:Suppress("MagicNumber")
package com.procrastilearn.app.overlay.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class OverlayColors(
    val backgroundGradientStart: Color,
    val backgroundGradientEnd: Color,
    val cardContainer: Color,
    val titleColor: Color,
    val newBadgeColor: Color,
    val innerCardContainer: Color,
    val translationText: Color,
    val showButtonContent: Color,
    val divider: Color,
    val helpText: Color,
    val difficultyAgainContainer: Color,
    val difficultyAgainContent: Color,
    val difficultyHardContainer: Color,
    val difficultyHardContent: Color,
    val difficultyGoodContainer: Color,
    val difficultyGoodContent: Color,
    val difficultyEasyContainer: Color,
    val difficultyEasyContent: Color,
    val disabledContainer: Color,
    val disabledContent: Color,
)

private val LocalOverlayColors = staticCompositionLocalOf {
    // Reasonable defaults; will be overridden by OverlayTheme
    OverlayColors(
        backgroundGradientStart = Color(0xFFFFFFFF),
        backgroundGradientEnd = Color(0xFFF5F5F5),
        cardContainer = Color(0xFFFFFFFF),
        titleColor = Color(0xFF111827),
        newBadgeColor = Color(0xFFB91C1C),
        innerCardContainer = Color(0xFFF3F4F6),
        translationText = Color(0xFF1D4ED8),
        showButtonContent = Color(0xFF1D4ED8),
        divider = Color(0xFFE5E7EB),
        helpText = Color(0xFF6B7280),
        difficultyAgainContainer = Color(0xFFEF4444),
        difficultyAgainContent = Color(0xFFFFFFFF),
        difficultyHardContainer = Color(0xFFF59E0B),
        difficultyHardContent = Color(0xFFFFFFFF),
        difficultyGoodContainer = Color(0xFF10B981),
        difficultyGoodContent = Color(0xFFFFFFFF),
        difficultyEasyContainer = Color(0xFF3B82F6),
        difficultyEasyContent = Color(0xFFFFFFFF),
        disabledContainer = Color(0xFFE5E7EB),
        disabledContent = Color(0xFF9CA3AF),
    )
}

// Dark theme: exactly the original overlay colors
private val OverlayDarkColors = OverlayColors(
    backgroundGradientStart = Color(0xFF0F172A),
    backgroundGradientEnd = Color(0xFF111827),
    cardContainer = Color(0xFF1F2937),
    titleColor = Color(0xFFF9FAFB),
    newBadgeColor = Color(0xFFEF4444),
    innerCardContainer = Color(0xFF111827),
    translationText = Color(0xFF93C5FD),
    showButtonContent = Color(0xFFBFDBFE),
    divider = Color(0xFF374151),
    helpText = Color(0xFF9CA3AF),
    difficultyAgainContainer = Color(0xFFEF4444),
    difficultyAgainContent = Color(0xFFFFFFFF),
    difficultyHardContainer = Color(0xFFF59E0B),
    difficultyHardContent = Color(0xFFFFFFFF),
    difficultyGoodContainer = Color(0xFF10B981),
    difficultyGoodContent = Color(0xFFFFFFFF),
    difficultyEasyContainer = Color(0xFF3B82F6),
    difficultyEasyContent = Color(0xFFFFFFFF),
    disabledContainer = Color(0xFF374151),
    disabledContent = Color(0xFF6B7280),
)

// Light theme: chosen to be readable and harmonious
private val OverlayLightColors = OverlayColors(
    backgroundGradientStart = Color(0xFFF7FAFC),
    backgroundGradientEnd = Color(0xFFEFF4FA),
    cardContainer = Color(0xFFFFFFFF),
    titleColor = Color(0xFF111827),
    newBadgeColor = Color(0xFFB91C1C),
    innerCardContainer = Color(0xFFF3F4F6),
    translationText = Color(0xFF1D4ED8),
    showButtonContent = Color(0xFF1D4ED8),
    divider = Color(0xFFE5E7EB),
    helpText = Color(0xFF6B7280),
    difficultyAgainContainer = Color(0xFFEF4444),
    difficultyAgainContent = Color(0xFFFFFFFF),
    difficultyHardContainer = Color(0xFFF59E0B),
    difficultyHardContent = Color(0xFFFFFFFF),
    difficultyGoodContainer = Color(0xFF10B981),
    difficultyGoodContent = Color(0xFFFFFFFF),
    difficultyEasyContainer = Color(0xFF3B82F6),
    difficultyEasyContent = Color(0xFFFFFFFF),
    disabledContainer = Color(0xFFE5E7EB),
    disabledContent = Color(0xFF9CA3AF),
)

@Composable
fun OverlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) OverlayDarkColors else OverlayLightColors
    CompositionLocalProvider(LocalOverlayColors provides colors) {
        content()
    }
}

object OverlayThemeTokens {
    val colors: OverlayColors
        @Composable get() = LocalOverlayColors.current
}
