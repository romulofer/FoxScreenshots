import 'dart:ui';

/// One non-destructive mark on top of a capture (SPEC §2.2).
///
/// Coordinates are **image pixels**, never logical screen pixels: the same
/// annotation list drives the on-screen preview (scaled down to fit the window)
/// and the full-resolution export, so what the user sees is what gets written.
///
/// Immutable — editing produces a new instance, which is what makes the undo
/// stack in `EditorController` cheap and safe.
sealed class Annotation {
  const Annotation({
    required this.id,
    required this.color,
    required this.strokeWidth,
  });

  /// Drags shorter than this are treated as a stray click, not a shape.
  static const double minDragExtent = 4;

  final String id;
  final Color color;

  /// Stroke width in image pixels.
  final double strokeWidth;

  /// The annotation as it should look with the pointer now at [point].
  ///
  /// Called on every pointer move while the user is still drawing.
  Annotation dragTo(Offset point);

  /// Whether the finished gesture is worth keeping. A click that never moved
  /// would otherwise leave an invisible zero-size shape in the document.
  bool get isMeaningful;

  /// Painted extent, stroke included. Used to decide what needs repainting and
  /// to hit-test in tests.
  Rect get bounds;
}

/// Shared base for the annotations defined by two drag corners.
sealed class ShapeAnnotation extends Annotation {
  const ShapeAnnotation({
    required super.id,
    required super.color,
    required super.strokeWidth,
    required this.start,
    required this.end,
  });

  final Offset start;
  final Offset end;

  /// Normalized rectangle spanned by the two corners (any drag direction).
  Rect get rect => Rect.fromPoints(start, end);

  @override
  Rect get bounds => rect.inflate(strokeWidth);

  @override
  bool get isMeaningful =>
      rect.width >= Annotation.minDragExtent ||
      rect.height >= Annotation.minDragExtent;
}

/// Straight line with a solid head at [end].
class ArrowAnnotation extends ShapeAnnotation {
  const ArrowAnnotation({
    required super.id,
    required super.color,
    required super.strokeWidth,
    required super.start,
    required super.end,
  });

  /// Head length as a multiple of the stroke width, so a thick arrow keeps its
  /// proportions instead of growing a pin-sized tip.
  static const double headScale = 4;

  double get headLength => strokeWidth * headScale;

  @override
  ArrowAnnotation dragTo(Offset point) => ArrowAnnotation(
    id: id,
    color: color,
    strokeWidth: strokeWidth,
    start: start,
    end: point,
  );

  /// An arrow is about its length, not its bounding box: a perfectly
  /// horizontal one has zero height and still counts.
  @override
  bool get isMeaningful => (end - start).distance >= Annotation.minDragExtent;

  @override
  Rect get bounds => rect.inflate(headLength);
}

/// Hollow rectangle outline.
class RectangleAnnotation extends ShapeAnnotation {
  const RectangleAnnotation({
    required super.id,
    required super.color,
    required super.strokeWidth,
    required super.start,
    required super.end,
  });

  @override
  RectangleAnnotation dragTo(Offset point) => RectangleAnnotation(
    id: id,
    color: color,
    strokeWidth: strokeWidth,
    start: start,
    end: point,
  );
}

/// Hollow ellipse inscribed in the dragged rectangle.
class EllipseAnnotation extends ShapeAnnotation {
  const EllipseAnnotation({
    required super.id,
    required super.color,
    required super.strokeWidth,
    required super.start,
    required super.end,
  });

  @override
  EllipseAnnotation dragTo(Offset point) => EllipseAnnotation(
    id: id,
    color: color,
    strokeWidth: strokeWidth,
    start: start,
    end: point,
  );
}

/// Translucent marker over a region — the content stays readable underneath.
class HighlightAnnotation extends ShapeAnnotation {
  const HighlightAnnotation({
    required super.id,
    required super.color,
    required super.strokeWidth,
    required super.start,
    required super.end,
  });

  /// Marker-pen opacity: enough to tint, not enough to hide the text.
  static const double opacity = 0.35;

  @override
  HighlightAnnotation dragTo(Offset point) => HighlightAnnotation(
    id: id,
    color: color,
    strokeWidth: strokeWidth,
    start: start,
    end: point,
  );

  @override
  Rect get bounds => rect;
}

/// How a [RedactionAnnotation] destroys the pixels underneath.
enum RedactionStyle {
  /// Gaussian blur — softer, still hints at the shape of what was there.
  blur,

