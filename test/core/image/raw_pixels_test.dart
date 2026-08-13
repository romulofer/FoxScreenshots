import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/core/image/raw_pixels.dart';

void main() {
  group('rgbaFromRaw', () {
    test('reorders BGRA pixels and forces an opaque alpha', () {
      // Two pixels: pure red, pure blue — stored blue-first, with a junk pad
      // byte like X11 leaves behind on a depth-24 visual.
      final source = Uint8List.fromList([
        0, 0, 255, 7, //
        255, 0, 0, 3,
      ]);

      final rgba = rgbaFromRaw(
        source: source,
        width: 2,
        height: 1,
        bytesPerLine: 8,
        bitsPerPixel: 32,
      );

      expect(rgba, [255, 0, 0, 255, 0, 0, 255, 255]);
    });

    test('keeps RGBA sources untouched apart from alpha', () {
      final source = Uint8List.fromList([10, 20, 30, 0]);

      final rgba = rgbaFromRaw(
        source: source,
        width: 1,
        height: 1,
        bytesPerLine: 4,
        bitsPerPixel: 32,
        order: RawPixelOrder.rgba,
      );

      expect(rgba, [10, 20, 30, 255]);
    });

    test('skips row padding when the stride is wider than the row', () {
      // 1 pixel per row, but rows are padded to 8 bytes.
      final source = Uint8List.fromList([
        1, 2, 3, 0, 99, 99, 99, 99, //
        4, 5, 6, 0, 99, 99, 99, 99,
      ]);

      final rgba = rgbaFromRaw(
        source: source,
        width: 1,
        height: 2,
        bytesPerLine: 8,
        bitsPerPixel: 32,
      );

      expect(rgba, [3, 2, 1, 255, 6, 5, 4, 255]);
    });

    test('handles 24-bit packed pixels', () {
      final source = Uint8List.fromList([1, 2, 3, 4, 5, 6]);

      final rgba = rgbaFromRaw(
        source: source,
        width: 2,
        height: 1,
        bytesPerLine: 6,
        bitsPerPixel: 24,
      );

      expect(rgba, [3, 2, 1, 255, 6, 5, 4, 255]);
    });

    test('rejects an unsupported pixel depth', () {
      expect(
        () => rgbaFromRaw(
          source: Uint8List(8),
          width: 1,
          height: 1,
          bytesPerLine: 8,
          bitsPerPixel: 16,
        ),
        throwsArgumentError,
      );
    });

    test('rejects a buffer too short for the stated size', () {
      expect(
        () => rgbaFromRaw(
          source: Uint8List(8),
          width: 4,
          height: 4,
          bytesPerLine: 16,
          bitsPerPixel: 32,
        ),
        throwsArgumentError,
      );
    });

    test('rejects a stride narrower than one row', () {
      expect(
        () => rgbaFromRaw(
          source: Uint8List(64),
          width: 4,
          height: 1,
          bytesPerLine: 8,
          bitsPerPixel: 32,
        ),
        throwsArgumentError,
      );
    });
  });
}
