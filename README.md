<div align="center">

# 🦊 FoxScreenShots

**Cross-platform screenshot capture & light editing — Windows · Linux · macOS**

Built with Flutter. Open source under the [MIT License](LICENSE).

</div>

---

## What it does

A desktop tool to capture and quickly annotate screenshots.

- **Instant mode** — hit a global hotkey; the screen freezes and you drag a
  selection to crop.
- **Timer mode** — pick the region first, then capture after a delay, so you can
  open menus, tooltips, and hover states before the shot is taken.
- **Editor** — crop, arrow, rectangle/ellipse, text, highlight, blur/pixelate,
  freehand pen, and numbered step markers. Undo/redo.
- **Output** — copy to clipboard **and** save as PNG.
- **Runs in the system tray** with a rebindable global hotkey.
- **Localized** in Portuguese (pt-BR) and English (en-US).
- **Light & dark themes.**

> 🔒 **Privacy:** everything runs locally. FoxScreenShots makes **no network
> calls** and never uploads your screen content anywhere.

## Status

🚧 Early development. See [`SPEC.md`](SPEC.md) for the full specification and
[`PLAN.md`](PLAN.md) for the build plan.

## Getting started

Requires the [Flutter SDK](https://docs.flutter.dev/get-started/install) with
desktop support enabled.

### Linux system packages

Capture talks to X11 directly, and the tray and global hotkey come from GTK
libraries, so a few system packages are needed. Debian/Ubuntu/Mint:

```bash
# to build
sudo apt install clang cmake ninja-build pkg-config libgtk-3-dev \
                 libkeybinder-3.0-dev libayatana-appindicator3-dev

# to run (usually already installed)
sudo apt install libx11-6 libkeybinder-3.0-0 libayatana-appindicator3-1
```

| Library | Used for | Without it |
|---|---|---|
| `libX11.so.6` | screen capture | capture is unavailable |
| `libkeybinder-3.0.so.0` | global hotkey | hotkey does not fire |
| `libayatana-appindicator3.so.1` | tray icon | no tray icon |

The app checks all of these at startup and shows a banner naming whatever is
missing, so users are never left with a button that silently does nothing.

**Wayland** is not supported yet — capture needs an X11 session for now
(a `xdg-desktop-portal` backend is planned).

```bash
# enable desktop (once)
flutter config --enable-linux-desktop --enable-windows-desktop --enable-macos-desktop

# install deps and run
flutter pub get
flutter gen-l10n
flutter run -d linux        # or: -d windows / -d macos
```

### Common commands

```bash
flutter analyze                 # lint / static analysis
dart format .                   # format
flutter test                    # unit + widget tests
flutter test integration_test   # e2e (needs a display; xvfb on CI)
flutter build linux             # release build (also windows / macos)
```

## Project layout

See [`SPEC.md` §4](SPEC.md). In short: `lib/core/` holds platform services
(capture, tray, hotkey, storage, theme, l10n); `lib/features/` holds the capture
overlay, editor, and settings; tests live in `test/` and `integration_test/`.

## Contributing

Issues and PRs welcome. Please keep both locales complete, use theme tokens (no
raw colors), and add tests for new logic. See the boundaries in
[`SPEC.md` §7](SPEC.md).

## License

[MIT](LICENSE) © 2026 Rômulo Fernandes Evangelista
