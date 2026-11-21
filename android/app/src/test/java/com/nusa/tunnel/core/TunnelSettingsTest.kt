package com.nusa.tunnel.core

import org.junit.Assert.assertEquals
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
    fun `Hev config uses selected SOCKS port UDP relay and log severity`() {
        val config = HevConfigBuilder.build(10999, EngineLogLevel.Verbose)
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
