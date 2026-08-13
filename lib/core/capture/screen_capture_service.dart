import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/capture_region.dart';
import '../../models/capture_result.dart';
import 'x11_screen_capture_service.dart';

/// How a capture is framed.
enum CaptureMode { instant, timer, fullScreen, activeWindow }

/// Why a capture could not be taken.
///
/// A closed set rather than free text so the UI can localize each case
/// (SPEC §2.6: no hardcoded strings).
enum CaptureFailure {
  /// The session is Wayland, where the X backend cannot see the desktop.
  waylandUnsupported,

  /// No backend is written for this operating system yet.
  platformUnsupported,

  /// The display server refused the connection or the grab.
  displayUnavailable,

  /// Window capture was asked for while nothing was focused.
  noActiveWindow,

  /// A capture was triggered before the app window existed.
  windowNotReady,
}

/// A capture that could not be taken.
///
/// Backends translate platform failures into this so the UI has a single type
/// to catch; [details] is developer-facing context and never contains screen
/// content (SPEC §7).
class CaptureException implements Exception {
  const CaptureException(this.failure, {this.details});

  final CaptureFailure failure;
  final String? details;

  @override
  String toString() =>
      'CaptureException(${failure.name})${details == null ? '' : ': $details'}';
}

/// Platform-agnostic screen capture contract (SPEC §2.1, §4).
///
/// The UI and editor depend only on this interface; per-OS implementations
/// (X11/Wayland/portal on Linux, GDI/DXGI on Windows, CG on macOS) are selected
/// at runtime. The Linux backend is deferred to PLAN.
abstract interface class ScreenCaptureService {
  /// Grabs a full-resolution snapshot of every monitor, composited into one
  /// image. Used as the frozen overlay backdrop for instant mode.
  Future<CaptureResult> grabFullVirtualScreen();

  /// Grabs [region] of the virtual screen as it looks *now*. Used by timer mode
  /// (the screen has moved on since the freeze) and by window capture.
  Future<CaptureResult> grabRegion(CaptureRegion region);

  /// Geometry of the currently focused top-level window, in virtual-screen
  /// pixels, or `null` when the platform cannot report one (nothing focused, or
  /// only the desktop). Callers pass it to [grabRegion].
  Future<CaptureRegion?> activeWindowRegion();
}

/// Fallback for platforms whose backend is not written yet. Constructs fine so
/// the app boots; throws only when a capture is actually attempted.
class UnsupportedScreenCaptureService implements ScreenCaptureService {
  const UnsupportedScreenCaptureService(this.failure, {this.details});

  /// Why capture is unavailable here.
  final CaptureFailure failure;
  final String? details;

  Never _unsupported() => throw CaptureException(failure, details: details);

  @override
  Future<CaptureResult> grabFullVirtualScreen() async => _unsupported();

  @override
  Future<CaptureResult> grabRegion(CaptureRegion region) async =>
      _unsupported();

  @override
  Future<CaptureRegion?> activeWindowRegion() async => _unsupported();
}

/// Picks the backend for the current session.
///
/// Linux runs on X11 through Xlib. A Wayland session is refused rather than
/// served a black frame: the X root window there is not the real desktop, so
/// the grab would appear to succeed. A Wayland backend (xdg-desktop-portal
/// `ScreenCast`) is still to come.
ScreenCaptureService defaultScreenCaptureService({
  Map<String, String>? environment,
}) {
  final env = environment ?? Platform.environment;
  if (Platform.isLinux) {
    final sessionType = env['XDG_SESSION_TYPE']?.toLowerCase();
    final isWayland =
        sessionType == 'wayland' ||
        (env['WAYLAND_DISPLAY']?.isNotEmpty ?? false);
    if (isWayland) {
      return const UnsupportedScreenCaptureService(
        CaptureFailure.waylandUnsupported,
        details: 'Wayland session: use xdg-desktop-portal (not implemented)',
      );
    }
    return const X11ScreenCaptureService();
  }
  return UnsupportedScreenCaptureService(
    CaptureFailure.platformUnsupported,
    details: 'No backend for ${Platform.operatingSystem}',
  );
}

/// Selects the capture backend for the current OS. Overridden in tests with a
/// fake (SPEC §6).
final screenCaptureServiceProvider = Provider<ScreenCaptureService>((ref) {
  return defaultScreenCaptureService();
});
