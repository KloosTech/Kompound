#!/usr/bin/env bash
# Every demo of the catalog needs a smoke flow (ADR 0008, section 4.4). This script needs no device, so CI can run it.
#
#   coverage.sh [--list]
#
# A demo without maestro/flows/components/<category>/<demo-id>/00-smoke.yaml fails the check unless it is in maestro/coverage-allowlist.txt.
# The allowlist may only shrink: a demo that has a smoke flow and is still listed fails too, and so does a listed demo that no longer exists.
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT" || exit 2
ALLOW="maestro/coverage-allowlist.txt"

DEMOS=$(maestro/scripts/demo-ids.sh)
[ -n "$DEMOS" ] || { echo "No demos found (is showcase/src/commonMain there?)" >&2; exit 2; }
COVERED=""; MISSING=""
while read -r id category; do
  if ls maestro/flows/components/*/"$id"/00-smoke.yaml >/dev/null 2>&1; then COVERED="$COVERED$id
"; else MISSING="$MISSING$id
"; fi
done <<< "$DEMOS"
ALLOWED=$(grep -v '^[[:space:]]*#' "$ALLOW" 2>/dev/null | grep -v '^[[:space:]]*$' | sort)
ALL_IDS=$(echo "$DEMOS" | awk '{print $1}' | sort)
N_ALL=$(echo "$ALL_IDS" | grep -c .); N_COV=$(printf '%s' "$COVERED" | grep -c .)

if [ "${1:-}" = "--list" ]; then printf '%s' "$MISSING" | sort; exit 0; fi

BAD=0
NEW=$(comm -23 <(printf '%s' "$MISSING" | sort) <(echo "$ALLOWED"))
if [ -n "$NEW" ]; then BAD=1; echo "Demos without a smoke flow (run maestro/scripts/scaffold.sh <demo-id> and fill it in):"; echo "$NEW" | sed 's/^/  /'; fi
STALE=$(comm -12 <(printf '%s' "$COVERED" | sort) <(echo "$ALLOWED"))
if [ -n "$STALE" ]; then BAD=1; echo "Demos that have a smoke flow but are still in $ALLOW (remove them; the list only shrinks):"; echo "$STALE" | sed 's/^/  /'; fi
GONE=$(comm -13 <(echo "$ALL_IDS") <(echo "$ALLOWED"))
if [ -n "$GONE" ]; then BAD=1; echo "Ids in $ALLOW that are not demos any more:"; echo "$GONE" | sed 's/^/  /'; fi
echo "Maestro coverage: $N_COV of $N_ALL demos have a smoke flow; $(echo "$ALLOWED" | grep -c .) are on the allowlist."
exit $BAD
