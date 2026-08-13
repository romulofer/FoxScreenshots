@TestOn('linux')
library;

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/capture/x11_screen_capture_service.dart';
import 'package:foxscreenshots/models/capture_region.dart';
import 'package:image/image.dart' as img;

/// Real-capture smoke test, gated to a headed/X11 runner (SPEC §6). It is
/// skipped on CI and under Wayland, where the X root window is not the desktop.
void main() {
  final env = Platform.environment;
  final hasX11 =
      (env['DISPLAY']?.isNotEmpty ?? false) &&
      env['XDG_SESSION_TYPE']?.toLowerCase() != 'wayland';

  group('X11ScreenCaptureService', () {
    test('grabs the virtual screen as a decodable PNG', () async {
      const service = X11ScreenCaptureService();

      final shot = await service.grabFullVirtualScreen();

      expect(shot.width, greaterThan(0));
      expect(shot.height, greaterThan(0));
      final decoded = img.decodePng(shot.pngBytes);
      expect(decoded, isNotNull);
      expect(decoded!.width, shot.width);
      expect(decoded.height, shot.height);
      // A real desktop is never a single flat color.
      expect(decoded.getPixel(0, 0), isNotNull);
    });

    test('grabs a region at exactly the requested size', () async {
      const service = X11ScreenCaptureService();

      final shot = await service.grabRegion(
        const CaptureRegion(x: 0, y: 0, width: 64, height: 32),
      );

      expect(shot.width, 64);
      expect(shot.height, 32);
      expect(img.decodePng(shot.pngBytes)?.width, 64);
    });

    test('clamps a region that runs past the screen edge', () async {
      const service = X11ScreenCaptureService();
      final full = await service.grabFullVirtualScreen();

      final shot = await service.grabRegion(
        CaptureRegion(
          x: full.width - 10,
          y: full.height - 10,
          width: 500,
          height: 500,
        ),
      );

      expect(shot.width, 10);
      expect(shot.height, 10);
    });
  }, skip: hasX11 ? null : 'needs a headed X11 session');
}
