import 'dart:typed_data';

import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/models/capture_region.dart';
import 'package:foxscreenshots/models/capture_result.dart';

/// A deterministic in-memory capture backend for tests (SPEC §6: capture via
/// mock, no real display dependency). Returns a fixed 1×1 PNG.
class FakeScreenCaptureService implements ScreenCaptureService {
  int fullScreenCalls = 0;
  int regionCalls = 0;

  static final Uint8List _onePxPng = Uint8List.fromList(<int>[
    0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
  ]);

  CaptureResult _result() => CaptureResult(
    id: 'fake-${DateTime.now().microsecondsSinceEpoch}',
    pngBytes: _onePxPng,
    width: 1,
    height: 1,
    takenAt: DateTime(2026, 1, 1),
  );

  @override
  Future<CaptureResult> grabFullVirtualScreen() async {
    fullScreenCalls++;
    return _result();
  }

  @override
  Future<CaptureResult> grabRegion(CaptureRegion region) async {
    regionCalls++;
    return _result();
  }
}
