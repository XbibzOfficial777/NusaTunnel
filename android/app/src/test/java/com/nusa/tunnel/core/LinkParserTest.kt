package com.nusa.tunnel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Unit test parser link — format link nyata yang beredar (v2rayN/v2rayNG).
 * Semua test jalan di JVM murni tanpa Android framework.
 */
class LinkParserTest {

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    // ---------------------------------------------------------- vmess

    @Test
    fun `parse vmess link standar v2rayN`() {
        val json = """
            {"v":"2","ps":"Server SG","add":"sg.example.com","port":"443",
             "id":"b831381d-6324-4d53-ad4f-8cda48b30811","aid":"0","scy":"auto",
             "net":"ws","type":"none","host":"cdn.example.com","path":"/nt","tls":"tls",
             "sni":"cdn.example.com","alpn":"h2,http/1.1","fp":"chrome"}
        """.trimIndent()
        val p = LinkParser.parse("vmess://" + b64(json))!!
        assertEquals(Proto.VMess, p.proto)
        assertEquals("Server SG", p.remarks)
        assertEquals("sg.example.com", p.host)
        assertEquals(443, p.port)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", p.secret)
        assertEquals("auto", p.encryption)
        assertEquals("ws", p.network)
        assertTrue(p.tls)
        assertEquals("cdn.example.com", p.sni)
        assertEquals("/nt", p.wsPath)
        assertEquals("chrome", p.fingerprint)
        assertEquals("h2,http/1.1", p.alpn)
    }

    @Test
    fun `parse vmess tanpa tls`() {
        val json = """{"ps":"N1","add":"1.2.3.4","port":"8080","id":"uuid-x","net":"tcp","scy":"auto"}"""
        val p = LinkParser.parse("vmess://" + b64(json))!!
        assertFalse(p.tls)
        assertEquals("tcp", p.network)
        assertEquals(8080, p.port)
    }

    // ---------------------------------------------------------- vless

    @Test
    fun `parse vless link dengan flow dan ws`() {
        val link = "vless://b831381d-6324-4d53-ad4f-8cda48b30811@vless.example.com:443" +
            "?encryption=none&security=tls&sni=vless.example.com&fp=chrome" +
            "&type=ws&path=%2Fws&host=cdn.example.com&flow=xtls-rprx-vision" +
            "#VLESS%20Premium"
        val p = LinkParser.parse(link)!!
        assertEquals(Proto.VLESS, p.proto)
        assertEquals("VLESS Premium", p.remarks)
        assertEquals("vless.example.com", p.host)
        assertEquals(443, p.port)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", p.secret)
        assertEquals("none", p.encryption)
        assertTrue(p.tls)
        assertEquals("ws", p.network)
        assertEquals("/ws", p.wsPath)
        assertEquals("cdn.example.com", p.wsHost)
        assertEquals("xtls-rprx-vision", p.flow)
        assertEquals("chrome", p.fingerprint)
    }

    @Test
    fun `parse vless grpc`() {
        val link = "vless://uuid-g@grpc.example.com:443?security=tls&type=grpc&serviceName=nt-svc#gRPC"
        val p = LinkParser.parse(link)!!
        assertEquals("grpc", p.network)
        assertEquals("nt-svc", p.grpcServiceName)
        assertTrue(p.tls)
    }

    @Test
    fun `parse vless reality keeps public key short id and spiderX`() {
        val link = "vless://reality-uuid@reality.example.com:443" +
            "?security=reality&sni=www.google.com&fp=chrome" +
            "&pbk=e2RLf57Li_-MDZGE9ss1BWPgP54mqRb5PfXhW2jcVVg" +
            "&sid=c39cc7310a&spx=%2F&type=tcp#VLESS-Reality"
        val p = LinkParser.parse(link)!!
        assertEquals(Proto.VLESS, p.proto)
        assertEquals("reality", p.streamSecurity)
        assertTrue(p.tls)
        assertEquals("www.google.com", p.sni)
        assertEquals("chrome", p.fingerprint)
        assertEquals("e2RLf57Li_-MDZGE9ss1BWPgP54mqRb5PfXhW2jcVVg", p.realityPublicKey)
        assertEquals("c39cc7310a", p.realityShortId)
        assertEquals("/", p.realitySpiderX)
    }

