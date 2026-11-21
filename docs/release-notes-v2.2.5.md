# NusaTunnel Android 2.2.5 — incremental settings release

This release publishes the Android settings work merged in PRs #1 and #2.

## Included

- Validated, persisted VPN DNS and MTU settings used by Android `VpnService`, Xray TUN, and Hev on the next connection.
- Numeric **Log entry limit** from 100 to 10,000 (default 1,000), with Reset/Cancel/Save. The saved limit trims the in-memory log by whole entries; multiline entries count once.
- KVM-backed Android UI tests and JVM tests for settings persistence, validation, and log-buffer trimming.

## Scope and verification limits

This is an incremental Android release, **not full HTTP Custom feature parity**. SSH, HCR, SlowDNS, OpenVPN, Psiphon, Share Net, the response checker, and many other reference controls remain unimplemented. Emulator instrumentation covers UI and preferences; it does not establish live VPN routing, DNS resolution, no-leak behavior, hotspot sharing, or physical-device behavior. See the [Android support matrix](https://github.com/XbibzOfficial777/NusaTunnel/blob/main/docs/android-support-matrix.md) and [HTTP Custom reference matrix](https://github.com/XbibzOfficial777/NusaTunnel/blob/main/docs/http-custom-reference-matrix.md) for the current support boundary.
