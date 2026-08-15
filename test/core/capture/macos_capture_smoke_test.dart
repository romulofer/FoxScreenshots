@TestOn('mac-os')
library;

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/capture/macos_screen_capture_service.dart';
import 'package:foxscreenshots/core/capture/screen_capture_service.dart';
import 'package:foxscreenshots/models/capture_region.dart';
import 'package:image/image.dart' as img;

/// Real-capture smoke test, gated to a macOS runner (SPEC §6). It needs a live
/// window server and the Screen Recording grant (CoreGraphics returns nothing
/// headless / unauthorized), so it is skipped everywhere else and tolerates a
/// permission denial on an ungranted machine.
void main() {
  group('MacosScreenCaptureService', () {
    test('captura a tela virtual como um PNG decodificável', () async {
      const service = MacosScreenCaptureService();

      try {
        final shot = await service.grabFullVirtualScreen();

        expect(shot.width, greaterThan(0));
        expect(shot.height, greaterThan(0));
        final decoded = img.decodePng(shot.pngBytes);
        expect(decoded, isNotNull);
        expect(decoded!.width, shot.width);
        expect(decoded.height, shot.height);
      } on CaptureException catch (e) {
        // A CI machine without the Screen Recording grant cannot capture; that
        // is the permission path working, not a backend failure.
        expect(e.failure, CaptureFailure.screenRecordingDenied);
      }
    });

    test('captura a região no tamanho exato pedido', () async {
      const service = MacosScreenCaptureService();

      try {
        final shot = await service.grabRegion(
          const CaptureRegion(x: 0, y: 0, width: 64, height: 32),
        );

        expect(shot.width, 64);
        expect(shot.height, 32);
        expect(img.decodePng(shot.pngBytes)?.width, 64);
      } on CaptureException catch (e) {
        expect(e.failure, CaptureFailure.screenRecordingDenied);
      }
    });
  });
}
