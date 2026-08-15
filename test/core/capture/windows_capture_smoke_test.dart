@TestOn('windows')
library;

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/capture/windows_screen_capture_service.dart';
import 'package:foxscreenshots/models/capture_region.dart';
import 'package:image/image.dart' as img;

/// Real-capture smoke test, gated to a Windows runner (SPEC §6). It needs a live
/// desktop session (GDI `BitBlt` returns black under a headless service), so it
/// is skipped everywhere else.
void main() {
  group('WindowsScreenCaptureService', () {
    test('captura a tela virtual como um PNG decodificável', () async {
      const service = WindowsScreenCaptureService();

      final shot = await service.grabFullVirtualScreen();

      expect(shot.width, greaterThan(0));
      expect(shot.height, greaterThan(0));
      final decoded = img.decodePng(shot.pngBytes);
      expect(decoded, isNotNull);
      expect(decoded!.width, shot.width);
      expect(decoded.height, shot.height);
    });

    test('captura a região no tamanho exato pedido', () async {
      const service = WindowsScreenCaptureService();

      final shot = await service.grabRegion(
        const CaptureRegion(x: 0, y: 0, width: 64, height: 32),
      );

      expect(shot.width, 64);
      expect(shot.height, 32);
      expect(img.decodePng(shot.pngBytes)?.width, 64);
    });

    test('limita uma região que passa da borda da tela', () async {
      const service = WindowsScreenCaptureService();
      final size = await service.virtualScreenSize();

      final shot = await service.grabRegion(
        CaptureRegion(
          x: size.width - 10,
          y: size.height - 10,
          width: 500,
          height: 500,
        ),
      );

      expect(shot.width, 10);
      expect(shot.height, 10);
    });
  });
}