    @Test
    fun `parse vless xhttp reality preserves transport mode and extra`() {
        val extra = "%7B%22xPaddingBytes%22%3A%22100-1000%22%2C%22xmux%22%3A%7B%22maxConnections%22%3A1%7D%7D"
        val link = "vless://xhttp-uuid@xhttp.example.com:443" +
            "?security=reality&type=xhttp&mode=stream-up&host=front.example.com&path=%2Fedge" +
            "&pbk=e2RLf57Li_-MDZGE9ss1BWPgP54mqRb5PfXhW2jcVVg&sid=c39cc7310a&fp=chrome" +
            "&extra=$extra#VLESS-XHTTP"
        val p = LinkParser.parse(link)!!
        assertEquals("xhttp", p.network)
        assertEquals("stream-up", p.xhttpMode)
        assertEquals("/edge", p.wsPath)
        assertEquals("front.example.com", p.wsHost)
        assertTrue(p.xhttpExtra.contains("xPaddingBytes"))
        assertTrue(p.xhttpExtra.contains("xmux"))
    }

    @Test
    fun `parse vless httpupgrade transport`() {
        val link = "vless://upgrade-uuid@upgrade.example.com:443" +
            "?security=tls&type=httpupgrade&host=cdn.example.com&path=%2Fconnect#HTTPUpgrade"
        val p = LinkParser.parse(link)!!
        assertEquals("httpupgrade", p.network)
        assertEquals("cdn.example.com", p.wsHost)
        assertEquals("/connect", p.wsPath)
    }

    @Test
    fun `parse Xray JSON TLS preserves ALPN list`() {
        val config = """
            {"outbounds":[{"protocol":"vless",
              "settings":{"vnext":[{"address":"tls.example.com","port":443,
                "users":[{"id":"tls-uuid","encryption":"none"}]}]},
              "streamSettings":{"network":"tcp","security":"tls",
                "tlsSettings":{"serverName":"tls.example.com","alpn":["h2","http/1.1"]}}}]}
        """.trimIndent()
        val p = LinkParser.parseXrayConfig(config).single()
        assertEquals("h2,http/1.1", p.alpn)
        val stream = kotlinx.serialization.json.Json.parseToJsonElement(
            XrayConfigBuilder.build(p, 10808)
        ).jsonObject["outbounds"]!!.jsonArray[0].jsonObject
            ["streamSettings"]!!.jsonObject
        val alpn = stream["tlsSettings"]!!.jsonObject["alpn"]!!.jsonArray
        assertEquals(listOf("h2", "http/1.1"), alpn.map { it.jsonPrimitive.content })
    }

    @Test
    fun `parse Xray JSON VLESS REALITY outbound`() {
        val json = """
            {"outbounds":[{"tag":"reality","protocol":"vless",
              "settings":{"vnext":[{"address":"reality.example.com","port":443,
                "users":[{"id":"uuid","encryption":"none","flow":"xtls-rprx-vision"}]}]},
              "streamSettings":{"network":"tcp","security":"reality",
                "realitySettings":{"serverName":"www.google.com","fingerprint":"chrome",
                  "publicKey":"e2RLf57Li_-MDZGE9ss1BWPgP54mqRb5PfXhW2jcVVg",
                  "shortId":"c39cc7310a","spiderX":"/"}}}]}
        """.trimIndent()
        val p = LinkParser.parseXrayConfig(json).single()
        assertEquals("reality", p.streamSecurity)
        assertEquals("www.google.com", p.sni)
        assertEquals("chrome", p.fingerprint)
        assertEquals("e2RLf57Li_-MDZGE9ss1BWPgP54mqRb5PfXhW2jcVVg", p.realityPublicKey)
        assertEquals("c39cc7310a", p.realityShortId)
        assertEquals("/", p.realitySpiderX)
    }

