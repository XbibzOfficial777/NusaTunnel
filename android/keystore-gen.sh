#!/usr/bin/env bash
set -euo pipefail
cat <<'MESSAGE'
The legacy release-keystore generator is disabled. It embedded an obsolete password and wrote
signing material into the repository tree. Do not reuse that signing identity.

Generate a new PKCS#12 keystore outside the repository and follow:
  docs/android-release-readiness.md
MESSAGE
exit 1
