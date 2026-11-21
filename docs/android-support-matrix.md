# Android support matrix (audit)

Audit date: 2026-11-21. This matrix describes the Android implementation, not the desktop client.

## Implemented in this change

| Area | Status |
| --- | --- |
| VMess, VLESS, Trojan, Shadowsocks | Parsed and built with the pinned Xray-core engine; the supplied live subscription smoke test checks all four. |
| VLESS/Trojan REALITY | REALITY keys, short IDs, SNI, fingerprint, and spiderX are retained. Invalid/missing public keys or malformed short IDs are rejected at import. |
| XHTTP and HTTPUpgrade | URL path/host/mode/`extra` and HTTPUpgrade path/host are mapped into Xray stream settings. XHTTP/REALITY and HTTPUpgrade configs are validated against the same Xray-core release as the Android AAR. |
| UDP packet engine | Persistent manual selector. HevSocks5Tunnel is the default on Android 10+; Xray native TUN is an alternate. Older Android versions fall back to Xray native TUN because the pinned Hev AAR declares API 29. |
| Engine logs | Warning, Verbose (Xray `debug`), and Error levels; takes effect on the next connection. |
| Log retention | Persists a 100 / 400 / 1,000-entry in-memory cap; changing it trims the current log immediately. |
| SOCKS5 port | Persists and validates ports 1–65535; rejects invalid input before connecting and applies the saved port on the next connection. |
| Destination sniffing | Persists the switch and passes it into the generated Xray config for active inbounds; changes apply on the next connection. |
| Auto-ping | Opt-in: pings the selected server every five minutes only while the app is foregrounded and the VPN is disconnected. Off by default to avoid extra idle traffic. |

Hev mode uses the Android VPN TUN fd in HevSocks5Tunnel and keeps Xray as a local SOCKS5 proxy. Its native worker is health-checked and stopped before Xray during teardown. The Xray-native mode remains available as a fallback.

## Not implemented in the Android app

| Requested protocol | Status / reason |
| --- | --- |
| SSH | No SSH profile parser or SSH forwarding engine is present. |
| SlowDNS | No DNS-tunnel implementation is present. |
| OpenVPN | No OpenVPN core or `.ovpn` profile importer is present. |
| Psiphon | No Psiphon SDK/core integration is present. |

These require distinct protocol engines and lifecycle/configuration support; they are not implemented by Xray's VMess/VLESS/Trojan/Shadowsocks outbound builder. The UI does not advertise them as supported.

The supplied subscription also currently contains Hysteria2/`hy2` and SSR links, which remain outside the Android parser's four supported outbound protocols. The feed is live and can change; the exact counts are recorded in the test/session results, not treated as a stable contract.

## Verification boundaries

The CI workflow runs Kotlin unit tests, fetches and parses the supplied subscription URL, builds the Nusa release APK without publishing it, and validates generated Xray JSON with Xray-core `run -test`. Those checks do not verify real-device VPN lifecycle, battery behavior, or per-app traffic; those require device-side validation.
