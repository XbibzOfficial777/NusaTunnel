# Android release readiness gate

**Status: BLOCKED — not a release candidate.** This checklist separates release-pipeline hardening from the Android feature-parity work. Passing the pipeline does not mean all 38 reference screenshots or `BACA.TXT` are implemented; use `android-settings-parity-roadmap.md` and `http-custom-reference-matrix.md` for that scope.

## Signing-key security incident and rotation

The GitHub repository is public, and an Android signing keystore was tracked in its history while its password was embedded in Gradle/workflow configuration. Treat that signing identity as compromised. Removing the file from the current tree does **not** erase it from existing Git commits, clones, caches, or prior releases. Do not use it for another public release. History cleanup is a separate coordinated operation and has not been performed.

The user approved key rotation. This repository change removes the old keystore from the current tree and makes release signing require a private keystore injected from protected GitHub Actions secrets. After the first v2.2.6 signed build failed its certificate check, the user explicitly authorized replacing that key; the current RSA-3072 PKCS#12 and matching `ANDROID_KEY_CERT_SHA256` variable are provisioned. Temporary key files were deleted; no private key or password is in the worktree or commit. GitHub secrets are write-only and no offline copy exists. The user explicitly accepts GitHub-only recovery risk for v2.2.6; preserve the current secrets and never reuse the compromised old key.

Before any release:

1. The user explicitly accepted GitHub-only storage for this v2.2.6 release. The configured keystore exists only in write-only GitHub Actions secrets; it cannot be downloaded again, and no independent offline copy exists. Keep the current secrets intact and do not regenerate the identity casually. If the secrets are lost, future updates signed with this identity will be impossible; Android cannot update an install across an unrelated signing key. Never reuse the exposed key.
2. In repository **Settings → Secrets and variables → Actions**, verify only the names (not values) of `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`; verify `ANDROID_KEY_CERT_SHA256` exists as a repository variable. Secret values cannot be read back through GitHub's API.
3. Verify the configured fingerprint against the new certificate during a signed release build; it is checked against every release APK.
4. For sideloaded v2.2.5 users, manual reconfiguration is required: v2.2.5 cannot be updated in place with the rotated key and has no export feature, so its local profiles/settings cannot be moved by the new 2.2.6 backup UI. The prominent warning is in `README.md` and `docs/android-signing-rotation-user-notice.md`; tell users to recover original profile links/credentials and subscription URLs before uninstalling, and not to uninstall if they cannot reconstruct them. The encrypted 2.2.6 backup only protects migrations from 2.2.6 onward and must pass export/restore tests.
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

- The rotated keystore secrets and certificate-fingerprint variable are provisioned. Earlier v2.2.6 workflow attempts failed APK certificate verification; the workflow now checks the decoded keystore's certificate before Gradle, requires production signing in Gradle, bypasses Gradle caches, and reports normalized expected/actual fingerprints at APK verification. These safeguards are not yet remotely validated; preserve the configured secrets and require a green tagged run before proceeding to draft review.
- The old private key remains present in public Git history. At the user's explicit direction, no history rewrite/purge will be performed for this release, and the residual exposure is accepted. Never use that old key to sign new builds.
- The v2.2.5 manual-reconfiguration warning is present in the merged v2.2.6 source; do not claim its app-local profiles/settings are preserved. The encrypted backup's unit and KVM migration tests passed in CI run [44955532796](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44955532796), but no release artifact has passed signing verification yet.
- The feature ledger is still partial: SSH/HCR, SlowDNS, complete Xray profile/routing editors, OpenVPN, Psiphon, Share Net, response checker, and additional screenshot controls remain incomplete. A green build does not clear those parity blockers.
- Android CI run [44955532796](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44955532796) passed on the merged `main` commit `90fc754`, including JVM tests, release-variant APK assembly/size reporting, Xray config validation, live subscription smoke, and KVM instrumentation (including encrypted backup restore). It predates the signing-cache safeguards now under review; rerun CI on their merged commit before attempting release validation.
- Live Xray/VpnService TUN traffic assertions and physical-device behavior remain unverified. No release may claim these are verified.
- Remote GitHub Actions must pass for the final commit and signed artifact. Existing tag `v2.2.6` points to `90fc754` and release run [44956238856](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44956238856) failed before asset upload; no draft or assets exist. Do not publish until the signing fix is remotely validated and the resulting artifacts pass review. Do not rewrite history or move the existing tag without explicit authorization.
