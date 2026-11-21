package com.nusa.tunnel.core

import java.util.Base64
import java.util.UUID

/**
 * Parser link server — format nyata yang dipakai dunia nyata:
 *  vmess://  (base64 JSON v2rayN: v/ps/add/port/id/aid/scy/net/type/host/path/tls/sni/alpn/fp)
 *  vless://  uuid@host:port?encryption=none&security=tls&sni=&fp=&type=ws&path=&flow=#remarks
 *  trojan:// password@host:port?security=tls&sni=&type=#remarks
 *  ss://     base64(method:password)@host:port#remarks  (atau bentuk lama tanpa @)
 */
object LinkParser {

    fun parse(raw: String): ServerProfile? {
        val link = raw.trim()
        return try {
            when {
                link.startsWith("vmess://", true) -> parseVmess(link.substring(8))
                link.startsWith("vless://", true) -> parseVless(link.substring(8))
                link.startsWith("trojan://", true) -> parseTrojan(link.substring(10))
                link.startsWith("ss://", true) -> parseShadowsocks(link.substring(5))
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseMany(text: String): List<ServerProfile> =
        text.lines().mapNotNull { l -> parse(l)?.takeIf { it.host.isNotBlank() } }

    /**
     * Parse config Xray-core JSON mentah (ala HTTP Custom: paste config penuh).
     * Mengambil semua outbound vmess/vless/trojan/shadowsocks beserta streamSettings-nya.
     */
    fun parseXrayConfig(jsonText: String): List<ServerProfile> = try {
        val root = org.json.JSONObject(jsonText)
        val arr = root.optJSONArray("outbounds") ?: return emptyList()
        val out = mutableListOf<ServerProfile>()
        for (i in 0 until arr.length()) {
            val ob = arr.optJSONObject(i) ?: continue
            val proto = when (ob.optString("protocol").lowercase()) {
                "vmess" -> Proto.VMess
                "vless" -> Proto.VLESS
                "trojan" -> Proto.Trojan
                "shadowsocks" -> Proto.Shadowsocks
                else -> null
            } ?: continue
            val p = outboundToProfile(ob, proto) ?: continue
            out.add(p)
        }
        out
    } catch (e: Exception) {
        emptyList()
    }

    private fun outboundToProfile(ob: org.json.JSONObject, proto: Proto): ServerProfile? {
        val s = ob.optJSONObject("settings") ?: return null
        var host = ""; var port = 0; var secret = ""; var enc = ""
        when (proto) {
            Proto.VMess, Proto.VLESS -> {
                val vnext = s.optJSONArray("vnext")?.optJSONObject(0) ?: return null
                val user = vnext.optJSONArray("users")?.optJSONObject(0)
                host = vnext.optString("address")
                port = vnext.optInt("port")
                secret = user?.optString("id") ?: ""
                enc = user?.optString("security") ?: user?.optString("encryption") ?: ""
            }
            Proto.Trojan -> {
                val srv = s.optJSONArray("servers")?.optJSONObject(0) ?: return null
                // Xray >= v26: password di level server; versi lama: di users[0]
                secret = if (srv.has("password")) srv.optString("password")
                else srv.optJSONArray("users")?.optJSONObject(0)?.optString("password") ?: ""
                host = srv.optString("address")
                port = srv.optInt("port")
            }
            Proto.Shadowsocks -> {
                val srv = s.optJSONArray("servers")?.optJSONObject(0) ?: return null
                host = srv.optString("address")
                port = srv.optInt("port")
                secret = srv.optString("password")
                enc = srv.optString("method")
            }
        }
        if (host.isBlank() || port !in 1..65535 || secret.isBlank()) return null

        val ss = ob.optJSONObject("streamSettings")
        val network = ss?.optString("network") ?: "tcp"
        val security = ss?.optString("security") ?: "none"
        val tls = security.equals("tls", true) || security.equals("reality", true)
        val tlsSet = ss?.optJSONObject("tlsSettings")
        val wsSet = ss?.optJSONObject("wsSettings")
        val grpcSet = ss?.optJSONObject("grpcSettings")
        val netSet = ss?.optJSONObject("networkSettings")

        return ServerProfile(
            id = java.util.UUID.randomUUID().toString(),
            proto = proto,
            remarks = ob.optString("tag").ifBlank { "$host:$port" },
            host = host, port = port, secret = secret,
            encryption = enc.ifBlank { if (proto == Proto.VMess) "auto" else if (proto == Proto.Shadowsocks) "aes-256-gcm" else "" },
            network = network,
            tls = tls,
            sni = tlsSet?.optString("serverName") ?: "",
            alpn = tlsSet?.optJSONArray("alpn")?.join("/") ?: "",
            fingerprint = tlsSet?.optString("fingerprint") ?: "",
            flow = s.optJSONArray("vnext")?.optJSONObject(0)
                ?.optJSONArray("users")?.optJSONObject(0)?.optString("flow") ?: "",
            wsPath = wsSet?.optString("path") ?: netSet?.optString("path") ?: "/",
            wsHost = wsSet?.optString("host") ?: netSet?.optString("host") ?: "",
            grpcServiceName = grpcSet?.optString("serviceName") ?: "",
            allowInsecure = tlsSet?.optBoolean("allowInsecure") ?: false,
        )
    }

    // ---------------------------------------------------------- vmess

    private fun parseVmess(body: String): ServerProfile? {
        val json = b64Decode(body) ?: return null
        val o = org.json.JSONObject(json)
        val net = o.optString("net", "tcp")
        val tls = o.optString("tls", "").equals("tls", true)
        return ServerProfile(
            id = UUID.randomUUID().toString(),
            proto = Proto.VMess,
            remarks = o.optString("ps").ifBlank { "${o.optString("add")}:${o.optString("port")}" },
            host = o.optString("add"),
            port = o.optInt("port"),
            secret = o.optString("id"),
            encryption = o.optString("scy").ifBlank { "auto" },
            network = net,
            tls = tls,
            sni = o.optString("sni"),
            alpn = o.optString("alpn"),
            fingerprint = o.optString("fp"),
            wsPath = o.optString("path").ifBlank { "/" },
            wsHost = o.optString("host"),
            grpcServiceName = o.optString("path"),
            allowInsecure = o.optString("allowInsecure").equals("1", true),
        )
    }

    // ---------------------------------------------------------- vless / trojan

    private fun parseVless(body: String): ServerProfile? =
        parseUserInfoHost(body, Proto.VLESS)?.let { (secret, host, port, q, remarks) ->
            ServerProfile(
                id = UUID.randomUUID().toString(),
                proto = Proto.VLESS,
                remarks = remarks.ifBlank { "$host:$port" },
                host = host, port = port, secret = secret,
                encryption = "none",
                network = q["type"] ?: "tcp",
                tls = (q["security"] ?: "").equals("tls", true) || (q["security"] ?: "").equals("reality", true),
                sni = q["sni"] ?: "",
                alpn = q["alpn"] ?: "",
                fingerprint = q["fp"] ?: "",
                flow = q["flow"] ?: "",
                wsPath = (q["path"] ?: "/"),
                wsHost = q["host"] ?: "",
                grpcServiceName = q["serviceName"] ?: "",
                allowInsecure = q["allowInsecure"].equals("1", true),
            )
        }

    private fun parseTrojan(body: String): ServerProfile? =
        parseUserInfoHost(body, Proto.Trojan)?.let { (secret, host, port, q, remarks) ->
            ServerProfile(
                id = UUID.randomUUID().toString(),
                proto = Proto.Trojan,
                remarks = remarks.ifBlank { "$host:$port" },
                host = host, port = port, secret = secret,
                network = q["type"] ?: "tcp",
                tls = true, // trojan selalu TLS
                sni = q["sni"] ?: "",
                alpn = q["alpn"] ?: "",
                fingerprint = q["fp"] ?: "",
                wsPath = (q["path"] ?: "/"),
                wsHost = q["host"] ?: "",
                grpcServiceName = q["serviceName"] ?: "",
                allowInsecure = q["allowInsecure"].equals("1", true),
            )
        }

    /** Bentuk umum: secret@host:port?query#fragment */
    private fun parseUserInfoHost(
        body: String, @Suppress("UNUSED_PARAMETER") proto: Proto,
    ): Tuple5? {
        val hashSplit = body.split("#", limit = 2)
        val remarks = if (hashSplit.size == 2) urlDecode(hashSplit[1]) else ""
        val beforeHash = hashSplit[0]
        val qSplit = beforeHash.split("?", limit = 2)
        val query = if (qSplit.size == 2) parseQuery(qSplit[1]) else emptyMap()
        val authority = qSplit[0]
        val at = authority.lastIndexOf('@')
        if (at < 1) return null
        val secret = urlDecode(authority.substring(0, at))
        val hostPort = authority.substring(at + 1)
        // host bisa [IPv6]
        val hp = if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            hostPort.substring(1, close) to hostPort.substring(close + 2, hostPort.length).trimStart(':')
        } else {
            val idx = hostPort.lastIndexOf(':')
            hostPort.substring(0, idx) to hostPort.substring(idx + 1)
        }
        val port = hp.second.toIntOrNull() ?: return null
        if (hp.first.isBlank() || port !in 1..65535) return null
        return Tuple5(secret, hp.first, port, query, remarks)
    }

    // ---------------------------------------------------------- shadowsocks

    private fun parseShadowsocks(body: String): ServerProfile? {
        val hashSplit = body.split("#", limit = 2)
        val remarks = if (hashSplit.size == 2) urlDecode(hashSplit[1]) else ""
        val main = hashSplit[0].substringBefore("?")
        return if (main.contains('@')) {
            // SIP002: base64(method:password)@host:port  (base64 boleh url-safe)
            val at = main.lastIndexOf('@')
            val userInfo = b64DecodeFlex(main.substring(0, at)) ?: return null
            val colon = userInfo.indexOf(':')
            val method = userInfo.substring(0, colon)
            val pass = userInfo.substring(colon + 1)
            val hostPort = main.substring(at + 1)
            val idx = hostPort.lastIndexOf(':')
            val host = hostPort.substring(0, idx)
            val port = hostPort.substring(idx + 1).toIntOrNull() ?: return null
            ssProfile(remarks, host, port, method, pass)
        } else {
            // bentuk lama: base64(method:password@host:port)
            val full = b64DecodeFlex(main) ?: return null
            val at = full.lastIndexOf('@')
            val userInfo = full.substring(0, at)
            val hostPort = full.substring(at + 1)
            val colon = userInfo.indexOf(':')
            val idx = hostPort.lastIndexOf(':')
            ssProfile(
                remarks, hostPort.substring(0, idx),
                hostPort.substring(idx + 1).toIntOrNull() ?: return null,
                userInfo.substring(0, colon), userInfo.substring(colon + 1),
            )
        }
    }

    private fun ssProfile(remarks: String, host: String, port: Int, method: String, pass: String) =
        if (host.isBlank() || port !in 1..65535) null
        else ServerProfile(
            id = UUID.randomUUID().toString(),
            proto = Proto.Shadowsocks,
            remarks = remarks.ifBlank { "$host:$port" },
            host = host, port = port, secret = pass,
            encryption = method,
        )

    // ---------------------------------------------------------- util

    private data class Tuple5(
        val secret: String, val host: String, val port: Int,
        val query: Map<String, String>, val remarks: String,
    )

    private fun parseQuery(q: String): Map<String, String> =
        q.split("&").filter { it.contains('=') }.associate {
            val k = it.substringBefore('=')
            urlDecode(it.substringAfter('=')) to urlDecode(k).let { _ -> urlDecode(it.substringAfter('=')) }
        }.mapKeys { urlDecode(it.key) }

    private fun urlDecode(s: String): String =
        java.net.URLDecoder.decode(s, "UTF-8")

    /** Helper: gabung array JSON string jadi satu string dengan pemisah. */
    private fun org.json.JSONArray.join(sep: String): String {
        val sb = StringBuilder()
        for (i in 0 until length()) {
            if (i > 0) sb.append(sep)
            sb.append(optString(i))
        }
        return sb.toString()
    }

    private fun b64Decode(s: String): String? = b64DecodeFlex(s)

    private fun b64DecodeFlex(s: String): String? = try {
        val cleaned = s.replace("-", "+").replace("_", "/").replace("\n", "").replace("\r", "")
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
