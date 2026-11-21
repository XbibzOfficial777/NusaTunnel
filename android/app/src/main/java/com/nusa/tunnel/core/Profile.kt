package com.nusa.tunnel.core

import kotlinx.serialization.Serializable

/** Protokol yang didukung engine Xray-core. */
enum class Proto { VMess, VLESS, Trojan, Shadowsocks }

/**
 * Profil server hasil import link `vmess://` `vless://` `trojan://` `ss://`.
 * Semua field dipetakan 1:1 ke config Xray-core.
 */
@Serializable
data class ServerProfile(
    val id: String,
    val proto: Proto,
    val remarks: String,
    val host: String,
    val port: Int,
    /* vmess: uuid; vless: uuid; trojan: password; ss: password */
    val secret: String,
    /* vmess: auto/none; ss: cipher (aes-256-gcm dst) */
    val encryption: String = "",
    val network: String = "tcp",           // tcp | ws | grpc | httpupgrade | xhttp/splithttp
    val tls: Boolean = false,
    /** none | tls | reality; blank preserves legacy profiles and derives from tls. */
    val streamSecurity: String = "",
    val realityPublicKey: String = "",     // REALITY pbk
    val realityShortId: String = "",       // REALITY sid
    val realitySpiderX: String = "",        // REALITY spx
    val sni: String = "",
    val alpn: String = "",
    val fingerprint: String = "",          // uTLS: chrome / firefox / ios dst
    val flow: String = "",                 // vless: xtls-rprx-vision dst
    /** Shared path/host fields used by WS, HTTPUpgrade, and XHTTP transports. */
    val wsPath: String = "/",
    val wsHost: String = "",
    val xhttpMode: String = "",
    /** Raw JSON object from VLESS/Trojan URL extra=; empty when absent/invalid. */
    val xhttpExtra: String = "",
    val grpcServiceName: String = "",
    val serviceName: String = "",
    val allowInsecure: Boolean = false,
    /* penanda asal: "" = import manual, atau URL subscription sumber server ini */
    val subTag: String = "",
    /* hasil ping nyata (ms), -1 = belum / gagal */
    var latencyMs: Int = -1,
)
