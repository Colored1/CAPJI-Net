package com.capji.netpulse.ui.theme

import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Status colours are fixed (not dynamic) so "blocked" is always red, "throttled" always amber. */
object NpColors {
    val Online = Color(0xFF3DDC97)
    val Blocked = Color(0xFFFF5470)
    val Throttled = Color(0xFFFFB020)
    val Quota = Color(0xFFB794F6)
    val Paused = Color(0xFF4FC3F7)
}

private val NpScheme = darkColorScheme(
    primary = Color(0xFF58E6D9),
    onPrimary = Color(0xFF00201D),
    secondary = Color(0xFF8AB4FF),
    tertiary = Color(0xFFB794F6),
    background = Color(0xFF0A0E1A),
    onBackground = Color(0xFFE6EAF5),
    surface = Color(0xFF111728),
    onSurface = Color(0xFFE6EAF5),
    surfaceVariant = Color(0xFF1B2338),
    onSurfaceVariant = Color(0xFF9AA4BF),
    outline = Color(0xFF3A4562),
    error = Color(0xFFFF5470),
)

private val NpTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.3.sp),
)

@Composable
fun NetPulseTheme(accent: Color = Accent.TEAL.color, content: @Composable () -> Unit) {
    val scheme = remember(accent) { NpScheme.copy(primary = accent) }
    MaterialTheme(colorScheme = scheme, typography = NpTypography, content = content)
}

/** Dark gradient + two soft glows behind everything; cards on top are translucent => glass look. */
@Composable
fun NpBackground(content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val p = scheme.primary
    val t = scheme.tertiary
    CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scheme.background)
                .drawBehind {
                    val r = size.width * 0.95f
                    drawCircle(
                        Brush.radialGradient(listOf(p.copy(alpha = 0.16f), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.04f), r),
                        radius = r, center = Offset(size.width * 0.95f, size.height * 0.04f),
                    )
                    drawCircle(
                        Brush.radialGradient(listOf(t.copy(alpha = 0.12f), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.85f), r),
                        radius = r, center = Offset(size.width * 0.05f, size.height * 0.85f),
                    )
                },
            content = content,
        )
    }
}
