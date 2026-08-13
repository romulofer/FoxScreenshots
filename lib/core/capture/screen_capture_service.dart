import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/capture_region.dart';
import '../../models/capture_result.dart';

/// How a capture is framed.
enum CaptureMode { instant, timer, fullScreen, activeWindow }

/// Platform-agnostic screen capture contract (SPEC §2.1, §4).
///
/// The UI and editor depend only on this interface; per-OS implementations
/// (X11/Wayland/portal on Linux, GDI/DXGI on Windows, CG on macOS) are selected
/// at runtime. The Linux backend is deferred to PLAN.
abstract interface class ScreenCaptureService {
  /// Grabs a full-resolution snapshot of every monitor, composited into one
  /// image. Used as the frozen overlay backdrop for instant mode.
  Future<CaptureResult> grabFullVirtualScreen();

  /// Crops [region] out of the (already frozen) virtual screen.
  Future<CaptureResult> grabRegion(CaptureRegion region);
}

/// Fallback used until a real per-OS backend lands. Constructs fine so the app
/// boots; throws only when a capture is actually attempted.
class UnsupportedScreenCaptureService implements ScreenCaptureService {
  const UnsupportedScreenCaptureService();

  Never _unsupported() => throw UnimplementedError(
    'No screen capture backend for ${Platform.operatingSystem} yet '
    '(deferred to PLAN).',
  );

  @override
  Future<CaptureResult> grabFullVirtualScreen() async => _unsupported();

  @override
  Future<CaptureResult> grabRegion(CaptureRegion region) async =>
      _unsupported();
}

/// Selects the capture backend for the current OS. Overridden in tests with a
/// fake, and later per-platform once backends exist.
final screenCaptureServiceProvider = Provider<ScreenCaptureService>((ref) {
  return const UnsupportedScreenCaptureService();
});
