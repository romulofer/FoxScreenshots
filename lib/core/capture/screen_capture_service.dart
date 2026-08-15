import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/capture_region.dart';
import '../../models/capture_result.dart';
import '../desktop/session_type.dart';
import 'portal/screenshot_portal.dart';
import 'portal_screen_capture_service.dart';
import 'windows_screen_capture_service.dart';
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

  /// The desktop refused the screenshot, or the user dismissed its permission
  /// dialog (Wayland, through xdg-desktop-portal).
  portalDenied,

  /// No screenshot portal answered — nothing can capture on this session.
  portalUnavailable,

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

  /// Size of the whole virtual screen in physical pixels, without grabbing it.
  /// Timer mode needs the dimensions to map a selection made over the *live*
  /// desktop, where there is no frozen frame to measure.
  Future<({int width, int height})> virtualScreenSize();
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

  @override
  Future<({int width, int height})> virtualScreenSize() async => _unsupported();
}

/// Picks the backend for the current session.
///
/// Windows copies the desktop through GDI (`BitBlt`), which needs no permission
/// and no display-server negotiation. On Linux, X11 reads the root window
/// through Xlib, which is instant and needs no permission either; Wayland forbids
/// that outright, so it goes through xdg-desktop-portal instead — slower, and
/// gated behind the desktop's own confirmation, but the only route a client is
/// given.
ScreenCaptureService defaultScreenCaptureService({
  Map<String, String>? environment,
}) {
  if (Platform.isWindows) return const WindowsScreenCaptureService();

  return switch (currentDesktopSession(environment: environment)) {
    DesktopSession.x11 => const X11ScreenCaptureService(),
    DesktopSession.wayland => PortalScreenCaptureService(XdgScreenshotPortal()),
    DesktopSession.other => UnsupportedScreenCaptureService(
      CaptureFailure.platformUnsupported,
      details: 'No backend for ${Platform.operatingSystem}',
    ),
  };
}

/// Selects the capture backend for the current OS. Overridden in tests with a
/// fake (SPEC §6).
final screenCaptureServiceProvider = Provider<ScreenCaptureService>((ref) {
  return defaultScreenCaptureService();
});
