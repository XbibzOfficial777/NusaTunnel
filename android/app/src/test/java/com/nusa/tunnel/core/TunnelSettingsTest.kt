package com.nusa.tunnel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelSettingsTest {
    @Test
    fun `UDP engine defaults to Hev and invalid preference falls back to Hev`() {
        assertEquals(UdpTunnelEngine.HevSocks5Tunnel, UdpTunnelEngine.fromPreference(null))
        assertEquals(UdpTunnelEngine.HevSocks5Tunnel, UdpTunnelEngine.fromPreference("unknown"))
        assertEquals(UdpTunnelEngine.XrayNative, UdpTunnelEngine.fromPreference("xray_native"))
    }

    @Test
    fun `log level defaults to warning and maps app labels to both engines`() {
        assertEquals(EngineLogLevel.Warning, EngineLogLevel.fromPreference(null))
        assertEquals("debug", EngineLogLevel.Verbose.xrayValue)
        assertEquals("debug", EngineLogLevel.Verbose.hevValue)
        assertEquals("error", EngineLogLevel.Error.xrayValue)
        assertEquals("error", EngineLogLevel.Error.hevValue)
    }

    @Test
    fun `SOCKS port parser accepts only the valid TCP port range`() {
        assertEquals(1, TunnelSettings.parseSocksPort("1"))
        assertEquals(10808, TunnelSettings.parseSocksPort(" 10808 "))
        assertEquals(65_535, TunnelSettings.parseSocksPort("65535"))
        listOf("", "0", "65536", "-1", "abc", "1.5").forEach {
            assertNull("invalid port should be rejected: $it", TunnelSettings.parseSocksPort(it))
        }
    }

    @Test
    fun `log retention choices use safe defaults for unknown stored values`() {
        assertEquals(LogRetention.Standard, LogRetention.fromPreference(400))
        assertEquals(LogRetention.Standard, LogRetention.fromPreference(999))
        assertEquals(100, LogRetention.Compact.preferenceValue)
        assertEquals(1_000, LogRetention.Extended.preferenceValue)
    }

    @Test
    fun `VPN behavior validation accepts numeric IPv4 DNS and safe MTU range only`() {
        assertTrue(TunnelSettings.isValidIpv4Address("8.8.8.8"))
        assertTrue(TunnelSettings.isValidIpv4Address("0.0.0.0"))
        listOf("", "8.8.8", "256.1.1.1", "1.2.3.999", "01.2.3.4", "dns.google").forEach {
            assertEquals("invalid DNS address: $it", false, TunnelSettings.isValidIpv4Address(it))
        }
        assertEquals(1280, TunnelSettings.parseVpnMtu("1280"))
        assertEquals(1500, TunnelSettings.parseVpnMtu("1500"))
        assertEquals(9000, TunnelSettings.parseVpnMtu("9000"))
        listOf("1279", "9001", "", "abc").forEach {
            assertNull("invalid MTU: $it", TunnelSettings.parseVpnMtu(it))
        }
    }

    @Test
    fun `Hev config uses selected SOCKS port UDP relay log severity and MTU`() {
        val config = HevConfigBuilder.build(10999, EngineLogLevel.Verbose, mtu = 1420)
        assertTrue(config.contains("mtu: 1420"))
        assertTrue(config.contains("address: 127.0.0.1"))
        assertTrue(config.contains("port: 10999"))
        assertTrue(config.contains("udp: 'udp'"))
        assertTrue(config.contains("log-level: 'debug'"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Hev config rejects invalid port`() {
        HevConfigBuilder.build(0)
    }
}