    @Test
    fun `parse Xray JSON VLESS XHTTP REALITY preserves extra object`() {
        val config = """
            {"outbounds":[{"tag":"xhttp-json","protocol":"vless",
              "settings":{"vnext":[{"address":"json-xhttp.example.com","port":443,
                "users":[{"id":"xhttp-uuid","encryption":"none"}]}]},
              "streamSettings":{"network":"xhttp","security":"reality",
                "xhttpSettings":{"host":"front.example.com","path":"/json-edge","mode":"packet-up",
                  "extra":{"xPaddingBytes":"100-1000","xmux":{"maxConnections":1}}},
                "realitySettings":{"serverName":"www.google.com","fingerprint":"chrome",
                  "publicKey":"e2RLf57Li_-MDZGE9ss1BWPgP54mqRb5PfXhW2jcVVg",
                  "shortId":"c39cc7310a","spiderX":"/"}}}]}
        """.trimIndent()
        val p = LinkParser.parseXrayConfig(config).single()
        assertEquals("xhttp", p.network)
        assertEquals("/json-edge", p.wsPath)
        assertEquals("front.example.com", p.wsHost)
        assertEquals("packet-up", p.xhttpMode)
        assertTrue(p.xhttpExtra.contains("xPaddingBytes"))
        assertTrue(p.xhttpExtra.contains("maxConnections"))
        assertEquals("reality", p.streamSecurity)
        assertEquals("www.google.com", p.sni)
        assertEquals("e2RLf57Li_-MDZGE9ss1BWPgP54mqRb5PfXhW2jcVVg", p.realityPublicKey)
    }

    // ---------------------------------------------------------- trojan

    @Test
    fun `parse trojan link`() {
        val link = "trojan://p4ssw0rd!@trojan.example.com:4443?security=tls&sni=tr.example.com&type=tcp#Trojan-X"
        val p = LinkParser.parse(link)!!
        assertEquals(Proto.Trojan, p.proto)
        assertEquals("p4ssw0rd!", p.secret)
        assertEquals("trojan.example.com", p.host)
        assertEquals(4443, p.port)
        assertTrue(p.tls) // trojan selalu TLS
        assertEquals("tr.example.com", p.sni)
        assertEquals("Trojan-X", p.remarks)
    }

    @Test
    fun `parse trojan reality`() {
        val link = "trojan://secret@tr-reality.example.com:443" +
            "?security=reality&sni=www.microsoft.com&fp=chrome" +
            "&pbk=e2RLf57Li_-MDZGE9ss1BWPgP54mqRb5PfXhW2jcVVg&sid=601e43eb94a28919#Trojan-Reality"
        val p = LinkParser.parse(link)!!
        assertEquals(Proto.Trojan, p.proto)
        assertEquals("reality", p.streamSecurity)
        assertTrue(p.tls)
        assertEquals("www.microsoft.com", p.sni)
        assertEquals("e2RLf57Li_-MDZGE9ss1BWPgP54mqRb5PfXhW2jcVVg", p.realityPublicKey)
        assertEquals("601e43eb94a28919", p.realityShortId)
    }

    @Test
    fun `parse trojan XHTTP REALITY link preserves extra object`() {
        val extra = "%7B%22scMaxEachPostBytes%22%3A%22100000-200000%22%2C%22xmux%22%3A%7B%22maxConnections%22%3A1%7D%7D"
        val link = "trojan://xhttp-password@trojan-xhttp.example.com:443" +
            "?security=reality&type=xhttp&mode=stream-one&host=front.example.com&path=%2Ftrojan-edge" +
            "&pbk=e2RLf57Li_-MDZGE9ss1BWPgP54mqRb5PfXhW2jcVVg&sid=601e43eb94a28919" +
            "&sni=www.microsoft.com&fp=chrome&extra=$extra#Trojan-XHTTP"
        val p = LinkParser.parse(link)!!
        assertEquals(Proto.Trojan, p.proto)
        assertEquals("xhttp", p.network)
        assertEquals("stream-one", p.xhttpMode)
        assertEquals("/trojan-edge", p.wsPath)
        assertEquals("front.example.com", p.wsHost)
        assertTrue(p.xhttpExtra.contains("scMaxEachPostBytes"))
        assertTrue(p.xhttpExtra.contains("xmux"))
        assertEquals("reality", p.streamSecurity)
        assertEquals("www.microsoft.com", p.sni)
    }

