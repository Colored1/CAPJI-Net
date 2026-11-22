package com.capji.netpulse.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.capji.netpulse.AppGraph
import com.capji.netpulse.R
import com.capji.netpulse.domain.model.UiText
import com.capji.netpulse.ui.components.GlassCard
import com.capji.netpulse.ui.components.Stagger
import com.capji.netpulse.ui.theme.Accent

/** Languages offered in the app. Names are shown in their own language so anyone can find theirs. */
private val LANGUAGES = listOf(
    "ar" to "العربية",
    "en" to "English",
    "fr" to "Français",
    "es" to "Español",
    "hi" to "हिन्दी",
)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val settings = AppGraph.settings
    val accentIndex by settings.accent.collectAsStateWithLifecycle()
    val haptics by settings.haptics.collectAsStateWithLifecycle()
    val keepOn by settings.keepScreenOn.collectAsStateWithLifecycle()
    val defaultSim by settings.defaultSimulate.collectAsStateWithLifecycle()
    val currentLang = LocalConfiguration.current.locales[0].language
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        // ---- Language ----
        Stagger(0) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.set_language), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                LANGUAGES.forEachIndexed { i, (tag, name) ->
                    val selected = tag == currentLang
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (!selected) AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            name,
                            Modifier.weight(1f),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) scheme.primary else scheme.onSurface,
                        )
                        if (selected) Icon(Icons.Filled.Check, null, tint = scheme.primary)
                    }
                    if (i < LANGUAGES.lastIndex) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.08f))
                }
            }
        }

        // ---- Appearance ----
        Stagger(1) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.set_appearance), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Accent.entries.forEachIndexed { i, accent ->
                        AccentDot(accent, selected = i == accentIndex) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            settings.setAccent(i)
                        }
                    }
                }
            }
        }

        // ---- Feedback & display ----
        Stagger(2) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.set_feedback), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                ToggleRow(stringResource(R.string.haptics), stringResource(R.string.haptics_desc), haptics) { settings.setHaptics(it) }
                HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.08f))
                ToggleRow(stringResource(R.string.keep_screen_on), stringResource(R.string.keep_screen_on_desc), keepOn) { settings.setKeepScreenOn(it) }
            }
        }

        // ---- Speed test ----
        Stagger(3) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.set_speedtest), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                ToggleRow(stringResource(R.string.default_simulate), stringResource(R.string.default_simulate_desc), defaultSim) {
                    settings.setDefaultSimulate(it)
                }
            }
        }

        // ---- Data ----
        Stagger(4) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.set_data), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        AppGraph.speedHistory.value = emptyList()
                        AppGraph.toast(UiText(R.string.toast_history_cleared))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.clear_history)) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        AppGraph.clearProfiles()
                        AppGraph.toast(UiText(R.string.toast_profiles_cleared))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.clear_profiles)) }
            }
        }

        // ---- About ----
        Stagger(5) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.set_about), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.about_version, versionName), color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.about_dev), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Colour swatch: selected one grows with a spring and shows a check + ring. */
@Composable
private fun AccentDot(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    val size by animateDpAsState(if (selected) 44.dp else 36.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "dot")
    val ring by animateColorAsState(if (selected) Color.White else Color.Transparent, label = "ring")
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(accent.color)
                .border(2.dp, ring, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Filled.Check, stringResource(accent.nameRes), tint = Color(0xFF00201D))
        }
    }
}
