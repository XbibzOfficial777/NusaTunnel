package com.nusa.tunnel.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import java.util.UUID

/**
 * Parser link server — format nyata yang dipakai dunia nyata:
 *  vmess://  (base64 JSON v2rayN: v/ps/add/port/id/aid/scy/net/type/host/path/tls/sni/alpn/fp)
 *  vless://  uuid@host:port?security=tls|reality&type=ws|grpc|httpupgrade|xhttp&path=&flow=#remarks
 *  trojan:// password@host:port?security=tls|reality&type=tcp|ws|grpc|httpupgrade|xhttp#remarks
 *  ss://     base64(method:password)@host:port#remarks  (atau bentuk lama tanpa @)
 *
 * JSON diproses dengan kotlinx.serialization (bukan org.json) supaya
 * parser berjalan identik di app, unit test JVM, maupun CI.
 */
object LinkParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): ServerProfile? {
        val link = raw.trim()
        return try {
            when {
                link.startsWith("vmess://", true) -> parseVmess(link.substring(8))
                link.startsWith("vless://", true) -> parseVless(link.substring(8))
                // CATATAN: "trojan://" panjangnya 9 karakter — substring(10)
                // akan memotong karakter pertama password (bug v2.2.1).
                link.startsWith("trojan://", true) -> parseTrojan(link.substring(9))
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
        val arr = json.parseToJsonElement(jsonText).jsonObject["outbounds"] as? JsonArray
            ?: return emptyList()
        arr.mapNotNull { el ->
            val ob = el as? JsonObject ?: return@mapNotNull null
            val proto = when ((ob["protocol"] as? JsonPrimitive)?.content?.lowercase()) {
                "vmess" -> Proto.VMess
                "vless" -> Proto.VLESS
                "trojan" -> Proto.Trojan
                "shadowsocks" -> Proto.Shadowsocks
                else -> null
            } ?: return@mapNotNull null
            outboundToProfile(ob, proto)
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun JsonObject.str(vararg keys: String): String {
        for (k in keys) {
            val v = this[k] as? JsonPrimitive ?: continue
            return v.contentOrNull ?: ""
        }
        return ""
    }

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

    /** gabung array JSON string jadi satu string dengan pemisah. */
    private fun JsonArray.joinStr(sep: String): String =
        mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(sep)

    private fun outboundToProfile(ob: JsonObject, proto: Proto): ServerProfile? {
        val s = ob.obj("settings") ?: return null
        var host = ""; var port = 0; var secret = ""; var enc = ""; var flow = ""
        when (proto) {
            Proto.VMess, Proto.VLESS -> {
                val vnext = s.arr("vnext")?.get(0) as? JsonObject ?: return null
                val user = vnext.arr("users")?.get(0) as? JsonObject
                host = vnext.str("address")
                port = vnext.int("port")
                secret = user?.str("id") ?: ""
                enc = user?.str("security", "encryption") ?: ""
                flow = user?.str("flow") ?: ""
            }
            Proto.Trojan -> {
                val srv = s.arr("servers")?.get(0) as? JsonObject ?: return null
                // Xray >= v26: password di level server; versi lama: di users[0]
                secret = if (srv.containsKey("password")) srv.str("password")
                else (srv.arr("users")?.get(0) as? JsonObject)?.str("password") ?: ""
                host = srv.str("address")
                port = srv.int("port")
            }
            Proto.Shadowsocks -> {
                val srv = s.arr("servers")?.get(0) as? JsonObject ?: return null
                host = srv.str("address")
                port = srv.int("port")
                secret = srv.str("password")
                enc = srv.str("method")
            }
        }
        if (host.isBlank() || port !in 1..65535 || secret.isBlank()) return null

        val ss = ob.obj("streamSettings")
        val network = ss?.str("network") ?: "tcp"
        val security = ss?.str("security") ?: "none"
        val tls = security.equals("tls", true) || security.equals("reality", true)
        val tlsSet = ss?.obj("tlsSettings")
        val realitySet = ss?.obj("realitySettings")
        val wsSet = ss?.obj("wsSettings")
        val httpUpgradeSet = ss?.obj("httpupgradeSettings")
        val httpSet = ss?.obj("httpSettings")
        val xhttpSet = ss?.obj("xhttpSettings") ?: ss?.obj("splithttpSettings")
        val grpcSet = ss?.obj("grpcSettings")
        val netSet = ss?.obj("networkSettings")
        val realityPublicKey = realitySet?.str("publicKey", "password") ?: ""
        val realityShortId = realitySet?.str("shortId") ?: ""
        if (security.equals("reality", true) && !isValidReality(realityPublicKey, realityShortId)) return null

        return ServerProfile(
            id = UUID.randomUUID().toString(),
            proto = proto,
            remarks = ob.str("tag").ifBlank { "$host:$port" },
            host = host, port = port, secret = secret,
            encryption = enc.ifBlank { if (proto == Proto.VMess) "auto" else if (proto == Proto.Shadowsocks) "aes-256-gcm" else "" },
            network = network,
            tls = tls,
            streamSecurity = security,
            realityPublicKey = realityPublicKey,
            realityShortId = realityShortId,
            realitySpiderX = realitySet?.str("spiderX") ?: "",
            sni = tlsSet?.str("serverName") ?: realitySet?.str("serverName") ?: "",
            alpn = tlsSet?.arr("alpn")?.joinStr(",") ?: "",
            fingerprint = tlsSet?.str("fingerprint") ?: realitySet?.str("fingerprint") ?: "",
            flow = flow,
            wsPath = wsSet?.str("path") ?: httpUpgradeSet?.str("path") ?: xhttpSet?.str("path")
                ?: httpSet?.str("path") ?: netSet?.str("path") ?: "/",
            wsHost = wsSet?.str("host") ?: httpUpgradeSet?.str("host") ?: xhttpSet?.str("host")
                ?: httpSet?.arr("host")?.joinStr(",") ?: netSet?.str("host") ?: "",
            xhttpMode = xhttpSet?.str("mode").orEmpty().ifBlank {
                xhttpSet?.obj("extra")?.str("mode") ?: ""
            },
            xhttpExtra = xhttpSet?.obj("extra")?.toString() ?: "",
            grpcServiceName = grpcSet?.str("serviceName") ?: "",
            allowInsecure = tlsSet?.bool("allowInsecure") ?: false,
        )
    }

    // ---------------------------------------------------------- vmess

    private fun parseVmess(body: String): ServerProfile? {
        val decoded = b64Decode(body) ?: return null
        val o = json.parseToJsonElement(decoded).jsonObject
        val net = o.str("net").ifBlank { "tcp" }
        val tls = o.str("tls").equals("tls", true)
        return ServerProfile(
            id = UUID.randomUUID().toString(),
            proto = Proto.VMess,
            remarks = o.str("ps").ifBlank { "${o.str("add")}:${o.str("port")}" },
            host = o.str("add"),
            port = o.int("port"),
            secret = o.str("id"),
            encryption = o.str("scy").ifBlank { "auto" },
            network = net,
            tls = tls,
            streamSecurity = if (tls) "tls" else "none",
            sni = o.str("sni"),
            alpn = o.str("alpn"),
            fingerprint = o.str("fp"),
            wsPath = o.str("path").ifBlank { "/" },
            wsHost = o.str("host"),
            grpcServiceName = o.str("path"),
            allowInsecure = o.str("allowInsecure").equals("1", true),
        )
    }

    // ---------------------------------------------------------- vless / trojan

    private fun parseVless(body: String): ServerProfile? =
        parseUserInfoHost(body)?.let { (secret, host, port, q, remarks) ->
            val security = q["security"] ?: "none"
            val realityPublicKey = q["pbk"] ?: q["publicKey"] ?: ""
            val realityShortId = q["sid"] ?: q["shortId"] ?: ""
            if (security.equals("reality", true) && !isValidReality(realityPublicKey, realityShortId)) {
                return@let null
            }
            ServerProfile(
                id = UUID.randomUUID().toString(),
                proto = Proto.VLESS,
                remarks = remarks.ifBlank { "$host:$port" },
                host = host, port = port, secret = secret,
                encryption = "none",
                network = normalizeNetwork(q["type"] ?: "tcp"),
                tls = security.equals("tls", true) || security.equals("reality", true),
                streamSecurity = security,
                realityPublicKey = realityPublicKey,
                realityShortId = realityShortId,
                realitySpiderX = q["spx"] ?: q["spiderX"] ?: "",
                sni = q["sni"] ?: "",
                alpn = q["alpn"] ?: "",
                fingerprint = q["fp"] ?: "",
                flow = q["flow"] ?: "",
                wsPath = q["path"] ?: "/",
                wsHost = q["host"] ?: "",
                xhttpMode = parseXhttpMode(q["mode"], q["extra"]),
                xhttpExtra = parseJsonObject(q["extra"]),
                grpcServiceName = q["serviceName"] ?: "",
                allowInsecure = isTruthy(q["allowInsecure"] ?: q["allowinsecure"] ?: q["insecure"]),
            )
        }

    private fun parseTrojan(body: String): ServerProfile? =
        parseUserInfoHost(body)?.let { (secret, host, port, q, remarks) ->
            val security = q["security"] ?: "tls"
            val realityPublicKey = q["pbk"] ?: q["publicKey"] ?: ""
            val realityShortId = q["sid"] ?: q["shortId"] ?: ""
            if (security.equals("reality", true) && !isValidReality(realityPublicKey, realityShortId)) {
                return@let null
            }
            ServerProfile(
                id = UUID.randomUUID().toString(),
                proto = Proto.Trojan,
                remarks = remarks.ifBlank { "$host:$port" },
                host = host, port = port, secret = secret,
                network = normalizeNetwork(q["type"] ?: "tcp"),
                tls = security.equals("tls", true) || security.equals("reality", true),
                streamSecurity = security,
                realityPublicKey = realityPublicKey,
                realityShortId = realityShortId,
                realitySpiderX = q["spx"] ?: q["spiderX"] ?: "",
                sni = q["sni"] ?: "",
                alpn = q["alpn"] ?: "",
                fingerprint = q["fp"] ?: "",
                wsPath = q["path"] ?: "/",
                wsHost = q["host"] ?: "",
                xhttpMode = parseXhttpMode(q["mode"], q["extra"]),
                xhttpExtra = parseJsonObject(q["extra"]),
                grpcServiceName = q["serviceName"] ?: "",
                allowInsecure = isTruthy(q["allowInsecure"] ?: q["allowinsecure"] ?: q["insecure"]),
            )
        }

    private fun normalizeNetwork(value: String): String =
        if (value.startsWith("tcp@", ignoreCase = true)) "tcp" else value.lowercase()

    private fun isValidReality(publicKey: String, shortId: String): Boolean {
        val keyBytes = runCatching {
            Base64.getDecoder().decode(publicKey.replace('-', '+').replace('_', '/'))
        }.getOrNull()
        val validShortId = shortId.length <= 16 && shortId.length % 2 == 0 && shortId.all {
            it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'
        }
        return keyBytes?.size == 32 && validShortId
    }

    private fun parseJsonObject(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            (json.parseToJsonElement(value) as? JsonObject)?.toString().orEmpty()
        }.getOrDefault("")
    }

    private fun parseXhttpMode(mode: String?, extra: String?): String {
        if (!mode.isNullOrBlank()) return mode
        val extraObject = parseJsonObject(extra)
        if (extraObject.isBlank()) return ""
        return runCatching {
            json.parseToJsonElement(extraObject).jsonObject.str("mode")
        }.getOrDefault("")
    }

    private fun isTruthy(value: String?): Boolean = when (value?.trim()?.lowercase()) {
        "1", "true", "yes", "on" -> true
        else -> false
    }

    /** Bentuk umum: secret@host:port?query#fragment */
    private fun parseUserInfoHost(body: String): Tuple5? {
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
            urlDecode(it.substringBefore('=')) to urlDecode(it.substringAfter('='))
        }

    private fun urlDecode(s: String): String =
        java.net.URLDecoder.decode(s, "UTF-8")

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
