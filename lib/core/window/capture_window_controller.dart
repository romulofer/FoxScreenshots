import 'dart:ui';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:screen_retriever/screen_retriever.dart';
import 'package:window_manager/window_manager.dart';

import '../desktop/session_type.dart';
import 'overlay_stacking.dart';
import 'window_focus.dart';
import 'window_geometry.dart';

/// Where the overlay window actually ended up, in logical pixels.
///
/// [window] is what the window manager granted, which is not always what was
/// asked for: some WMs clamp a window to a single monitor. Keeping both rects
/// lets the overlay draw the frozen frame 1:1 with the desktop underneath
/// instead of stretching it, however the request was honored.
///
/// [physicalWindow] is the same rect measured at the display server, in
/// screenshot pixels. When it is there it wins: on Linux [window] is whatever
/// the toolkit last asked for, which is stale as soon as the window manager
/// places the window somewhere else (see [WindowGeometryProbe]).
class OverlayPlacement {
  const OverlayPlacement({
    required this.window,
    required this.virtualScreen,
    this.physicalWindow,
  }) : fitsImage = false;

  /// An overlay that cannot be lined up with the desktop at all: under Wayland
  /// a window is given neither a position nor a way to ask for one, so the
  /// frozen frame is scaled to fit inside [window] instead. The selection is
  /// still exact — the drag is mapped back through the same scale.
  const OverlayPlacement.fitted({required this.window})
    : virtualScreen = window,
      physicalWindow = null,
      fitsImage = true;

  final Rect window;
  final Rect virtualScreen;
  final Rect? physicalWindow;
  final bool fitsImage;

  @override
  bool operator ==(Object other) =>
      other is OverlayPlacement &&
      other.window == window &&
      other.virtualScreen == virtualScreen &&
      other.physicalWindow == physicalWindow &&
      other.fitsImage == fitsImage;

  @override
  int get hashCode =>
      Object.hash(window, virtualScreen, physicalWindow, fitsImage);

