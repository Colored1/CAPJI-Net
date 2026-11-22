package com.capji.netpulse.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.capji.netpulse.R

/** User-selectable accent colours (all bright enough for the dark onPrimary). */
enum class Accent(val color: Color, @StringRes val nameRes: Int) {
    TEAL(Color(0xFF58E6D9), R.string.accent_teal),
    BLUE(Color(0xFF6EA8FF), R.string.accent_blue),
    PURPLE(Color(0xFFB794F6), R.string.accent_purple),
    ORANGE(Color(0xFFFFA45C), R.string.accent_orange),
    PINK(Color(0xFFFF7AB6), R.string.accent_pink),
    GREEN(Color(0xFF7BE495), R.string.accent_green),
}

/** Swapped in for LocalHapticFeedback when the user turns haptics off in Settings. */
object NoHaptic : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
