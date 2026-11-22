# Android release readiness gate

**Status: v2.2.7 is published; Android feature parity remains incomplete.** The release pipeline passed, but publication does not mean all 38 reference screenshots or `BACA.TXT` are implemented; use `android-settings-parity-roadmap.md` and `http-custom-reference-matrix.md` for that scope.

## Signing-key security incident and rotation

The GitHub repository is public, and an Android signing keystore was tracked in its history while its password was embedded in Gradle/workflow configuration. Treat that signing identity as compromised. Removing the file from the current tree does **not** erase it from existing Git commits, clones, caches, or prior releases. Do not use it for another public release. History cleanup is a separate coordinated operation and has not been performed.

The user approved key rotation. This repository change removes the old keystore from the current tree and makes release signing require a private keystore injected from protected GitHub Actions secrets. After the first v2.2.6 signed build failed its certificate check, the user explicitly authorized replacing that key; the current RSA-3072 PKCS#12 and matching `ANDROID_KEY_CERT_SHA256` variable are provisioned for the v2.2.6+ signing line, including the published v2.2.7. Temporary key files were deleted; no private key or password is in the worktree or commit. GitHub secrets are write-only and no offline copy exists. The user explicitly accepts GitHub-only recovery risk; preserve the current secrets and never reuse the compromised old key.

Before any release:

1. The user explicitly accepted GitHub-only storage for the v2.2.6+ signing identity, used for the published v2.2.7 release. The configured keystore exists only in write-only GitHub Actions secrets; it cannot be downloaded again, and no independent offline copy exists. Keep the current secrets intact and do not regenerate the identity casually. If the secrets are lost, future updates signed with this identity will be impossible; Android cannot update an install across an unrelated signing key. Never reuse the exposed key.
2. In repository **Settings → Secrets and variables → Actions**, verify only the names (not values) of `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`; verify `ANDROID_KEY_CERT_SHA256` exists as a repository variable. Secret values cannot be read back through GitHub's API.
3. Verify the configured fingerprint against the new certificate during a signed release build; it is checked against every release APK.
4. For sideloaded v2.2.5 users, manual reconfiguration is required: v2.2.5 cannot be updated in place with the rotated key and has no export feature, so its local profiles/settings cannot be moved by the encrypted backup. The prominent warning is in `README.md`, `docs/android-signing-rotation-user-notice.md`, and the v2.2.7 release notes; tell users to recover original profile links/credentials and subscription URLs before uninstalling, and not to uninstall if they cannot reconstruct them. Encrypted backup begins in the v2.2.6 code line (versionCode 7) and is first publicly packaged in v2.2.7 (versionCode 8); it only supports migrations from v2.2.6 onward.
5. For future release cycles, increment `versionCode`/`versionName` and add matching `docs/release-notes-v<VERSION>.md`; do not retag or overwrite an existing release.

## Pipeline gates

The hardened workflow in `.github/workflows/android-release.yml` is intended to:

- run only for a pushed `v*` tag;
- require the tag to match the app version, point exactly at current `main`, have matching release notes, and have a successful `Android CI` push run for that exact commit;
- run JVM/config validation and KVM UI instrumentation, failing closed if `/dev/kvm` is unavailable;
- on the signed release build, install the published v2.2.5 APK on KVM, confirm an in-place update is rejected specifically for the changed signing certificate, then uninstall and verify clean install/launch of v2.2.7;
- require the new keystore secrets, verify that the keystore/alias can be opened, and reject a checked-in keystore;
- verify the decoded PKCS#12 certificate matches `ANDROID_KEY_CERT_SHA256` before building, require Gradle to have all production signing values (no debug fallback), disable Gradle build caches for this sensitive build, and verify every produced APK against that same fingerprint;
- create a **draft**, not a public release, after all jobs pass. A maintainer must review the draft, test assets/install/update behavior and explicitly publish it.

Normal Android CI builds may use the debug signing key only to prove that the release variant assembles; those CI APKs are not publishable. The release workflow must never fall back to that key or the exposed key.

## v2.2.7 release result and remaining product gaps

- **Published:** [NusaTunnel 2.2.7 on GitHub Releases](https://github.com/XbibzOfficial777/NusaTunnel/releases/tag/v2.2.7). The `v2.2.7` tag resolves to `460d3d46fac3c18574a73fbba6e461672c7c5716`; `v2.2.6` remains unchanged at `90fc754398136b34f8391be6dc34c40a77b8344b`.
- Protected-key release workflow [44987666169](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44987666169) passed preflight, JVM/config tests, KVM instrumentation, keystore validation, APK build, signer verification, install/update smoke test, and draft upload. All five APKs reported the expected signing certificate SHA-256 `73350baae2e6c7166f3fddb4187b697e80ab0dcd671f6b02935728a0153b68b2`.
- The KVM smoke test checksum-verified and installed the public v2.2.5 APK, confirmed its update to v2.2.7 was rejected with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, then uninstalled it and clean-installed/launched v2.2.7. The migration warning remains prominent: v2.2.5 has no export and users must recover profiles, credentials, subscription URL, and needed settings before uninstalling; if they cannot reconstruct them, they should not uninstall.
- The published assets are the arm64-v8a, armeabi-v7a, x86, x86_64, and universal APKs; the universal `.apks` bundle; and `SHA256SUMS.txt`. The published assets were downloaded for review: every APK/.apks hash matched the checksum manifest and GitHub's asset digest. The release notes accurately describe the v2.2.6-code-line encrypted backup (first public packaging in v2.2.7), its included data, on-device passphrase encryption, and exclusion of the signing key.
- The old compromised signing key remains in public Git history. At the user's explicit direction, no history rewrite/purge is performed; never use the old key. Preserve the current GitHub signing secrets and do not rotate them without fresh authorization; the replacement key is stored only in GitHub and has no offline copy.
- The earlier v2.2.6 signing-verification failure [44956238856](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44956238856) and first v2.2.7 parser failure [44975383895](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44975383895) are superseded by the successful v2.2.7 run. The parser fix is merged; do not rotate the working key.
- Android CI [44987288760](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44987288760) passed on the release commit `460d3d4` before tagging, including both standard CI jobs.
- Android parity remains partial: SSH/HCR, SlowDNS, complete Xray profile/routing editors, OpenVPN, Psiphon, Share Net, response checker, and additional screenshot controls remain incomplete. A successful release build does not claim full HTTP Custom parity.
- Live Xray/VpnService TUN traffic assertions and behavior on physical devices remain unverified; do not claim these were tested by the release pipeline.
