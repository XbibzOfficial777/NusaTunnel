# Android support matrix (audit)

Audit date: 2026-11-21. This matrix describes the Android app only. The full screenshot-by-screenshot HTTP Custom parity scope is tracked in [`http-custom-reference-matrix.md`](http-custom-reference-matrix.md); planned controls are not supported features.

## Implemented in merged Phase 1 (`main` at `dac9fbc`)

| Area | Actual Android behavior |
| --- | --- |
| VMess, VLESS, Trojan, Shadowsocks | Parsed and built with the pinned Xray-core engine; the supplied live subscription smoke test checks all four. |
| VLESS/Trojan REALITY | REALITY keys, short IDs, SNI, fingerprint, and spiderX are retained. Invalid/missing public keys or malformed short IDs are rejected at import. |
| XHTTP and HTTPUpgrade | URL path/host/mode/`extra` and HTTPUpgrade path/host are mapped into Xray stream settings. XHTTP/REALITY and HTTPUpgrade configs are validated against the same Xray-core release as the Android AAR. |
| UDP packet engine | Persistent selector between HevSocks5Tunnel (default on Android 10+) and Xray native TUN. Older Android versions fall back to Xray native TUN because the pinned Hev AAR declares API 29. This is **not** the HTTP Custom BadVPN/UDPGW selector. |
| Engine logs | Warning, Verbose (Xray `debug`), and Error levels; takes effect on the next connection. |
| Log retention | Persists a 100 / 400 / 1,000-entry in-memory cap; changing it trims the current log immediately. This is not the full reference numeric 100–10,000 editor. |
| SOCKS5 port | Persists and validates ports 1–65,535; rejects invalid input before connecting and applies the saved port on the next connection. |
| Destination sniffing | Persists the switch and passes it into generated Xray config for active inbounds; changes apply on the next connection. This does not implement the full V2Ray routing-only/rules UI. |
| VPN DNS and MTU | Persists two numeric IPv4 DNS servers and MTU (1280–9000); Android `VpnService.Builder`, Xray TUN config, and Hev config consume the saved values on the next connection. Unit/config generation tests passed in merged CI. KVM attempts 44883875004, 44884293254 and 44884773232 uncovered SDK bootstrap issues. Run 44885075397 installed API 37.1, verified `/dev/kvm`/emulator acceleration, and booted the x86_64 Android device, but Gradle stopped before instrumentation because Maven Central dependencies `error_prone_annotations:2.30.0` and `hamcrest-library:1.3` were unavailable to that runner. `android/settings.gradle.kts` now places Google's official Maven Central mirror before Maven Central. KVM boot is verified; app instrumentation remains pending a successful dependency-resolution/test run. |
| IPv6 route | IPv6 remains routed through the VPN. There is deliberately no off switch yet because that could leak IPv6 outside the tunnel until a real packet-blocking path is validated. |
| Auto Ping | Opt-in: pings the selected server every five minutes only while the app is foregrounded and the VPN is disconnected. It does not provide the reference's Keep-alive/Reset/Aggressive modes or advanced controls. |

Hev mode uses the Android VPN TUN fd in HevSocks5Tunnel and keeps Xray as a local SOCKS5 proxy. Its native worker is health-checked and stopped before Xray during teardown. The Xray-native mode remains available as a fallback. Neither choice is represented as BadVPN.

## Not implemented / not to expose as active controls yet

| Requested area | Status |
| --- | --- |
| SSH, payload proxy methods, HCR | No SSH profile parser/forwarding engine or HCR exchange exists. |
| SlowDNS | No DNS-tunnel implementation exists. |
| OpenVPN | No OpenVPN core or `.ovpn` profile importer exists. |
| Psiphon | No Psiphon SDK/core integration exists. |
| BadVPN / UDPGW | No BadVPN engine or configurable UDPGW endpoint/port exists. |
| Advanced connection behavior | Per-app VPN selection, connection-check scheduling, Auto Ping modes/recovery, richer reconnect properties, Share Net/local proxy, and associated controls are not implemented. |
| Advanced V2Ray/Xray settings | Complete profile GUI/JSON editor, configurable routing rules/presets, custom DNS hosts, asset manager, and other screenshot options are not implemented as a complete feature set. |
| UDP Custom tuning | Upload/download rate, receive window, idle timeout and Path MTU discovery controls are not implemented. |
| Response checker | HTTP/SNI/UDP/DNS checks, manual proxy selection and custom headers/body are not implemented. |
| Hardware ID / Access ID authorization | The UI/service contract is absent. The reference's “Get ID” requires an approved authorization endpoint and response/privacy contract; no fake success path is acceptable. |
| General and navigation parity | Language/theme/dynamic color, numeric log cap 100–10,000, speed unit, and several other screenshot controls are missing. The references show Home / Profiles / Settings / Guide while this app uses Beranda / Server / Atur / Log; Guide content is not supplied, so behavior must be agreed without silently removing Logs. |

These require their own real runtime/configuration support, tests and lifecycle handling. They are not implemented by Xray's VMess/VLESS/Trojan/Shadowsocks outbound builder. The supplied subscription also contains Hysteria2/`hy2` and SSR links, which remain outside the Android parser's four supported outbound protocols. The feed is live and can change; exact counts are not a stable contract.

## Verification status and limits

- Merged PR CI [44882508257](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44882508257) passed unit tests, live subscription smoke, Nusa release APK assembly/size reporting, and Xray-core config validation. It did **not** boot an Android emulator.
- The follow-up branch adds a Compose instrumentation test for navigating to VPN Behavior, saving DNS/MTU through the actual UI into Android preferences, and rejecting invalid DNS without altering saved values. A GitHub Actions job requires `/dev/kvm`, checks emulator acceleration, boots an x86_64 Android virtual device, runs `connectedDebugAndroidTest`, and uploads instrumentation reports. The first remote result is pending; do not report this test as passing until Actions confirms it.
- Instrumentation verifies UI/persistence, not live DNS resolution, VPN routing/leak behavior, battery policy, per-app routing, hotspot sharing, or physical-device system settings. Those need controlled network services and/or a real Android device as the parity matrix specifies.