    @Test
    fun `parse trojan dengan karakter spesial di password`() {
        val link = "trojan://p%40ss%3Aword@t.example.com:443#T"
        val p = LinkParser.parse(link)!!
        assertEquals("p@ss:word", p.secret)
    }

    // ---------------------------------------------------------- shadowsocks

    @Test
    fun `parse ss link SIP002 base64 userinfo`() {
        val userInfo = b64("aes-256-gcm:s3cret-pass")
        val link = "ss://$userInfo@ss.example.com:8388#SS-JP"
        val p = LinkParser.parse(link)!!
        assertEquals(Proto.Shadowsocks, p.proto)
        assertEquals("aes-256-gcm", p.encryption)
        assertEquals("s3cret-pass", p.secret)
        assertEquals("ss.example.com", p.host)
        assertEquals(8388, p.port)
        assertEquals("SS-JP", p.remarks)
    }

    @Test
    fun `parse ss link bentuk lama full base64`() {
        val full = b64("aes-128-gcm:pass123@old.example.com:1234")
        val p = LinkParser.parse("ss://$full#Old")!!
        assertEquals(Proto.Shadowsocks, p.proto)
        assertEquals("old.example.com", p.host)
        assertEquals(1234, p.port)
        assertEquals("aes-128-gcm", p.encryption)
        assertEquals("pass123", p.secret)
    }

    // ---------------------------------------------------------- many & invalid

    @Test
    fun `parseMany mengabaikan baris non-link`() {
        val text = """
            komentar biasa
            trojan://pw@h.com:443#Satu
            vmess://${b64("""{"ps":"Dua","add":"2.2.2.2","port":"80","id":"u2","net":"tcp"}""")}
        """.trimIndent()
        val list = LinkParser.parseMany(text)
        assertEquals(2, list.size)
        assertEquals("Satu", list[0].remarks)
        assertEquals("Dua", list[1].remarks)
    }

    @Test
    fun `link rusak menghasilkan null`() {
        assertNull(LinkParser.parse("vmess://bukan-base64-valid"))
        assertNull(LinkParser.parse("https://bukan-link-vpn.com"))
        assertNull(LinkParser.parse("trojan://tanpa-host"))
        assertNull(LinkParser.parse("vless://uuid@host:bukan-angka"))
    }

    // ---------------------------------------------------------- config Xray JSON

    @Test
    fun `parseXrayConfig mengekstrak outbound trojan format baru (password di server)`() {
        val cfg = """
        {
          "outbounds": [
            {"tag":"direct","protocol":"freedom"},
            {"tag":"proxy","protocol":"trojan","settings":{"servers":[
               {"address":"xbibz.example.com","port":4443,"password":"tr-pw","level":8}
            ]},"streamSettings":{"network":"tcp","security":"tls",
               "tlsSettings":{"serverName":"xb.example.com","allowInsecure":false}}}
          ]
        }
        """.trimIndent()
        val list = LinkParser.parseXrayConfig(cfg)
        assertEquals(1, list.size)
        val p = list[0]
        assertEquals(Proto.Trojan, p.proto)
        assertEquals("xbibz.example.com", p.host)
        assertEquals(4443, p.port)
        assertEquals("tr-pw", p.secret)
        assertTrue(p.tls)
        assertEquals("xb.example.com", p.sni)
    }

    @Test
    fun `parseXrayConfig mengekstrak outbound vmess`() {
        val cfg = """
        {"outbounds":[{"tag":"sg","protocol":"vmess","settings":{"vnext":[
           {"address":"vm.example.com","port":443,"users":[
             {"id":"b831381d-6324-4d53-ad4f-8cda48b30811","security":"auto","alterId":0}
           ]}
        ]},"streamSettings":{"network":"ws","security":"tls","wsSettings":{"path":"/w","host":"h.com"}}}]}
        """.trimIndent()
        val p = LinkParser.parseXrayConfig(cfg).single()
        assertEquals(Proto.VMess, p.proto)
        assertEquals("vm.example.com", p.host)
        assertEquals("b831381d-6324-4d53-ad4f-8cda48b30811", p.secret)
        assertEquals("auto", p.encryption)
        assertEquals("ws", p.network)
        assertEquals("/w", p.wsPath)
    }
}
