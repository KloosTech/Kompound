#!/usr/bin/env bash
# Removes every demo that now has a smoke flow from coverage-allowlist.txt (the ratchet only goes one way).
set -eu
cd "$(dirname "$0")/../.."
for id in $(grep -v '^#' maestro/coverage-allowlist.txt); do
  if ls maestro/flows/components/*/"$id"/00-smoke.yaml >/dev/null 2>&1; then sed -i '' "/^$id\$/d" maestro/coverage-allowlist.txt; fi
done
maestro/scripts/coverage.sh | tail -1
