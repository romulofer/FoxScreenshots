import 'dart:io';

import '../desktop/session_type.dart';
import 'x11_overlay_stacking.dart';

/// Puts the capture overlay above everything else on screen.
///
/// Always-on-top is not enough on its own: a window manager keeps fullscreen
/// windows in a layer of their own, above the "above" layer, so a video or a
/// presentation running fullscreen on another monitor stays in front of the
/// overlay and the user drags a selection over a screen they cannot see. The
/// way out is to be fullscreen too — across every monitor, which X11 spells
/// `_NET_WM_FULLSCREEN_MONITORS`.
abstract interface class OverlayStacking {
  /// Asks the window manager to make the app's window fullscreen across every
  /// monitor. Returns `false` when this session cannot do it, in which case the
  /// caller falls back to sizing the window by hand.
  Future<bool> spanAllMonitors();

  /// Drops the fullscreen state again. Safe to call when it was never set.
  Future<void> clear();
}

/// Used where there is nothing to ask (Windows, macOS, Wayland, tests).
class NoOverlayStacking implements OverlayStacking {
  const NoOverlayStacking();

  @override
  Future<bool> spanAllMonitors() async => false;

  @override
  Future<void> clear() async {}
}

/// Picks the implementation for the current session. Mirrors
/// `defaultWindowGeometryProbe`.
OverlayStacking defaultOverlayStacking({Map<String, String>? environment}) {
  if (!Platform.isLinux) return const NoOverlayStacking();
  return currentDesktopSession(environment: environment) == DesktopSession.x11
      ? const X11OverlayStacking()
      // Wayland has its own fullscreen path through the toolkit, and no
      // window there may span monitors anyway.
      : const NoOverlayStacking();
}
