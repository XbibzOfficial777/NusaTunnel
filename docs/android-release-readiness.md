# Android release readiness gate

**Status: BLOCKED — not a release candidate.** This checklist separates release-pipeline hardening from the Android feature-parity work. Passing the pipeline does not mean all 38 reference screenshots or `BACA.TXT` are implemented; use `android-settings-parity-roadmap.md` and `http-custom-reference-matrix.md` for that scope.

## Signing-key security incident and rotation

The GitHub repository is public, and an Android signing keystore was tracked in its history while its password was embedded in Gradle/workflow configuration. Treat that signing identity as compromised. Removing the file from the current tree does **not** erase it from existing Git commits, clones, caches, or prior releases. Do not use it for another public release. History cleanup is a separate coordinated operation and has not been performed.

The user approved key rotation. This repository change removes the old keystore from the current tree and makes release signing require a private keystore injected from protected GitHub Actions secrets. After the first v2.2.6 signed build failed its certificate check, the user explicitly authorized replacing that key; the current RSA-3072 PKCS#12 and matching `ANDROID_KEY_CERT_SHA256` variable are provisioned for the v2.2.6+ signing line, including v2.2.7. Temporary key files were deleted; no private key or password is in the worktree or commit. GitHub secrets are write-only and no offline copy exists. The user explicitly accepts GitHub-only recovery risk; preserve the current secrets and never reuse the compromised old key.

Before any release:

1. The user explicitly accepted GitHub-only storage for the v2.2.6+ signing identity, now used for the v2.2.7 candidate. The configured keystore exists only in write-only GitHub Actions secrets; it cannot be downloaded again, and no independent offline copy exists. Keep the current secrets intact and do not regenerate the identity casually. If the secrets are lost, future updates signed with this identity will be impossible; Android cannot update an install across an unrelated signing key. Never reuse the exposed key.
2. In repository **Settings → Secrets and variables → Actions**, verify only the names (not values) of `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`; verify `ANDROID_KEY_CERT_SHA256` exists as a repository variable. Secret values cannot be read back through GitHub's API.
3. Verify the configured fingerprint against the new certificate during a signed release build; it is checked against every release APK.
4. For sideloaded v2.2.5 users, manual reconfiguration is required: v2.2.5 cannot be updated in place with the rotated key and has no export feature, so its local profiles/settings cannot be moved by the encrypted backup. The prominent warning is in `README.md`, `docs/android-signing-rotation-user-notice.md`, and the v2.2.7 release notes; tell users to recover original profile links/credentials and subscription URLs before uninstalling, and not to uninstall if they cannot reconstruct them. Encrypted backup begins in the v2.2.6 code line (versionCode 7) and is first publicly packaged in v2.2.7 (versionCode 8); it only supports migrations from v2.2.6 onward.
5. For future release cycles, increment `versionCode`/`versionName` and add matching `docs/release-notes-v<VERSION>.md`; do not retag or overwrite an existing release.

## Pipeline gates

The hardened workflow in `.github/workflows/android-release.yml` is intended to:

- run only for a pushed `v*` tag;
- require the tag to match the app version, point exactly at current `main`, have matching release notes, and have a successful `Android CI` push run for that exact commit;
- run JVM/config validation and KVM UI instrumentation, failing closed if `/dev/kvm` is unavailable;
- require the new keystore secrets, verify that the keystore/alias can be opened, and reject a checked-in keystore;
- verify the decoded PKCS#12 certificate matches `ANDROID_KEY_CERT_SHA256` before building, require Gradle to have all production signing values (no debug fallback), disable Gradle build caches for this sensitive build, and verify every produced APK against that same fingerprint;
- create a **draft**, not a public release, after all jobs pass. A maintainer must review the draft, test assets/install/update behavior and explicitly publish it.

Normal Android CI builds may use the debug signing key only to prove that the release variant assembles; those CI APKs are not publishable. The release workflow must never fall back to that key or the exposed key.

## Remaining release blockers

- The rotated keystore secrets and certificate-fingerprint variable are provisioned. In v2.2.7 tagged run [44975383895](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44975383895), preflight, unit/config tests, KVM instrumentation, keytool fingerprint validation, and APK assembly all passed. `apksigner` reported an APK SHA-256 digest equal to the expected certificate, but verification failed because the script only parsed the older `Signer #1 certificate SHA-256 digest:` output while Build Tools 37 emits `V2 Signer: certificate SHA-256 digest:`. The key is correctly signing the build; do not rotate it. The verifier parser is being fixed to support both formats. No draft or assets were uploaded.
- The old private key remains present in public Git history. At the user's explicit direction, no history rewrite/purge will be performed for this release, and the residual exposure is accepted. Never use that old key to sign new builds.
- The v2.2.5 manual-reconfiguration warning is retained and updated for the v2.2.7 candidate; do not claim v2.2.5 app-local profiles/settings are preserved. The encrypted backup's unit and KVM migration tests passed in CI runs [44955532796](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44955532796) and [44962307243](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44962307243); the v2.2.7 APK's signature was valid and its certificate digest matched, but the workflow's digest-parsing gate has not yet passed or uploaded assets.
- The feature ledger is still partial: SSH/HCR, SlowDNS, complete Xray profile/routing editors, OpenVPN, Psiphon, Share Net, response checker, and additional screenshot controls remain incomplete. A green build does not clear those parity blockers.
- Android CI run [44962307243](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44962307243) passed on `main` commit `f6c50a6`, including JVM tests, release-variant APK assembly/size reporting, Xray config validation, live subscription smoke, and KVM instrumentation (including encrypted backup restore). This confirms the merge did not regress the non-production build/test path; only a tagged release run can validate the protected-key signing path.
- Live Xray/VpnService TUN traffic assertions and physical-device behavior remain unverified. No release may claim these are verified.
- The existing `v2.2.6` tag remains unchanged at `90fc754`; its release run [44956238856](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44956238856) failed before asset upload. The new `v2.2.7` tag points at `6d9c9f8`; run [44975383895](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44975383895) built APKs signed by the expected cert but stopped at the output-parser mismatch, so no draft or assets exist. The parser fix must be merged and pass main CI. Release preflight requires the tag to equal current `main`; rerunning after the fix will require moving/recreating `v2.2.7` or preparing another version. Do not move `v2.2.7` without explicit authorization, do not move `v2.2.6`, and do not publish until assets are reviewed.
