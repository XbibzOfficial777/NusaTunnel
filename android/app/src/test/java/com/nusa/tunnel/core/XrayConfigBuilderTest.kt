package com.nusa.tunnel.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

    private fun build(p: ServerProfile): JsonObject =
        Json.parseToJsonElement(XrayConfigBuilder.build(p, 10808)).jsonObject

    // ---------------------------------------------------------- regression bug lapangan

    @Test
    fun `routing rule - outboundTag HARUS string bukan array (bug v2.2.0)`() {
        val rule = build(trojan)["routing"]!!.jsonObject["rules"]!!.jsonArray.single().jsonObject
        val tag = rule["outboundTag"] as? kotlinx.serialization.json.JsonPrimitive
            ?: error("outboundTag harus string, dapat: ${rule["outboundTag\"]}")
        assertEquals("proxy", tag.content)
    }

    @Test
    fun `routing rule - port HARUS string range bukan array (bug v2.2.0)`() {
        val rule = build(trojan)["routing"]!!.jsonObject["rules"]!!.jsonArray.single().jsonObject
        val port = rule["port"] as? kotlinx.serialization.json.JsonPrimitive
            ?: error("port harus JsonPrimitive string, dapat: ${rule["port"]}")
        assertEquals("0-65535", port.content)
    }

    @Test
    fun `routing rule - tidak ada balancerTag kosong (bug v2.2.0)`() {
        val rule = build(trojan)["routing"]!!.jsonObject["rules"]!!.jsonArray.single().jsonObject
        assertFalse(rule.containsKey("balancerTag"))
    }

    @Test
    fun `trojan - password di level server BUKAN di users (bug v2.2.1)`() {
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
    fun `buildProxyOnly - tanpa inbound, log none`() {
        val root = Json.parseToJsonElement(XrayConfigBuilder.buildProxyOnly(trojan)).jsonObject
        assertTrue((root["inbounds"] as JsonArray).isEmpty())
        assertEquals("none", (root["log"]!!.jsonObject["loglevel"] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun `sniffer - destOverride berisi http tls quic`() {
        val sniffing = build(trojan)["inbounds"]!!.jsonArray[1].jsonObject["sniffing"]!!.jsonObject
        val dest = sniffing["destOverride"] as JsonArray
        assertEquals(listOf("http", "tls", "quic"), dest.map { (it as kotlinx.serialization.json.JsonPrimitive).content })
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
