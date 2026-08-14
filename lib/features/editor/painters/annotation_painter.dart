import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter/painting.dart';

import '../models/annotation.dart';

/// Draws annotations onto a canvas in **image coordinates**.
///
/// One painter for both jobs: the live preview (canvas scaled to fit the
/// window) and the export compositor (canvas at 1:1). Sharing the drawing code
/// is what guarantees the saved PNG matches what the user framed on screen —
/// a second, export-only renderer would drift.
class AnnotationPainter {
  const AnnotationPainter({required this.base, required this.textDirection});

  /// The image under the annotations. Required by redactions, which re-draw
  /// the pixels they are hiding through a filter.
  final ui.Image? base;

  final TextDirection textDirection;

  void paintAll(ui.Canvas canvas, List<Annotation> annotations) {
    for (final annotation in annotations) {
      paint(canvas, annotation);
    }
  }

  void paint(ui.Canvas canvas, Annotation annotation) {
    switch (annotation) {
      case ArrowAnnotation():
        _paintArrow(canvas, annotation);
      case RectangleAnnotation():
        canvas.drawRect(annotation.rect, _stroke(annotation));
      case EllipseAnnotation():
        canvas.drawOval(annotation.rect, _stroke(annotation));
      case HighlightAnnotation():
        canvas.drawRect(
          annotation.rect,
          ui.Paint()
            ..color = annotation.color.withValues(
              alpha: HighlightAnnotation.opacity,
            ),
        );
      case RedactionAnnotation():
        _paintRedaction(canvas, annotation);
      case PenAnnotation():
        _paintPen(canvas, annotation);
      case TextAnnotation():
        _paintText(canvas, annotation);
      case StepAnnotation():
        _paintStep(canvas, annotation);
    }
  }

  ui.Paint _stroke(Annotation annotation) => ui.Paint()
    ..style = ui.PaintingStyle.stroke
    ..strokeWidth = annotation.strokeWidth
    ..strokeCap = ui.StrokeCap.round
    ..strokeJoin = ui.StrokeJoin.round
    ..color = annotation.color
    ..isAntiAlias = true;

  void _paintArrow(ui.Canvas canvas, ArrowAnnotation arrow) {
    final paint = _stroke(arrow);
    final vector = arrow.end - arrow.start;
    final length = vector.distance;
    if (length == 0) return;

    final angle = math.atan2(vector.dy, vector.dx);
    final head = math.min(arrow.headLength, length);

    // Stop the shaft where the head begins, so a thick stroke does not poke
    // out of the tip.
    final shaftEnd =
        arrow.end - ui.Offset(math.cos(angle), math.sin(angle)) * head * 0.6;
    canvas.drawLine(arrow.start, shaftEnd, paint);

    const spread = math.pi / 7;
    final path = ui.Path()
      ..moveTo(arrow.end.dx, arrow.end.dy)
      ..lineTo(
        arrow.end.dx - head * math.cos(angle - spread),
        arrow.end.dy - head * math.sin(angle - spread),
      )
      ..lineTo(
        arrow.end.dx - head * math.cos(angle + spread),
        arrow.end.dy - head * math.sin(angle + spread),
      )
      ..close();
    canvas.drawPath(
      path,
      ui.Paint()
        ..color = arrow.color
        ..isAntiAlias = true,
    );
  }

  void _paintPen(ui.Canvas canvas, PenAnnotation pen) {
    final paint = _stroke(pen);
    if (pen.points.length == 1) {
      canvas.drawCircle(pen.points.first, pen.strokeWidth / 2, paint);
      return;
    }
    final path = ui.Path()..moveTo(pen.points.first.dx, pen.points.first.dy);
    for (final point in pen.points.skip(1)) {
      path.lineTo(point.dx, point.dy);
    }
    canvas.drawPath(path, paint);
  }

  void _paintText(ui.Canvas canvas, TextAnnotation annotation) {
    _textPainter(annotation).paint(canvas, annotation.position);
  }

