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
}
