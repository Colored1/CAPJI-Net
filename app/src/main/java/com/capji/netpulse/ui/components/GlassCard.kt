package com.capji.netpulse.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism-lite: translucent white fill + gradient hairline border over the gradient background.
 * (Real backdrop blur needs RenderEffect on API 31+ and only blurs the node's own content, so we
 * fake the depth with translucency + glow instead - it is cheap and works on API 26+.)
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    contentPadding: Dp = 16.dp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val border = if (accent != null) {
        BorderStroke(1.2.dp, accent.copy(alpha = 0.7f))
    } else {
        BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))))
    }
    val fill = Color.White.copy(alpha = 0.055f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding), horizontalAlignment = horizontalAlignment, content = content)
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = fill, contentColor = onSurface, border = border, content = inner)
    } else {
        Surface(modifier = modifier, shape = shape, color = fill, contentColor = onSurface, border = border, content = inner)
    }
}
