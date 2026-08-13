import 'dart:ui';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:screen_retriever/screen_retriever.dart';
import 'package:window_manager/window_manager.dart';

/// Window moves the capture flows need (SPEC §2.1).
///
/// Behind an interface so the flows stay unit-testable: real window calls need
/// a desktop embedder, which `flutter test` does not have.
abstract interface class CaptureWindowController {
  /// Hides the hub window so it does not end up inside the screenshot, and
  /// waits long enough for the compositor to actually repaint without it.
  Future<void> hideForCapture();

  /// Turns the app window into a borderless, always-on-top surface covering the
  /// whole virtual screen — the frozen-frame overlay.
  Future<void> enterOverlay();

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
  Future<void> enterOverlay() async {
    _restoreBounds ??= await windowManager.getBounds();
    await windowManager.setTitleBarStyle(
      TitleBarStyle.hidden,
      windowButtonVisibility: false,
    );
    await windowManager.setResizable(false);
    await windowManager.setAlwaysOnTop(true);
    await windowManager.setSkipTaskbar(true);
    await windowManager.setBounds(await virtualScreenBounds());
    await windowManager.show();
    await windowManager.focus();
  }

  @override
  Future<void> leaveOverlay() async {
    await windowManager.setAlwaysOnTop(false);
    await windowManager.setSkipTaskbar(false);
    await windowManager.setTitleBarStyle(TitleBarStyle.normal);
    await windowManager.setResizable(true);
    final bounds = _restoreBounds;
    if (bounds != null) await windowManager.setBounds(bounds);
    await windowManager.hide();
  }

  @override
  Future<void> restore() async {
    final bounds = _restoreBounds;
    if (bounds != null) await windowManager.setBounds(bounds);
    _restoreBounds = null;
    if (_wasVisible) {
      await windowManager.show();
      await windowManager.focus();
    }
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
