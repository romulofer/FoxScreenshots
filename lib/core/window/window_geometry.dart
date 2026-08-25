import 'dart:io';
import 'dart:ui';

import 'x11_window_geometry.dart';

/// Asks the display server where the app's own window really is.
///
/// `window_manager` answers [Rect] questions from the values it last *pushed*
/// to the toolkit, which on Linux go stale the moment the window manager
/// overrides them — and it does exactly that for a window as big as the whole
/// virtual screen. The overlay maps the frozen screenshot onto the desktop from
/// that origin, so a stale answer paints one monitor's pixels over another.
/// Measuring at the source keeps the two in step (SPEC §2.1).
abstract interface class WindowGeometryProbe {
  /// Polls for the app's own window whose client area measures [physicalSize]
  /// — the surface the engine is rendering into — until two readings in a row
  /// agree (settled) or [timeout] runs out, sleeping [pollInterval] between
  /// tries. `null` when this platform cannot say, or no window of that size
  /// ever showed up; callers then fall back to the window manager's own
  /// numbers.
  ///
  /// Implementations that need a native connection open it once for the
  /// whole poll instead of once per try — reconnecting on every 30ms tick is
  /// what made overlay reveal feel sluggish.
  Future<Offset?> settledOrigin(
    Size physicalSize, {
    required Duration timeout,
    required Duration pollInterval,
  });
}

/// Used wherever there is no native probe yet (Windows, macOS, tests).
class UnknownWindowGeometry implements WindowGeometryProbe {
  const UnknownWindowGeometry();

  @override
  Future<Offset?> settledOrigin(
    Size physicalSize, {
    required Duration timeout,
    required Duration pollInterval,
  }) async => null;
}

/// Picks the probe for the current session — X11 through Xlib, nothing
/// elsewhere. Mirrors `defaultScreenCaptureService`.
WindowGeometryProbe defaultWindowGeometryProbe({
  Map<String, String>? environment,
}) {
  final env = environment ?? Platform.environment;
  if (!Platform.isLinux) return const UnknownWindowGeometry();

  final sessionType = env['XDG_SESSION_TYPE']?.toLowerCase();
  final isWayland =
      sessionType == 'wayland' || (env['WAYLAND_DISPLAY']?.isNotEmpty ?? false);
  // Under Wayland a client cannot know its own position at all, so there is
  // nothing better to offer than the toolkit's answer.
  if (isWayland) return const UnknownWindowGeometry();

  return const X11WindowGeometry();
}
