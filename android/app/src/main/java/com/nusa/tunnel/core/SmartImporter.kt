package com.nusa.tunnel.core

import java.util.Base64

/**
 * Import pintar ala HTTP Custom — user menempelkan APA PUN, sistem mendeteksi otomatis:
 *
 *  1. Link tunggal / banyak baris : vmess:// vless:// trojan:// ss://
 *  2. URL http(s) tunggal          : dianggap subscription → di-fetch (daftar link / base64 / JSON)
 *  3. Blob base64                  : didecode → daftar link atau config JSON
 *  4. Config Xray JSON mentah      : {"inbounds":…, "outbounds":[…]} → outbound diekstrak
 *
 * Detect urutan bisa campur per baris (link + URL di teks yang sama).
 */
object SmartImporter {

    data class ImportResult(
        val servers: List<ServerProfile>,
        /** URL subscription yang terdeteksi dari input (utk dipasang otomatis), null bila bukan sub. */
        val subscriptionUrl: String? = null,
        val error: String? = null,
    )

    /** Deteksi & parse (suspend karena bisa melakukan fetch jaringan). */
    suspend fun importText(text: String): ImportResult {
        val input = text.trim()
        if (input.isEmpty()) return ImportResult(emptyList(), error = "Teks kosong")

        // --- kasus 1: URL subscription tunggal (http/https, tanpa baris lain) ---
        val singleLine = input.lines().filter { it.isNotBlank() }
        if (singleLine.size == 1 && isHttpUrl(singleLine[0])) {
            val url = singleLine[0].trim()
            val res = SubscriptionManager.fetch(url)
            return if (res.error != null) {
                ImportResult(emptyList(), error = res.error)
            } else {
                // URL tunggal dianggap subscription → pasang otomatis juga
                ImportResult(res.servers, subscriptionUrl = url)
            }
        }

        // --- kasus 2: config Xray JSON mentah ---
        if (input.startsWith("{")) {
            val fromJson = LinkParser.parseXrayConfig(input)
            if (fromJson.isNotEmpty()) return ImportResult(fromJson)
        }

        // --- kasus 3: daftar link per baris (boleh campur link) ---
        val links = LinkParser.parseMany(input)
        if (links.isNotEmpty()) return ImportResult(links)

        // --- kasus 4: blob base64 → decode → coba lagi sebagai link/JSON ---
        val decoded = b64DecodeOrNull(input)
        if (decoded != null) {
            if (decoded.trim().startsWith("{")) {
                val fromJson = LinkParser.parseXrayConfig(decoded.trim())
                if (fromJson.isNotEmpty()) return ImportResult(fromJson)
            }
            val linksFromB64 = LinkParser.parseMany(decoded)
            if (linksFromB64.isNotEmpty()) return ImportResult(linksFromB64)
        }

        return ImportResult(
            emptyList(),
            error = "Format tidak dikenali. Tempel link vmess/vless/trojan/ss, URL subscription, base64, atau config JSON.",
        )
    }

    fun isHttpUrl(s: String): Boolean {
        val t = s.trim()
        return (t.startsWith("http://", true) || t.startsWith("https://", true)) &&
                !t.contains("\n") && t.length > 8
    }

    private fun b64DecodeOrNull(s: String): String? = try {
        val cleaned = s.replace("-", "+").replace("_", "/")
            .replace("\n", "").replace("\r", "").replace(" ", "")
        if (cleaned.length < 8 || cleaned.length % 4 == 1) return null
        val padded = when (cleaned.length % 4) {
            2 -> cleaned + "=="
            3 -> cleaned + "="
            else -> cleaned
        }
        val bytes = Base64.getMimeDecoder().decode(padded)
        // pastikan hasilnya teks layak (bukan sampah biner)
        val str = String(bytes, Charsets.UTF_8)
        if (str.any { it == '\uFFFD' }) null else str
    } catch (e: Exception) {
        null
    }
}
