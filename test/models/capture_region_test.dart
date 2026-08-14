import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/models/capture_region.dart';

void main() {
  group('CaptureRegion.fromPoints', () {
    test('normaliza os cantos independente da direção do arrasto', () {
      final downRight = CaptureRegion.fromPoints(
        const Offset(10, 20),
        const Offset(110, 220),
      );
      final upLeft = CaptureRegion.fromPoints(
        const Offset(110, 220),
        const Offset(10, 20),
      );

      expect(downRight, upLeft);
      expect(upLeft.x, 10);
      expect(upLeft.y, 20);
      expect(upLeft.width, 100);
      expect(upLeft.height, 200);
    });

    test('um arrasto de área zero é vazio', () {
      final region = CaptureRegion.fromPoints(
        const Offset(5, 5),
        const Offset(5, 5),
      );
      expect(region.isEmpty, isTrue);
    });

    test('toRect leva e traz os limites sem perda', () {
      const region = CaptureRegion(x: 3, y: 4, width: 7, height: 9);
      expect(region.toRect(), const Rect.fromLTWH(3, 4, 7, 9));
    });
  });

  group('clampedTo', () {
    test('deixa em paz a região que já está dentro dos limites', () {
      const region = CaptureRegion(x: 10, y: 10, width: 20, height: 20);
      expect(region.clampedTo(100, 100), region);
    });

    test('apara o que sobra nas bordas direita e inferior', () {
      const region = CaptureRegion(x: 90, y: 95, width: 40, height: 40);

      expect(
        region.clampedTo(100, 100),
        const CaptureRegion(x: 90, y: 95, width: 10, height: 5),
      );
    });

    test('apara origens negativas', () {
      const region = CaptureRegion(x: -10, y: -20, width: 40, height: 60);

      expect(
        region.clampedTo(100, 100),
        const CaptureRegion(x: 0, y: 0, width: 30, height: 40),
      );
    });

    test('fica vazia quando a região erra os limites por completo', () {
      const region = CaptureRegion(x: 200, y: 200, width: 10, height: 10);
      expect(region.clampedTo(100, 100).isEmpty, isTrue);
    });
  });

  group('scaled', () {
    test('escala origem e tamanho pela razão de pixels do dispositivo', () {
      const region = CaptureRegion(x: 10, y: 20, width: 30, height: 40);

      expect(
        region.scaled(1.5),
        const CaptureRegion(x: 15, y: 30, width: 45, height: 60),
      );
    });
  });
}
