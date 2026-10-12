#!/usr/bin/env bash
#
# report.sh — build the Allure report of the last check run. The one documented way.
#
# Usage (from the repo root, straight after the checks have run):
#   .github/scripts/report.sh
#
# It turns the result files in target/allure-results into ONE self-contained page,
# target/site/allure-maven-plugin/index.html, that opens from disk with no server
# and no network.
#
# Why a script and not the bare "./mvnw -N allure:report":
#
# 1. Analytics. Allure Report 2.46.1 puts a Google Analytics tag (googletagmanager.com)
#    into the page AND sends an event of its own to Google each time a report is built.
#    Both are switched off by the environment variable ALLURE_NO_ANALYTICS and by
#    nothing else: allure-maven has no setting for it and passes no environment of its
#    own to the tool, which just inherits this shell's. The value must be exactly
#    "true": the tool reads it for the page tag with Boolean.parseBoolean, so "1"
#    leaves the tag in. Setting it here means nobody has to remember it.
#
# 2. No results, no report. With no result files the tool would still write a page
#    (an empty report that looks like a real one), and a report of an earlier run
#    could still be lying there. So the script builds nothing, removes a stale
#    index.html (that one file only) and exits 1.
#
# 3. Proof, not trust. After the build it checks that the page exists and holds no
#    "googletagmanager", so a future tool version that ignores the variable fails
#    loudly here instead of silently shipping a page that phones home.
#
# Any Maven lifecycle command started from the root (even "validate") empties
# target/allure-results, so build the report before running anything else.
#
# Portable between macOS bash 3.2 and Ubuntu bash 5.

set -euo pipefail

RESULTS_DIR="target/allure-results"
REPORT="target/site/allure-maven-plugin/index.html"

# Exactly "true", see point 1 above.
export ALLURE_NO_ANALYTICS=true

# One result file per check; a missing folder counts as none.
CHECKS=0
if [ -d "$RESULTS_DIR" ]; then
  CHECKS=$(find "$RESULTS_DIR" -maxdepth 1 -type f -name '*-result.json' | wc -l | tr -d ' ')
fi

if [ "$CHECKS" -eq 0 ]; then
  echo "No results to report: $RESULTS_DIR holds no *-result.json file." >&2
  echo "Run the checks first (for example: ./mvnw clean test), then run this script again." >&2
  if [ -f "$REPORT" ]; then
    rm -f "$REPORT"
    echo "Removed the report of an earlier run ($REPORT) so it is not mistaken for this one." >&2
  fi
  exit 1
fi

./mvnw -B -ntp -N allure:report

if [ ! -f "$REPORT" ]; then
  echo "Report not built: $REPORT does not exist after allure:report." >&2
  exit 1
fi
if grep -q -i 'googletagmanager' "$REPORT"; then
  echo "Report not safe to share: $REPORT still holds the Google Analytics tag (googletagmanager)." >&2
  exit 1
fi

echo "Allure report: $(pwd)/$REPORT ($CHECKS checks)"
