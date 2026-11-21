package com.nusa.tunnel.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test XrayConfigBuilder — memastikan struktur JSON yang dihasilkan
 * DITERIMA Xray-core (pelajaran dari 3 bug lapangan v2.2.0/v2.2.1).
 */
class XrayConfigBuilderTest {

    private val trojan = ServerProfile(
        id = "t1", proto = Proto.Trojan, remarks = "Trojan-X",
        host = "xb.example.com", port = 4443, secret = "tr-pw", tls = true,
        sni = "xb.example.com",
    )
    private val vmess = ServerProfile(
        id = "v1", proto = Proto.VMess, remarks = "VM",
        host = "vm.example.com", port = 443, secret = "b831381d-6324-4d53-ad4f-8cda48b30811",
        encryption = "auto", network = "ws", tls = true, wsPath = "/w",
    )
    private val vless = ServerProfile(
        id = "vl1", proto = Proto.VLESS, remarks = "VL",
        host = "vl.example.com", port = 443, secret = "uuid-vl",
        network = "grpc", grpcServiceName = "svc", tls = true, flow = "xtls-rprx-vision",
    )
    private val ss = ServerProfile(
        id = "s1", proto = Proto.Shadowsocks, remarks = "SS",
        host = "ss.example.com", port = 8388, secret = "pw",
        encryption = "aes-256-gcm",
    )
    private val realityKey = "e2RLf57Li_-MDZGE9ss1BWPgP54mqRb5PfXhW2jcVVg"
    private val vlessReality = vless.copy(
        streamSecurity = "reality", realityPublicKey = realityKey,
        realityShortId = "c39cc7310a", realitySpiderX = "/",
        sni = "www.google.com", fingerprint = "chrome",
    )
    private val trojanReality = trojan.copy(
        streamSecurity = "reality", realityPublicKey = realityKey,
        realityShortId = "601e43eb94a28919", sni = "www.microsoft.com",
        fingerprint = "chrome",
    )
    private val vlessXhttpReality = vless.copy(
        network = "xhttp", streamSecurity = "reality", realityPublicKey = realityKey,
        realityShortId = "c39cc7310a", sni = "www.google.com", fingerprint = "chrome",
        wsPath = "/edge", wsHost = "front.example.com", xhttpMode = "stream-up",
        xhttpExtra = """{"xPaddingBytes":"100-1000","xmux":{"maxConnections":1}}""",
    )
    private val vlessHttpUpgrade = vless.copy(
        network = "httpupgrade", streamSecurity = "tls", sni = "upgrade.example.com",
        wsPath = "/connect", wsHost = "cdn.example.com",
    )
    private val trojanXhttpReality = trojan.copy(
        network = "xhttp", streamSecurity = "reality", realityPublicKey = realityKey,
        realityShortId = "601e43eb94a28919", sni = "www.microsoft.com", fingerprint = "chrome",
        wsPath = "/trojan-edge", wsHost = "front.example.com", xhttpMode = "stream-one",
        xhttpExtra = """{"scMaxEachPostBytes":"100000-200000","xmux":{"maxConnections":1}}""",
    )

    private fun build(p: ServerProfile): JsonObject =
        Json.parseToJsonElement(XrayConfigBuilder.build(p, 10808)).jsonObject

    // ---------------------------------------------------------- regression bug lapangan

    @Test
    fun `routing rule - outboundTag HARUS string bukan array (bug v220)`() {
        val rule = build(trojan)["routing"]!!.jsonObject["rules"]!!.jsonArray.single().jsonObject
        val tag = rule["outboundTag"]
        assertTrue("outboundTag harus string bukan array, dapat: $tag", tag is JsonPrimitive)
        assertEquals("proxy", (tag as JsonPrimitive).content)
    }

    @Test
    fun `routing rule - port HARUS string range bukan array (bug v220)`() {
        val rule = build(trojan)["routing"]!!.jsonObject["rules"]!!.jsonArray.single().jsonObject
        val port = rule["port"]
        assertTrue("port harus string range, dapat: $port", port is JsonPrimitive)
        assertEquals("0-65535", (port as JsonPrimitive).content)
    }

    @Test
    fun `routing rule - tidak ada balancerTag kosong (bug v220)`() {
        val rule = build(trojan)["routing"]!!.jsonObject["rules"]!!.jsonArray.single().jsonObject
        assertFalse(rule.containsKey("balancerTag"))
    }

