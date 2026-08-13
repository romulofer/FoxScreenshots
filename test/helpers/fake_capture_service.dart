import 'dart:typed_data';

import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/models/capture_region.dart';
import 'package:foxscreenshots/models/capture_result.dart';
import 'package:image/image.dart' as img;

/// A deterministic in-memory capture backend for tests (SPEC §6: capture via a
/// mock, no real display dependency).
///
/// Frames are real PNGs — small, but decodable — so crop and rendering paths
/// behave the same as they do against a live backend.
class FakeScreenCaptureService implements ScreenCaptureService {
  FakeScreenCaptureService({
    this.screenWidth = 400,
    this.screenHeight = 300,
    this.activeWindow,
    this.failure,
  });

  final int screenWidth;
  final int screenHeight;

  /// What [activeWindowRegion] reports; `null` means "nothing focused".
  final CaptureRegion? activeWindow;

  /// When set, every call throws this instead of returning a frame.
  final CaptureFailure? failure;

  int fullScreenCalls = 0;
  int regionCalls = 0;
  CaptureRegion? lastRegion;

  @override
  Future<CaptureResult> grabFullVirtualScreen() async {
    _maybeFail();
    fullScreenCalls++;
    return _result(screenWidth, screenHeight);
  }

  @override
  Future<CaptureResult> grabRegion(CaptureRegion region) async {
    _maybeFail();
    regionCalls++;
    lastRegion = region;
    return _result(region.width, region.height);
  }

  @override
  Future<CaptureRegion?> activeWindowRegion() async {
    _maybeFail();
    return activeWindow;
  }

  void _maybeFail() {
    if (failure != null) throw CaptureException(failure!);
  }

  CaptureResult _result(int width, int height) {
    return CaptureResult(
      id: 'fake-${DateTime.now().microsecondsSinceEpoch}-$regionCalls',
      pngBytes: solidPng(width, height),
      width: width,
      height: height,
      takenAt: DateTime(2026, 1, 1),
    );
  }
}

/// A gradient PNG of [width]×[height]; the gradient makes a wrong crop offset
/// visible instead of hiding it behind a flat color.
Uint8List solidPng(int width, int height) {
  final image = img.Image(width: width, height: height);
  for (var y = 0; y < height; y++) {
    for (var x = 0; x < width; x++) {
      image.setPixelRgba(x, y, x % 256, y % 256, 128, 255);
    }
  }
  return img.encodePng(image);
}
