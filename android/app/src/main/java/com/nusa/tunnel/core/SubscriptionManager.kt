package com.nusa.tunnel.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

/**
 * Subscription URL — gaya v2rayNG/HTTP Custom:
 *  - fetch URL (http/https, redirect diikuti, UA browser-like)
 *  - isi bisa: daftar link (vmess:// vless:// trojan:// ss://) satu per baris,
 *    blob base64 yang berisi daftar link, atau config JSON Xray mentah
 *  - hasil diparse dan menandai server dengan subTag = URL sumber
 */
object SubscriptionManager {

    private const val UA = "NusaTunnel/2.2.1 (Android; github.com/XbibzOfficial777/NusaTunnel)"
    private const val TIMEOUT_MS = 15_000
    private const val MAX_BYTES = 8L * 1024 * 1024  // 8 MB cukup utk subscription besar

    /** Hasil update subscription. */
    data class SubResult(val servers: List<ServerProfile>, val error: String? = null)

    /** Unduh & parse isi URL subscription. Jalan di IO dispatcher. */
    suspend fun fetch(url: String): SubResult = withContext(Dispatchers.IO) {
        try {
            val body = download(url.trim())
            val servers = parseBody(body).map { it.copy(subTag = url.trim()) }
            if (servers.isEmpty()) {
                SubResult(emptyList(), "Konten subscription tidak dikenali (${body.length} karakter)")
            } else {
                SubResult(servers)
            }
        } catch (e: Exception) {
            SubResult(emptyList(), e.message ?: "Gagal menghubungi server subscription")
        }
    }

    /** Update subscription aktif di store: ganti server lama milik sub ini. */
    suspend fun update(store: ServerStore, url: String): SubResult {
        val res = fetch(url)
        if (res.error == null && res.servers.isNotEmpty()) {
            store.replaceSubscriptionServers(url, res.servers)
        }
        return res
    }

    /** Auto-update saat app dibuka (dipanggil NusaApp bila setting aktif). */
    suspend fun autoUpdate(store: ServerStore) {
        val url = store.subscriptionUrl.first()
        val auto = store.subscriptionAuto.first()
        if (auto && !url.isNullOrBlank()) {
            val res = update(store, url)
            TunnelManager.log(
                if (res.error == null) "Subscription otomatis: ${res.servers.size} server diperbarui"
                else "Subscription otomatis gagal: ${res.error}"
            )
        }
    }

    // ---------------------------------------------------------- download

    private fun download(url: String): String {
        var current = url
        var redirects = 0
        while (true) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "*/*")
            try {
                val code = conn.responseCode
                if (code in 300..399) {  // manual redirect utk http<->https mix
                    val loc = conn.getHeaderField("Location") ?: throw Exception("Redirect tanpa lokasi")
                    // konstruktor URL(URL, String): konteks harus objek URL, bukan String
                    current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                    if (++redirects > 5) throw Exception("Terlalu banyak redirect")
                    continue
                }
                if (code !in 200..299) throw Exception("HTTP $code dari server subscription")
                val bytes = conn.inputStream.use { ins ->
                    val buf = ByteArray(64 * 1024)
                    val out = java.io.ByteArrayOutputStream()
                    var read: Int
                    while (ins.read(buf).also { read = it } > 0) {
                        out.write(buf, 0, read)
                        if (out.size().toLong() > MAX_BYTES) throw Exception("Konten subscription terlalu besar")
                    }
                    out.toByteArray()
                }
                return String(bytes, Charsets.UTF_8)
            } finally {
                conn.disconnect()
            }
        }
    }

    // ---------------------------------------------------------- parse isi

    /** Isi subscription: daftar link langsung, base64, atau config JSON. */
    fun parseBody(body: String): List<ServerProfile> {
        val trimmed = body.trim()
        // 1. config JSON mentah
        if (trimmed.startsWith("{")) {
            val fromJson = LinkParser.parseXrayConfig(trimmed)
            if (fromJson.isNotEmpty()) return fromJson
        }
        // 2. daftar link langsung
        val direct = LinkParser.parseMany(trimmed)
        if (direct.isNotEmpty()) return direct
        // 3. blob base64 (subscription klasik)
        val decoded = b64DecodeFlex(trimmed)
        if (decoded != null) {
            if (decoded.trim().startsWith("{")) {
                val fromJson = LinkParser.parseXrayConfig(decoded.trim())
                if (fromJson.isNotEmpty()) return fromJson
            }
            val links = LinkParser.parseMany(decoded)
            if (links.isNotEmpty()) return links
        }
        return emptyList()
    }

    private fun b64DecodeFlex(s: String): String? = try {
        val cleaned = s.replace("-", "+").replace("_", "/")
            .replace("\n", "").replace("\r", "").replace(" ", "")
        val padded = when (cleaned.length % 4) {
            2 -> cleaned + "=="
            3 -> cleaned + "="
            else -> cleaned
        }
        String(Base64.getMimeDecoder().decode(padded), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
