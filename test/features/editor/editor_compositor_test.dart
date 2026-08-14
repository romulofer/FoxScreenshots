import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/painting.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:foxscreenshots/features/editor/editor_compositor.dart';
import 'package:foxscreenshots/features/editor/models/annotation.dart';
import 'package:image/image.dart' as img;

import '../../helpers/test_images.dart';

void main() {
  /// Decodes the export so pixels can be inspected the way a viewer would.
  img.Image decode(Uint8List pngBytes) {
    final decoded = img.decodePng(pngBytes);
    expect(decoded, isNotNull, reason: 'export must be a valid PNG');
    return decoded!;
  }

  int luminanceAt(img.Image image, int x, int y) {
    final pixel = image.getPixel(x, y);
    return ((pixel.r + pixel.g + pixel.b) / 3).round();
  }

  Future<FlattenedImage> flatten(ui.Image base, List<Annotation> annotations) =>
      flattenToPng(
        base: base,
        annotations: annotations,
        textDirection: TextDirection.ltr,
      );

  testWidgets('export keeps the base size and encodes a valid PNG', (
    tester,
  ) async {
    await tester.runAsync(() async {
      final base = solidImage(width: 120, height: 90);
      addTearDown(base.dispose);

      final flattened = await flatten(base, const []);
      addTearDown(flattened.image.dispose);

      expect(flattened.width, 120);
      expect(flattened.height, 90);
      expect(decode(flattened.pngBytes).width, 120);
    });
  });

  testWidgets('annotations are baked into the exported pixels', (tester) async {
    await tester.runAsync(() async {
      final base = solidImage(
        width: 100,
        height: 80,
        color: const ui.Color(0xFF000000),
      );
      addTearDown(base.dispose);

      final flattened = await flatten(base, [
        const RectangleAnnotation(
          id: 'r',
          color: ui.Color(0xFFFFFFFF),
          strokeWidth: 8,
          start: ui.Offset(20, 20),
          end: ui.Offset(60, 60),
        ),
      ]);
      addTearDown(flattened.image.dispose);

      final exported = decode(flattened.pngBytes);
      expect(
        luminanceAt(exported, 20, 40),
        greaterThan(200),
        reason: 'the stroke should be on the left edge of the rectangle',
      );
      expect(
        luminanceAt(exported, 90, 70),
        lessThan(20),
        reason: 'untouched areas keep the original pixels',
      );
    });
  });

  testWidgets('a highlight tints without hiding what is underneath', (
    tester,
  ) async {
    await tester.runAsync(() async {
      final base = solidImage(
        width: 100,
        height: 80,
        color: const ui.Color(0xFF000000),
      );
      addTearDown(base.dispose);

      final flattened = await flatten(base, [
        const HighlightAnnotation(
          id: 'h',
          color: ui.Color(0xFFFFFF00),
          strokeWidth: 4,
          start: ui.Offset(0, 0),
          end: ui.Offset(50, 40),
        ),
      ]);
      addTearDown(flattened.image.dispose);

      final exported = decode(flattened.pngBytes);
      final tinted = luminanceAt(exported, 10, 10);
      expect(tinted, greaterThan(0), reason: 'the marker must be visible');
      expect(
        tinted,
        lessThan(200),
        reason: 'a marker is translucent, not opaque paint',
      );
    });
  });

  testWidgets('a blur redaction destroys the hard edge underneath', (
    tester,
  ) async {
    await tester.runAsync(() async {
      final base = splitImage(width: 100, height: 80);
      addTearDown(base.dispose);

      final flattened = await flatten(base, [
        const RedactionAnnotation(
          id: 'b',
          color: ui.Color(0xFF000000),
          strokeWidth: 6,
          start: ui.Offset(30, 10),
          end: ui.Offset(70, 70),
          style: RedactionStyle.blur,
        ),
      ]);
      addTearDown(flattened.image.dispose);

      final exported = decode(flattened.pngBytes);
      final atSeam = luminanceAt(exported, 50, 40);
      expect(
        atSeam,
        allOf(greaterThan(40), lessThan(215)),
        reason: 'the black/white seam should have smeared into grey',
      );
      expect(
        luminanceAt(exported, 5, 40),
        lessThan(20),
        reason: 'pixels outside the redaction are untouched',
      );
    });
  });

  testWidgets('a pixelate redaction merges detail across the seam', (
    tester,
  ) async {
    await tester.runAsync(() async {
      final base = splitImage(width: 100, height: 80);
      addTearDown(base.dispose);

      // Block size 15 (6 × 2.5) straddles the seam at x=50, so the black and
      // white sides collapse into one block.
      final flattened = await flatten(base, [
        const RedactionAnnotation(
          id: 'p',
          color: ui.Color(0xFF000000),
          strokeWidth: 6,
          start: ui.Offset(30, 10),
          end: ui.Offset(70, 70),
          style: RedactionStyle.pixelate,
        ),
      ]);
      addTearDown(flattened.image.dispose);

      final exported = decode(flattened.pngBytes);
      expect(
        luminanceAt(exported, 47, 40),
        luminanceAt(exported, 52, 40),
        reason: 'both sides of the seam fall in the same block',
      );
      expect(
        luminanceAt(exported, 95, 40),
        greaterThan(235),
        reason: 'the untouched right half stays white',
      );
    });
  });

  testWidgets('crop trims to whole pixels and re-origins the content', (
    tester,
  ) async {
    await tester.runAsync(() async {
      final base = splitImage(width: 100, height: 80);
      addTearDown(base.dispose);

      final cropped = await cropImage(
        base: base,
        rect: const ui.Rect.fromLTWH(60, 20, 30, 30),
      );
      addTearDown(cropped.image.dispose);

      expect(cropped.width, 30);
      expect(cropped.height, 30);
      expect(
        luminanceAt(decode(cropped.pngBytes), 2, 2),
        greaterThan(235),
        reason: 'the crop started inside the white half',
      );
    });
  });

  testWidgets('a crop dragged past the edge is clipped to the image', (
    tester,
  ) async {
    await tester.runAsync(() async {
      final base = solidImage(width: 100, height: 80);
      addTearDown(base.dispose);

      final cropped = await cropImage(
        base: base,
        rect: const ui.Rect.fromLTWH(80, 60, 400, 400),
      );
      addTearDown(cropped.image.dispose);

      expect(cropped.width, 20);
      expect(cropped.height, 20);
    });
  });
}
