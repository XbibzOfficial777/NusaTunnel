package com.nusa.tunnel.core

/** Builds the HevSocks5Tunnel YAML used with the Android VpnService TUN file descriptor. */
object HevConfigBuilder {
    fun build(socksPort: Int, logLevel: EngineLogLevel = EngineLogLevel.Warning): String {
        require(socksPort in 1..65535) { "SOCKS5 port must be 1..65535" }
        return """
            tunnel:
              mtu: 1500
              ipv4: 10.10.14.1
              ipv6: 'fc00::10:10:14:1'
              icmp: 'off'
            socks5:
              address: 127.0.0.1
              port: $socksPort
              udp: 'udp'
            misc:
              log-file: stderr
              log-level: '${logLevel.hevValue}'
        """.trimIndent() + "\n"
    }
}
