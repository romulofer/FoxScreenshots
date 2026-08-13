# FoxScreenShots — Specification

> Living document. Source of truth for scope, architecture, and boundaries.
> Changes to scope or the color palette require sign-off from the author.

## 1. Objective

Cross-platform desktop app (Windows, Linux, macOS) built in Flutter to
**capture** and do **basic editing** of screenshots.

**Target user:** anyone on desktop who needs quick, good-looking screenshots
with light annotation — bug reports, documentation, tutorials, support.

**Non-goals:** video/GIF recording, cloud sync, image library management,
heavy photo editing (layers, filters), OCR. May be revisited later.

### Operating modes

1. **Instant (primary)** — user triggers via global hotkey; all screens
   **freeze** (a full-resolution snapshot is shown as a fullscreen always-on-top
   overlay) and the user drags a selection rectangle of the region to crop.
2. **Timer** — user selects the region *first*, then the shot is taken after a
   configurable delay (seconds). Lets the user open menus, tooltips, hover
   states, etc. before capture.

### Runtime & trigger — *decided*

- Runs in the **system tray** in the background (`tray_manager`).
- **Left-click on the tray icon opens the main window** (a Shutter-like hub,
  see §2.7). Right-click opens a context menu (both modes, Settings, Quit).
- **Global hotkey** triggers capture (`hotkey_manager`), default `PrintScreen`,
  rebindable in Settings — works without opening the window.
- Closing the main window hides it to the tray (app keeps running); Quit is
  explicit (tray menu / app menu).

## 2. Features & acceptance criteria

### 2.1 Capture — *decided*
- Freeze-frame overlay across **all monitors**; selection with live dimensions
  and a magnifier for pixel-precise edges.
- Timer mode: pick region, choose delay, countdown, then capture.
- Cross-platform capture backend abstracted behind one service; native per-OS
  (see §4). **Linux backend research deferred to PLAN** (X11 vs Wayland /
  xdg-desktop-portal).

**Accept:** both modes produce a correct cropped bitmap on Linux (dev env);
Windows/macOS behind the same interface with platform tests.

### 2.2 Editor — *decided (all of the below)*
- **Basic:** crop, arrow, rectangle/ellipse, text.
- **Highlight** (translucent marker over a region).
- **Blur / pixelate** (redact sensitive data).
- **Freehand pen** + **numbered step markers** (1, 2, 3…).
- Undo/redo. Color + stroke-width picker. Non-destructive annotation layer
  composited on export.

**Accept:** each tool has a unit test for its model/geometry and a widget test
for its interaction; export composites annotations onto the base image losslessly.

### 2.3 Output — *decided*
- **Copy to clipboard** (image) **and** save to a **file** (PNG).
- Save via dialog; remember last folder. Auto-save folder + filename pattern is
  a Settings option (timestamped) — nice-to-have, not blocking.

**Accept:** clipboard receives a valid PNG on all three OSes; saved file opens
in a standard viewer.

### 2.4 Toolbar / menus
- App menu bar: **Arquivo / File**, **Configurações / Settings**, plus Edit and
  Help. Tray menu mirrors core actions.

### 2.5 Main window — *Shutter-like hub — decided*
Left-clicking the tray icon opens a hub window modeled on the **Shutter** app:

- **Capture toolbar** (top): Instant (region), Timer, Full screen, Active
  window. Each starts the corresponding flow.
- **Session gallery** (center): thumbnails of screenshots taken this session,
  most-recent first. Select a thumbnail to preview.
- **Per-item actions:** Edit (opens the editor §2.2), Copy, Save, Delete,
  Reveal in file manager.
- **Footer / toolbar:** Settings, and capture-delay + mode quick-toggles.
- Session list is in-memory + optionally persisted to the output folder; it is
  **not** a permanent library (see non-goals).

**Accept:** tray left-click shows the window; a new capture appears as a
thumbnail; Edit/Copy/Save/Delete act on the selected item; closing hides to tray.

### 2.6 Localization — *required*
- **pt-BR (primary)** and **en-US**, `flutter_localizations` + `intl` ARB files.
- **pt-BR is the default/fallback locale** (used when the OS locale is neither
  pt-BR nor en-US, and as the base ARB `template-arb-file`). Locale follows OS
  when it matches a supported one, overridable in Settings. No hardcoded strings.

**Accept:** every user-facing string resolves from ARB; switching locale updates
the UI live; both locales complete (no missing keys).

### 2.7 Theming — *required*
- Light/dark schemes **ported from `~/development/mobile/foxdevelops`**.
- Follows OS theme by default, overridable in Settings.

Palette (from foxdevelops `values/colors.xml` + `values-night`):

| Token          | Light     | Dark      |
|----------------|-----------|-----------|
| app_background | `#FFFCF9` | `#000000` |
| surface        | `#F2EBE5` | `#241C18` |
| text_primary   | `#1B1411` | `#FFFFFF` |
| text_secondary | `#6A5D56` | `#B8ADA6` |
| brand          | `#A63F10` | `#D9531E` |
| accent         | `#A65A00` | `#FFB74D` |

