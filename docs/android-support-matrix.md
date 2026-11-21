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
| Log retention | The current PR #2 branch replaces fixed choices with a persisted numeric cap from 100–10,000 and immediately trims the actual entry buffer (multiline entries count once). JVM range/trimming tests and KVM save/reset/cancel/invalid/recreation coverage are added. Run 44886470982 passed the non-emulator job but KVM test compilation failed on an unsupported `assertExists` matcher; the test now uses `assertIsDisplayed` and awaits rerun. |
| SOCKS5 port | Persists and validates ports 1–65,535; rejects invalid input before connecting and applies the saved port on the next connection. |
| Destination sniffing | Persists the switch and passes it into generated Xray config for active inbounds; changes apply on the next connection. This does not implement the full V2Ray routing-only/rules UI. |
| VPN DNS and MTU | Persists two numeric IPv4 DNS servers and MTU (1280–9000); Android `VpnService.Builder`, Xray TUN config, and Hev config consume the saved values on the next connection. Unit/config generation tests passed in merged CI; KVM run [44885399758](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44885399758) passed the DNS/MTU UI persistence and invalid-input instrumentation cases on x86_64 Android. This does not establish live DNS, VPN routing, or leak behavior. |
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
| General and navigation parity | The numeric log cap 100–10,000 is implemented on the current PR #2 branch, with its new remote checks pending. Language/theme/dynamic color, speed unit, and several other screenshot controls remain missing. The references show Home / Profiles / Settings / Guide while this app uses Beranda / Server / Atur / Log; Guide content is not supplied, so behavior must be agreed without silently removing Logs. |

These require their own real runtime/configuration support, tests and lifecycle handling. They are not implemented by Xray's VMess/VLESS/Trojan/Shadowsocks outbound builder. The supplied subscription also contains Hysteria2/`hy2` and SSR links, which remain outside the Android parser's four supported outbound protocols. The feed is live and can change; exact counts are not a stable contract.

## Verification status and limits

- Merged PR CI [44882508257](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44882508257) passed unit tests, live subscription smoke, Nusa release APK assembly/size reporting, and Xray-core config validation. It did **not** boot an Android emulator.
- The follow-up branch adds Compose instrumentation tests for DNS/MTU settings and log entry limit UI behavior. Run [44885399758](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44885399758) passed the original three DNS/MTU tests and uploaded the reports. The current PR head extends coverage to log-limit save/reset/cancel/rejection/recreation. Run 44886470982 failed at test compilation because the pinned Compose API has no `assertExists` matcher; the assertion was changed to `assertIsDisplayed`, pending rerun. The KVM job requires `/dev/kvm`, checks acceleration, boots x86_64 Android, runs `connectedDebugAndroidTest`, and uploads instrumentation reports.
- Instrumentation verifies UI/persistence, not live DNS resolution, VPN routing/leak behavior, battery policy, per-app routing, hotspot sharing, or physical-device system settings. Those need controlled network services and/or a real Android device as the parity matrix specifies.