  /// Nearest-neighbour downscale — coarse blocks, nothing recoverable by eye.
  pixelate,
}

/// Obscures a region so sensitive data can be shared (SPEC §2.2).
///
/// The redaction is applied when the image is flattened for export, so the
/// original pixels never leave the in-memory document — and the exported PNG
/// has no layer to peel back.
class RedactionAnnotation extends ShapeAnnotation {
  const RedactionAnnotation({
    required super.id,
    required super.color,
    required super.strokeWidth,
    required super.start,
    required super.end,
    required this.style,
  });

  /// Blur sigma / pixel block size as a multiple of the stroke width. Tied to
  /// the stroke slider so the strength picker is the one the user already has.
  static const double strengthScale = 2.5;

  final RedactionStyle style;

  /// Gaussian sigma in image pixels.
  double get sigma => strokeWidth * strengthScale;

  /// Block edge in image pixels; at least 2, or "pixelated" is a no-op.
  double get blockSize {
    final block = strokeWidth * strengthScale;
    return block < 2 ? 2 : block;
  }

  @override
  RedactionAnnotation dragTo(Offset point) => RedactionAnnotation(
    id: id,
    color: color,
    strokeWidth: strokeWidth,
    start: start,
    end: point,
    style: style,
  );

  @override
  Rect get bounds => rect;
}

/// Freehand stroke: the polyline the pointer traced.
class PenAnnotation extends Annotation {
  const PenAnnotation({
    required super.id,
    required super.color,
    required super.strokeWidth,
    required this.points,
  });

  /// Minimum gap between recorded points, in image pixels.
  ///
  /// A pointer that barely moves reports dozens of events per second, and every
  /// one of them would copy the whole polyline — quadratic work, plus a stroke
  /// with thousands of collinear points to paint on each frame and re-paint on
  /// export. Below this distance the move is folded into the previous point.
  static const double minSegment = 1.5;

  final List<Offset> points;

  @override
  PenAnnotation dragTo(Offset point) {
    final last = points.isEmpty ? null : points.last;
    if (last != null && (point - last).distance < minSegment) return this;
    return PenAnnotation(
      id: id,
      color: color,
      strokeWidth: strokeWidth,
      points: [...points, point],
    );
  }

  /// A dot counts: lifting the pen immediately is a legitimate mark, unlike a
  /// zero-size rectangle.
  @override
  bool get isMeaningful => points.isNotEmpty;

  @override
  Rect get bounds {
    if (points.isEmpty) return Rect.zero;
    var rect = Rect.fromPoints(points.first, points.first);
    for (final p in points.skip(1)) {
      rect = rect.expandToInclude(Rect.fromPoints(p, p));
    }
    return rect.inflate(strokeWidth);
  }
}

/// A caption anchored at its top-left corner.
class TextAnnotation extends Annotation {
  const TextAnnotation({
    required super.id,
    required super.color,
    required super.strokeWidth,
    required this.position,
    required this.text,
    required this.fontSize,
  });

  /// Font size derived from the stroke slider, so text scales with the same
  /// control as every other tool.
  static const double fontScale = 6;

  static double fontSizeFor(double strokeWidth) => strokeWidth * fontScale;

  final Offset position;
  final String text;
  final double fontSize;

  /// Text is placed by a single tap and typed in a dialog, so a drag does not
  /// move it.
  @override
  TextAnnotation dragTo(Offset point) => this;

  @override
  bool get isMeaningful => text.trim().isNotEmpty;

  /// Rough box: exact metrics need a laid-out `TextPainter`, which lives in the
  /// painter. Good enough for repaint decisions.
  @override
  Rect get bounds => Rect.fromLTWH(
    position.dx,
    position.dy,
    text.length * fontSize * 0.6,
    fontSize * 1.4,
  );
}

/// Numbered circular badge for step-by-step walkthroughs (1, 2, 3…).
class StepAnnotation extends Annotation {
  const StepAnnotation({
    required super.id,
    required super.color,
    required super.strokeWidth,
    required this.center,
    required this.number,
  });

  /// Badge radius as a multiple of the stroke width.
  static const double radiusScale = 4;

  final Offset center;
  final int number;

  double get radius => strokeWidth * radiusScale;

  /// Placed by a tap, like text.
  @override
  StepAnnotation dragTo(Offset point) => this;

  @override
  bool get isMeaningful => true;

  @override
  Rect get bounds => Rect.fromCircle(center: center, radius: radius);
}
