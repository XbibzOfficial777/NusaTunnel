package com.nusa.tunnel.core

import kotlinx.serialization.json.*

/**
 * Builder config Xray-core (JSON) untuk satu profil server.
 * Struktur mengikuti pola yang sama dengan template v2rayNG
 * (inbound tun + socks, outbound proxy + freedom + dns, stats on).
 */
object XrayConfigBuilder {

    private val json = Json { encodeDefaults = false }

    fun build(p: ServerProfile, socksPort: Int, snifferEnabled: Boolean = true): String {
        val sniffing = buildJsonObject {
            put("enabled", snifferEnabled)
            if (snifferEnabled) putJsonArray("destOverride") {
                add("http"); add("tls"); add("quic")
            }
        }

        val inbounds = buildJsonArray {
            add(buildJsonObject {
                put("tag", "tun")
                put("protocol", "tun")
                put("settings", buildJsonObject {
                    put("name", "nusatun0")
                    put("MTU", 1500)
                    put("userLevel", 8)
                })
                put("sniffing", sniffing)
            })
            add(buildJsonObject {
                put("tag", "socks")
                put("port", socksPort)
                put("listen", "127.0.0.1")
                put("protocol", "socks")
                put("settings", buildJsonObject {
                    put("auth", "noauth")
                    put("udp", true)
                    put("userLevel", 8)
                })
                put("sniffing", sniffing)
            })
        }

        val outbound = when (p.proto) {
            Proto.VMess -> vmessOutbound(p)
            Proto.VLESS -> vlessOutbound(p)
            Proto.Trojan -> trojanOutbound(p)
            Proto.Shadowsocks -> ssOutbound(p)
        }

        val outbounds = buildJsonArray {
            add(outbound) // tag "proxy"
            add(buildJsonObject {
                put("tag", "direct")
                put("protocol", "freedom")
            })
            add(buildJsonObject {
                put("tag", "block")
                put("protocol", "blackhole")
            })
        }

        val config = buildJsonObject {
            put("stats", buildJsonObject { })
            put("log", buildJsonObject { put("loglevel", "warning") })
            put("policy", buildJsonObject {
                put("levels", buildJsonObject {
                    put("8", buildJsonObject {
                        put("handshake", 4)
                        put("connIdle", 300)
                        put("uplinkOnly", 1)
                        put("downlinkOnly", 1)
                    })
                })
                put("system", buildJsonObject {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                })
            })
            put("inbounds", inbounds)
            put("outbounds", outbounds)
            put("dns", buildJsonObject {
                putJsonArray("servers") {
                    add("1.1.1.1"); add("8.8.8.8"); add("localhost")
                }
            })
            put("routing", buildJsonObject {
                put("domainStrategy", "IPIfNonMatch")
                putJsonArray("rules") {
                    add(buildJsonObject {
                        put("type", "field")
                        // CATATAN: outboundTag HARUS string (bukan array) —
                        // RouterRule.outboundTag di Xray-core bertipe string.
                        // "port" juga HARUS string ("0-65535"), bukan array —
                        // Xray-core >= v25 menolak array di field port.
                        put("outboundTag", "proxy")
                        put("port", "0-65535")
                    })
                    // DNS di dalam tunnel dilempar ke freedom (langsung via proxy routing)
                }
            })
            put("remarks", p.remarks)
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    /** Config tanpa inbound (untuk pengukuran latency nyata via measureOutboundDelay). */
    fun buildProxyOnly(p: ServerProfile): String {
        val outbound = when (p.proto) {
            Proto.VMess -> vmessOutbound(p)
            Proto.VLESS -> vlessOutbound(p)
            Proto.Trojan -> trojanOutbound(p)
            Proto.Shadowsocks -> ssOutbound(p)
        }
        val config = buildJsonObject {
            put("log", buildJsonObject { put("loglevel", "none") })
            put("inbounds", buildJsonArray { })
            put("outbounds", buildJsonArray {
                add(outbound)
                add(buildJsonObject { put("tag", "direct"); put("protocol", "freedom") })
            })
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    // ---------------------------------------------------------- outbounds

    private fun vmessOutbound(p: ServerProfile) = baseOutbound(p, "vmess", buildJsonObject {
        putJsonArray("vnext") {
            add(buildJsonObject {
                put("address", p.host)
                put("port", p.port)
                putJsonArray("users") {
                    add(buildJsonObject {
                        put("id", p.secret)
                        put("security", p.encryption.ifBlank { "auto" })
                        put("alterId", 0)
                        put("level", 8)
                    })
                }
            })
        }
    })

    private fun vlessOutbound(p: ServerProfile) = baseOutbound(p, "vless", buildJsonObject {
        putJsonArray("vnext") {
            add(buildJsonObject {
                put("address", p.host)
                put("port", p.port)
                putJsonArray("users") {
                    add(buildJsonObject {
                        put("id", p.secret)
                        put("encryption", "none")
                        put("flow", p.flow)
                        put("level", 8)
                    })
                }
            })
        }
    })

    private fun trojanOutbound(p: ServerProfile) = baseOutbound(p, "trojan", buildJsonObject {
        putJsonArray("servers") {
            add(buildJsonObject {
                put("address", p.host)
                put("port", p.port)
                // Xray-core >= v26: password trojan ada di level server,
                // BUKAN di users[]. Format lama ditolak: "Trojan password is not specified."
                put("password", p.secret)
                put("level", 8)
            })
        }
    })

    private fun ssOutbound(p: ServerProfile) = baseOutbound(p, "shadowsocks", buildJsonObject {
        putJsonArray("servers") {
            add(buildJsonObject {
                put("address", p.host)
                put("port", p.port)
                put("method", p.encryption.ifBlank { "aes-256-gcm" })
                put("password", p.secret)
                put("uot", true)
                put("level", 8)
            })
        }
    })

    private fun baseOutbound(p: ServerProfile, protocol: String, settings: JsonObject): JsonObject =
        buildJsonObject {
            put("tag", "proxy")
            put("protocol", protocol)
            put("settings", settings)
            put("streamSettings", streamSettings(p))
            put("mux", buildJsonObject {
                put("enabled", false)
                put("concurrency", -1)
            })
        }

    private fun streamSettings(p: ServerProfile): JsonObject = buildJsonObject {
        put("network", p.network.ifBlank { "tcp" })
        when (p.network.lowercase()) {
            "ws" -> put("wsSettings", buildJsonObject {
                put("path", p.wsPath.ifBlank { "/" })
                if (p.wsHost.isNotBlank()) put("host", p.wsHost)
            })
            "grpc" -> put("grpcSettings", buildJsonObject {
                put("serviceName", p.grpcServiceName.ifBlank { p.serviceName })
                put("multiMode", false)
            })
        }
        put("security", if (p.tls) "tls" else "none")
        if (p.tls) {
            put("tlsSettings", buildJsonObject {
                put("serverName", p.sni.ifBlank { p.host })
                put("allowInsecure", p.allowInsecure)
                if (p.alpn.isNotBlank()) putJsonArray("alpn") {
                    p.alpn.split(",").forEach { add(it.trim()) }
                }
                if (p.fingerprint.isNotBlank()) put("fingerprint", p.fingerprint)
            })
        }
    }
}
