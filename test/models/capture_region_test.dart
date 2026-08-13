import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/models/capture_region.dart';

void main() {
  group('CaptureRegion.fromPoints', () {
    test('normalizes corners regardless of drag direction', () {
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

    test('a zero-area drag is empty', () {
      final region = CaptureRegion.fromPoints(
        const Offset(5, 5),
        const Offset(5, 5),
      );
      expect(region.isEmpty, isTrue);
    });

    test('toRect round-trips the bounds', () {
      const region = CaptureRegion(x: 3, y: 4, width: 7, height: 9);
      expect(region.toRect(), const Rect.fromLTWH(3, 4, 7, 9));
    });
  });

  group('clampedTo', () {
    test('leaves a region already inside the bounds alone', () {
      const region = CaptureRegion(x: 10, y: 10, width: 20, height: 20);
      expect(region.clampedTo(100, 100), region);
    });

    test('trims the part hanging off the right and bottom edges', () {
      const region = CaptureRegion(x: 90, y: 95, width: 40, height: 40);

      expect(
        region.clampedTo(100, 100),
        const CaptureRegion(x: 90, y: 95, width: 10, height: 5),
      );
    });

    test('trims negative origins', () {
      const region = CaptureRegion(x: -10, y: -20, width: 40, height: 60);

      expect(
        region.clampedTo(100, 100),
        const CaptureRegion(x: 0, y: 0, width: 30, height: 40),
      );
    });

    test('is empty when the region misses the bounds entirely', () {
      const region = CaptureRegion(x: 200, y: 200, width: 10, height: 10);
      expect(region.clampedTo(100, 100).isEmpty, isTrue);
    });
  });

  group('scaled', () {
    test('scales origin and size by the device pixel ratio', () {
      const region = CaptureRegion(x: 10, y: 20, width: 30, height: 40);

      expect(
        region.scaled(1.5),
        const CaptureRegion(x: 15, y: 30, width: 45, height: 60),
      );
    });
  });
}
