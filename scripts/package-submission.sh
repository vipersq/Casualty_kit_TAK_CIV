#!/usr/bin/env bash
# Builds the source zip for the tak.gov third-party pipeline (https://tak.gov/user_builds).
# Usage: scripts/package-submission.sh <ATAK version, e.g. 5.8.0>
# Output: dist/casualtykit-takgov-<version>.zip with a single root folder "casualtykit".
set -euo pipefail
VERSION="${1:?usage: $0 <ATAK version, e.g. 5.8.0>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
DEST="$WORK/casualtykit"
mkdir -p "$DEST" "$ROOT/dist"
cd "$ROOT"
# Tracked files only, so local.properties, build output and keys can never leak into the zip.
git ls-files -z -- app build.gradle settings.gradle gradle.properties gradlew gradlew.bat gradle \
    core/src/main docs/user_manual docs/COT-SPEC.md \
  | while IFS= read -r -d '' f; do mkdir -p "$DEST/$(dirname "$f")"; cp "$f" "$DEST/$f"; done
cp docs/TAKGOV_README.md "$DEST/README.md"
sed -i -E "s/ext\.ATAK_VERSION = \"[^\"]+\"/ext.ATAK_VERSION = \"$VERSION\"/" "$DEST/app/build.gradle"
grep -q "ext.ATAK_VERSION = \"$VERSION\"" "$DEST/app/build.gradle" || { echo "could not set ATAK_VERSION" >&2; exit 1; }
chmod +x "$DEST/gradlew"
OUT="$ROOT/dist/casualtykit-takgov-$VERSION.zip"
rm -f "$OUT"
(cd "$WORK" && zip -qr "$OUT" casualtykit)
echo "wrote $OUT"
