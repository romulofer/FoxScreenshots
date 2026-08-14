import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/painting.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'models/annotation.dart';
import 'painters/annotation_painter.dart';

/// Bakes the annotation layer into the image (SPEC §2.2: "composited on
/// export").
///
/// Records the same painter the preview uses against a full-resolution canvas,
/// so export is lossless with respect to what the user saw — no re-layout, no
/// second renderer to keep in sync.
typedef ImageFlattener = Future<FlattenedImage> Function({
  required ui.Image base,
  required List<Annotation> annotations,
  required TextDirection textDirection,
});

/// A rendered image with its PNG bytes, so callers can hand the bytes to the
/// clipboard/output services and keep the decoded image for further editing.
class FlattenedImage {
  const FlattenedImage({required this.image, required this.pngBytes});

  final ui.Image image;
  final Uint8List pngBytes;

  int get width => image.width;
  int get height => image.height;
}

/// Draws [base] plus [annotations] at 1:1 and encodes the result as PNG.
Future<FlattenedImage> flattenToPng({
  required ui.Image base,
  required List<Annotation> annotations,
  required TextDirection textDirection,
}) {
  return _record(
    width: base.width,
    height: base.height,
    draw: (canvas) {
      canvas.drawImage(base, ui.Offset.zero, ui.Paint());
      AnnotationPainter(
        base: base,
        textDirection: textDirection,
      ).paintAll(canvas, annotations);
    },
  );
}

/// Trims [base] to [rect] (image pixels), returning the new base image.
///
/// Goes through the same canvas path as [flattenToPng] rather than the PNG
/// codec: the decoded image is needed right away for the next preview frame,
/// and this avoids a decode/encode/decode round trip per crop.
Future<FlattenedImage> cropImage({
  required ui.Image base,
  required ui.Rect rect,
}) {
  final area = _rounded(
    rect.intersect(
      ui.Rect.fromLTWH(0, 0, base.width.toDouble(), base.height.toDouble()),
    ),
  );
  return _record(
    width: area.width.round(),
    height: area.height.round(),
    draw: (canvas) => canvas.drawImageRect(
      base,
      area,
      ui.Rect.fromLTWH(0, 0, area.width, area.height),
      ui.Paint()..filterQuality = ui.FilterQuality.none,
    ),
  );
}

/// Whole-pixel bounds: a fractional source rect would resample the crop and
/// soften text that should have been copied verbatim.
ui.Rect _rounded(ui.Rect rect) => ui.Rect.fromLTRB(
  rect.left.floorToDouble(),
  rect.top.floorToDouble(),
  rect.right.ceilToDouble(),
  rect.bottom.ceilToDouble(),
);

Future<FlattenedImage> _record({
  required int width,
  required int height,
  required void Function(ui.Canvas canvas) draw,
}) async {
  final recorder = ui.PictureRecorder();
  draw(ui.Canvas(recorder));
  final picture = recorder.endRecording();
  try {
    final image = await picture.toImage(width, height);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    if (data == null) {
      image.dispose();
      throw StateError('Could not encode the edited image');
    }
    return FlattenedImage(image: image, pngBytes: data.buffer.asUint8List());
  } finally {
    picture.dispose();
  }
}

/// Overridden in widget tests, where engine rasterization needs `runAsync`.
final imageFlattenerProvider = Provider<ImageFlattener>((ref) => flattenToPng);
