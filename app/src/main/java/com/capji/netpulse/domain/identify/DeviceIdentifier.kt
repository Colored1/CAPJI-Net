package com.capji.netpulse.domain.identify

import com.capji.netpulse.domain.model.DeviceKind

/**
 * Device identification = OUI (MAC vendor) lookup + hostname fingerprinting.
 *
 * The OUI table below is a tiny SAMPLE so the demo works offline. In production, bundle the IEEE OUI
 * list (~35k rows) as a compressed asset and load it into a prefix map / SQLite once.
 */
object DeviceIdentifier {

    const val VENDOR_PRIVATE = "@private"
    const val VENDOR_UNKNOWN = "@unknown"

    data class Result(val vendor: String, val model: String?, val kind: DeviceKind, val randomized: Boolean)

    private val oui = mapOf(
        "3C:22:FB" to "Apple", "F0:18:98" to "Apple",
        "8C:77:12" to "Samsung",
        "64:09:80" to "Xiaomi",
        "48:46:FB" to "Huawei",
        "50:C7:BF" to "TP-Link",
        "3C:A9:F4" to "Intel",
        "00:1C:62" to "LG Electronics",
        "00:D9:D1" to "Sony Interactive",
        "00:09:BF" to "Nintendo",
        "24:6F:28" to "Espressif",
        "B8:27:EB" to "Raspberry Pi",
        "3C:5A:B4" to "Google",
        "44:65:0D" to "Amazon",
    )

    /** Locally-administered bit (0x02 of the first octet) set => private / randomized MAC. */
    fun isRandomized(mac: String): Boolean {
        val first = mac.take(2).toIntOrNull(16) ?: return false
        return (first and 0x02) != 0
    }

    fun identify(mac: String, hostname: String): Result {
        val randomized = isRandomized(mac)
        val vendor = if (randomized) VENDOR_PRIVATE else oui[mac.uppercase().take(8)] ?: VENDOR_UNKNOWN
        val h = hostname.lowercase()

        var model: String? = null
        var kind: DeviceKind? = null
        when {
            "iphone" in h -> { model = "iPhone"; kind = DeviceKind.PHONE }
            "ipad" in h -> { model = "iPad"; kind = DeviceKind.TABLET }
            "macbook" in h -> { model = "MacBook"; kind = DeviceKind.LAPTOP }
            "galaxy" in h || h.startsWith("sm-") -> { model = "Samsung Galaxy"; kind = DeviceKind.PHONE }
            "redmi" in h || "poco" in h || "xiaomi" in h -> { model = "Xiaomi Redmi/POCO"; kind = DeviceKind.PHONE }
            "huawei" in h || "honor" in h -> { model = "Huawei / Honor"; kind = DeviceKind.PHONE }
            "pixel" in h -> { model = "Google Pixel"; kind = DeviceKind.PHONE }
            h == "android" || h.startsWith("android-") -> { model = "Android device"; kind = DeviceKind.PHONE }
            h.startsWith("desktop-") || h.startsWith("laptop-") -> { model = "Windows PC"; kind = DeviceKind.LAPTOP }
            "tv" in h || "roku" in h || "bravia" in h -> { model = "Smart TV"; kind = DeviceKind.TV }
            "ps5" in h || "ps4" in h || "playstation" in h -> { model = "PlayStation"; kind = DeviceKind.CONSOLE }
            "xbox" in h || "switch" in h -> { model = "Game console"; kind = DeviceKind.CONSOLE }
            h.startsWith("esp") || "tasmota" in h || "plug" in h || "cam" in h -> { model = "IoT device"; kind = DeviceKind.IOT }
        }

        if (kind == null) {
            kind = when (vendor) {
                "Apple", "Samsung", "Xiaomi", "Huawei", "Google" -> DeviceKind.PHONE
                "Intel" -> DeviceKind.LAPTOP
                "LG Electronics" -> DeviceKind.TV
                "Sony Interactive", "Nintendo" -> DeviceKind.CONSOLE
                "Espressif", "Raspberry Pi", "Amazon" -> DeviceKind.IOT
                else -> DeviceKind.UNKNOWN
            }
        }
        return Result(vendor, model, kind, randomized)
    }
}
