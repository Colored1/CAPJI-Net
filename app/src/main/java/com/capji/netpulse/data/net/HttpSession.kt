package com.capji.netpulse.data.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class Resp(val code: Int, val body: String, val headers: Headers, val url: String)

/**
 * One cookie-keeping HTTP session towards ONE router. Home routers use self-signed HTTPS certificates,
 * so HTTPS certificate checks are skipped - but ONLY for private LAN addresses (see [guard]).
 */
class HttpSession {
    private val cookies = ConcurrentHashMap<String, String>()

    private val jar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { c -> if (c.value.isEmpty() || c.value == "deleted") this@HttpSession.cookies.remove(c.name) else this@HttpSession.cookies[c.name] = c.value }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies.map { (k, v) -> Cookie.Builder().name(k).value(v).hostOnlyDomain(url.host).path("/").build() }
    }

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val client: OkHttpClient = run {
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        OkHttpClient.Builder()
            .cookieJar(jar)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    fun setCookie(name: String, value: String) { cookies[name] = value }
    fun cookie(name: String): String? = cookies[name]
    fun reset() = cookies.clear()

    private fun guard(url: String) {
        val u = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("Bad URL")
        if (u.isHttps && !isPrivate(u.host)) throw SecurityException("HTTPS without certificate check is only allowed on LAN addresses")
    }

    private fun isPrivate(h: String): Boolean {
        val p = h.split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168) || (p[0] == 169 && p[1] == 254)
    }

    private fun exec(req: Request): Resp {
        guard(req.url.toString())
        client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            Diag.add("${req.method} ${req.url.encodedPath}${req.url.encodedQuery?.let { "?" + it.take(80) } ?: ""} -> ${r.code} ${text.take(160).replace('\n', ' ')}")
            return Resp(r.code, text, r.headers, req.url.toString())
        }
    }

    private fun builder(url: String, headers: Map<String, String>) =
        Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }

    fun get(url: String, headers: Map<String, String> = emptyMap()): Resp = exec(builder(url, headers).get().build())

    fun postForm(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): Resp {
        val fb = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        return exec(builder(url, headers).post(fb).build())
    }

    fun postRaw(url: String, body: String, contentType: String, headers: Map<String, String> = emptyMap()): Resp =
        exec(builder(url, headers).post(body.toRequestBody(contentType.toMediaType())).build())
}