  /// Laid-out text for [annotation]; also used to measure its real box.
  TextPainter _textPainter(TextAnnotation annotation) {
    return TextPainter(
      text: TextSpan(
        text: annotation.text,
        style: TextStyle(
          color: annotation.color,
          fontSize: annotation.fontSize,
          fontWeight: FontWeight.w600,
          // Screenshots are busy; a dark halo keeps light text readable over
          // light content and vice versa.
          shadows: const [Shadow(color: ui.Color(0x99000000), blurRadius: 3)],
        ),
      ),
      textDirection: textDirection,
    )..layout();
  }

  void _paintStep(ui.Canvas canvas, StepAnnotation step) {
    canvas
      ..drawCircle(
        step.center,
        step.radius,
        ui.Paint()
          ..color = step.color
          ..isAntiAlias = true,
      )
      ..drawCircle(
        step.center,
        step.radius,
        ui.Paint()
          ..style = ui.PaintingStyle.stroke
          ..strokeWidth = math.max(1, step.strokeWidth / 3)
          ..color = const ui.Color(0xFFFFFFFF)
          ..isAntiAlias = true,
      );

    final label = TextPainter(
      text: TextSpan(
        text: '${step.number}',
        style: TextStyle(
          color: const ui.Color(0xFFFFFFFF),
          fontSize: step.radius * 1.1,
          fontWeight: FontWeight.w700,
        ),
      ),
      textDirection: textDirection,
    )..layout();
    label.paint(
      canvas,
      step.center - ui.Offset(label.width / 2, label.height / 2),
    );
  }

  /// Re-draws the region through a blur or pixelate filter.
  ///
  /// The source rectangle is padded so the filter has real pixels to sample at
  /// the edges (otherwise a blur fades into transparency and leaves a halo);
  /// the clip then trims the result back to exactly the region the user drew.
  void _paintRedaction(ui.Canvas canvas, RedactionAnnotation redaction) {
    final image = base;
    final rect = redaction.rect;
    if (image == null || rect.isEmpty) return;

    final pad = switch (redaction.style) {
      RedactionStyle.blur => redaction.sigma * 3,
      RedactionStyle.pixelate => redaction.blockSize * 2,
    };
    final padded = rect.inflate(pad);
    final source = padded.intersect(
      ui.Rect.fromLTWH(0, 0, image.width.toDouble(), image.height.toDouble()),
    );
    if (source.isEmpty) return;

    final filter = switch (redaction.style) {
      RedactionStyle.blur => ui.ImageFilter.blur(
        sigmaX: redaction.sigma,
        sigmaY: redaction.sigma,
        tileMode: ui.TileMode.decal,
      ),
      RedactionStyle.pixelate => _pixelateFilter(redaction.blockSize),
    };

    canvas
      ..save()
      ..clipRect(rect)
      ..drawImageRect(
        image,
        source,
        source,
        ui.Paint()
          ..imageFilter = filter
          ..filterQuality = ui.FilterQuality.none,
      )
      ..restore();
  }

  /// Nearest-neighbour downscale followed by an upscale of the same factor:
  /// the round trip throws the detail away and leaves square blocks, with no
  /// offscreen buffer to manage.
  static ui.ImageFilter _pixelateFilter(double blockSize) {
    return ui.ImageFilter.compose(
      outer: ui.ImageFilter.matrix(
        _scaleMatrix(blockSize),
        filterQuality: ui.FilterQuality.none,
      ),
      inner: ui.ImageFilter.matrix(
        _scaleMatrix(1 / blockSize),
        filterQuality: ui.FilterQuality.none,
      ),
    );
  }

  static Float64List _scaleMatrix(double scale) =>
      Float64List.fromList(<double>[
        scale, 0, 0, 0, //
        0, scale, 0, 0,
        0, 0, 1, 0,
        0, 0, 0, 1,
      ]);
}
