package com.capji.netpulse.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.capji.netpulse.domain.model.DataQuota
import com.capji.netpulse.domain.model.QuotaAction
import com.capji.netpulse.domain.model.SpeedLimit
import com.capji.netpulse.ui.util.formatMb
import com.capji.netpulse.ui.util.formatRate
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Material 3 modal sheet (drag/fling uses the library's spring-based anchored draggable). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NpSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { content(close) }
    }
}

/** Discrete slider with a haptic tick on every step and an animated value label. */
@Composable
fun StepSlider(title: String, steps: List<Int>, index: Int, onIndex: (Int) -> Unit, labelOf: (Int) -> String) {
    val haptic = LocalHapticFeedback.current
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            AnimatedContent(targetState = labelOf(steps[index]), label = "sliderLabel") {
                Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Slider(
            value = index.toFloat(),
            onValueChange = {
                val i = it.roundToInt()
                if (i != index) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onIndex(i)
                }
            },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = steps.size - 2,
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Speed limit (Kbps; 0 = unlimited, kept as the LAST step so the slider ends at "Unlimited")
// ----------------------------------------------------------------------------------------------
private val LIMIT_STEPS = listOf(128, 256, 512, 1000, 2000, 3000, 5000, 10000, 20000, 50000, 100000, 0)

private fun limitIndex(kbps: Int?): Int {
    if (kbps == null || kbps == 0) return LIMIT_STEPS.lastIndex
    return LIMIT_STEPS.dropLast(1).indices.minByOrNull { abs(LIMIT_STEPS[it] - kbps) } ?: 0
}

private fun limitLabel(kbps: Int) = if (kbps == 0) "Unlimited" else formatRate(kbps)

@Composable
fun LimitSheet(initial: SpeedLimit?, onApply: (SpeedLimit?) -> Unit, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var down by remember { mutableIntStateOf(limitIndex(initial?.downKbps)) }
    var up by remember { mutableIntStateOf(limitIndex(initial?.upKbps)) }

    NpSheet(onDismiss) { close ->
        Text("Speed limit", style = MaterialTheme.typography.titleLarge)

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(512, 1000, 2000, 5000, 10000, 0).forEach { preset ->
                AssistChip(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        down = limitIndex(preset)
                        up = limitIndex(preset)
                    },
                    label = { Text(limitLabel(preset)) },
                )
            }
        }

        StepSlider("Download", LIMIT_STEPS, down, { down = it }, ::limitLabel)
        StepSlider("Upload", LIMIT_STEPS, up, { up = it }, ::limitLabel)

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val d = LIMIT_STEPS[down]
                val u = LIMIT_STEPS[up]
                onApply(if (d == 0 && u == 0) null else SpeedLimit(d, u))
                close()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply limit") }

        if (initial != null) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onApply(null)
                    close()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Remove limit") }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Data quota (MB)
// ----------------------------------------------------------------------------------------------
private val QUOTA_STEPS = listOf(500, 1024, 2048, 5120, 10240, 20480, 51200)

@Composable
fun QuotaSheet(initial: DataQuota?, onApply: (DataQuota?) -> Unit, onDismiss: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var idx by remember {
        mutableIntStateOf(
            initial?.let { q -> QUOTA_STEPS.indices.minByOrNull { abs(QUOTA_STEPS[it] - q.capMb) } } ?: 2,
        )
    }
    var action by remember { mutableStateOf(initial?.action ?: QuotaAction.THROTTLE) }

    NpSheet(onDismiss) { close ->
        Text("Data quota", style = MaterialTheme.typography.titleLarge)
        StepSlider("Cap", QUOTA_STEPS, idx, { idx = it }) { formatMb(it.toLong()) }

        Text("When the cap is reached", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuotaAction.entries.forEach { a ->
                FilterChip(
                    selected = action == a,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        action = a
                    },
                    label = { Text(a.label) },
                )
            }
        }
        Text(
            "Applying starts a fresh counter from 0 MB.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onApply(DataQuota(capMb = QUOTA_STEPS[idx].toLong(), usedMb = 0, action = action))
                close()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Apply quota") }

        if (initial != null) {
            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onApply(null)
                    close()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Remove quota") }
        }
    }
}
