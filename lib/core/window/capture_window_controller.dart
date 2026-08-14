import 'dart:ui';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:screen_retriever/screen_retriever.dart';
import 'package:window_manager/window_manager.dart';

/// Where the overlay window actually ended up, in logical pixels.
///
/// [window] is what the window manager granted, which is not always what was
/// asked for: some WMs clamp a window to a single monitor. Keeping both rects
/// lets the overlay draw the frozen frame 1:1 with the desktop underneath
/// instead of stretching it, however the request was honored.
class OverlayPlacement {
  const OverlayPlacement({required this.window, required this.virtualScreen});

  final Rect window;
  final Rect virtualScreen;

  @override
  bool operator ==(Object other) =>
      other is OverlayPlacement &&
      other.window == window &&
      other.virtualScreen == virtualScreen;

  @override
  int get hashCode => Object.hash(window, virtualScreen);

  @override
  String toString() =>
      'OverlayPlacement(window: $window, '
      'virtualScreen: $virtualScreen)';
}

/// Window moves the capture flows need (SPEC §2.1).
///
/// Behind an interface so the flows stay unit-testable: real window calls need
/// a desktop embedder, which `flutter test` does not have.
abstract interface class CaptureWindowController {
  /// Hides the hub window so it does not end up inside the screenshot, and
  /// waits long enough for the compositor to actually repaint without it.
  Future<void> hideForCapture();

  /// Turns the app window into a borderless, always-on-top surface covering
  /// the whole virtual screen, while staying hidden. The overlay route is
  /// pushed against the returned placement before anything is shown, so the
  /// hub is never seen stretched across the monitors.
  Future<OverlayPlacement> enterOverlay();

  /// Shows the overlay and reports where it truly landed.
  Future<OverlayPlacement> revealOverlay();

  /// Restores the window to how it was before [enterOverlay].
  Future<void> leaveOverlay();

  /// Brings the hub window back after a capture.
  Future<void> restore();
}

/// `window_manager` + `screen_retriever` implementation.
class WindowManagerCaptureWindow implements CaptureWindowController {
  WindowManagerCaptureWindow({
    this.repaintDelay = const Duration(milliseconds: 220),
  });

  /// How long to wait after hiding the window before grabbing pixels. Too short
  /// and the disappearing window is still in the frame.
  final Duration repaintDelay;

  Rect? _restoreBounds;
  Rect _virtualScreen = Rect.zero;
  bool _wasVisible = true;

  @override
  Future<void> hideForCapture() async {
    _wasVisible = await windowManager.isVisible();
    if (_wasVisible) {
      _restoreBounds ??= await windowManager.getBounds();
      await windowManager.hide();
      await Future<void>.delayed(repaintDelay);
    }
  }

  @override
  Future<OverlayPlacement> enterOverlay() async {
    _restoreBounds ??= await windowManager.getBounds();
    _virtualScreen = await virtualScreenBounds();

    await windowManager.setBackgroundColor(const Color(0xFF000000));
    await windowManager.setTitleBarStyle(
      TitleBarStyle.hidden,
      windowButtonVisibility: false,
    );
    await windowManager.setResizable(false);
    await windowManager.setAlwaysOnTop(true);
    await windowManager.setSkipTaskbar(true);
    // Minimum size would otherwise refuse a request smaller than the hub's.
    await windowManager.setMinimumSize(const Size(1, 1));
    await windowManager.setBounds(_virtualScreen);

    return OverlayPlacement(
      window: _virtualScreen,
      virtualScreen: _virtualScreen,
    );
  }

  @override
  Future<OverlayPlacement> revealOverlay() async {
    await windowManager.show();
    await windowManager.focus();
    // Several window managers only honor geometry once the window is mapped,
    // so ask again and then measure what was actually granted.
    await windowManager.setBounds(_virtualScreen);
    final granted = await windowManager.getBounds();

    return OverlayPlacement(window: granted, virtualScreen: _virtualScreen);
  }

  @override
  Future<void> leaveOverlay() async {
    // Un-pin first: these two are what keep the window above everything and
    // out of the alt-tab list, so they must be undone even if a later call in
    // this teardown throws.
    await _unpin();
    await windowManager.setBackgroundColor(const Color(0xFF000000));
    await windowManager.setTitleBarStyle(TitleBarStyle.normal);
    await windowManager.setResizable(true);
    await windowManager.setMinimumSize(const Size(640, 480));
    final bounds = _restoreBounds;
    if (bounds != null) await windowManager.setBounds(bounds);
    await windowManager.hide();
  }

  @override
  Future<void> restore() async {
    // Belt and braces: a teardown that failed halfway must not leave the hub
    // pinned on top of the user's desktop and unreachable by alt-tab.
    await _unpin();
    final bounds = _restoreBounds;
    if (bounds != null) await windowManager.setBounds(bounds);
    _restoreBounds = null;
    if (_wasVisible) {
      await windowManager.show();
      await windowManager.focus();
    }
  }

  /// Drops the always-on-top and skip-taskbar flags the overlay sets.
  Future<void> _unpin() async {
    await windowManager.setAlwaysOnTop(false);
    await windowManager.setSkipTaskbar(false);
  }

  /// Union of every display, in logical pixels — the area the overlay must
  /// cover so a selection can cross monitors (SPEC §2.1).
  Future<Rect> virtualScreenBounds() async {
    final displays = await screenRetriever.getAllDisplays();
    if (displays.isEmpty) return const Rect.fromLTWH(0, 0, 1280, 720);

    var bounds = _boundsOf(displays.first);
    for (final display in displays.skip(1)) {
      bounds = bounds.expandToInclude(_boundsOf(display));
    }
    return bounds;
  }

  Rect _boundsOf(Display display) {
    final origin = display.visiblePosition ?? Offset.zero;
    return Rect.fromLTWH(
      origin.dx,
      origin.dy,
      display.size.width,
      display.size.height,
    );
  }
}

final captureWindowControllerProvider = Provider<CaptureWindowController>((
  ref,
) {
  return WindowManagerCaptureWindow();
});
