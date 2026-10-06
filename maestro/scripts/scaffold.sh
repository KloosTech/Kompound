#!/usr/bin/env bash
# Creates the flow files for one demo (ADR 0008, section 4.2):
#
#   scaffold.sh <demo-id> [--text "headline text"] [--force]
#
# 00-smoke.yaml is complete and runnable (opens the demo, waits for the harness, asserts the optional --text in the preview, checks nothing crashed).
# 10-interact, 20-states and 30-env are skeletons tagged `todo` (excluded from runs until the tag is removed); they share a `_core.yaml`.
# The demo's controls are listed in the comments of 20-states.yaml when the source can be read, so you know what to preset.
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
ID="${1:-}"; [ -n "$ID" ] || { sed -n '2,9p' "$0"; exit 2; }; shift
TEXT=""; FORCE=0
while [ $# -gt 0 ]; do case "$1" in --text) TEXT="$2"; shift 2 ;; --force) FORCE=1; shift ;; *) echo "unknown option $1" >&2; exit 2 ;; esac; done

CAT=$(maestro/scripts/demo-ids.sh | awk -v id="$ID" '$1==id {print $2}')
[ -n "$CAT" ] || { echo "No demo with id '$ID'. Known ids: maestro/scripts/demo-ids.sh" >&2; exit 1; }
DIR="maestro/flows/components/$CAT/$ID"
if [ -d "$DIR" ] && [ "$FORCE" = 0 ]; then echo "$DIR exists (use --force to overwrite)" >&2; exit 1; fi
mkdir -p "$DIR"
SRC=$(grep -rl "id = \"$ID\"" showcase/src/commonMain | head -n 1)
CONTROLS=""
[ -n "$SRC" ] && CONTROLS=$(grep -ohE '(textControl|boolControl|choiceControl|floatControl)\("[^"]+"[^)]*' "$SRC" | sed 's/^/#   /' | head -n 20)
HEADER() { # name suffix, tag
cat <<EOF
appId: tech.kloos.kompound.catalog.maestro
name: $ID $1
tags:
  - $2
  - component:$ID
  - category:$CAT
$3properties:
  junitClassname: $ID
---
EOF
}
{
  HEADER smoke smoke ""
  echo "- runFlow:"; echo "    file: ../../../_lib/open-demo.yaml"; echo "    env:"; echo "      DEMO: $ID"
  if [ -n "$TEXT" ]; then echo "- runFlow:"; echo "    file: ../../../_lib/preview-see.yaml"; echo "    env: { TEXT: \"$TEXT\" }"; fi
  echo "- runFlow: ../../../_lib/assert-no-crash.yaml"
} > "$DIR/00-smoke.yaml"
cat > "$DIR/_core.yaml" <<EOF
# What $ID does, in whatever environment the caller opens it in.
# env: THEME, FONT, RTL, DENSITY, LANG (all optional, see _lib/open-demo.yaml)
appId: tech.kloos.kompound.catalog.maestro
---
- runFlow:
    file: ../../../_lib/open-demo.yaml
    env:
      DEMO: $ID
      THEME: \${THEME}
      FONT: \${FONT}
      RTL: \${RTL}
      DENSITY: \${DENSITY}
      LANG: \${LANG}
# TODO: the behaviour to check in every environment (taps, typing, selection), using the _lib helpers (preview-see, preview-tap, assert-disabled, ...)
- runFlow: ../../../_lib/assert-no-crash.yaml
EOF
{ HEADER interact interact "  - todo
"; echo "- runFlow: _core.yaml"; echo "# TODO: the rest of the behaviour of the demo"; } > "$DIR/10-interact.yaml"
{ HEADER states states "  - todo
"
  echo "# TODO: disabled, error, empty, loading, long text. Preset controls with CONTROLS (url-encoded, Name::value):"
  echo "#   - runFlow: { file: ../../../_lib/open-demo.yaml, env: { DEMO: $ID, CONTROLS: \"control=Enabled::false\" } }"
  echo "# Controls of this demo:"; echo "$CONTROLS"
  # Maestro parses every flow it discovers, even tagged todo ones, and a flow needs at least one command.
  echo "- runFlow:"; echo "    file: ../../../_lib/open-demo.yaml"; echo "    env:"; echo "      DEMO: $ID"; } > "$DIR/20-states.yaml"
{ HEADER environments env "  - todo
"
  for e in 'THEME: dark' 'RTL: "true"' 'FONT: "2.0"' 'DENSITY: compact' 'LANG: de'; do echo "- runFlow:"; echo "    file: _core.yaml"; echo "    env: { $e }"; done; } > "$DIR/30-env.yaml"
echo "Created $DIR (00-smoke runnable; 10, 20, 30 tagged todo)."
echo "Next: maestro/scripts/maestro.sh --component $ID --exclude-tags quarantine,wip   (todo flows run once you remove their todo tag)"
