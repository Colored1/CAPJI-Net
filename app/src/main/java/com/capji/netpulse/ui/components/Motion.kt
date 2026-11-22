package com.capji.netpulse.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay

/** Spring used for every shared-element morph (list card <-> control screen). */
@OptIn(ExperimentalSharedTransitionApi::class)
val NpBoundsTransform = BoundsTransform { _, _ ->
    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = Rect.VisibilityThreshold)
}

/** Staggered spring entrance: each section appears 70 ms after the previous one. */
@Composable
fun Stagger(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100L + index * 70L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring(stiffness = Spring.StiffnessLow)) +
            slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) { it / 3 },
    ) { content() }
}
