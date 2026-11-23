package com.capji.netpulse.data.net

import android.util.Log

/**
 * Ring buffer of what the drivers sent/received (URLs + status + first bytes of the answer, never passwords).
 * Settings -> "Router diagnostics" lets the user copy it, so a driver can be fixed for an exact firmware.
 */
object Diag {
    private const val TAG = "CAPJI"
    private const val MAX = 200
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(msg: String) {
        Log.d(TAG, msg)
        if (lines.size >= MAX) lines.removeFirst()
        lines.addLast(msg)
    }

    @Synchronized
    fun dump(): String = lines.joinToString("\n")

    @Synchronized
    fun clear() = lines.clear()
}
