#!/usr/bin/env bash
#
# access_key.sh — choose the access key for one CI job and export it as API_TOKEN.
#
# Usage (from the repo root, as a GitHub Actions step, after the checkout):
#   SECRET_API_TOKEN="${{ secrets.API_TOKEN }}" .github/scripts/access_key.sh
#
# The key feeds the lab AND the framework: the repository secret if one is set,
# otherwise the lab's documented local key (public, not a secret), read from
# application.yml's ${API_TOKEN:<default>} placeholder, so there is one source of
# truth and no key literal in the workflow.
#
# The fallback is required, not cosmetic: an unset secret expands to "" and Spring's
# ${API_TOKEN:default} only falls back when the variable is *missing*, so the lab
# would accept an empty key while the framework treats blank as unset and sends the
# default -> every check gets 401. One value feeds both sides.
#
# The key is masked BEFORE it goes into $GITHUB_ENV, so every later step (whose env
# header lists API_TOKEN) shows ***. That is also why it is not job-level env: the
# runner prints a step's env header before any ::add-mask:: could run.

set -euo pipefail

LAB_SETTINGS="mock-lab-app/src/main/resources/application.yml"

DEFAULT=$(sed -n 's/.*\${API_TOKEN:\([^}]*\)}.*/\1/p' "$LAB_SETTINGS" | head -n 1)
KEY="${SECRET_API_TOKEN:-$DEFAULT}"
if [ -z "$KEY" ]; then
  echo "::error::No access key: the API_TOKEN secret is unset and no \${API_TOKEN:<default>} was found in $LAB_SETTINGS"
  exit 1
fi
echo "::add-mask::$KEY"
echo "API_TOKEN=$KEY" >> "$GITHUB_ENV"
if [ -n "${SECRET_API_TOKEN:-}" ]; then
  echo "Access key: the API_TOKEN repository secret"
else
  echo "Access key: the lab default from application.yml (no API_TOKEN secret set)"
fi
