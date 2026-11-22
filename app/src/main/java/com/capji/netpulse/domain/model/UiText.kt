package com.capji.netpulse.domain.model

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.capji.netpulse.R

/**
 * A message that is resolved to a string only when it is shown, so it always follows the language
 * the user picked. Drivers/ViewModels return UiText instead of hard-coded English.
 * Arguments may themselves be UiText (e.g. a localized action name inside a sentence).
 */
data class UiText(
    @StringRes val id: Int = 0,
    val args: List<Any> = emptyList(),
    val raw: String? = null,
) {
    fun resolve(context: Context): String {
        raw?.let { return it }
        val resolved = args.map { if (it is UiText) it.resolve(context) else it }
        return context.getString(id, *resolved.toTypedArray())
    }
}

@Composable
fun UiText.asString(): String = resolve(LocalContext.current)

class UiTextException(val text: UiText) : Exception(text.raw)

fun Throwable.toUiText(): UiText =
    (this as? UiTextException)?.text
        ?: message?.takeIf { it.isNotBlank() }?.let { UiText(raw = it) }
        ?: UiText(R.string.err_action_failed)
