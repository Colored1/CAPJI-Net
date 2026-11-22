package com.capji.netpulse.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Locale.US everywhere so digits stay Latin even on Arabic-locale devices.
fun fmt1(v: Float): String = String.format(Locale.US, "%.1f", v)

fun fmtSpeed(v: Float): String = if (v >= 100f) String.format(Locale.US, "%.0f", v) else fmt1(v)

fun formatRate(kbps: Int): String = when {
    kbps <= 0 -> "0 Kbps"
    kbps >= 1000 -> "${fmt1(kbps / 1000f)} Mbps"
    else -> "$kbps Kbps"
}

fun formatMb(mb: Long): String = if (mb >= 1024) "${fmt1(mb / 1024f)} GB" else "$mb MB"

fun formatTime(ms: Long): String = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(ms))

fun formatCountdown(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}
