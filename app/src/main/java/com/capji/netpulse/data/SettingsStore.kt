package com.capji.netpulse.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App preferences (SharedPreferences) exposed as StateFlows so the UI reacts instantly. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _accent = MutableStateFlow(prefs.getInt("accent", 0))
    val accent: StateFlow<Int> = _accent.asStateFlow()

    private val _haptics = MutableStateFlow(prefs.getBoolean("haptics", true))
    val haptics: StateFlow<Boolean> = _haptics.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean("keep_screen_on", false))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _defaultSimulate = MutableStateFlow(prefs.getBoolean("default_simulate", false))
    val defaultSimulate: StateFlow<Boolean> = _defaultSimulate.asStateFlow()

    private val _bgTimers = MutableStateFlow(prefs.getBoolean("bg_timers", true))
    val bgTimers: StateFlow<Boolean> = _bgTimers.asStateFlow()
    fun setBgTimers(on: Boolean) { _bgTimers.value = on; prefs.edit().putBoolean("bg_timers", on).apply() }

    fun setAccent(index: Int) { _accent.value = index; prefs.edit().putInt("accent", index).apply() }
    fun setHaptics(on: Boolean) { _haptics.value = on; prefs.edit().putBoolean("haptics", on).apply() }
    fun setKeepScreenOn(on: Boolean) { _keepScreenOn.value = on; prefs.edit().putBoolean("keep_screen_on", on).apply() }
    fun setDefaultSimulate(on: Boolean) { _defaultSimulate.value = on; prefs.edit().putBoolean("default_simulate", on).apply() }
}
