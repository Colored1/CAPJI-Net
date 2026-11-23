package com.capji.netpulse.data.net

import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher

object Crypto {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    fun sha256Hex(s: String) = hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray()))
    fun md5Hex(s: String) = hex(MessageDigest.getInstance("MD5").digest(s.toByteArray()))
    fun b64(s: String): String = Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP)

    /** RSA / PKCS#1 v1.5 with a public key given as hex modulus + hex exponent. Returns lowercase hex. */
    fun rsaHex(modulusHex: String, exponentHex: String, text: String): String {
        val n = BigInteger(modulusHex, 16)
        val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, BigInteger(exponentHex, 16)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val out = cipher.doFinal(text.toByteArray())
        val size = (n.bitLength() + 7) / 8
        return hex(out).padStart(size * 2, '0')
    }
}

/** Tiny tolerant XML helpers (router XML is flat and sometimes malformed). */
object Xml {
    fun tag(s: String, name: String): String? =
        Regex("<$name>([\\s\\S]*?)</$name>", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.trim()

    fun blocks(s: String, name: String): List<String> =
        Regex("<$name>([\\s\\S]*?)</$name>", RegexOption.IGNORE_CASE).findAll(s).map { it.groupValues[1] }.toList()

    fun errorCode(s: String): Int? =
        Regex("<error>\\s*<code>(\\d+)</code>", RegexOption.IGNORE_CASE).find(s)?.groupValues?.get(1)?.toIntOrNull()
}

object Macs {
    private val any = Regex("(?i)\\b([0-9a-f]{2}([:-])){5}[0-9a-f]{2}\\b")

    /** AA:BB:CC:DD:EE:FF (upper-case) or null when it isn't a MAC. */
    fun norm(s: String): String? {
        val m = any.find(s.trim())?.value ?: return null
        return m.uppercase().replace('-', ':')
    }

    fun findAll(s: String): List<String> = any.findAll(s).map { it.value.uppercase().replace('-', ':') }.toList()
    fun dashed(mac: String) = mac.uppercase().replace(':', '-')
}
