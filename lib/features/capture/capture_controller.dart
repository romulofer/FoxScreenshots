import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/capture/screen_capture_service.dart';
import '../../models/capture_region.dart';
import '../../models/capture_result.dart';
import '../home/session_controller.dart';

/// Orchestrates the capture flows (SPEC §2.1). Pure logic — no BuildContext.
///
/// The selection overlay and timer UI drive this; the real per-OS crop lands
/// once a capture backend exists (deferred to PLAN). For now it delegates to
/// [ScreenCaptureService] and pushes results into the session.
class CaptureController {
  CaptureController(this._ref);

  final Ref _ref;

  ScreenCaptureService get _service => _ref.read(screenCaptureServiceProvider);

  /// Freezes all screens and returns the composited snapshot to back the
  /// selection overlay.
  Future<CaptureResult> beginInstant() => _service.grabFullVirtualScreen();

  /// Crops the chosen [region] and records it in the session.
  Future<CaptureResult> finishRegion(CaptureRegion region) async {
    final result = await _service.grabRegion(region);
    _ref.read(sessionControllerProvider.notifier).add(result);
    return result;
  }
}

final captureControllerProvider = Provider<CaptureController>((ref) {
  return CaptureController(ref);
});
