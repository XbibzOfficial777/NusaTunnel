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
    val network: String = "tcp",           // tcp | ws | grpc
    val tls: Boolean = false,
    val sni: String = "",
    val alpn: String = "",
    val fingerprint: String = "",          // utls: chrome / firefox / ios dst
    val flow: String = "",                 // vless: xtls-rprx-vision dst
    val wsPath: String = "/",
    val wsHost: String = "",
    val grpcServiceName: String = "",
    val serviceName: String = "",
    val allowInsecure: Boolean = false,
    /* penanda asal: "" = import manual, atau URL subscription sumber server ini */
    val subTag: String = "",
    /* hasil ping nyata (ms), -1 = belum / gagal */
    var latencyMs: Int = -1,
)
