# Android settings parity plan

**Baseline:** v2.2.4 (`e6c8011`, released 2026-11-21)  
**Reference:** all 38 settings screenshots in the supplied `ISI SETTINGS` folder and `BACA.TXT`.  
**Status:** Phase 1 is in progress on a local feature branch. The new settings hierarchy and persisted DNS/MTU wiring are not yet verified by remote CI or a device. Planning and implementation must be feature-backed; an enabled UI control is not complete until its stored value changes real runtime behavior.

## Acceptance rule — no simulation

For every setting that is shown as available:

1. its value is validated and persisted;
2. the Android service or the selected protocol engine consumes that value (not just the UI);
3. changes take effect at the stated time, usually on the next connection;
4. tests cover defaults, validation, persistence/config generation where possible, and CI validates generated native-engine configs;
5. unsupported engine-specific settings are omitted or explicitly shown as unavailable. They must not be represented by active controls that do nothing.

Do not call Android builds or app tests in the sandbox. Use GitHub Actions for builds/tests. Do not tag/publish a release until required remote checks pass. Device-only behavior (VPN lifecycle, routing, battery, hotspot sharing) must be identified as unverified until tested on a real Android device.

## Reference inventory

### General

- Language, Dynamic color, Theme colors, Theme mode
- Log filter, Log entry limit, Speed unit

### Connection

- Payload client/remote buffers
- Load-balancing strategy
- SOCKS engine type, SOCKS UDP support, BadVPN UDPGW port
- VPN behavior: DNS, MTU, IPv6, per-app VPN, auto-reconnect, keep-awake, battery settings, always-on VPN
- Connection-check URL, interval, timeout, retry limit, stop-on-failure
- Auto Ping modes and advanced target/interval/timeout/reset/failure/retry settings
- Share Net: enabled state, reach method, local HTTP proxy/port, optional authentication

### Tunnel

- Properties: connection timeout, maximum reconnect attempts
- HCR: upload/download parallelism, batch, chunk, frame, adaptive download, calibration
- SlowDNS: multi-login, sessions, KCP window, calibration, QNAME and EDNS0 limits
- SSH: keep-alive, proxy handling, TCP_NODELAY, compression
- V2Ray: default view, SOCKS UDP, proxy handling, destination detection/routing-only, detected protocols, fragment/noise, DNS, remote DNS, DNS-host presets/custom hosts, routing rules
- Psiphon: default view and proxy handling
- OpenVPN: default view and proxy handling
- UDP Custom: upload/download rates, receive window, idle timeout, Path MTU discovery

## Phased implementation

### Phase 0 — inventory and safeguards (this document)

- Map every screenshot item to a persistence owner, runtime consumer, protocol dependency, and verification requirement.
- Keep unsupported controls out of the active UI until the corresponding engine path exists.

### Phase 1 — settings architecture and existing Android/Xray path

- Replace the flat settings form with the reference's General / Connection / Tunnel hierarchy and real subpages.
- Consolidate preference validation and migration; keep existing SOCKS port, log level/retention, auto-ping, UDP-engine, and sniffing behavior intact.
- Add only settings that can be wired safely to the existing Android `VpnService`, Hev, and Xray path (for example VPN DNS/MTU and Xray routing/DNS/UDP options after runtime-config validation).
- Do not add an IPv6-off control until the packet path can block IPv6 without leaking it outside the VPN.
- Confirm UI state survives navigation/recreation and preferences survive app restart; run remote CI, including Xray-core config validation.

### Phase 2 — SSH, proxy/payload, and HCR

- Add an actual SSH connection/forwarding engine and profile model/import path, then wire payload/proxy modes, keep-alive, TCP_NODELAY, and compression to that engine.
- Treat HCR upload/download/batch/chunk/frame/adaptive/calibration as incomplete until the actual compatible HCR exchange and measurements are implemented; calibration must perform network requests and report real results.
- Verify server handshake, proxy authentication, failures/timeouts, cancellation, and teardown in CI/device scenarios before exposing controls as available.

### Phase 3 — SlowDNS

- Integrate a real, license-compatible DNS-tunnel client and route its transport through the connection lifecycle.
- Wire sessions/multi-login, KCP window, QNAME/EDNS0 limits and calibration to the implementation; verify DNS packet limits and failure behavior with a controlled endpoint.

### Phase 4 — V2Ray/Xray advanced routing

- Expand actual Xray runtime configuration for SOCKS UDP, DNS, routing rules/domain strategy, and supported transport/security options.
- Enable only options supported by the pinned Xray core. Fragment/noise controls must be rejected or hidden unless the exact generated config is accepted by that core and works in traffic tests.
- Validate representative generated configs using the same Xray binary/AAR version used by the app.

### Phase 5 — OpenVPN and Psiphon engines

- Integrate and lifecycle-manage real OpenVPN and Psiphon clients as distinct engines, including profile import, credentials, logs, foreground-service/VpnService coordination, and stop/error handling.
- Before adding native dependencies, assess ABI coverage, APK/data-size impact, licensing/source obligations, and compatibility with the existing single Android VPN interface.
- Do not route these profiles through Xray as a substitute.

### Phase 6 — remaining connection and UDP features

- Implement real per-app VPN selection, auto-reconnect/connection checks, richer Auto Ping recovery, Share Net's local proxy/authentication, and UDP Custom tuning only where the selected engine provides a real runtime parameter.
- Security-sensitive behavior (credential storage, listening interfaces, proxy authentication, IPv6 leak prevention, hotspot reachability) requires explicit tests and user-visible status.

### Phase 7 — parity review, size, and release gate

- Walk every row in the reference inventory and record: implemented runtime consumer, persistence/migration, tests, and any device-only verification.
- Audit the supplied icon and all launcher variants, strings, accessibility, and settings navigation.
- Measure release APKs per ABI and universal, plus installed data separately; the supplied 50 MB / 150 MB figures are goals, not promises. A universal APK containing multiple native ABIs is not directly comparable to a per-ABI APK.
- Publish only after the remote test/build/release checks pass. Do not claim perfect/no-bug behavior without device validation.

## Current baseline reality

At v2.2.4 Android has working VMess, VLESS, Trojan, and Shadowsocks through Xray; Hev and Xray-native TUN choices; a validated/persisted local SOCKS port; Xray sniffing; engine log level and retention; and a limited foreground/offline Auto Ping. SSH, HCR, SlowDNS, OpenVPN, Psiphon, Share Net, and the remaining screenshot controls are **not yet implemented** in Android. The authoritative protocol boundary is `docs/android-support-matrix.md`.

## Size baseline

v2.2.3 universal APK: 181,052,794 bytes. v2.2.4 GitHub Actions reported ~32.81 MiB for arm64 and ~69.65 MiB for universal; GitHub Release contains per-ABI APKs. New engines must have their native-library footprint measured per ABI before release.
