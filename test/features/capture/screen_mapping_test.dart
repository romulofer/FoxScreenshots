import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/window/capture_window_controller.dart';
import 'package:foxscreenshots/features/capture/screen_mapping.dart';
import 'package:foxscreenshots/models/capture_region.dart';

void main() {
  group('fromPlacement', () {
    test('fica 1:1 quando a sobreposição cobre a tela virtual inteira', () {
      // Two 1760x1080 monitors side by side, no display scaling.
      const placement = OverlayPlacement(
        window: Rect.fromLTWH(0, 0, 3520, 1080),
        virtualScreen: Rect.fromLTWH(0, 0, 3520, 1080),
      );

      final mapping = ScreenMapping.fromPlacement(
        placement,
        imageWidth: 3520,
        imageHeight: 1080,
      );

      expect(mapping.imageOrigin, Offset.zero);
      expect(mapping.imagePixelsPerLogical, 1);
      expect(mapping.toImage(const Offset(100, 200)), const Offset(100, 200));
    });

    test('desloca o fundo quando o gerenciador de janelas prende a sobreposição a um monitor', () {
      // The window manager only granted the right-hand monitor.
      const placement = OverlayPlacement(
        window: Rect.fromLTWH(1760, 0, 1760, 1080),
        virtualScreen: Rect.fromLTWH(0, 0, 3520, 1080),
      );

      final mapping = ScreenMapping.fromPlacement(
        placement,
        imageWidth: 3520,
        imageHeight: 1080,
      );

      // Overlay pixel (0,0) is screenshot pixel (1760,0) — without this the
      // frozen frame would look shifted by a whole monitor.
      expect(mapping.imageOrigin, const Offset(1760, 0));
      expect(mapping.toImage(const Offset(10, 10)), const Offset(1770, 10));
    });

    test('leva em conta o fator de escala do monitor', () {
      // 2x HiDPI: 1920 logical, 3840 physical pixels.
      const placement = OverlayPlacement(
        window: Rect.fromLTWH(0, 0, 1920, 1080),
        virtualScreen: Rect.fromLTWH(0, 0, 1920, 1080),
      );

      final mapping = ScreenMapping.fromPlacement(
        placement,
        imageWidth: 3840,
        imageHeight: 2160,
      );

      expect(mapping.imagePixelsPerLogical, 2);
      expect(mapping.toImage(const Offset(10, 20)), const Offset(20, 40));
    });

    test('lida com uma tela virtual que começa em origem negativa', () {
      // Second monitor placed to the left of the primary one.
      const placement = OverlayPlacement(
        window: Rect.fromLTWH(-1920, 0, 1920, 1080),
        virtualScreen: Rect.fromLTWH(-1920, 0, 3840, 1080),
      );

      final mapping = ScreenMapping.fromPlacement(
        placement,
        imageWidth: 3840,
        imageHeight: 2160,
      );

      expect(mapping.imageOrigin, Offset.zero);
    });

    test('prefere a posição medida no servidor gráfico à do gerenciador de janelas', () {
      // What the bug looked like: the window manager had already moved the
      // overlay to the origin, but `window_manager` still answered with the
      // hub's old spot on the second monitor — so the backdrop was drawn from
      // that monitor's pixels and the monitor itself came out black.
      const placement = OverlayPlacement(
        window: Rect.fromLTWH(2100, 82, 3520, 1080),
        virtualScreen: Rect.fromLTWH(0, 0, 3520, 1080),
        physicalWindow: Rect.fromLTWH(0, 0, 3520, 1080),
      );

      final mapping = ScreenMapping.fromPlacement(
        placement,
        imageWidth: 3520,
        imageHeight: 1080,
      );

      expect(mapping.imageOrigin, Offset.zero);
      expect(mapping.toImage(const Offset(2000, 10)), const Offset(2000, 10));
    });

    test('a posição medida já vem em pixels do screenshot', () {
      // 2x HiDPI, overlay clamped to the right-hand monitor: 960 logical
      // pixels in, 1920 physical pixels across the screenshot.
      const placement = OverlayPlacement(
        window: Rect.fromLTWH(960, 0, 960, 540),
        virtualScreen: Rect.fromLTWH(0, 0, 1920, 1080),
        physicalWindow: Rect.fromLTWH(1920, 0, 1920, 1080),
      );

      final mapping = ScreenMapping.fromPlacement(
        placement,
        imageWidth: 3840,
        imageHeight: 2160,
      );

      expect(mapping.imagePixelsPerLogical, 2);
      expect(mapping.imageOrigin, const Offset(1920, 0));
      expect(mapping.toImage(const Offset(10, 20)), const Offset(1940, 40));
    });

    test('a posição medida é relativa à origem da tela virtual', () {
      // Second monitor to the left: the screenshot starts at virtual (-1920,0),
      // so a window measured there is screenshot pixel (0,0).
      const placement = OverlayPlacement(
        window: Rect.fromLTWH(0, 0, 1920, 1080),
        virtualScreen: Rect.fromLTWH(-1920, 0, 3840, 1080),
        physicalWindow: Rect.fromLTWH(-1920, 0, 3840, 1080),
      );

      final mapping = ScreenMapping.fromPlacement(
        placement,
        imageWidth: 3840,
        imageHeight: 2160,
      );

      expect(mapping.imageOrigin, Offset.zero);
    });

    test('cai para 1:1 em uma tela virtual degenerada', () {
      const placement = OverlayPlacement(
        window: Rect.zero,
        virtualScreen: Rect.zero,
      );

      expect(
        ScreenMapping.fromPlacement(
          placement,
          imageWidth: 100,
          imageHeight: 100,
        ).imagePixelsPerLogical,
        1,
      );
    });
  });

  group('fitted', () {
    test('encaixa a tela virtual inteira dentro da janela, centralizada', () {
      // Wayland: the overlay is fullscreen on one 1600x900 monitor and has to
      // show a 3520x1080 desktop.
      final mapping = ScreenMapping.fitted(
        image: const Size(3520, 1080),
        window: const Size(1600, 900),
      );

      expect(mapping.imagePixelsPerLogical, 2.2);
      // Letterboxed: the drawn frame is shorter than the window, so the top
      // edge of the window sits 450 screenshot pixels above the image.
      expect(mapping.imageOrigin.dx, closeTo(0, 1e-9));
      expect(mapping.imageOrigin.dy, closeTo(-450, 1e-9));
      // The middle of the window is the middle of the desktop.
      final centre = mapping.toImage(const Offset(800, 450));
      expect(centre.dx, closeTo(1760, 1e-9));
      expect(centre.dy, closeTo(540, 1e-9));
    });

    test('o arrasto volta para pixels exatos do screenshot', () {
      final mapping = ScreenMapping.fitted(
        image: const Size(1000, 500),
        window: const Size(500, 500),
      );

      final region = mapping.toRegion(
        const Offset(100, 125),
        const Offset(200, 200),
        imageWidth: 1000,
        imageHeight: 500,
      );

      expect(
        region,
        const CaptureRegion(x: 200, y: 0, width: 200, height: 150),
      );
    });

    test('uma janela ou imagem degenerada não quebra o mapeamento', () {
      final mapping = ScreenMapping.fitted(
        image: Size.zero,
        window: const Size(800, 600),
      );

      expect(mapping.imagePixelsPerLogical, 1);
      expect(mapping.imageOrigin, Offset.zero);
    });

    test('a sobreposição sem posição usa o encaixe, não a geometria', () {
      const placement = OverlayPlacement.fitted(
        window: Rect.fromLTWH(0, 0, 1600, 900),
      );

      final mapping = ScreenMapping.fromPlacement(
        placement,
        imageWidth: 3520,
        imageHeight: 1080,
      );

      expect(mapping.imagePixelsPerLogical, 2.2);
    });
  });

  group('toRegion', () {
    test('mapeia o arrasto para pixels do screenshot, em qualquer direção', () {
      const mapping = ScreenMapping(
        imageOrigin: Offset(1760, 0),
        imagePixelsPerLogical: 2,
      );

      final region = mapping.toRegion(
        const Offset(110, 90),
        const Offset(10, 40),
        imageWidth: 3520,
        imageHeight: 2160,
      );

      expect(
        region,
        const CaptureRegion(x: 1780, y: 80, width: 200, height: 100),
      );
    });

    test('corta o arrasto que sai do screenshot', () {
      const mapping = ScreenMapping(
        imageOrigin: Offset(3400, 1000),
        imagePixelsPerLogical: 1,
      );

      final region = mapping.toRegion(
        const Offset(0, 0),
        const Offset(500, 500),
        imageWidth: 3520,
        imageHeight: 1080,
      );

      expect(region.width, 120);
      expect(region.height, 80);
    });
  });
}
