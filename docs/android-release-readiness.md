# Android release readiness gate

**Status: BLOCKED — not a release candidate.** This checklist separates release-pipeline hardening from the Android feature-parity work. Passing the pipeline does not mean all 38 reference screenshots or `BACA.TXT` are implemented; use `android-settings-parity-roadmap.md` and `http-custom-reference-matrix.md` for that scope.

## Signing-key security incident and rotation

The GitHub repository is public, and an Android signing keystore was tracked in its history while its password was embedded in Gradle/workflow configuration. Treat that signing identity as compromised. Removing the file from the current tree does **not** erase it from existing Git commits, clones, caches, or prior releases. Do not use it for another public release. History cleanup is a separate coordinated operation and has not been performed.

The user approved key rotation. This repository change removes the old keystore from the current tree and changes release signing to require a private keystore injected from protected GitHub Actions secrets. A fresh RSA-3072 PKCS#12 key was generated in a temporary directory, uploaded directly to the repository's Actions secrets, and the temporary key file was deleted; no private key or password is in the worktree or commit. The four signing secrets and `ANDROID_KEY_CERT_SHA256` repository variable are now configured. GitHub Actions secrets are write-only, and this agent did not create a separate offline key backup; maintainers must decide how to protect recovery before release.

Before any release:

1. Do not generate or replace the configured key casually. The current keystore exists only in write-only GitHub Actions secrets; it cannot be downloaded again, and no independent offline copy was created. Establish a protected recovery plan before release. If that requires replacing the unrecoverable key, generate a new identity only with a separately secured keystore copy ready before provisioning. Keep any backup outside the repository and never paste key material/passwords into issues, commits, or chat. Never reuse the exposed key.
2. In repository **Settings → Secrets and variables → Actions**, verify only the names (not values) of `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`; verify `ANDROID_KEY_CERT_SHA256` exists as a repository variable. Secret values cannot be read back through GitHub's API.
3. Verify the configured fingerprint against the new certificate during a signed release build; it is checked against every release APK.
4. For sideloaded v2.2.5 users, the user has accepted manual reconfiguration: v2.2.5 cannot be updated in place with the rotated key and has no export feature, so its local profiles/settings cannot be moved by the new 2.2.6 backup UI. Publish the prominent notice in `README.md` and `docs/android-signing-rotation-user-notice.md` before tagging; tell users to recover original profile links/credentials and subscription URLs before uninstalling, and not to uninstall if they cannot reconstruct them. The encrypted 2.2.6 backup only protects migrations from 2.2.6 onward and must pass export/restore tests.
5. Increment `versionCode`/`versionName` and add matching `docs/release-notes-v<VERSION>.md`; do not retag or overwrite an existing release.

## Pipeline gates

The hardened workflow in `.github/workflows/android-release.yml` is intended to:

- run only for a pushed `v*` tag;
- require the tag to match the app version, point exactly at current `main`, have matching release notes, and have a successful `Android CI` push run for that exact commit;
- run JVM/config validation and KVM UI instrumentation, failing closed if `/dev/kvm` is unavailable;
- require the new keystore secrets, verify that the keystore/alias can be opened, and reject a checked-in keystore;
- verify every produced APK against `ANDROID_KEY_CERT_SHA256`;
- create a **draft**, not a public release, after all jobs pass. A maintainer must review the draft, test assets/install/update behavior and explicitly publish it.

Normal Android CI builds may use the debug signing key only to prove that the release variant assembles; those CI APKs are not publishable. The release workflow must never fall back to that key or the exposed key.

## Remaining release blockers

- The rotated keystore secrets and certificate-fingerprint variable are provisioned, but the release workflow has not yet verified a signed release APK; no separate offline key backup is documented.
- The old private key remains present in public Git history. The current tree is being cleaned; history purge/revocation and distribution-channel notice need owner coordination.
- The v2.2.5 manual-reconfiguration warning must be merged into the public main branch before any tag; do not claim its app-local profiles/settings are preserved. The new encrypted backup must pass tests for v2.2.6-and-later migrations.
- The feature ledger is still partial: SSH/HCR, SlowDNS, complete Xray profile/routing editors, OpenVPN, Psiphon, Share Net, response checker, and additional screenshot controls remain incomplete. A green build does not clear those parity blockers.
- Android CI workflow-dispatch run [44937854633](https://github.com/XbibzOfficial777/NusaTunnel/actions/runs/44937854633) passed on `android-release-hardening` at `f19a7bb`, including JVM tests, release-variant APK assembly/size reporting, Xray config validation, live subscription smoke, and KVM instrumentation (including encrypted backup restore). This is not a successful `push` run on `main`; rerun CI on the final merged main commit before any release tag.
- Live Xray/VpnService TUN traffic assertions and physical-device behavior remain unverified. No release may claim these are verified.
- Remote GitHub Actions must pass for the final commit and draft release. Do not build/test the Android app in the local sandbox, and do not push a release tag or publish until all required checks and user-facing migration steps are ready.
