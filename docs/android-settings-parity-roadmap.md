# Android HTTP Custom parity roadmap

**Baseline:** `main` at merge commit `d3ad310849232f03afe37a8b862fcbda3c6bd926` after PRs [#1–#5](https://github.com/XbibzOfficial777/NusaTunnel/pulls). PR #3 adds measured traffic-rate units and per-app VPN policy; PR #4 adds the opt-in Android CPU wake lock and Settings shortcuts; PR #5 adds HTTP(S) Connection Check via the local Xray SOCKS5 inbound. PR #5's CI and post-merge CI pass, but live Xray/VPN traffic verification remains pending. None of these slices constitutes full parity.

**Reference:** all 38 supplied screenshots under `ISI SETTINGS` and `BACA.TXT`. The item-by-item source/feature/runtime/test mapping is [`http-custom-reference-matrix.md`](http-custom-reference-matrix.md); it is the authoritative omission check.

**Prior verification:** post-merge PR #3 CI [44894114685](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44894114685) passed. PR #4's final CI [44895607868](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44895607868) and post-merge main CI [44895981845](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44895981845) passed; the PR KVM artifact recorded 11 tests, 0 failures/errors/skips, including the real PowerManager wake-lock lifecycle. Connection Check PR CI [44905947111](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44905947111) passed both jobs, including 12 KVM tests, 0 failures/errors/skips; post-merge main CI [44906424937](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44906424937) also passed. These checks do **not** establish a GET through a live Xray instance/VpnService TUN path, end-to-end VPN routing, battery policy, Always-on/lockdown, or physical-device behavior. All app builds/tests must run on remote GitHub Actions, not in the local sandbox.

**Existing release:** v2.2.5 is published as an explicitly incremental release, not full HTTP Custom parity. PR #2's post-merge run [44889231145](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44889231145) passed both Android CI jobs; the numeric log cap is validated at 100–10,000 and trims the real in-memory entry buffer. VPN routing/DNS/leak behavior remains unverified, and no new release is planned for this partial slice.

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
- The KVM follow-up is merged in PR #2. Post-merge run [44889231145](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44889231145) passed unit/config/live-subscription/release assembly and KVM instrumentation. The four instrumented cases cover valid DNS/MTU persistence, invalid DNS/MTU rejection, and numeric log-limit save/reset/cancel/rejection/activity recreation. This verifies settings UI behavior only, not live VPN routing or traffic safety.
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

- The numeric log-entry cap (100–10,000) with Reset/Cancel/Save, persistent preference, and actual entry-based buffer trimming is implemented on `main`; JVM and KVM coverage passed in post-merge run 44889231145. PR #3 implements persisted Byte/s versus bit/s selection backed by measured Xray counter deltas and live Beranda rates; JVM/KVM checks passed in run 44891577574. Language/theme/dynamic color, payload buffers/load balancing and BadVPN/UDPGW choices still need real consumers before exposure.
- PR #3 adds persisted per-app VPN modes and a searchable launchable-app picker wired into `VpnService.Builder`; JVM policy tests and KVM selection/persistence/recreation checks passed in run 44893277188. PR #4 adds persisted opt-in CPU-awake behavior backed by an Android `PARTIAL_WAKE_LOCK` held only while the VPN interface is active, plus Android app-info battery and VPN settings shortcuts. Run 44895022995 passed 11 KVM tests (0 failures), including actual PowerManager lock lifecycle and shortcut-intent resolution. This does not enable battery exemption or Always-on/lockdown and is not physical-device proof. Still required before claiming end-to-end per-app routing: controlled traffic assertions for included/excluded apps plus reconnect behavior. Automatic reconnect, richer Auto Ping, connection properties/retry limits, Share Net local HTTP proxy/authentication/reach modes, and UDP Custom parameters remain unimplemented. PR #5 merged to `main` at `d3ad310849232f03afe37a8b862fcbda3c6bd926` and adds validated/persisted HTTP(S) GET checks through the active local Xray SOCKS5 inbound, a connected-only monitor with bounded timeout/retries and opt-in stop-on-cycle-failure, plus JVM/KVM tests. After two corrective CI iterations, PR run [44905947111](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44905947111) and post-merge main run [44906424937](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44906424937) passed; the KVM artifact records 12 tests, 0 failures/errors/skips. The fake-SOCKS tests exercise the SOCKS handshake and HTTP/TLS client behavior, but do not prove a live Xray/VPN route. A controlled Xray/tunnel traffic test is still required before claiming live-path runtime parity. Validate lifecycle, port conflicts, credential protection, no-leak behavior and observable status.
- Implement the response checker for HTTP/SNI/UDP/DNS with direct/manual proxy, custom headers/body, cancellation and structured results. Verify each mode against controlled services.
- Hardware ID/Access ID: keep the reference row explicitly blocked until the product owner supplies or approves the authorization endpoint, request/response contract, identity lifetime and privacy policy. Never make “Get ID” report success against a fake/local-only service.
- Use emulator instrumentation plus controlled test services for app-level behavior; mark hotspot, physical routing, battery and Android system-settings behavior as device-only until a real device check is completed.

### Phase 7 — parity review, size and release gate

- Walk all 38 rows and record each setting's model, persistence/migration, validation, runtime consumer, automated test evidence, CI result and remaining device-only checks.
- Compare the reference Home / Profiles / Settings / Guide navigation with NusaTunnel's current Beranda / Server / Atur / Log structure; agree the Guide destination's behavior (no Guide content screenshot was supplied) without silently removing Logs. Audit accessibility, strings, import/export and negative/error states—not just screenshot similarity.
- Measure release APKs per ABI and universal, plus installed app/data separately. The supplied 50 MB APK / 150 MB including data figures are goals, not verified sizes or promises; a universal multi-ABI APK is not comparable to a per-ABI APK.
- Publish only after all required remote tests/builds pass. Keep release publishing separate from pull-request CI and block release on any failing or skipped required check.

## Current Android support boundary

On `main` at merge commit `d3ad310`, Android has VMess, VLESS, Trojan and Shadowsocks through Xray; Hev and Xray-native TUN choices; a validated/persisted local SOCKS port; Xray sniffing; engine log level and the tested 100–10,000 numeric in-memory log cap; limited foreground/offline Auto Ping; persisted VPN DNS/MTU consumed by Android/Xray/Hev on the next connection; PR #4's opt-in active-VPN wake lock and Android settings shortcuts; and PR #5's persisted HTTP(S) Connection Check over the local SOCKS5 inbound. Post-merge CI run [44906424937](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44906424937) passed. Physical-device and network behavior remain subject to the caveats in `android-support-matrix.md`.

Per-app VPN policy selection is wired to Android's allow/disallow application builder rules, but included/excluded app traffic still needs controlled end-to-end verification. Connection Check has JVM protocol/retry tests and KVM settings-recreation coverage; PR CI [44905947111](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44905947111) and post-merge CI [44906424937](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44906424937) pass, but fake-SOCKS tests do not prove a live Xray/VPN route. SSH, HCR, SlowDNS, OpenVPN, Psiphon, BadVPN/UDPGW, Share Net, automatic reconnect, response checking, and the remaining screenshot features are **not yet fully implemented**. Do not expose unsupported options as working controls.

## Size baseline

The merged CI reported per-ABI release APKs of 32.82 MiB (arm64-v8a), 33.38 MiB (armeabi-v7a), 34.32 MiB (x86), 33.81 MiB (x86_64), and 69.66 MiB universal. These are package sizes, not installed/data sizes. The v2.2.3 universal APK baseline was 181,052,794 bytes. Re-measure after every native engine addition.
