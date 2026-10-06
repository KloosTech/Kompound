#!/usr/bin/env bash
# Prints "<demo-id> <category>" for every @KompoundDemo in the showcase sources (the same registry the catalog is built from), sorted.
# Used by coverage.sh and scaffold.sh.
cd "$(dirname "$0")/../.." || exit 2
grep -rl '@KompoundDemo' showcase/src/commonMain | while read -r f; do
  perl -0777 -ne 'while (/\@KompoundDemo\(\s*id = "([^"]+)".*?category = KompoundCategory\.(\w+)/gs) { print lc($2) . "\t" . $1 . "\n" }' "$f"
done | sort -k2 | awk -F'\t' '{print $2, $1}'