    @Test
    fun `trojan - password di level server BUKAN di users (bug v221)`() {
        val ob = build(trojan)["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("trojan", (ob["protocol"] as kotlinx.serialization.json.JsonPrimitive).content)
        val servers = ob["settings"]!!.jsonObject["servers"]!!.jsonArray
        assertEquals(1, servers.size)
        val srv = servers[0].jsonObject
        assertEquals("tr-pw", (srv["password"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertFalse("trojan tidak boleh punya users[]", srv.containsKey("users"))
    }

    // ---------------------------------------------------------- struktur umum

    @Test
    fun `struktur dasar - inbounds tun+socks, outbounds proxy+direct+block`() {
        val root = build(vmess)
        val inbounds = root["inbounds"]!!.jsonArray
        assertEquals(2, inbounds.size)
        assertEquals("tun", (inbounds[0].jsonObject["tag"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("socks", (inbounds[1].jsonObject["tag"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals(10808, (inbounds[1].jsonObject["port"] as kotlinx.serialization.json.JsonPrimitive).content.toInt())

        val outbounds = root["outbounds"]!!.jsonArray
        assertEquals(listOf("proxy", "direct", "block"),
            outbounds.map { (it.jsonObject["tag"] as kotlinx.serialization.json.JsonPrimitive).content })
    }

    @Test
    fun `vmess - vnext dengan alterId 0 dan level 8`() {
        val ob = build(vmess)["outbounds"]!!.jsonArray[0].jsonObject
        val user = ob["settings"]!!.jsonObject["vnext"]!!.jsonArray[0]
            .jsonObject["users"]!!.jsonArray[0].jsonObject
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", (user["id"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals(0, (user["alterId"] as kotlinx.serialization.json.JsonPrimitive).content.toInt())
    }

    @Test
    fun `vmess - ws settings path dan host`() {
        val ss0 = build(vmess)["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject
        assertEquals("ws", (ss0["network"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("/w", (ss0["wsSettings"]!!.jsonObject["path"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun `vless - flow dan grpc serviceName`() {
        val ss0 = build(vless)["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject
        val grpc = ss0["grpcSettings"]!!.jsonObject
        assertEquals("svc", (grpc["serviceName"] as kotlinx.serialization.json.JsonPrimitive).content)
        val user = build(vless)["outbounds"]!!.jsonArray[0].jsonObject["settings"]!!
            .jsonObject["vnext"]!!.jsonArray[0].jsonObject["users"]!!.jsonArray[0].jsonObject
        assertEquals("xtls-rprx-vision", (user["flow"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("none", (user["encryption"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun `ss - method dan password di servers`() {
        val srv = build(ss)["outbounds"]!!.jsonArray[0].jsonObject["settings"]!!
            .jsonObject["servers"]!!.jsonArray[0].jsonObject
        assertEquals("aes-256-gcm", (srv["method"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("pw", (srv["password"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun `tls - serverName dan alpn array`() {
        val p = vmess.copy(sni = "s.example.com", alpn = "h2, http/1.1")
        val tls = build(p)["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!
            .jsonObject["tlsSettings"]!!.jsonObject
        assertEquals("s.example.com", (tls["serverName"] as kotlinx.serialization.json.JsonPrimitive).content)
        val alpn = tls["alpn"] as JsonArray
        assertEquals(listOf("h2", "http/1.1"), alpn.map { (it as kotlinx.serialization.json.JsonPrimitive).content })
    }

    @Test
    fun `vless reality - emits realitySettings and never tlsSettings`() {
        val stream = build(vlessReality)["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject
        assertEquals("reality", (stream["security"] as JsonPrimitive).content)
        assertFalse(stream.containsKey("tlsSettings"))
        val reality = stream["realitySettings"]!!.jsonObject
        assertEquals("www.google.com", (reality["serverName"] as JsonPrimitive).content)
        assertEquals("chrome", (reality["fingerprint"] as JsonPrimitive).content)
        assertEquals(realityKey, (reality["publicKey"] as JsonPrimitive).content)
        assertEquals("c39cc7310a", (reality["shortId"] as JsonPrimitive).content)
        assertEquals("/", (reality["spiderX"] as JsonPrimitive).content)
    }

    @Test
    fun `trojan reality - emits reality client settings`() {
        val stream = build(trojanReality)["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject
        assertEquals("reality", (stream["security"] as JsonPrimitive).content)
        assertFalse(stream.containsKey("tlsSettings"))
        val reality = stream["realitySettings"]!!.jsonObject
        assertEquals(realityKey, (reality["publicKey"] as JsonPrimitive).content)
    }

    @Test
    fun `xhttp reality - emits path host mode and extra JSON`() {
        val stream = build(vlessXhttpReality)["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject
        val xhttp = stream["xhttpSettings"]!!.jsonObject
        assertEquals("/edge", (xhttp["path"] as JsonPrimitive).content)
        assertEquals("front.example.com", (xhttp["host"] as JsonPrimitive).content)
        assertEquals("stream-up", (xhttp["mode"] as JsonPrimitive).content)
        val extra = xhttp["extra"]!!.jsonObject
        assertEquals("100-1000", (extra["xPaddingBytes"] as JsonPrimitive).content)
        val xmux = extra["xmux"]!!.jsonObject
        assertEquals(1, (xmux["maxConnections"] as JsonPrimitive).content.toInt())
    }

    @Test
    fun `trojan xhttp reality - emits extra and reality settings together`() {
        val outbound = build(trojanXhttpReality)["outbounds"]!!.jsonArray[0].jsonObject
        assertEquals("trojan", (outbound["protocol"] as JsonPrimitive).content)
        val stream = outbound["streamSettings"]!!.jsonObject
        assertEquals("reality", (stream["security"] as JsonPrimitive).content)
        assertFalse(stream.containsKey("tlsSettings"))
        val xhttp = stream["xhttpSettings"]!!.jsonObject
        assertEquals("/trojan-edge", (xhttp["path"] as JsonPrimitive).content)
        assertEquals("stream-one", (xhttp["mode"] as JsonPrimitive).content)
        val extra = xhttp["extra"]!!.jsonObject
        assertEquals("100000-200000", (extra["scMaxEachPostBytes"] as JsonPrimitive).content)
        val reality = stream["realitySettings"]!!.jsonObject
        assertEquals(realityKey, (reality["publicKey"] as JsonPrimitive).content)
    }

    @Test
    fun `httpupgrade - emits its transport settings`() {
        val stream = build(vlessHttpUpgrade)["outbounds"]!!.jsonArray[0].jsonObject["streamSettings"]!!.jsonObject
        val httpUpgrade = stream["httpupgradeSettings"]!!.jsonObject
        assertEquals("/connect", (httpUpgrade["path"] as JsonPrimitive).content)
        assertEquals("cdn.example.com", (httpUpgrade["host"] as JsonPrimitive).content)
    }

    @Test
    fun `Hev mode config omits TUN inbound and applies log level`() {
        val root = Json.parseToJsonElement(
            XrayConfigBuilder.build(vmess, 10999, includeTunInbound = false, logLevel = "debug")
        ).jsonObject
        val inbounds = root["inbounds"]!!.jsonArray
        assertEquals(1, inbounds.size)
        assertEquals("socks", (inbounds.single().jsonObject["tag"] as JsonPrimitive).content)
        assertEquals("debug", (root["log"]!!.jsonObject["loglevel"] as JsonPrimitive).content)
    }

    @Test
    fun `invalid Xray log level safely falls back to warning`() {
        val root = Json.parseToJsonElement(
            XrayConfigBuilder.build(vmess, 10808, logLevel = "not-a-level")
        ).jsonObject
        assertEquals("warning", (root["log"]!!.jsonObject["loglevel"] as JsonPrimitive).content)
    }

    @Test
    fun `buildProxyOnly - tanpa inbound, log none`() {
        val root = Json.parseToJsonElement(XrayConfigBuilder.buildProxyOnly(trojan)).jsonObject
        assertTrue((root["inbounds"] as JsonArray).isEmpty())
        assertEquals("none", (root["log"]!!.jsonObject["loglevel"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun `sniffer enabled - applies http tls quic overrides to all active inbounds`() {
        val inbounds = build(trojan)["inbounds"]!!.jsonArray
        assertEquals(2, inbounds.size)
        inbounds.forEach { inbound ->
            val sniffing = inbound.jsonObject["sniffing"]!!.jsonObject
            assertEquals(true, (sniffing["enabled"] as JsonPrimitive).content.toBoolean())
            val dest = sniffing["destOverride"] as JsonArray
            assertEquals(
                listOf("http", "tls", "quic"),
                dest.map { (it as JsonPrimitive).content },
            )
        }
    }

    @Test
    fun `sniffer disabled - setting is honored without destination overrides`() {
        val root = Json.parseToJsonElement(
            XrayConfigBuilder.build(trojan, 10808, snifferEnabled = false),
        ).jsonObject
        val inbounds = root["inbounds"]!!.jsonArray
        inbounds.forEach { inbound ->
            val sniffing = inbound.jsonObject["sniffing"]!!.jsonObject
            assertEquals(false, (sniffing["enabled"] as JsonPrimitive).content.toBoolean())
            assertFalse(sniffing.containsKey("destOverride"))
        }
    }

    @Test
    fun `output JSON valid & bisa di-parse ulang untuk semua protokol`() {
        listOf(vmess, vless, trojan, ss).forEach { p ->
            val txt = XrayConfigBuilder.build(p, 10808)
            val root = Json.parseToJsonElement(txt).jsonObject // throw kalau rusak
            assertTrue(root.isNotEmpty())
        }
    }
}
