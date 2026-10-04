#!/usr/bin/env bash
# Publish the Kompound libraries to Maven local, Maven Central, or both.
#
#   scripts/publish.sh local   [version]   ~/.m2 only (default version: the one in gradle.properties)
#   scripts/publish.sh central <version>   upload to Maven Central, release by hand in the portal
#   scripts/publish.sh release <version>   upload and release automatically (cannot be undone)
#   scripts/publish.sh both    <version>   local first, then central
#
# Central needs credentials in the environment (see docs/RELEASING.md):
#   ORG_GRADLE_PROJECT_mavenCentralUsername / _mavenCentralPassword
#   ORG_GRADLE_PROJECT_signingInMemoryKey / _signingInMemoryKeyId / _signingInMemoryKeyPassword
# If ~/.kompound/publish.env exists it is sourced first (lines like `export ORG_GRADLE_PROJECT_mavenCentralUsername=...`).
set -euo pipefail

target="${1:-}"
version="${2:-}"
case "$target" in
  local|central|release|both) ;;
  *) sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac

if [ "$target" != "local" ] && [ -z "$version" ]; then
  echo "A version is required for $target, for example: scripts/publish.sh $target 0.1.0-alpha03" >&2
  exit 2
fi
if [ "$target" = "release" ]; then
  read -r -p "Release $version to Maven Central now? Versions are immutable once released. Type 'release' to continue: " answer
  [ "$answer" = "release" ] || { echo "Cancelled."; exit 1; }
fi

[ -f "$HOME/.kompound/publish.env" ] && . "$HOME/.kompound/publish.env"

cd "$(dirname "$0")/.."
args=(publishLibraries "-PpublishTo=$target")
[ -n "$version" ] && args+=("-PVERSION_NAME=$version")
exec ./gradlew "${args[@]}"
