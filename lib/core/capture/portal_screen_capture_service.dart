import 'dart:typed_data';

import '../../models/capture_region.dart';
import '../../models/capture_result.dart';
import '../image/png_codec.dart';
import 'portal/screenshot_portal.dart';
import 'screen_capture_service.dart';

/// Wayland capture backend, through `xdg-desktop-portal` (SPEC §2.1).
///
/// Wayland gives a client no way to read the screen — not its own window, and
/// certainly not another's — so every frame comes from the portal, which asks
/// the user once and then hands over a finished PNG of the whole desktop.
///
/// The portal has no notion of a region: [grabRegion] takes a fresh full frame
/// and crops it, which is also what makes timer mode work there.
class PortalScreenCaptureService implements ScreenCaptureService {
  const PortalScreenCaptureService(
    this._portal, {
    this.codec = const PngCodec(),
  });

  final ScreenshotPortal _portal;

  /// Test seam: [PngCodec.inline] keeps cropping on the calling isolate, since
  /// an isolate hop never completes under the fake clock `flutter test` runs.
  final PngCodec codec;

  @override
  Future<CaptureResult> grabFullVirtualScreen() async {
    final frame = await _capture();
    return CaptureResult(
      id: _newId(),
      pngBytes: frame.png,
      width: frame.width,
      height: frame.height,
      takenAt: DateTime.now(),
    );
  }

  @override
  Future<CaptureResult> grabRegion(CaptureRegion region) async {
    final full = await _capture();
    final cropped = await codec.crop(full.png, region);
    if (cropped == null) {
      throw const CaptureException(
        CaptureFailure.displayUnavailable,
        details: 'the selected region is outside the screen',
      );
    }
    return CaptureResult(
      id: _newId(),
      pngBytes: cropped.pngBytes,
      width: cropped.width,
      height: cropped.height,
      takenAt: DateTime.now(),
    );
  }

  /// Always `null`: under Wayland a client cannot ask what other windows are,
  /// let alone where. Callers report this as "no active window".
  @override
  Future<CaptureRegion?> activeWindowRegion() async => null;

  @override
  Future<({int width, int height})> virtualScreenSize() async {
    final frame = await _capture();
    return (width: frame.width, height: frame.height);
  }

  /// Runs a portal request and measures what came back, translating every
  /// failure into the app-wide [CaptureException] so the UI has a single type
  /// to catch.
  Future<({Uint8List png, int width, int height})> _capture() async {
    try {
      final png = await _portal.capture();
      final size = pngSize(png);
      return (png: png, width: size.width, height: size.height);
    } on PortalException catch (e) {
      throw CaptureException(switch (e.failure) {
        PortalFailure.denied => CaptureFailure.portalDenied,
        PortalFailure.unavailable => CaptureFailure.portalUnavailable,
      }, details: e.details);
    } on FormatException catch (e) {
      throw CaptureException(
        CaptureFailure.portalUnavailable,
        details:
            'the portal returned something that is not a PNG: ${e.message}',
      );
    }
  }

  String _newId() => 'shot-${DateTime.now().microsecondsSinceEpoch}';
}
