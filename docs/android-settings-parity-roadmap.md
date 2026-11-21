# Android HTTP Custom parity roadmap

**Baseline:** `main` at `dac9fbcf665aea1e091a959b3216486a1be3f35a` (Phase 1 merged through PR [#1](https://github.com/XbibzOfficial777/NusaTunnel/pull/1), 2026-11-21).

**Reference:** all 38 supplied screenshots under `ISI SETTINGS` and `BACA.TXT`. The item-by-item source/feature/runtime/test mapping is [`http-custom-reference-matrix.md`](http-custom-reference-matrix.md); it is the authoritative omission check.

**Prior verification:** merged PR CI [44882508257](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44882508257) passed its unit tests, live subscription smoke test, release APK assembly/size report, and Xray-core config validation. That run did **not** boot Android.

**Current follow-up:** branch `settings-parity-kvm-emulator` adds a KVM-gated x86_64 Android emulator job. Runs [44883875004](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44883875004), [44884293254](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44884293254), and [44884773232](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44884773232) successively exposed and fixed SDK setup issues. Run [44885075397](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44885075397) booted the emulator but failed resolving two Maven artifacts; Google's official Maven Central mirror fixed that. Run [44885399758](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44885399758) passed the existing JVM/config/release checks and three DNS/MTU instrumentation tests. Run [44886470982](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44886470982) caught an unsupported Compose `assertExists` test matcher; it was replaced with `assertIsDisplayed`. Latest run [44887021779](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44887021779) passed both jobs: JVM/config/live-subscription/release checks and four KVM instrumentation tests (DNS/MTU persistence/validation plus log-limit save/reset/cancel/rejection/activity recreation). Unit tests also exercise 100–10,000 bounds and actual entry-based buffer trimming. No live VPN routing or leak behavior is established by these settings tests. Full HTTP Custom parity is **not implemented**; no release is authorized by this roadmap.

## Non-negotiable acceptance rule — no simulation

For every setting/control that is exposed as available:

1. Its value is owned by a typed setting/profile model, validated at UI and persistence/runtime boundaries, persisted, and migrated safely.
2. The Android service or the actual selected protocol engine consumes it; a UI state change or saved preference alone is not a functional feature.
3. The user can observe the defined behavior and the documented apply time (immediately, next connection, reconnect, or OS settings).
4. Tests cover defaults, min/max and invalid input, persistence/recreation, config generation and appropriate runtime behavior. CI validates generated configs with the same engine version where possible.
5. Network/protocol controls require controlled local test services and traffic assertions; emulator navigation alone is not proof of operation. Unsupported controls are omitted or explicitly marked unavailable until their engine/path exists.
6. VPN lifecycle, routing, leakage, hotspot, battery and OS settings behaviors are not claimed verified from UI tests. Record what still needs a physical-device check.

Never build or run app tests in the local sandbox. Use remote GitHub Actions only. Do not publish/tag a release until all required remote checks pass. Do not claim bug-free behavior without adequate remote and device coverage.

## Reference coverage summary

The 38-row matrix includes every image, including screenshots of profile editors, logs, a device-authorization dialog and a response checker—not just the Settings screen. The main feature groups are:

- **General:** language; dynamic color; theme palette/mode; log verbosity; numeric log-entry cap 100–10,000; Byte/s versus bit/s.
- **Connection:** client/remote payload buffers; load balancing; BadVPN/Hev SOCKS engine and UDPGW port; DNS/MTU/IPv6; per-app VPN; reconnect/keep-awake/battery/Always-on; connection-check URL/timing/retries/stop-on-failure; Auto Ping modes and advanced reset policy; Share Net reach method/HTTP proxy/port/auth/status; connection timeout/reconnect limit.
- **SSH, Payload and HCR:** Standard/HCR modes; Payload/Enhanced/TLS/SlowDNS methods; SSH host/account/password; payload and remote proxy; SNI/version/TLS verification; keep-alive, proxy handling, TCP_NODELAY and compression; HCR concurrency, batch/chunk/frame, adaptation and real calibration.
- **SlowDNS:** profile-level resolver/type/nameserver/public key and global multi-login/session/KCP/QNAME/EDNS0/calibration behavior.
- **V2Ray/Xray:** GUI and JSON profiles; transport/TLS/SNI/fingerprint/ALPN/ECH/pinned cert/Mux; SOCKS UDP/proxy/sniffing/routing-only; DNS/host presets/custom hosts; routing rules/presets; asset files; fragment/noise only if supported by the pinned engine.
- **Other engines and tools:** Psiphon/OpenVPN profile/proxy behavior; UDP Custom rate/window/idle/PMTU settings; runtime logs; Hardware ID/Access ID authorization; HTTP/SNI/UDP/DNS response checker with proxy and custom headers/body.

See the matrix for exact screenshot filenames, each distinct visible item, current support boundaries, owner, and acceptance evidence. The duplicate-looking HCR state and repeated SSH/V2Ray editor/navigation screenshots remain separate rows so no reference image silently disappears.

## Phased implementation

### Phase 0 — screenshot inventory and safeguards

- **Recorded:** the 38 screenshots have been individually checked and mapped row-by-row in `http-custom-reference-matrix.md`.
- Keep that matrix and `android-support-matrix.md` accurate as implementation changes. Every row must eventually name the runtime consumer and passing tests/device checks; “planned” is never reported as “supported.”
- Keep the KVM check fail-closed: if `/dev/kvm` is unavailable, fail CI instead of silently using a non-accelerated emulator.

### Phase 1 — settings architecture and existing Android/Xray path (**merged**)

- The General / Connection / Tunnel settings hierarchy and the first real VPN Behavior page are merged in `dac9fbc`.
- Existing Android DNS and MTU values are validated/persisted and consumed by Android `VpnService.Builder`, Xray TUN config and Hev config on the next connection. IPv6 remains routed through the VPN; no IPv6-off switch until packet blocking is validated without leakage.
- The earlier merged CI run passed JVM/config/release checks but had no Android virtual device. The follow-up KVM job fixed SDK bootstrap and Maven mirror issues. Run 44885399758 passed the original three DNS/MTU Compose tests. Run 44886470982 caught an unsupported Compose `assertExists` matcher; after replacing it with `assertIsDisplayed`, run [44887021779](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44887021779) passed both CI jobs and all four KVM instrumentation tests, including the new log-limit persistence/recreation/validation path. This verifies settings UI behavior only, not live VPN routing or traffic safety.
- Phase 1 is not full Settings parity; General/Connection/Tunnel controls absent from the app remain absent.

### Phase 2 — SSH, proxy/payload and HCR

- First select and integrate a real, maintained, license-compatible SSH client/forwarding engine; define credential storage, profile versioning/import, host/proxy parsing, cancellation and VpnService lifecycle before exposing an SSH profile.
- Implement the reference's Standard/HCR modes, Payload/Enhanced/TLS methods, remote proxy/authentication, SNI/version and explicit certificate validation policy. Wire keep-alive, SSH-direct proxy mode, TCP_NODELAY and compression to the actual client.
- Implement HCR's compatible framed exchange before exposing upload/download/batch/chunk/frame controls. Adaptive download and calibration must make real requests and return measured results; no fabricated “optimal” values.
- Test against controlled SSH, HTTP CONNECT/payload, TLS and HCR endpoints: handshake success/failure, auth, cert validation/insecure opt-in, timeouts, retries, backpressure, cancellation and teardown. Assess ABI/license/data-size impact before selecting dependencies.

### Phase 3 — SlowDNS

- Integrate a real, license-compatible DNS-tunnel client and route its transport through the selected connection lifecycle; do not emulate it with ordinary DNS or Xray.
- Cover profile-level DNS type/resolver/NS/public-key fields and global multi-login, sessions, KCP window, QNAME/EDNS0 limits and calibration.
- Use controlled DNS-tunnel authorities/resolvers to verify wire-size boundaries, session semantics, calibration, failure handling and stop behavior.

### Phase 4 — advanced V2Ray/Xray and assets

- Expand the actual pinned Xray configuration/profile path for screenshot-backed transport, security, DNS, SOCKS UDP, sniffing/routing-only, custom DNS hosts and routing rules/presets.
- Add the GUI profile editor and raw JSON editor only with validation, safe secret handling, persistence/round-trip tests and a runtime proof that the saved config is the one started by Xray.
- Add Asset files actions only if downloads are integrity-checked and the app can report stale, absent, corrupt and offline states. Keep the bundled/updated data size measured.
- Test generated configs with the exact Xray-core version used by the app and prove routing/DNS behavior using controlled traffic. Reject/hide fragment/noise unless that exact core accepts the settings and traffic tests confirm the behavior.

### Phase 5 — OpenVPN and Psiphon engines

- Integrate and lifecycle-manage real OpenVPN and Psiphon clients as distinct engines, including profile import, credentials, logs, foreground-service/VpnService coordination and stop/error handling. Do not route these profiles through Xray as a substitute.
- Resolve licensing/source obligations, ABI coverage, native-library footprint and compatibility with the single Android VPN interface before implementation. Test with controlled local endpoints and both positive/negative profile cases.

### Phase 6 — remaining General, Connection, UDP and diagnostics

- A numeric log-entry cap (100–10,000) with Reset/Cancel/Save, persistent preference, and actual entry-based buffer trimming is implemented on the current follow-up branch. JVM range/buffer tests and KVM save/reset/cancel/rejection/activity-recreation coverage passed in run 44887021779. Remaining General preferences (language/theme/dynamic color, speed unit), payload buffers/load balancing and SOCKS/BadVPN/UDPGW choices still need real consumers before exposure.
- Implement per-app VPN, connection-check URL/interval/timeout/attempt/stop policy, richer Auto Ping, connection properties/reconnect limits, Share Net local HTTP proxy/authentication/reach modes, and UDP Custom parameters. Validate lifecycle, port conflicts, credential protection, no-leak behavior and observable status.
- Implement the response checker for HTTP/SNI/UDP/DNS with direct/manual proxy, custom headers/body, cancellation and structured results. Verify each mode against controlled services.
- Hardware ID/Access ID: keep the reference row explicitly blocked until the product owner supplies or approves the authorization endpoint, request/response contract, identity lifetime and privacy policy. Never make “Get ID” report success against a fake/local-only service.
- Use emulator instrumentation plus controlled test services for app-level behavior; mark hotspot, physical routing, battery and Android system-settings behavior as device-only until a real device check is completed.

### Phase 7 — parity review, size and release gate

- Walk all 38 rows and record each setting's model, persistence/migration, validation, runtime consumer, automated test evidence, CI result and remaining device-only checks.
- Compare the reference Home / Profiles / Settings / Guide navigation with NusaTunnel's current Beranda / Server / Atur / Log structure; agree the Guide destination's behavior (no Guide content screenshot was supplied) without silently removing Logs. Audit accessibility, strings, import/export and negative/error states—not just screenshot similarity.
- Measure release APKs per ABI and universal, plus installed app/data separately. The supplied 50 MB APK / 150 MB including data figures are goals, not verified sizes or promises; a universal multi-ABI APK is not comparable to a per-ABI APK.
- Publish only after all required remote tests/builds pass. Keep release publishing separate from pull-request CI and block release on any failing or skipped required check.

## Current Android support boundary

As merged, Android has VMess, VLESS, Trojan and Shadowsocks through Xray; Hev and Xray-native TUN choices; a validated/persisted local SOCKS port; Xray sniffing; engine log level and fixed in-memory log retention; limited foreground/offline Auto Ping; and persisted VPN DNS/MTU consumed by Android/Xray/Hev on the next connection. The current follow-up branch replaces fixed log retention with the reference's 100–10,000 numeric cap; the JVM/KVM acceptance checks passed remotely in run 44887021779. Exact caveats and test boundaries are in `android-support-matrix.md`.

SSH, HCR, SlowDNS, OpenVPN, Psiphon, BadVPN/UDPGW, Share Net, advanced per-app/recovery/routing controls, response checking, and the remaining screenshot features are **not yet fully implemented**. Do not expose unsupported options as working controls.

## Size baseline

The merged CI reported per-ABI release APKs of 32.82 MiB (arm64-v8a), 33.38 MiB (armeabi-v7a), 34.32 MiB (x86), 33.81 MiB (x86_64), and 69.66 MiB universal. These are package sizes, not installed/data sizes. The v2.2.3 universal APK baseline was 181,052,794 bytes. Re-measure after every native engine addition.
