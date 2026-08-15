#!/usr/bin/env bash
#
# Builds a self-contained FoxScreenShots.AppImage from the Flutter Linux bundle.
#
# Usage:
#   tool/build_appimage.sh [--no-flutter-build]
#
# Requirements:
#   - flutter (unless --no-flutter-build and a bundle already exists)
#   - appimagetool on PATH, or set APPIMAGETOOL=/path/to/appimagetool
#
# The AppImage layout keeps the Flutter runtime files (executable, lib/, data/)
# together under usr/bin so the engine finds its assets relative to the binary.
set -euo pipefail

APP_ID="com.foxdevelops.foxscreenshots"
BIN_NAME="foxscreenshots"
APP_NAME="FoxScreenShots"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="$(grep -m1 '^version:' pubspec.yaml | sed -E 's/^version:\s*([0-9]+\.[0-9]+\.[0-9]+).*/\1/')"
BUNDLE="build/linux/x64/release/bundle"
APPDIR="build/appimage/${APP_NAME}.AppDir"
ARCH="${ARCH:-x86_64}"
OUT="build/${APP_NAME}-${VERSION}-${ARCH}.AppImage"

if [[ "${1:-}" != "--no-flutter-build" ]]; then
  echo ">> flutter build linux --release"
  flutter build linux --release
fi

if [[ ! -x "$BUNDLE/$BIN_NAME" ]]; then
  echo "!! bundle not found at $BUNDLE — run without --no-flutter-build" >&2
  exit 1
fi

echo ">> staging AppDir at $APPDIR"
rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/bin"
cp -a "$BUNDLE/." "$APPDIR/usr/bin/"

# Icon at AppDir root (name must match the desktop Icon= key).
cp icon.png "$APPDIR/${BIN_NAME}.png"
mkdir -p "$APPDIR/usr/share/icons/hicolor/256x256/apps"
cp icon.png "$APPDIR/usr/share/icons/hicolor/256x256/apps/${BIN_NAME}.png"

cat > "$APPDIR/${BIN_NAME}.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=${APP_NAME}
Comment=Cross-platform screenshot capture & light editing
Exec=${BIN_NAME}
Icon=${BIN_NAME}
Categories=Graphics;Utility;
Terminal=false
StartupWMClass=${APP_ID}
EOF

cat > "$APPDIR/AppRun" <<'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
export LD_LIBRARY_PATH="$HERE/usr/bin/lib:${LD_LIBRARY_PATH}"
exec "$HERE/usr/bin/foxscreenshots" "$@"
EOF
chmod +x "$APPDIR/AppRun"

APPIMAGETOOL="${APPIMAGETOOL:-appimagetool}"
echo ">> packing $OUT"
# ARCH env is read by appimagetool to name the runtime; --appimage-extract-and-run
# avoids needing FUSE on the build host.
ARCH="$ARCH" "$APPIMAGETOOL" --appimage-extract-and-run "$APPDIR" "$OUT"

echo ">> done: $OUT"