Note (from source app): brand orange `#D9531E` reads at 3.6:1 on white — under
4.5:1 — so the **light** brand is the darker `#A63F10`. Keep this; do not use
`#D9531E` on light backgrounds for text.

## 3. Commands

```bash
flutter pub get                     # install deps
flutter gen-l10n                    # generate localizations from ARB
flutter run -d linux                # run (dev env); -d windows / -d macos
flutter analyze                     # static analysis / lints
dart format .                       # format
flutter test                        # unit + widget tests
flutter test integration_test       # e2e (needs a display / xvfb on CI)
flutter build linux                 # release build; also windows / macos
```

## 4. Project structure

```
lib/
  main.dart                 # bootstrap: window_manager, tray, hotkeys, run app
  app.dart                  # MaterialApp, theme, locale wiring
  core/
    theme/                  # app_colors.dart, app_theme.dart (light/dark)
    l10n/                   # app_*.arb + generated
    capture/                # screen_capture_service.dart (interface) + impls
    hotkey/                 # hotkey_service.dart
    tray/                   # tray_service.dart
    storage/
      settings_service.dart # shared_preferences wrapper
      output_service.dart   # save-to-file
      clipboard_service.dart# image to clipboard
    utils/
  features/
    home/                   # Shutter-like hub window (§2.5)
      home_screen.dart      # capture toolbar + session gallery
      session_controller.dart# in-memory list of captures this session
      widgets/              # thumbnail_tile, capture_toolbar
    capture/
      selection_overlay.dart# fullscreen frozen overlay + rubber-band select
      capture_controller.dart# instant, timer, fullscreen, active-window
      timer_capture.dart
      widgets/              # magnifier, dimension_badge, dimmer
    editor/
      editor_screen.dart
      tools/                # arrow, rect, text, highlight, blur, pen, number
      models/               # annotation models (immutable)
      painters/             # CustomPainter per tool + compositor
      editor_controller.dart
    settings/
      settings_screen.dart
      settings_controller.dart
    menu/                   # app menu bar + tray menu builders
  models/                   # shared value types (capture_result, region…)
  widgets/                  # shared UI atoms
test/                       # unit + widget
integration_test/           # e2e flows
```

- **State management:** Riverpod (`flutter_riverpod`) — testable, modular,
  no BuildContext coupling in logic.
- **Capture abstraction:** `ScreenCaptureService` interface with per-OS
  implementations selected at runtime; UI/editor never touch platform code.

### Key dependencies (proposed, confirm in PLAN)
`window_manager`, `tray_manager`, `hotkey_manager`, `screen_retriever`,
`flutter_riverpod`, `intl` + `flutter_localizations`, `shared_preferences`,
`image` (raster ops: crop/blur/pixelate/encode), `super_clipboard` (image
clipboard), `file_selector` + `path_provider`. Linux capture backend TBD in PLAN.

## 5. Code style

- **Effective Dart** + `flutter_lints` (or `very_good_analysis`); zero analyzer
  warnings on merge.
- `dart format` (default 80 col) enforced.
- Immutable models; `const` where possible; no logic in `build()`.
- One primary type per file; `snake_case.dart` filenames; `UpperCamelCase` types.
- No hardcoded strings (i18n) and no hardcoded colors (theme tokens).
- Doc comments on public service APIs. Code/identifiers in English; user-facing
  copy localized.

## 6. Testing strategy

- **Unit** — services (settings, output, clipboard, capture via mock), editor
  tool geometry/models, image compositor (crop/blur correctness).
- **Widget** — selection overlay interaction, each editor tool, settings screen,
  locale + theme switching.
- **Golden** — light/dark theme snapshots of key screens.
- **e2e (`integration_test`)** — timer flow and instant flow with a **mocked
  capture service** (no real display dependency); real-capture smoke test gated
  to a headed/xvfb runner.
- **CI:** `flutter analyze` + `flutter test` on every push; e2e under xvfb.
- **Security review each release** (§7).

## 7. Boundaries

### Always
- **Single author on commits:** `Rômulo Fernandes Evangelista`
  (`rfe89@hotmail.com`). No `Co-Authored-By` trailers on this repo.
- Local-only, offline. Screenshots may contain sensitive data — process and
  store **only on the user's machine**.
- Both locales (pt-BR, en-US) kept complete. Theme tokens only, no raw colors.
- Modular, reusable, well-organized code (per acceptance criteria).
- Security review before each release.

### Ask first
- Adding a heavy/native dependency, or changing the capture backend approach.
- Changing the color palette or brand identity.
- Any feature that touches the filesystem outside the chosen output folder.

### Never
- **No network calls, telemetry, analytics, or crash-reporting that ships image
  or screen content off-device.** No auto-upload.
- No secrets committed. No bundled binaries of unknown provenance.
- Don't break offline operation.

---

*Open source. License: MIT. Repo scaffolding (README, LICENSE, .gitignore, git)
initialized alongside this spec.*
