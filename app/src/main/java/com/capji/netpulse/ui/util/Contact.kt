package com.capji.netpulse.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Developer contact details (taken from the owner's own earlier messages). Change them here only. */
object Contact {
    const val INSTAGRAM_USER = "axh.d"
    const val TIKTOK_USER = "c1fq_7"
    const val PHONE_LOCAL = "772678571"
    const val PHONE_INTL = "967772678571" // Yemen +967

    val instagram get() = "https://instagram.com/$INSTAGRAM_USER"
    val tiktok get() = "https://www.tiktok.com/@$TIKTOK_USER"
    val whatsapp get() = "https://wa.me/$PHONE_INTL"
    val tel get() = "tel:+$PHONE_INTL"

    /** Opens a link in its app/browser; returns false if nothing can handle it. */
    fun open(context: Context, uri: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
