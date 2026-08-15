#!/usr/bin/env bash
#
# Builds a Debian package from the Flutter Linux bundle.
#
# Usage:
#   tool/build_deb.sh [--no-flutter-build]
#
# Requirements:
#   - flutter (unless --no-flutter-build and a bundle already exists)
#   - dpkg-deb (from the dpkg package; present on every Debian/Ubuntu host)
#
# Layout: the Flutter runtime lands under /usr/lib/foxscreenshots (the binary
# finds its lib/ and data/ via its $ORIGIN rpath), with a /usr/bin symlink, a
# desktop entry and an icon so it shows up in the launcher.
set -euo pipefail

APP_ID="com.foxdevelops.foxscreenshots"
BIN_NAME="foxscreenshots"
APP_NAME="FoxScreenShots"
MAINTAINER="Rômulo Fernandes Evangelista <rfe89@hotmail.com>"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="$(grep -m1 '^version:' pubspec.yaml | sed -E 's/^version:\s*([0-9]+\.[0-9]+\.[0-9]+).*/\1/')"
ARCH="${ARCH:-amd64}"
BUNDLE="build/linux/x64/release/bundle"
STAGE="build/deb/${BIN_NAME}"
OUT="build/${APP_NAME}-${VERSION}-${ARCH}.deb"

if [[ "${1:-}" != "--no-flutter-build" ]]; then
  echo ">> flutter build linux --release"
  flutter build linux --release
fi

if [[ ! -x "$BUNDLE/$BIN_NAME" ]]; then
  echo "!! bundle not found at $BUNDLE — run without --no-flutter-build" >&2
  exit 1
fi

echo ">> staging package tree at $STAGE"
rm -rf "$STAGE"
mkdir -p "$STAGE/usr/lib/$BIN_NAME" "$STAGE/usr/bin" \
  "$STAGE/usr/share/applications" \
  "$STAGE/usr/share/icons/hicolor/256x256/apps" \
  "$STAGE/DEBIAN"

cp -a "$BUNDLE/." "$STAGE/usr/lib/$BIN_NAME/"
ln -sf "/usr/lib/$BIN_NAME/$BIN_NAME" "$STAGE/usr/bin/$BIN_NAME"
cp icon.png "$STAGE/usr/share/icons/hicolor/256x256/apps/${BIN_NAME}.png"

cat > "$STAGE/usr/share/applications/${BIN_NAME}.desktop" <<EOF
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

# Installed-Size is in kibibytes, per Debian policy.
INSTALLED_KB="$(du -ks "$STAGE/usr" | cut -f1)"

cat > "$STAGE/DEBIAN/control" <<EOF
Package: ${BIN_NAME}
Version: ${VERSION}
Section: graphics
Priority: optional
Architecture: ${ARCH}
Maintainer: ${MAINTAINER}
Installed-Size: ${INSTALLED_KB}
Depends: libgtk-3-0, libkeybinder-3.0-0, libayatana-appindicator3-1
Description: Cross-platform screenshot capture & light editing
 FoxScreenShots captures screenshots and offers light editing:
 instant and timer modes, region selection and basic annotation.
EOF

echo ">> packing $OUT"
dpkg-deb --root-owner-group --build "$STAGE" "$OUT"

echo ">> done: $OUT"
dpkg-deb --info "$OUT" | sed -n '1,12p'