  @override
  String toString() =>
      'OverlayPlacement(window: $window, '
      'virtualScreen: $virtualScreen, physicalWindow: $physicalWindow, '
      'fitsImage: $fitsImage)';
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
    WindowGeometryProbe? geometry,
    OverlayStacking? stacking,
    WindowFocuser? focuser,
    DesktopSession? session,
  }) : _geometry = geometry ?? defaultWindowGeometryProbe(),
       _stacking = stacking ?? defaultOverlayStacking(),
       _focuser = focuser ?? defaultWindowFocuser(),
       _session = session ?? currentDesktopSession();

  /// How long to wait after hiding the window before grabbing pixels. Too short
  /// and the disappearing window is still in the frame.
  final Duration repaintDelay;

  /// Measures where the overlay really landed, since the window manager is free
  /// to ignore the geometry it was handed.
  final WindowGeometryProbe _geometry;

  /// Keeps the overlay above windows that are themselves fullscreen.
  final OverlayStacking _stacking;

  /// Forces real OS keyboard focus onto the overlay (see [WindowFocuser]).
  final WindowFocuser _focuser;

  /// Wayland gives a window no say in where it goes and no way to ask, so the
  /// overlay is opened fullscreen there and the frozen frame is fitted into it
  /// instead of being laid over the desktop 1:1.
  final DesktopSession _session;

  bool get _placesWindows => _session != DesktopSession.wayland;

  /// How long to keep asking for the overlay's placement before giving up and
  /// using the window manager's own numbers.
  static const Duration _placementTimeout = Duration(milliseconds: 600);
  static const Duration _placementPoll = Duration(milliseconds: 30);

  /// How long to keep retrying [WindowManager.focus] before giving up.
  ///
  /// Matters mainly on Linux: showing a window that was fully hidden (closed
  /// to the tray) does not reliably win it keyboard focus on the first try —
  /// most window managers apply focus-stealing prevention to a window with no
  /// recent user-interaction timestamp, which is exactly what a reveal
  /// triggered by a global hotkey or a tray click has. Without a real OS-level
  /// focus grant, Esc cannot cancel the overlay: the Flutter [Focus] widget
  /// never sees the keypress because the display server never routed it here.
  static const Duration _focusTimeout = Duration(milliseconds: 500);
  static const Duration _focusPoll = Duration(milliseconds: 30);

  Rect? _restoreBounds;
  Rect _virtualScreen = Rect.zero;
  bool _wasVisible = true;

  /// Whether the overlay is currently holding the fullscreen state, which the
  /// teardown has to give back — the hub must not come back fullscreen.
  bool _fullScreen = false;

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

    // None of these six depend on each other's result, so they are fired
    // together instead of round-tripping the platform channel one at a time —
    // that serial chain was adding tens of ms to every reveal.
    await Future.wait([
      windowManager.setBackgroundColor(const Color(0xFF000000)),
      windowManager.setTitleBarStyle(
        TitleBarStyle.hidden,
        windowButtonVisibility: false,
      ),
      windowManager.setResizable(false),
      windowManager.setAlwaysOnTop(true),
      windowManager.setSkipTaskbar(true),
      // Minimum size would otherwise refuse a request smaller than the hub's.
      windowManager.setMinimumSize(const Size(1, 1)),
    ]);
    if (_placesWindows) {
      await windowManager.setBounds(_virtualScreen);
    } else {
      await windowManager.setFullScreen(true);
    }

    return _placesWindows
        ? OverlayPlacement(
            window: _virtualScreen,
            virtualScreen: _virtualScreen,
          )
        : OverlayPlacement.fitted(window: _viewBounds() ?? _virtualScreen);
  }

  @override
  Future<OverlayPlacement> revealOverlay() async {
    await windowManager.show();
    await _ensureFocused();
    await _focuser.forceFocus();
    if (!_placesWindows) {
      // Nothing to measure: the compositor chose the monitor and will not say
      // which. The frozen frame is fitted into whatever surface came back.
      return OverlayPlacement.fitted(window: _viewBounds() ?? _virtualScreen);
    }

    // Several window managers only honor geometry once the window is mapped,
    // so ask again and then measure what was actually granted.
    await windowManager.setBounds(_virtualScreen);
    // Sizing the window by hand leaves it below anything that is *itself*
    // fullscreen — a video or a slideshow on another monitor would sit in front
    // of the overlay. Being fullscreen too, across every monitor, is what puts
    // the overlay in the same layer.
    var fullScreen = await _stacking.spanAllMonitors();
    var measured = await _measurePlacement();
    if (fullScreen && !_covers(measured)) {
      // Granted, but kept to a single monitor: a selection could not cross
      // screens any more, so give the state back and place the window by hand.
      await _stacking.clear();
      await windowManager.setBounds(_virtualScreen);
      measured = await _measurePlacement();
      fullScreen = false;
    }
    _fullScreen = fullScreen;
    final granted = await windowManager.getBounds();
    // The fullscreen client message above re-maps the window on some window
    // managers, which can drop focus again — force it one more time now that
    // the overlay has settled into its final state.
    await _focuser.forceFocus();

    return OverlayPlacement(
      window: granted,
      virtualScreen: _virtualScreen,
      physicalWindow: measured,
    );
  }

  /// Calls [WindowManager.focus] until [WindowManager.isFocused] confirms it
  /// landed or [_focusTimeout] runs out (see field doc for why one call is
  /// not enough).
  Future<void> _ensureFocused() async {
    final deadline = DateTime.now().add(_focusTimeout);
    while (true) {
      await windowManager.focus();
      if (await windowManager.isFocused()) return;
      if (!DateTime.now().isBefore(deadline)) return;
      await Future<void>.delayed(_focusPoll);
    }
  }

  /// Whether [measured] is the whole virtual screen, give or take rounding.
  bool _covers(Rect? measured) {
    final view = PlatformDispatcher.instance.implicitView;
    if (measured == null || view == null) return false;

    final expected = _virtualScreen.size * view.devicePixelRatio;
    return (measured.width - expected.width).abs() <= 2 &&
        (measured.height - expected.height).abs() <= 2;
  }

  /// The overlay surface in logical pixels, straight from the engine — the one
  /// size that is right even when nobody will say where the window is.
  Rect? _viewBounds() {
    final view = PlatformDispatcher.instance.implicitView;
    if (view == null) return null;
    final size = view.physicalSize / view.devicePixelRatio;
    return Offset.zero & size;
  }

  /// Where the overlay ended up according to the display server, in physical
  /// pixels, or `null` when it cannot be confirmed.
  ///
  /// A window manager resizes and moves a freshly mapped window in stages, so a
  /// single reading is easily taken between the two — the overlay is already
  /// the size of the virtual screen while still sitting where the hub was. Two
  /// readings in a row have to agree before the placement counts as settled.
  Future<Rect?> _measurePlacement() async {
    final view = PlatformDispatcher.instance.implicitView;
    if (view == null) return null;

    final deadline = DateTime.now().add(_placementTimeout);
    Rect? previous;
    while (true) {
      final size = view.physicalSize;
      final origin = await _geometry.ownWindowOrigin(size);
      final measured = origin == null ? null : origin & size;
      if (measured != null && measured == previous) return measured;
      previous = measured;
      // Out of time: a recent reading still beats the stale one the window
      // manager reports, so hand back whatever the last one was.
      if (!DateTime.now().isBefore(deadline)) return measured;
      await Future<void>.delayed(_placementPoll);
    }
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

  /// Drops everything that keeps the overlay on top: the always-on-top and
  /// skip-taskbar flags, and the fullscreen state when it was granted. Leaving
  /// any of them on would bring the hub back pinned over the desktop.
  Future<void> _unpin() async {
    if (_fullScreen) {
      _fullScreen = false;
      await _stacking.clear();
    }
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
