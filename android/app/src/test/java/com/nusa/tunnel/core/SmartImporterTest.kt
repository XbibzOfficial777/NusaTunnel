package com.nusa.tunnel.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Unit test SmartImporter (import pintar ala HTTP Custom).
 * Bagian fetch jaringan tidak di-test di sini (butuh server);
 * yang di-test adalah deteksi format lokal: link, base64, JSON.
 */
class SmartImporterTest {

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())

    @Test
    fun `import daftar link campur 3 protokol`() = runTest {
        val text = """
            trojan://pw1@h1.example.com:443?security=tls#Satu
            vmess://${b64("""{"ps":"Dua","add":"2.2.2.2","port":"80","id":"u2","net":"tcp"}""")}
            ss://${b64("aes-256-gcm:p")}@3.3.3.3:8388#Tiga
        """.trimIndent()
        val res = SmartImporter.importText(text)
        assertNull(res.error)
        assertEquals(3, res.servers.size)
        assertEquals("Satu", res.servers[0].remarks)
        assertEquals("Dua", res.servers[1].remarks)
        assertEquals("Tiga", res.servers[2].remarks)
        assertNull(res.subscriptionUrl)
    }

    @Test
    fun `import blob base64 berisi daftar link`() = runTest {
        val links = "trojan://pw@h.example.com:443?security=tls#B64\n" +
            "ss://${b64("aes-256-gcm:p")}@4.4.4.4:1234#SS-b64"
        val res = SmartImporter.importText(b64(links))
        assertNull(res.error)
        assertEquals(2, res.servers.size)
        assertEquals("B64", res.servers[0].remarks)
    }

    @Test
    fun `import config Xray JSON mentah`() = runTest {
        val cfg = """
        {"inbounds":[],"outbounds":[
          {"tag":"proxy","protocol":"vless","settings":{"vnext":[
            {"address":"vl.example.com","port":443,"users":[{"id":"u-vl","encryption":"none","flow":""}]}
          ]},"streamSettings":{"network":"tcp","security":"tls"}}
        ]}
        """.trimIndent()
        val res = SmartImporter.importText(cfg)
        assertNull(res.error)
        assertEquals(1, res.servers.size)
        assertEquals(Proto.VLESS, res.servers[0].proto)
    }

    @Test
    fun `teks sampah menghasilkan error jelas`() = runTest {
        val res = SmartImporter.importText("ini cuma teks biasa tanpa server")
        assertNotNull(res.error)
        assertTrue(res.servers.isEmpty())
    }

    @Test
    fun `teks kosong ditolak`() = runTest {
        val res = SmartImporter.importText("   ")
        assertNotNull(res.error)
    }

    @Test
    fun `deteksi URL http`() {
        assertTrue(SmartImporter.isHttpUrl("https://sub.example.com/api/v1/sub"))
        assertTrue(SmartImporter.isHttpUrl("http://1.2.3.4:8080/sub"))
        assertEquals(false, SmartImporter.isHttpUrl("vmess://abcdef"))
        assertEquals(false, SmartImporter.isHttpUrl("trojan://pw@h:443"))
    }
}
